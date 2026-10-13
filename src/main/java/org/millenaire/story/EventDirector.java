package org.millenaire.story;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.economy.Need;
import org.millenaire.economy.NeedCategory;
import org.millenaire.economy.NeedService;
import org.millenaire.logistics.ContractState;
import org.millenaire.logistics.DeliveryContract;
import org.millenaire.logistics.LogisticsService;
import org.millenaire.perf.plan.CommitBudget;
import org.millenaire.village.Village;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

/**
 * Bounded story director over existing needs/contracts/households/religion signals.
 * Selection is cheap and capped; activation re-validates on the server thread.
 */
public final class EventDirector {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_PER_VILLAGE = 2;
   private static final int MAX_GLOBAL = 12;
   private static final int CANDIDATE_BUDGET = 8;
   private static final long COOLDOWN_TICKS = 24000L;

   private EventDirector() {
   }

   public static void tick(ServerLevel level) {
      StorySavedData data = StorySavedData.get(level);
      VillageManager manager = VillageSavedData.get(level).getVillageManager();
      CommitBudget budget = CommitBudget.get();
      long gameTime = level.getGameTime();

      data.pruneFinished(gameTime);
      if (data.activeCount() >= MAX_GLOBAL) {
         return;
      }

      int candidates = 0;
      int activated = 0;
      for (Village village : manager.getAllVillages()) {
         if (candidates >= CANDIDATE_BUDGET || activated >= 1 || !budget.trySpendCandidate(1)) {
            break;
         }
         if (village.isLoneBuilding()) {
            continue;
         }
         candidates++;
         if (data.countForVillage(village.getId().uuid()) >= MAX_PER_VILLAGE) {
            continue;
         }
         if (gameTime - data.lastStoryGameTime(village.getId().uuid()) < COOLDOWN_TICKS) {
            continue;
         }
         StorylineCandidate candidate = pickCandidate(level, village, data, gameTime);
         if (candidate == null) {
            continue;
         }
         // Re-validate just before activate
         if (!stillValid(level, village, candidate)) {
            continue;
         }
         if (!budget.trySpendCommit(1)) {
            break;
         }
         StorylineInstance inst = new StorylineInstance(
            UUID.randomUUID(),
            village.getId().uuid(),
            candidate.kind(),
            candidate.summary(),
            gameTime,
            gameTime + 48000L,
            candidate.needId(),
            candidate.contractId()
         );
         data.add(inst);
         village.recordChronicleEvent(
            level,
            org.millenaire.village.VillageEventType.STORY,
            "Story: " + candidate.kind(),
            candidate.summary()
         );
         activated++;
         LOGGER.debug("[Story] Activated {} in {}", candidate.kind(), village.getVillageName());
      }
   }

   private static StorylineCandidate pickCandidate(ServerLevel level, Village village, StorySavedData data, long gameTime) {
      NeedService needs = village.getNeedService();
      for (Need need : needs.activeNeeds()) {
         if (need.category() == NeedCategory.SUBSISTENCE && need.remaining() >= 8) {
            if (!data.recentlyTold(village.getId().uuid(), "FOOD_SHORTAGE", gameTime)) {
               return new StorylineCandidate("FOOD_SHORTAGE", "Food shortage: " + need.remaining() + " remaining", need.id(), null);
            }
         }
         if ("missing_iron_for_tools".equals(need.blockedReason())) {
            return new StorylineCandidate("TOOL_BOTTLENECK", "Smith waits on iron for tools", need.id(), null);
         }
      }
      for (DeliveryContract c : LogisticsService.openContractsFor(level, village.getId().uuid())) {
         if (c.state() == ContractState.BLOCKED) {
            return new StorylineCandidate("TRADE_BLOCKED", "Trade blocked: " + c.reason(), null, c.id());
         }
      }
      for (VillagerRecord rec : village.getVillagerRecords().values()) {
         if (rec.isKilled()) {
            continue;
         }
         if (rec.getMentorUuid() == null && rec.getCraftXp("craft") > 0 && rec.getCraftXp("craft") < 20) {
            return new StorylineCandidate("APPRENTICE_NEED", rec.getFirstName() + " needs a mentor", null, null);
         }
      }
      if (village.getReligionTension() > 20) {
         return new StorylineCandidate("FAITH_TENSION", "Religious tension is rising", null, null);
      }
      return null;
   }

   private static boolean stillValid(ServerLevel level, Village village, StorylineCandidate candidate) {
      return switch (candidate.kind()) {
         case "FOOD_SHORTAGE" -> {
            Need need = candidate.needId() != null ? village.getNeedService().get(candidate.needId()) : null;
            yield need != null && need.acceptsDelivery();
         }
         case "TRADE_BLOCKED" -> {
            boolean found = false;
            for (DeliveryContract c : LogisticsService.openContractsFor(level, village.getId().uuid())) {
               if (c.state() == ContractState.BLOCKED) {
                  found = true;
                  break;
               }
            }
            yield found;
         }
         case "FAITH_TENSION" -> village.getReligionTension() > 10;
         default -> true;
      };
   }

   private record StorylineCandidate(String kind, String summary, UUID needId, UUID contractId) {
   }
}
