package org.millenaire.economy;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.GoodAvailabilityHelper;
import org.millenaire.commerce.TradeGoodsLoader;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.logistics.LogisticsService;
import org.millenaire.perf.TickPerfCounters;
import org.millenaire.perf.StaggeredVillageJob;
import org.millenaire.perf.plan.BackgroundWorkGate;
import org.millenaire.perf.plan.CommitBudget;
import org.millenaire.village.Village;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * Server-thread facade for needs + economy planning. Heavy planning is off-tick via {@link EconomyPlanner}.
 */
public final class EconomyService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int SNAPSHOT_STOCK_LINES = 24;
   private static final int SUBMIT_BUDGET = 2;
   private static final int CANDIDATE_BUDGET = 8;
   private static final int COMMIT_BUDGET = 2;

   private EconomyService() {
   }

   public static void onServerStarted(ServerLevel overworld) {
      EconomyPlanner.get().onServerStarted(MillenaireServerConfig.SERVER.economyPlannerThreads.getAsInt());
      LOGGER.debug("[Economy] started session={}", BackgroundWorkGate.get().worldSessionId());
   }

   public static void onServerStopping() {
      EconomyPlanner.get().shutdown();
   }

   public static void tick(ServerLevel level) {
      if (!MillenaireServerConfig.SERVER.economySimulationEnabled.getAsBoolean()) {
         return;
      }
      CommitBudget budget = CommitBudget.get();
      VillageManager manager = VillageSavedData.get(level).getVillageManager();
      long gameTime = level.getGameTime();
      long session = BackgroundWorkGate.get().worldSessionId();

      // Apply results with shared commit budget (server thread only).
      int commits = 0;
      for (EconomyPlanResult result : EconomyPlanner.get().drainResults(COMMIT_BUDGET)) {
         if (commits >= COMMIT_BUDGET || !budget.trySpendCommit(1)) {
            break;
         }
         if (result.worldSessionId() != session) {
            continue;
         }
         applyResult(level, manager, result, gameTime);
         commits++;
         TickPerfCounters.ECONOMY_COMMITS.incrementAndGet();
      }

      // Mark staggered dirty villages (idle cost ~0 when not due).
      int marked = 0;
      for (Village village : manager.getAllVillages()) {
         if (marked >= 4) {
            break;
         }
         if (village.isLoneBuilding()) {
            continue;
         }
         if (StaggeredVillageJob.shouldRun(level, village, 100)) {
            village.getNeedService().tickExpiry(gameTime);
            EconomyPlanner.get().markDirty(village.getId().uuid(), gameTime);
            marked++;
         }
      }

      int submits = 0;
      int candidates = 0;
      for (UUID villageId : EconomyPlanner.get().pollDirtyVillages(CANDIDATE_BUDGET)) {
         if (candidates >= CANDIDATE_BUDGET || submits >= SUBMIT_BUDGET) {
            break;
         }
         if (!budget.trySpendCandidate(1)) {
            break;
         }
         candidates++;
         Village village = findVillage(manager, villageId);
         if (village == null) {
            EconomyPlanner.get().clearDirty(villageId);
            continue;
         }
         if (!EconomyPlanner.get().canSubmit(villageId)) {
            continue;
         }
         EconomySnapshot snap = buildSnapshot(level, manager, village, session, gameTime);
         EconomyPlanner.get().submit(snap);
         submits++;
      }
   }

   public static void onVillageNightEdge(ServerLevel level, Village village) {
      if (!MillenaireServerConfig.SERVER.economySimulationEnabled.getAsBoolean()) {
         return;
      }
      if (village == null || village.isLoneBuilding()) {
         return;
      }
      EconomyPlanner.get().markDirty(village.getId().uuid(), level.getGameTime());
   }

   private static void applyResult(ServerLevel level, VillageManager manager, EconomyPlanResult result, long gameTime) {
      Village village = findVillage(manager, result.villageId());
      if (village == null) {
         return;
      }
      NeedService needs = village.getNeedService();
      for (EconomyPlanResult.NeedMutation mutation : result.needMutations()) {
         Need need = needs.upsert(
            mutation.category(),
            mutation.item(),
            mutation.amount(),
            mutation.priority(),
            mutation.deadlineGameTime(),
            mutation.sourceRef(),
            gameTime
         );
         if (need != null && mutation.blockedReason() != null) {
            need.setBlockedReason(mutation.blockedReason());
         }
      }
      needs.setLastPlan(result.choice(), result.summary(), gameTime);

      if (result.choice() == ProductionChoice.REQUEST_TRADE
         && result.tradePartnerVillageId() != null
         && result.focusItem() != null
         && MillenaireServerConfig.SERVER.logisticsSimulationEnabled.getAsBoolean()) {
         LogisticsService.requestTradeContract(
            level,
            village,
            result.tradePartnerVillageId(),
            result.focusItem(),
            Math.max(1, result.suggestedAmount()),
            gameTime
         );
         org.millenaire.diplomacy.TreatyService.maybeOfferFromEconomy(
            level,
            village,
            result.tradePartnerVillageId(),
            result.focusItem(),
            Math.max(1, result.suggestedAmount()),
            gameTime
         );
      }
      boolean hasFoodNeed = false;
      for (Need n : needs.activeNeeds()) {
         if (n.category() == NeedCategory.SUBSISTENCE) {
            hasFoodNeed = true;
            break;
         }
      }
      village.getBudget()
         .rankProjectsAdvice(hasFoodNeed, village.getPendingProject() != null, village.getReligionTension() > 0);
   }

   private static EconomySnapshot buildSnapshot(
      ServerLevel level,
      VillageManager manager,
      Village village,
      long session,
      long gameTime
   ) {
      BuildingInstance th = village.getTownhall();
      List<EconomySnapshot.StockLine> stocks = new ArrayList<>();
      Map<Item, Integer> targets = TradeGoodsLoader.getTargetQuantities(village.getCultureId());
      int lines = 0;
      // Prefer known targets + a few essentials — avoid full item registry scan.
      List<Item> watch = new ArrayList<>(16);
      watch.add(Items.BREAD);
      watch.add(Items.WHEAT);
      watch.add(Items.IRON_INGOT);
      watch.add(Items.COAL);
      watch.add(Items.OAK_LOG);
      watch.add(Items.COBBLESTONE);
      for (Item item : targets.keySet()) {
         if (!watch.contains(item)) {
            watch.add(item);
         }
         if (watch.size() >= SNAPSHOT_STOCK_LINES) {
            break;
         }
      }
      for (Item item : watch) {
         if (lines >= SNAPSHOT_STOCK_LINES) {
            break;
         }
         int count = th != null && th.getInventory() != null ? th.getInventory().getCount(level, item) : 0;
         int target = targets.getOrDefault(item, 0);
         int reserved = GoodAvailabilityHelper.getConstructionReservedQuantity(item, level, village, null);
         if (count > 0 || target > 0 || reserved > 0) {
            stocks.add(new EconomySnapshot.StockLine(item, count, target, reserved));
            lines++;
         }
      }

      List<EconomySnapshot.NeedLine> needLines = new ArrayList<>();
      for (Need need : village.getNeedService().activeNeeds()) {
         needLines.add(
            new EconomySnapshot.NeedLine(
               need.id(),
               need.category(),
               need.item(),
               need.remaining(),
               need.priority(),
               need.sourceRef(),
               need.blockedReason()
            )
         );
         if (needLines.size() >= 12) {
            break;
         }
      }

      List<EconomySnapshot.PartnerLine> partners = new ArrayList<>();
      int partnerBudget = 4;
      for (Village other : manager.getAllVillages()) {
         if (partnerBudget <= 0) {
            break;
         }
         if (other.getId().equals(village.getId()) || other.isLoneBuilding()) {
            continue;
         }
         int relation = village.getRelation(other.getId());
         if (relation < 0) {
            continue;
         }
         BuildingInstance otherTh = other.getTownhall();
         if (otherTh == null || otherTh.getInventory() == null) {
            continue;
         }
         // Simple complementary pair: they have iron, we need food or vice versa
         int theirIron = otherTh.getInventory().getCount(level, Items.IRON_INGOT);
         int theirBread = otherTh.getInventory().getCount(level, Items.BREAD);
         int ourBread = th != null && th.getInventory() != null ? th.getInventory().getCount(level, Items.BREAD) : 0;
         int ourIron = th != null && th.getInventory() != null ? th.getInventory().getCount(level, Items.IRON_INGOT) : 0;
         if (theirIron >= 8 && ourBread < 16) {
            partners.add(
               new EconomySnapshot.PartnerLine(other.getId().uuid(), Items.IRON_INGOT, theirIron, Items.BREAD, 16, relation)
            );
            partnerBudget--;
         } else if (theirBread >= 16 && ourIron < 8) {
            partners.add(
               new EconomySnapshot.PartnerLine(other.getId().uuid(), Items.BREAD, theirBread, Items.IRON_INGOT, 8, relation)
            );
            partnerBudget--;
         }
      }

      int freeLabor = Math.max(0, village.getVillagerRecords().size() / 4);
      boolean pending = village.getPendingProject() != null
         || (village.getControlledQueue() != null && !village.getControlledQueue().isEmpty());

      return new EconomySnapshot(
         village.getId().uuid(),
         session,
         gameTime,
         level.getDayTime() / 24000L,
         village.getCultureId(),
         village.getVillagerRecords().size(),
         freeLabor,
         pending,
         village.isPlayerControlled(),
         List.copyOf(stocks),
         List.copyOf(needLines),
         List.copyOf(partners)
      );
   }

   private static Village findVillage(VillageManager manager, UUID villageId) {
      for (Village v : manager.getAllVillages()) {
         if (v.getId().uuid().equals(villageId)) {
            return v;
         }
      }
      return null;
   }
}
