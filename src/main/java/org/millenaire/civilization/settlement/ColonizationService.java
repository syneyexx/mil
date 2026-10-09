package org.millenaire.civilization.settlement;

import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.civilization.CivilizationConfig;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.civilization.population.MigrationResult;
import org.millenaire.civilization.population.MigrationTransactionService;
import org.millenaire.civilization.population.PopulationAggregator;
import org.millenaire.civilization.population.PopulationSnapshot;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
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
import org.millenaire.worldgen.WorldgenEngine;
import org.millenaire.worldgen.plan.PlannedBuilding;
import org.millenaire.worldgen.plan.SettlementLayoutPlanner;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;
import org.millenaire.worldgen.plan.SettlementPlanStatus;
import org.millenaire.worldgen.plan.SettlementPlanner;
import org.millenaire.worldgen.plan.SettlementRegionKey;
import org.slf4j.Logger;

/**
 * Bounded autonomous founding using existing worldgen planning + migration.
 * Default-off master switch. Explicit lifecycle: layout → costs → materialize → activate →
 * faction assign → migrate founders. Never invents free residents or free building goods.
 *
 * <p>Founding levy: sum of level-0 {@code requiredResources} for planned buildings
 * (from BuildingCostCalculator content). If that sum is empty, a documented minimum levy of
 * 32 any-log + 32 cobblestone applies so founding is never free.
 */
