package org.millenaire.civilization.settlement;

import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import org.millenaire.building.BuildingInventory;
import org.millenaire.civilization.CivilizationConfig;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.civilization.population.MigrationTransactionService;
import org.millenaire.civilization.population.PopulationAggregator;
import org.millenaire.civilization.population.PopulationSnapshot;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.events.LivingEventSource;
import org.millenaire.living.events.LivingEventType;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;
import org.millenaire.worldgen.plan.SettlementPlanStatus;
import org.millenaire.worldgen.plan.SettlementPlanner;
import org.millenaire.worldgen.plan.SettlementRegionKey;
import org.slf4j.Logger;

/**
 * Bounded autonomous founding using existing worldgen planning + migration.
 * Default-off master switch. Never invents free residents or free building goods.
 */
public final class ColonizationService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MIN_RESIDENTS = 12;
   private static final int MIN_OVERCROWD = 2;
   private static final int WOOD_COST = 32;
   private static final int COBBLE_COST = 32;
   private static final int MIGRANTS_PER_FOUNDING = 2;

   private ColonizationService() {
   }

   public static void tick(ServerLevel level, CivilizationSavedData state) {
      if (!Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationAutonomousFoundingEnabled.get())) {
         return;
      }
      ColonizationSavedData store = ColonizationSavedData.get(level);
      advanceExisting(level, store);
      proposeNew(level, state, store);
      store.setDirty();
   }

   private static void proposeNew(ServerLevel level, CivilizationSavedData state, ColonizationSavedData store) {
      int maxOps = Math.min(
         CivilizationConfig.MAX_COLONIZATION_OPS_PER_CYCLE,
         MillenaireServerConfig.SERVER.civilizationColonizationOpsBudget.getAsInt()
      );
      int maxCandidates = Math.min(
         CivilizationConfig.MAX_COLONIZATION_CANDIDATES_PER_CYCLE,
         MillenaireServerConfig.SERVER.civilizationColonizationCandidateBudget.getAsInt()
      );
      if (maxOps <= 0) {
         return;
      }
      List<Village> villages = new ArrayList<>(VillageSavedData.get(level).getVillageManager().getAllVillages());
      villages.sort(Comparator.comparing(v -> v.getId().uuid()));
      if (villages.isEmpty()) {
         return;
      }
      int n = villages.size();
      int start = Math.floorMod(state.colonizationCursor(), n);
      int scanned = 0;
      int created = 0;
      for (int i = 0; i < n && scanned < maxCandidates && created < maxOps; i++) {
         scanned++;
         Village parent = villages.get((start + i) % n);
         String block = evaluateParent(level, parent, store);
         if (block != null) {
            state.updateStateMarker("colonize-block:" + parent.getId().uuid(), block);
            continue;
         }
         ColonizationOperation op = tryFound(level, parent, store);
         if (op != null) {
            created++;
            state.updateStateMarker("colonize-plan:" + op.operationId(), "planned:" + op.planId());
         }
      }
      state.setColonizationCursor(start + Math.max(1, scanned));
   }

   private static String evaluateParent(ServerLevel level, Village parent, ColonizationSavedData store) {
      if (parent.getFactionId() == null) {
         return "no_faction";
      }
      if (isAtWar(level, parent.getFactionId())) {
         return "at_war";
      }
      for (ColonizationOperation op : store.operations().values()) {
         if (op.isActive() && op.parentVillageId().equals(parent.getId().uuid())) {
            return "already_colonizing";
         }
      }
      String cd = "colonize-cd:" + parent.getId().uuid();
      long cooldown = MillenaireServerConfig.SERVER.civilizationColonizationCooldownDays.getAsInt() * 24000L;
      if (CivilizationSavedData.get(level).isPublished(cd, level.getGameTime(), cooldown)) {
         return "cooldown";
      }
      PopulationSnapshot pop = PopulationAggregator.aggregateVillage(parent, level.getGameTime());
      if (pop.residents() < MIN_RESIDENTS) {
         return "population_low";
      }
      if (pop.residents() < pop.housingCapacity() + MIN_OVERCROWD && pop.housingCapacity() > 0) {
         // Prefer expansion under housing pressure; also allow large villages with spare workers.
         if (pop.residents() < MIN_RESIDENTS + 8) {
            return "no_housing_pressure";
         }
      }
      if (parent.getVillageItemCount(level, Items.OAK_LOG) + parent.getVillageItemCount(level, Items.SPRUCE_LOG)
         + parent.getVillageItemCount(level, Items.BIRCH_LOG) < WOOD_COST) {
         return "insufficient_wood";
      }
      if (parent.getVillageItemCount(level, Items.COBBLESTONE) < COBBLE_COST) {
         return "insufficient_stone";
      }
      return null;
   }

   private static ColonizationOperation tryFound(ServerLevel level, Village parent, ColonizationSavedData store) {
      int regionSize = MillenaireServerConfig.SERVER.worldgenRegionSizeChunks.getAsInt();
      int spacing = Math.max(
         MillenaireServerConfig.SERVER.minVillageDistance.getAsInt(),
         MillenaireServerConfig.SERVER.worldgenMinimumSettlementSpacing.getAsInt()
      );
      SettlementPlanSavedData plans = SettlementPlanSavedData.get(level);
      // Search nearby region offsets deterministically.
      int parentRx = parent.getCenter().getX() >> 4;
      int parentRz = parent.getCenter().getZ() >> 4;
      SettlementRegionKey parentRegion = SettlementRegionKey.fromChunk(new ChunkPos(parentRx, parentRz), regionSize);
      List<SettlementRegionKey> candidates = new ArrayList<>();
      for (int dr = -2; dr <= 2; dr++) {
         for (int dz = -2; dz <= 2; dz++) {
            if (dr == 0 && dz == 0) {
               continue;
            }
            candidates.add(new SettlementRegionKey(parentRegion.regionX() + dr, parentRegion.regionZ() + dz));
         }
      }
      candidates.sort(Comparator.comparingLong(r -> (long)r.regionX() * 31 + r.regionZ()));
      for (SettlementRegionKey region : candidates) {
         if (plans.getByRegion(region) != null) {
            continue;
         }
         int cx = region.regionX() * regionSize * 16 + regionSize * 8;
         int cz = region.regionZ() * regionSize * 16 + regionSize * 8;
         BlockPos approx = new BlockPos(cx, parent.getCenter().getY(), cz);
         if (plans.hasActiveOrPlannedNear(new SettlementPlanSavedData.BlockPosApprox(approx.getX(), approx.getZ()), spacing)) {
            continue;
         }
         ChunkPos chunk = new ChunkPos(approx);
         // Only create a plan when the destination chunk is already available — otherwise wait.
         if (!level.hasChunk(chunk.x, chunk.z)) {
            continue;
         }
         SettlementPlan plan = SettlementPlanner.ensureRegionPlan(level, chunk);
         if (plan == null || plan.status().isTerminalFailure() || plan.loneBuilding()) {
            continue;
         }
         if (!reserveCosts(level, parent)) {
            stateBlock(level, parent, "reserve_failed");
            return null;
         }
         UUID opId = UUID.nameUUIDFromBytes(
            ("colonize:" + parent.getId().uuid() + ":" + plan.planId()).getBytes(StandardCharsets.UTF_8)
         );
         if (store.get(opId) != null) {
            return store.get(opId);
         }
         ColonizationOperation op = new ColonizationOperation(
            opId,
            parent.getId().uuid(),
            parent.getFactionId().asString(),
            plan.planId(),
            plan.center(),
            level.getGameTime()
         );
         op.setResourcesReserved(true);
         op.setReason("founded_from_housing_pressure");
         op.setStatus(ColonizationOperation.Status.AWAITING_CHUNKS);
         store.put(op);
         CivilizationSavedData.get(level).tryMarkPublished(
            "colonize-cd:" + parent.getId().uuid(),
            level.getGameTime(),
            MillenaireServerConfig.SERVER.civilizationColonizationCooldownDays.getAsInt() * 24000L
         );
         LOGGER.info(
            "[Millenaire] Colonization planned parent={} plan={} at {}",
            parent.getId().uuid(),
            plan.planId(),
            plan.center().toShortString()
         );
         return op;
      }
      stateBlock(level, parent, "no_valid_site");
      return null;
   }

   private static void advanceExisting(ServerLevel level, ColonizationSavedData store) {
      for (ColonizationOperation op : List.copyOf(store.operations().values())) {
         if (op.isTerminal()) {
            continue;
         }
         SettlementPlan plan = SettlementPlanSavedData.get(level).getPlan(op.planId());
         if (plan == null || plan.status().isTerminalFailure()) {
            refundIfNeeded(level, op);
            op.setStatus(ColonizationOperation.Status.FAILED);
            op.setReason(plan == null ? "plan_missing" : plan.failureDetail());
            continue;
         }
         if (plan.status() == SettlementPlanStatus.MATERIALIZING || plan.status() == SettlementPlanStatus.PHYSICAL_COMPLETE) {
            op.setStatus(ColonizationOperation.Status.MATERIALIZING);
         }
         if (plan.status() == SettlementPlanStatus.ACTIVE || plan.activated()) {
            op.setStatus(ColonizationOperation.Status.ACTIVATED);
            migrateFounders(level, op, plan);
         }
      }
   }

   private static void migrateFounders(ServerLevel level, ColonizationOperation op, SettlementPlan plan) {
      Village parent = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(op.parentVillageId()));
      Village child = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(plan.villageUuid()));
      if (parent == null || child == null) {
         op.setReason("village_not_ready");
         return;
      }
      int moved = 0;
      List<UUID> candidates = new ArrayList<>();
      for (VillagerRecord record : parent.getVillagerRecords().values()) {
         if (MigrationTransactionService.isEligibleForPermanentMigration(level, parent, record)) {
            candidates.add(record.getUuid());
         }
      }
      candidates.sort(Comparator.naturalOrder());
      for (UUID id : candidates) {
         if (moved >= MIGRANTS_PER_FOUNDING) {
            break;
         }
         UUID mig = MigrationTransactionService.migrate(level, parent.getId(), child.getId(), id);
         if (mig != null) {
            moved++;
         }
      }
      if (moved > 0) {
         op.setStatus(ColonizationOperation.Status.MIGRATED);
         op.setReason("migrated=" + moved);
         LivingEventService.emit(
            level,
            LivingEventType.VILLAGE_FOUNDED,
            LivingEventSource.SYSTEM,
            child,
            null,
            null,
            "autonomous_colony parent=" + parent.getId().uuid() + " migrants=" + moved,
            "public",
            "colonize:" + op.operationId()
         );
      } else {
         op.setReason("no_migrants_moved");
         // Keep ACTIVATED — colony exists; migration may retry next cycle via housing evaluator.
         op.setStatus(ColonizationOperation.Status.ACTIVATED);
      }
   }

   private static boolean reserveCosts(ServerLevel level, Village parent) {
      // Spend real inventory (not abstract balances). Fail closed if not enough.
      int woodRemoved = removeAnyLogs(level, parent, WOOD_COST);
      if (woodRemoved < WOOD_COST) {
         addLogsBack(level, parent, woodRemoved);
         return false;
      }
      int stone = removeItem(level, parent, Items.COBBLESTONE, COBBLE_COST);
      if (stone < COBBLE_COST) {
         addItem(level, parent, Items.COBBLESTONE, stone);
         addLogsBack(level, parent, woodRemoved);
         return false;
      }
      return true;
   }

   private static void refundIfNeeded(ServerLevel level, ColonizationOperation op) {
      if (!op.resourcesReserved()) {
         return;
      }
      Village parent = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(op.parentVillageId()));
      if (parent == null) {
         return;
      }
      addLogsBack(level, parent, WOOD_COST);
      addItem(level, parent, Items.COBBLESTONE, COBBLE_COST);
      op.setResourcesReserved(false);
   }

   private static int removeAnyLogs(ServerLevel level, Village village, int amount) {
      int left = amount;
      left -= removeItem(level, village, Items.OAK_LOG, left);
      if (left > 0) {
         left -= removeItem(level, village, Items.SPRUCE_LOG, left);
      }
      if (left > 0) {
         left -= removeItem(level, village, Items.BIRCH_LOG, left);
      }
      return amount - left;
   }

   private static void addLogsBack(ServerLevel level, Village village, int amount) {
      addItem(level, village, Items.OAK_LOG, amount);
   }

   private static int removeItem(ServerLevel level, Village village, Item item, int amount) {
      int left = amount;
      for (var building : village.getBuildings()) {
         if (left <= 0) {
            break;
         }
         BuildingInventory inv = building.getInventory();
         if (inv == null) {
            continue;
         }
         left -= inv.remove(level, item, left);
      }
      return amount - left;
   }

   private static void addItem(ServerLevel level, Village village, Item item, int amount) {
      int left = amount;
      for (var building : village.getBuildings()) {
         if (left <= 0) {
            break;
         }
         BuildingInventory inv = building.getInventory();
         if (inv == null) {
            continue;
         }
         int added = inv.add(level, item, left);
         left -= added;
      }
   }

   private static void stateBlock(ServerLevel level, Village parent, String reason) {
      CivilizationSavedData.get(level).updateStateMarker("colonize-block:" + parent.getId().uuid(), reason);
   }

   private static boolean isAtWar(ServerLevel level, FactionId factionId) {
      if (factionId == null) {
         return false;
      }
      for (Faction other : FactionSavedData.get(level).getFactionManager().getAllFactions()) {
         if (!other.getId().equals(factionId) && WarSavedData.get(level).getWarManager().areAtWar(factionId, other.getId())) {
            return true;
         }
      }
      return false;
   }
}