public final class ColonizationService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MIN_RESIDENTS = 12;
   private static final int MIN_OVERCROWD = 2;
   private static final int FALLBACK_WOOD_COST = 32;
   private static final int FALLBACK_COBBLE_COST = 32;
   private static final int MIGRANTS_PER_FOUNDING = 2;
   private static final int MAX_ADVANCE_PER_TICK = 4;

   private ColonizationService() {
   }

   public static void tick(ServerLevel level, CivilizationSavedData state) {
      if (!Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationAutonomousFoundingEnabled.get())) {
         return;
      }
      if (!WorldgenEngine.isEnabled()) {
         // Explicit block — do not leave silent dead operations when worldgen is off.
         state.updateStateMarker("colonize-worldgen", "blocked_worldgen_disabled");
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
         if (pop.residents() < MIN_RESIDENTS + 8) {
            return "no_housing_pressure";
         }
      }
      // Soft pre-check only — authoritative reserve happens after layout with exact costs.
      if (parent.getVillageItemCount(level, Items.OAK_LOG) + parent.getVillageItemCount(level, Items.SPRUCE_LOG)
         + parent.getVillageItemCount(level, Items.BIRCH_LOG) < FALLBACK_WOOD_COST) {
         return "insufficient_wood";
      }
      if (parent.getVillageItemCount(level, Items.COBBLESTONE) < FALLBACK_COBBLE_COST) {
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
         if (!level.hasChunk(chunk.x, chunk.z)) {
            continue;
         }
         SettlementPlan plan = SettlementPlanner.ensureRegionPlan(level, chunk);
         if (plan == null || plan.status().isTerminalFailure() || plan.loneBuilding()) {
            continue;
         }
         // Culture continuity: colony must match parent culture when the planner picked differently.
         if (parent.getCultureId() != null && plan.cultureId() != null && !parent.getCultureId().equals(plan.cultureId())) {
            continue;
         }
         // Verify real centre chunk and footprint availability — not only the approximate region centre.
         ChunkPos realCentre = new ChunkPos(plan.center());
         if (!level.hasChunk(realCentre.x, realCentre.z)) {
            continue;
         }

         UUID opId = UUID.nameUUIDFromBytes(
            ("colonize:" + parent.getId().uuid() + ":" + plan.planId()).getBytes(StandardCharsets.UTF_8)
         );
         if (store.get(opId) != null) {
            return store.get(opId);
         }

         plan.setFoundingMode(SettlementPlan.FoundingMode.COLONIZATION);
         plan.setFoundingFactionId(parent.getFactionId().asString());
         // Drive layout immediately — do not wait for a new chunk event.
         boolean laidOut = SettlementLayoutPlanner.ensureLayout(level, plan);
         if (!laidOut || plan.status().isTerminalFailure() || plan.buildings().isEmpty()) {
            plans.markDirtyPlan();
            continue;
         }
         plans.markDirtyPlan();

         ColonizationOperation op = new ColonizationOperation(
            opId,
            parent.getId().uuid(),
            parent.getFactionId().asString(),
            plan.planId(),
            plan.center(),
            level.getGameTime()
         );
         op.setFounderTarget(MIGRANTS_PER_FOUNDING);
         op.setReason("founded_from_housing_pressure");
         op.setStatus(ColonizationOperation.Status.LAYOUT);

         Map<ResourceLocation, Integer> costs = computeFoundingCosts(plan);
         if (!reserveExactCosts(level, parent, op, costs)) {
            stateBlock(level, parent, "insufficient_resources");
            return null;
         }
         op.setStatus(ColonizationOperation.Status.RESERVED);
         selectIntendedFounders(level, parent, op);

         WorldgenEngine.kickPlanExecution(level, plan);
         op.setStatus(ColonizationOperation.Status.MATERIALIZING);
         store.put(op);

         CivilizationSavedData.get(level).tryMarkPublished(
            "colonize-cd:" + parent.getId().uuid(),
            level.getGameTime(),
            MillenaireServerConfig.SERVER.civilizationColonizationCooldownDays.getAsInt() * 24000L
         );
         LOGGER.info(
            "[Millenaire] Colonization reserved parent={} plan={} buildings={} at {}",
            parent.getId().uuid(),
            plan.planId(),
            plan.buildings().size(),
            plan.center().toShortString()
         );
         return op;
      }
      stateBlock(level, parent, "no_valid_site");
      return null;
   }

   private static void advanceExisting(ServerLevel level, ColonizationSavedData store) {
      int advanced = 0;
      List<ColonizationOperation> ordered = new ArrayList<>(store.operations().values());
      ordered.sort(Comparator.comparing(ColonizationOperation::operationId));
      for (ColonizationOperation op : ordered) {
         if (op.isTerminal()) {
            // Still drain outstanding refunds.
            if (!op.refundOutstanding().isEmpty()) {
               tryRefund(level, op);
               store.put(op);
            }
            continue;
         }
         if (advanced >= MAX_ADVANCE_PER_TICK) {
            break;
         }
         advanced++;

         Village parent = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(op.parentVillageId()));
         if (parent == null) {
            failOp(level, op, "parent_vanished");
            store.put(op);
            continue;
         }
         FactionId factionId = FactionId.tryParse(op.factionId());
         if (factionId == null || FactionSavedData.get(level).getFactionManager().getFaction(factionId) == null) {
            failOp(level, op, "faction_missing");
            store.put(op);
            continue;
         }

         SettlementPlan plan = SettlementPlanSavedData.get(level).getPlan(op.planId());
         if (plan == null || plan.status().isTerminalFailure()) {
            failOp(level, op, plan == null ? "plan_missing" : plan.failureDetail());
            store.put(op);
            continue;
         }

         // Keep colonization context on the plan across reloads.
         if (!plan.isColonizationFounding()) {
            plan.setFoundingMode(SettlementPlan.FoundingMode.COLONIZATION);
            plan.setFoundingFactionId(op.factionId());
            SettlementPlanSavedData.get(level).markDirtyPlan();
         }

         if (op.status() == ColonizationOperation.Status.LAYOUT
            || (op.status() == ColonizationOperation.Status.AWAITING_CHUNKS && plan.buildings().isEmpty())) {
            SettlementLayoutPlanner.ensureLayout(level, plan);
            if (plan.buildings().isEmpty() || plan.status().isTerminalFailure()) {
               op.setReason("layout_pending_or_failed");
               store.put(op);
               continue;
            }
            if (!op.resourcesReserved()) {
               Map<ResourceLocation, Integer> costs = computeFoundingCosts(plan);
               if (!reserveExactCosts(level, parent, op, costs)) {
                  failOp(level, op, "insufficient_resources");
                  store.put(op);
                  continue;
               }
            }
            selectIntendedFounders(level, parent, op);
            op.setStatus(ColonizationOperation.Status.RESERVED);
         }

         if (op.status() == ColonizationOperation.Status.RESERVED
            || op.status() == ColonizationOperation.Status.MATERIALIZING
            || op.status() == ColonizationOperation.Status.AWAITING_CHUNKS) {
            WorldgenEngine.kickPlanExecution(level, plan);
            if (plan.status() == SettlementPlanStatus.MATERIALIZING
               || plan.status() == SettlementPlanStatus.PHYSICAL_COMPLETE
               || plan.status() == SettlementPlanStatus.ACTIVATING) {
               op.setStatus(ColonizationOperation.Status.MATERIALIZING);
               if (!op.costsConsumed()) {
                  op.setCostsConsumed(true); // physical build consumes the reserved levy
               }
            }
         }

         if (plan.status() == SettlementPlanStatus.ACTIVE || plan.activated()) {
            if (op.status() != ColonizationOperation.Status.MIGRATED) {
               op.setStatus(ColonizationOperation.Status.MIGRATING);
               migrateFounders(level, op, plan);
            }
         }
         store.put(op);
      }
   }

   private static void migrateFounders(ServerLevel level, ColonizationOperation op, SettlementPlan plan) {
      Village parent = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(op.parentVillageId()));
      Village child = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(plan.villageUuid()));
      if (parent == null || child == null) {
         op.setReason("village_not_ready");
         return;
      }

      if (op.intendedFounders().isEmpty()) {
         selectIntendedFounders(level, parent, op);
      }
      if (op.intendedFounders().isEmpty()) {
         op.setReason("no_suitable_founders");
         op.setStatus(ColonizationOperation.Status.ACTIVATED);
         return;
      }

      int need = op.founderTarget() - op.migratedFounders().size();
      for (UUID id : List.copyOf(op.intendedFounders())) {
         if (need <= 0) {
            break;
         }
         if (op.migratedFounders().contains(id)) {
            continue;
         }
         // Already at destination from a prior partial run.
         if (child.getVillagerRecords().containsKey(id)) {
            op.migratedFounders().add(id);
            need--;
            continue;
         }
         if (!parent.getVillagerRecords().containsKey(id)) {
            continue;
         }
         MigrationResult result = MigrationTransactionService.migrate(level, parent.getId(), child.getId(), id);
         if (result.completed()) {
            op.migratedFounders().add(id);
            need--;
         } else if (!result.retryable()) {
            op.setReason("migration_failed:" + result.reasonKey());
         }
      }

      if (op.migratedFounders().size() >= op.founderTarget()) {
         op.setStatus(ColonizationOperation.Status.MIGRATED);
         op.setReason("migrated=" + op.migratedFounders().size());
         if (!op.foundingEventPublished()) {
            LivingEventService.emit(
               level,
               LivingEventType.VILLAGE_FOUNDED,
               LivingEventSource.SYSTEM,
               child,
               null,
               null,
               "autonomous_colony parent=" + parent.getId().uuid() + " migrants=" + op.migratedFounders().size(),
               "public",
               "colonize:" + op.operationId()
            );
            op.setFoundingEventPublished(true);
         }
         // Reserved levy consumed by founding — clear reservation without refund.
         op.reservedStacks().clear();
         op.setResourcesReserved(false);
         op.setCostsConsumed(true);
      } else {
         op.setReason("partial_migrants=" + op.migratedFounders().size() + "/" + op.founderTarget());
         op.setStatus(ColonizationOperation.Status.MIGRATING);
      }
   }

   private static void selectIntendedFounders(ServerLevel level, Village parent, ColonizationOperation op) {
      if (!op.intendedFounders().isEmpty()) {
         return;
      }
      List<UUID> candidates = new ArrayList<>();
      for (VillagerRecord record : parent.getVillagerRecords().values()) {
         if (MigrationTransactionService.isEligibleForPermanentMigration(level, parent, record)) {
            candidates.add(record.getUuid());
         }
      }
      candidates.sort(Comparator.naturalOrder());
      for (UUID id : candidates) {
         if (op.intendedFounders().size() >= op.founderTarget()) {
            break;
         }
         op.intendedFounders().add(id);
      }
   }

   private static Map<ResourceLocation, Integer> computeFoundingCosts(SettlementPlan plan) {
      Map<ResourceLocation, Integer> costs = new LinkedHashMap<>();
      for (PlannedBuilding building : plan.buildings()) {
         if (building.planSetId() == null) {
            continue;
         }
         BuildingPlanSet set = ModCultures.getBuildingPlanSet(building.planSetId());
         if (set == null) {
            continue;
         }
         BuildingPlanSet.LevelDef level0 = set.getLevel(building.variant(), Math.max(0, building.level()));
         if (level0 == null || level0.requiredResources() == null) {
            continue;
         }
         for (Entry<ResourceLocation, Integer> e : level0.requiredResources().entrySet()) {
            costs.merge(e.getKey(), e.getValue(), Integer::sum);
         }
      }
      if (costs.isEmpty()) {
         // Documented minimum founding levy when content has no requiredResources.
         costs.put(BuiltInRegistries.ITEM.getKey(Items.OAK_LOG), FALLBACK_WOOD_COST);
         costs.put(BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE), FALLBACK_COBBLE_COST);
      }
      return costs;
   }

   private static boolean reserveExactCosts(
      ServerLevel level,
      Village parent,
      ColonizationOperation op,
      Map<ResourceLocation, Integer> costs
   ) {
      if (op.resourcesReserved() && !op.reservedStacks().isEmpty()) {
         return true;
      }
      List<ItemStack> taken = new ArrayList<>();
      for (Entry<ResourceLocation, Integer> e : costs.entrySet()) {
         Item item = BuiltInRegistries.ITEM.get(e.getKey());
         if (item == null || e.getValue() <= 0) {
            continue;
         }
         int need = e.getValue();
         // Any-log flexibility for oak_log entries when stock mixes species.
         if (item == Items.OAK_LOG) {
            List<ItemStack> logs = removeAnyLogsExact(level, parent, need);
            int got = logs.stream().mapToInt(ItemStack::getCount).sum();
            if (got < need) {
               rollbackStacks(level, parent, taken);
               rollbackStacks(level, parent, logs);
               return false;
            }
            taken.addAll(logs);
            continue;
         }
         int removed = removeItem(level, parent, item, need);
         if (removed < need) {
            if (removed > 0) {
               taken.add(new ItemStack(item, removed));
            }
            rollbackStacks(level, parent, taken);
            return false;
         }
         taken.add(new ItemStack(item, removed));
      }
      op.setReservedStacks(taken);
      op.setResourcesReserved(true);
      return true;
   }

   private static List<ItemStack> removeAnyLogsExact(ServerLevel level, Village village, int amount) {
      List<ItemStack> taken = new ArrayList<>();
      int left = amount;
      for (Item item : List.of(Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG)) {
         if (left <= 0) {
            break;
         }
         int removed = removeItem(level, village, item, left);
         if (removed > 0) {
            taken.add(new ItemStack(item, removed));
            left -= removed;
         }
      }
      return taken;
   }

   private static void failOp(ServerLevel level, ColonizationOperation op, String reason) {
      op.setStatus(ColonizationOperation.Status.FAILED);
      op.setReason(reason == null ? "failed" : reason);
      tryRefund(level, op);
   }

   private static void tryRefund(ServerLevel level, ColonizationOperation op) {
      if (op.costsConsumed()) {
         // Physical build already used the levy — do not invent a full refund.
         op.reservedStacks().clear();
         op.setResourcesReserved(false);
         return;
      }
      Village parent = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(op.parentVillageId()));
      if (parent == null) {
         return;
      }
      List<ItemStack> pending = new ArrayList<>();
      if (!op.refundOutstanding().isEmpty()) {
         pending.addAll(op.refundOutstanding());
      } else {
         pending.addAll(op.reservedStacks());
      }
      op.refundOutstanding().clear();
      List<ItemStack> leftover = new ArrayList<>();
      for (ItemStack stack : pending) {
         int left = addExactStack(level, parent, stack.copy());
         if (left > 0) {
            ItemStack rem = stack.copy();
            rem.setCount(left);
            leftover.add(rem);
         }
      }
      op.reservedStacks().clear();
      if (leftover.isEmpty()) {
         op.setResourcesReserved(false);
      } else {
         op.refundOutstanding().addAll(leftover);
         op.setResourcesReserved(true);
         op.setReason((op.reason().isEmpty() ? "" : op.reason() + ";") + "refund_outstanding");
      }
   }

   private static void rollbackStacks(ServerLevel level, Village village, List<ItemStack> stacks) {
      for (ItemStack stack : stacks) {
         addExactStack(level, village, stack.copy());
      }
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

   /** @return remaining count that did not fit */
   private static int addExactStack(ServerLevel level, Village village, ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return 0;
      }
      ItemStack remaining = stack.copy();
      for (var building : village.getBuildings()) {
         if (remaining.isEmpty()) {
            break;
         }
         BuildingInventory inv = building.getInventory();
         if (inv == null) {
            continue;
         }
         BuildingInventory.AddStackResult result = inv.addExactStack(level, remaining);
         remaining = result.remainder();
      }
      return remaining.getCount();
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
