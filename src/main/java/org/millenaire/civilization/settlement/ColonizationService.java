package org.millenaire.civilization.settlement;

import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import org.millenaire.building.AnywoodHelper;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.civilization.CivilizationConfig;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.civilization.population.MigrationResult;
import org.millenaire.civilization.population.MigrationTransactionService;
import org.millenaire.civilization.population.PopulationAggregator;
import org.millenaire.civilization.population.PopulationSnapshot;
import org.millenaire.civilization.settlement.ColonizationOperation.FounderSlot;
import org.millenaire.civilization.settlement.ColonizationOperation.FounderState;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.living.events.LivingEvent;
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
 *
 * <p>Consumption policy: reserved stacks are held until physical materialization begins
 * ({@link ColonizationOperation#costsConsumed()}). Full refund applies only when founding
 * fails before that transition. Incomplete refunds persist as {@code refundOutstanding}.
 */
public final class ColonizationService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MIN_RESIDENTS = 12;
   private static final int MIN_OVERCROWD = 2;
   private static final int FALLBACK_WOOD_COST = 32;
   private static final int FALLBACK_COBBLE_COST = 32;
   private static final int MIGRANTS_PER_FOUNDING = 2;
   private static final int MAX_ADVANCE_PER_TICK = 4;
   private static final int MAX_CANDIDATES_PER_TICK = 16;
   private static final int MAX_REFUNDS_PER_TICK = 4;
   private static final int MAX_REFUND_STACKS_PER_TICK = 16;
   private static final int MAX_FOUNDER_SCAN = 48;
   private static final long TEMP_BLOCK_BACKOFF = 20L * 60L * 2L; // 2 minutes
   private static final long SUCCESS_MIG_COOLDOWN = 24000L;

   private ColonizationService() {
   }

   public static void tick(ServerLevel level, CivilizationSavedData state) {
      ColonizationSavedData store = ColonizationSavedData.get(level);
      store.reconcileOrphanReservations();
      // Refunds always run — independent of new founding / worldgen switches.
      processRefunds(level, store);
      boolean foundingEnabled = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationAutonomousFoundingEnabled.get());
      if (!WorldgenEngine.isEnabled()) {
         state.updateStateMarker("colonize-worldgen", "blocked_worldgen_disabled");
         // No physical worldgen, but always continue migration bookkeeping / safe admin recovery.
         advanceExisting(level, store, false);
         store.setDirty();
         return;
      }
      if (foundingEnabled) {
         advanceExisting(level, store, true);
         proposeNew(level, state, store);
      } else {
         // Config off: no new proposals; continue safe handling of active ops + refunds only.
         advanceExisting(level, store, true);
         state.updateStateMarker("colonize-config", "new_proposals_disabled");
      }
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
         Village parent = villages.get(Math.floorMod(start + i, n));
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
      int logs = 0;
      for (var building : parent.getBuildings()) {
         BuildingInventory inv = building.getInventory();
         if (inv != null) {
            logs += inv.getCountByTag(level, AnywoodHelper.LOGS_TAG);
         }
      }
      if (logs < FALLBACK_WOOD_COST) {
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
         if (parent.getCultureId() != null && plan.cultureId() != null && !parent.getCultureId().equals(plan.cultureId())) {
            continue;
         }
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
         // Persist before cost take so incomplete rollback cannot vanish with a false return.
         store.put(op);

         Map<ResourceLocation, Integer> costs = computeFoundingCosts(plan);
         if (!reserveExactCosts(level, parent, op, costs, store)) {
            op.setStatus(ColonizationOperation.Status.FAILED);
            op.setReason(op.refundOutstanding().isEmpty() ? "insufficient_resources" : "insufficient_resources;refund_outstanding");
            store.put(op);
            stateBlock(level, parent, "insufficient_resources");
            return null;
         }
         op.setStatus(ColonizationOperation.Status.RESERVED);
         revalidateAndReserveFounders(level, parent, op, store, null);
         publishTransition(level, parent, op, LivingEventType.SETTLEMENT_DEVELOPMENT, "proposal_reserved", "colonize-reserved:" + op.operationId());

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

   private static void advanceExisting(ServerLevel level, ColonizationSavedData store, boolean allowWorldgenKick) {
      List<ColonizationOperation> ordered = new ArrayList<>();
      for (ColonizationOperation op : store.operations().values()) {
         if (!op.isTerminal()) {
            ordered.add(op);
         }
      }
      ordered.sort(Comparator.comparing(ColonizationOperation::operationId));
      if (ordered.isEmpty()) {
         return;
      }
      int n = ordered.size();
      int start = Math.floorMod(store.advanceCursor(), n);
      int examined = 0;
      int advanced = 0;
      for (int i = 0; i < n && examined < MAX_CANDIDATES_PER_TICK && advanced < MAX_ADVANCE_PER_TICK; i++) {
         int idx = Math.floorMod(start + i, n);
         ColonizationOperation op = ordered.get(idx);
         examined++;
         boolean progressed = advanceOne(level, store, op, allowWorldgenKick);
         store.put(op);
         if (progressed) {
            advanced++;
         }
      }
      store.setAdvanceCursor(start + Math.max(1, examined));
   }

   private static boolean advanceOne(
      ServerLevel level,
      ColonizationSavedData store,
      ColonizationOperation op,
      boolean allowWorldgenKick
   ) {
      Village parent = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(op.parentVillageId()));
      if (parent == null) {
         failOp(level, store, op, "parent_vanished");
         return true;
      }
      FactionId factionId = FactionId.tryParse(op.factionId());
      if (factionId == null || FactionSavedData.get(level).getFactionManager().getFaction(factionId) == null) {
         failOp(level, store, op, "faction_missing");
         return true;
      }

      SettlementPlan plan = SettlementPlanSavedData.get(level).getPlan(op.planId());
      if (plan == null || plan.status().isTerminalFailure()) {
         failOp(level, store, op, plan == null ? "plan_missing" : plan.failureDetail());
         return true;
      }

      if (!plan.isColonizationFounding()) {
         plan.setFoundingMode(SettlementPlan.FoundingMode.COLONIZATION);
         plan.setFoundingFactionId(op.factionId());
         SettlementPlanSavedData.get(level).markDirtyPlan();
      }

      String priorReason = op.reason();
      boolean mutated = false;

      if (op.status() == ColonizationOperation.Status.LAYOUT
         || (op.status() == ColonizationOperation.Status.AWAITING_CHUNKS && plan.buildings().isEmpty())) {
         SettlementLayoutPlanner.ensureLayout(level, plan);
         if (plan.buildings().isEmpty() || plan.status().isTerminalFailure()) {
            setWaitingReason(op, "layout_pending_or_failed");
            maybePublishReasonChange(level, parent, op, priorReason);
            return false;
         }
         if (!op.resourcesReserved() || op.reservedStacks().isEmpty()) {
            Map<ResourceLocation, Integer> costs = computeFoundingCosts(plan);
            if (!reserveExactCosts(level, parent, op, costs, store)) {
               failOp(level, store, op, op.refundOutstanding().isEmpty() ? "insufficient_resources" : "insufficient_resources;refund_outstanding");
               return true;
            }
         }
         revalidateAndReserveFounders(level, parent, op, store, null);
         op.setStatus(ColonizationOperation.Status.RESERVED);
         mutated = true;
      }

      if (op.status() == ColonizationOperation.Status.RESERVED
         || op.status() == ColonizationOperation.Status.MATERIALIZING
         || op.status() == ColonizationOperation.Status.AWAITING_CHUNKS) {
         ChunkPos centre = new ChunkPos(plan.center());
         if (!level.hasChunk(centre.x, centre.z)) {
            setWaitingReason(op, "chunk_not_loaded");
            op.setStatus(ColonizationOperation.Status.AWAITING_CHUNKS);
            maybePublishReasonChange(level, parent, op, priorReason);
            return mutated;
         }
         if (allowWorldgenKick) {
            WorldgenEngine.kickPlanExecution(level, plan);
         }
         if (plan.status() == SettlementPlanStatus.MATERIALIZING
            || plan.status() == SettlementPlanStatus.PHYSICAL_COMPLETE
            || plan.status() == SettlementPlanStatus.ACTIVATING) {
            op.setStatus(ColonizationOperation.Status.MATERIALIZING);
            if (!op.costsConsumed()) {
               // Levy is consumed when physical build actually starts — not merely on RESERVED.
               op.setCostsConsumed(true);
               op.reservedStacks().clear();
               op.setResourcesReserved(false);
            }
            mutated = true;
         }
         if (plan.status() == SettlementPlanStatus.MATERIALIZING) {
            setWaitingReason(op, "materializing");
         }
      }

      if (plan.status() == SettlementPlanStatus.ACTIVE || plan.activated()) {
         if (op.status() != ColonizationOperation.Status.MIGRATED) {
            op.setStatus(ColonizationOperation.Status.MIGRATING);
            migrateFounders(level, store, op, plan);
            mutated = true;
         }
      }

      maybePublishReasonChange(level, parent, op, priorReason);
      return mutated || !op.reason().equals(priorReason);
   }

   private static void processRefunds(ServerLevel level, ColonizationSavedData store) {
      List<ColonizationOperation> refundable = new ArrayList<>();
      for (ColonizationOperation op : store.operations().values()) {
         if (op.needsRefundWork()) {
            refundable.add(op);
         }
      }
      refundable.sort(Comparator.comparing(ColonizationOperation::operationId));
      if (refundable.isEmpty()) {
         return;
      }
      int n = refundable.size();
      int start = Math.floorMod(store.refundCursor(), n);
      int examined = 0;
      int handled = 0;
      int stacksWorked = 0;
      for (int i = 0; i < n && examined < MAX_CANDIDATES_PER_TICK && handled < MAX_REFUNDS_PER_TICK && stacksWorked < MAX_REFUND_STACKS_PER_TICK; i++) {
         ColonizationOperation op = refundable.get(Math.floorMod(start + i, n));
         examined++;
         int budgetLeft = MAX_REFUND_STACKS_PER_TICK - stacksWorked;
         int worked = tryRefundBudgeted(level, store, op, budgetLeft);
         // Failed attempts still consume budget.
         stacksWorked += Math.max(1, worked);
         store.put(op);
         handled++;
      }
      store.setRefundCursor(start + Math.max(1, examined));
   }

   private static void migrateFounders(
      ServerLevel level,
      ColonizationSavedData store,
      ColonizationOperation op,
      SettlementPlan plan
   ) {
      Village parent = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(op.parentVillageId()));
      Village child = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(plan.villageUuid()));
      if (parent == null || child == null) {
         setWaitingReason(op, "village_not_ready");
         return;
      }

      revalidateAndReserveFounders(level, parent, op, store, child);
      if (op.migratedFounders().size() >= op.founderTarget()) {
         completeFounding(level, store, op, parent, child);
         return;
      }
      if (op.intendedFounders().isEmpty() && op.migratedFounders().size() < op.founderTarget()) {
         setWaitingReason(op, "no_suitable_founders");
         op.setStatus(ColonizationOperation.Status.ACTIVATED);
         return;
      }

      int need = op.founderTarget() - op.migratedFounders().size();
      for (FounderSlot slot : List.copyOf(op.founderSlots())) {
         if (need <= 0) {
            break;
         }
         if (slot.state() == FounderState.MIGRATED || op.migratedFounders().contains(slot.villagerId())) {
            continue;
         }
         if (slot.state() == FounderState.UNSUITABLE) {
            continue;
         }
         if (slot.state() == FounderState.TEMP_BLOCKED && slot.blockedUntil() > level.getGameTime()) {
            setWaitingReason(op, slot.reason().isEmpty() ? "founder_temp_blocked" : slot.reason());
            continue;
         }
         // Already at destination from a prior partial run.
         if (child.getVillagerRecords().containsKey(slot.villagerId())) {
            markMigrated(store, op, slot);
            need--;
            publishMigration(level, child, op, slot.villagerId(), parent);
            continue;
         }
         if (!parent.getVillagerRecords().containsKey(slot.villagerId())) {
            slot.setState(FounderState.UNSUITABLE);
            slot.setReason("founder_missing");
            store.releaseFounder(slot.villagerId(), op.operationId());
            continue;
         }
         MigrationResult result = MigrationTransactionService.migrate(level, parent.getId(), child.getId(), slot.villagerId());
         if (result.completed()) {
            markMigrated(store, op, slot);
            need--;
            publishMigration(level, child, op, slot.villagerId(), parent);
         } else if (!result.retryable()) {
            slot.setState(FounderState.UNSUITABLE);
            slot.setReason("migration_failed:" + result.reasonKey());
            store.releaseFounder(slot.villagerId(), op.operationId());
            setWaitingReason(op, "migration_failed:" + result.reasonKey());
         } else {
            slot.setState(FounderState.TEMP_BLOCKED);
            slot.setBlockedUntil(level.getGameTime() + TEMP_BLOCK_BACKOFF);
            slot.setReason(result.reasonKey() == null ? "founder_temp_blocked" : result.reasonKey());
            setWaitingReason(op, slot.reason());
         }
      }

      // Replace permanently unsuitable slots if still short.
      if (op.migratedFounders().size() < op.founderTarget()) {
         revalidateAndReserveFounders(level, parent, op, store, child);
      }

      if (op.migratedFounders().size() >= op.founderTarget()) {
         completeFounding(level, store, op, parent, child);
      } else {
         setWaitingReason(op, "partial_migrants=" + op.migratedFounders().size() + "/" + op.founderTarget());
         op.setStatus(ColonizationOperation.Status.MIGRATING);
      }
   }

   private static void markMigrated(ColonizationSavedData store, ColonizationOperation op, FounderSlot slot) {
      slot.setState(FounderState.MIGRATED);
      slot.setReason("migrated");
      if (!op.migratedFounders().contains(slot.villagerId())) {
         op.migratedFounders().add(slot.villagerId());
      }
      store.releaseFounder(slot.villagerId(), op.operationId());
   }

   private static void completeFounding(
      ServerLevel level,
      ColonizationSavedData store,
      ColonizationOperation op,
      Village parent,
      Village child
   ) {
      op.setStatus(ColonizationOperation.Status.MIGRATED);
      op.setReason("migrated=" + op.migratedFounders().size());
      store.releaseAllForOperation(op.operationId());
      if (!op.foundingEventPublished()) {
         LivingEvent event = LivingEventService.emit(
            level,
            LivingEventType.VILLAGE_FOUNDED,
            LivingEventSource.SYSTEM,
            child,
            null,
            null,
            "autonomous_colony parent="
               + safeVillageName(parent)
               + " migrants="
               + op.migratedFounders().size()
               + "/"
               + op.founderTarget(),
            "public",
            "colonize:" + op.operationId()
         );
         if (event != null) {
            op.setFoundingEventPublished(true);
         }
      }
      // Reserved levy already consumed during materialization, or cleared here if never started physically.
      if (!op.costsConsumed()) {
         op.reservedStacks().clear();
         op.setResourcesReserved(false);
         op.setCostsConsumed(true);
      }
   }

   private static void publishMigration(
      ServerLevel level,
      Village child,
      ColonizationOperation op,
      UUID villagerId,
      Village parent
   ) {
      LivingEventService.emit(
         level,
         LivingEventType.MIGRATION,
         LivingEventSource.SYSTEM,
         child,
         villagerId,
         null,
         "founder from=" + safeVillageName(parent) + " to=" + safeVillageName(child),
         "public",
         "colonize-mig:" + op.operationId() + ":" + villagerId
      );
   }

   /**
    * Revalidates intended/reserved founders, replaces permanently unsuitable ones,
    * and maintains exclusive reservations in {@link ColonizationSavedData}.
    */
   static void revalidateAndReserveFounders(
      ServerLevel level,
      Village parent,
      ColonizationOperation op,
      ColonizationSavedData store,
      @Nullable Village destination
   ) {
      // Preserve migrated records; drop unsuitable non-migrated.
      List<FounderSlot> keep = new ArrayList<>();
      Set<UUID> seen = new HashSet<>();
      for (FounderSlot slot : op.founderSlots()) {
         if (slot.state() == FounderState.MIGRATED || op.migratedFounders().contains(slot.villagerId())) {
            slot.setState(FounderState.MIGRATED);
            keep.add(slot);
            seen.add(slot.villagerId());
            store.releaseFounder(slot.villagerId(), op.operationId());
            continue;
         }
         if (destination != null && destination.getVillagerRecords().containsKey(slot.villagerId())) {
            slot.setState(FounderState.MIGRATED);
            if (!op.migratedFounders().contains(slot.villagerId())) {
               op.migratedFounders().add(slot.villagerId());
            }
            keep.add(slot);
            seen.add(slot.villagerId());
            store.releaseFounder(slot.villagerId(), op.operationId());
            continue;
         }
         FounderVerdict verdict = classifyFounder(level, parent, store, op, slot.villagerId());
         if (verdict == FounderVerdict.OK || verdict == FounderVerdict.TEMP) {
            if (verdict == FounderVerdict.TEMP) {
               slot.setState(FounderState.TEMP_BLOCKED);
               if (slot.blockedUntil() <= level.getGameTime()) {
                  slot.setBlockedUntil(level.getGameTime() + TEMP_BLOCK_BACKOFF);
               }
               slot.setReason(slot.reason().isEmpty() ? "founder_temp_blocked" : slot.reason());
            } else {
               slot.setState(FounderState.RESERVED);
               slot.setReason("");
            }
            if (store.reserveFounder(slot.villagerId(), op.operationId())) {
               keep.add(slot);
               seen.add(slot.villagerId());
            } else {
               slot.setState(FounderState.UNSUITABLE);
               slot.setReason("founder_reserved_elsewhere");
               store.releaseFounder(slot.villagerId(), op.operationId());
            }
         } else {
            slot.setState(FounderState.UNSUITABLE);
            slot.setReason(verdict.reason);
            store.releaseFounder(slot.villagerId(), op.operationId());
         }
      }
      op.founderSlots().clear();
      op.founderSlots().addAll(keep);

      int need = op.founderTarget() - op.migratedFounders().size() - countActiveSlots(op);
      if (need <= 0) {
         return;
      }
      List<UUID> candidates = new ArrayList<>();
      int scanned = 0;
      List<VillagerRecord> records = new ArrayList<>(parent.getVillagerRecords().values());
      records.sort(Comparator.comparing(VillagerRecord::getUuid));
      for (VillagerRecord record : records) {
         if (scanned >= MAX_FOUNDER_SCAN) {
            break;
         }
         scanned++;
         UUID id = record.getUuid();
         if (seen.contains(id) || op.migratedFounders().contains(id)) {
            continue;
         }
         if (CivilizationSavedData.get(level).isPublished("mig-cd:" + id, level.getGameTime(), SUCCESS_MIG_COOLDOWN)) {
            continue;
         }
         FounderVerdict verdict = classifyFounder(level, parent, store, op, id);
         if (verdict != FounderVerdict.OK) {
            continue;
         }
         candidates.add(id);
      }
      candidates.sort(Comparator.naturalOrder());
      for (UUID id : candidates) {
         if (need <= 0) {
            break;
         }
         if (!store.reserveFounder(id, op.operationId())) {
            continue;
         }
         FounderSlot slot = new FounderSlot(id, FounderState.RESERVED);
         op.founderSlots().add(slot);
         need--;
      }
      if (need > 0 && countActiveSlots(op) + op.migratedFounders().size() < op.founderTarget()) {
         setWaitingReason(op, "no_suitable_founders");
      }
   }

   private static int countActiveSlots(ColonizationOperation op) {
      int n = 0;
      for (FounderSlot slot : op.founderSlots()) {
         if (slot.state() == FounderState.RESERVED || slot.state() == FounderState.INTENDED || slot.state() == FounderState.TEMP_BLOCKED) {
            n++;
         }
      }
      return n;
   }

   private enum FounderVerdict {
      OK(""),
      TEMP("founder_temp_blocked"),
      BAD("founder_unsuitable");

      final String reason;

      FounderVerdict(String reason) {
         this.reason = reason;
      }
   }

   private static FounderVerdict classifyFounder(
      ServerLevel level,
      Village parent,
      ColonizationSavedData store,
      ColonizationOperation op,
      UUID villagerId
   ) {
      if (store.isFounderReservedByOther(villagerId, op.operationId())) {
         return FounderVerdict.BAD;
      }
      VillagerRecord record = parent.getVillagerRecord(villagerId);
      if (record == null || record.isKilled()) {
         return FounderVerdict.BAD;
      }
      if (!MigrationTransactionService.isEligibleForPermanentMigration(level, parent, record)) {
         // Hired/raiding may be temporary.
         if (record.getHiredBy() != null || record.isAwayHired() || record.isAwayRaiding() || record.isRaidingVillage()) {
            return FounderVerdict.TEMP;
         }
         return FounderVerdict.BAD;
      }
      return FounderVerdict.OK;
   }

   /** Exposed for MigrationEvaluator / transaction checks. */
   public static boolean isReservedFounder(ServerLevel level, UUID villagerId) {
      if (level == null || villagerId == null) {
         return false;
      }
      return ColonizationSavedData.get(level).reservationHolder(villagerId) != null;
   }

   public static boolean isReservedFounderForOther(ServerLevel level, UUID villagerId, @Nullable UUID allowOperationId) {
      if (level == null || villagerId == null) {
         return false;
      }
      UUID holder = ColonizationSavedData.get(level).reservationHolder(villagerId);
      if (holder == null) {
         return false;
      }
      return allowOperationId == null || !holder.equals(allowOperationId);
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
         costs.put(AnywoodHelper.ANYWOOD_LOG, FALLBACK_WOOD_COST);
         costs.put(BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE), FALLBACK_COBBLE_COST);
      }
      return costs;
   }

   /**
    * Authoritative exact-stack reservation. On partial failure, all taken stacks are rolled back;
    * any leftover that cannot be reinserted is persisted on the operation as refundOutstanding
    * (operation must already be in the store).
    */
   private static boolean reserveExactCosts(
      ServerLevel level,
      Village parent,
      ColonizationOperation op,
      Map<ResourceLocation, Integer> costs,
      ColonizationSavedData store
   ) {
      if (op.resourcesReserved() && !op.reservedStacks().isEmpty()) {
         return true;
      }
      List<ItemStack> taken = new ArrayList<>();
      for (Entry<ResourceLocation, Integer> e : costs.entrySet()) {
         if (e.getValue() <= 0) {
            continue;
         }
         int need = e.getValue();
         BuildingInventory.RemoveStacksResult result;
         if (AnywoodHelper.isAnywood(e.getKey()) || e.getKey().equals(BuiltInRegistries.ITEM.getKey(Items.OAK_LOG))) {
            result = removeAnyLogsExact(level, parent, need);
         } else {
            Item item = BuiltInRegistries.ITEM.get(e.getKey());
            if (item == null || item == Items.AIR) {
               continue;
            }
            result = removeItemExact(level, parent, item, need);
         }
         taken.addAll(result.stacks());
         if (!result.fullySatisfied()) {
            List<ItemStack> leftover = rollbackStacksExact(level, parent, taken);
            op.reservedStacks().clear();
            if (!leftover.isEmpty()) {
               op.refundOutstanding().addAll(leftover);
               op.setResourcesReserved(true);
               op.setReason("insufficient_resources;refund_outstanding");
            } else {
               op.setResourcesReserved(false);
            }
            store.put(op);
            return false;
         }
      }
      op.setReservedStacks(taken);
      op.setResourcesReserved(true);
      store.put(op);
      return true;
   }

   private static BuildingInventory.RemoveStacksResult removeAnyLogsExact(ServerLevel level, Village village, int amount) {
      List<ItemStack> taken = new ArrayList<>();
      int left = amount;
      for (var building : village.getBuildings()) {
         if (left <= 0) {
            break;
         }
         BuildingInventory inv = building.getInventory();
         if (inv == null) {
            continue;
         }
         BuildingInventory.RemoveStacksResult part = inv.removeByTagExact(level, AnywoodHelper.LOGS_TAG, left, Set.of());
         taken.addAll(part.stacks());
         left = part.unsatisfied();
      }
      int removed = taken.stream().mapToInt(ItemStack::getCount).sum();
      return new BuildingInventory.RemoveStacksResult(removed, Math.max(0, amount - removed), taken);
   }

   private static BuildingInventory.RemoveStacksResult removeItemExact(ServerLevel level, Village village, Item item, int amount) {
      List<ItemStack> taken = new ArrayList<>();
      int left = amount;
      for (var building : village.getBuildings()) {
         if (left <= 0) {
            break;
         }
         BuildingInventory inv = building.getInventory();
         if (inv == null) {
            continue;
         }
         BuildingInventory.RemoveStacksResult part = inv.removeByItemExact(level, item, left);
         taken.addAll(part.stacks());
         left = part.unsatisfied();
      }
      int removed = taken.stream().mapToInt(ItemStack::getCount).sum();
      return new BuildingInventory.RemoveStacksResult(removed, Math.max(0, amount - removed), taken);
   }

   private static void failOp(ServerLevel level, ColonizationSavedData store, ColonizationOperation op, String reason) {
      op.setStatus(ColonizationOperation.Status.FAILED);
      op.setReason(reason == null ? "failed" : reason);
      store.releaseAllForOperation(op.operationId());
      tryRefund(level, store, op);
      Village parent = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(op.parentVillageId()));
      if (parent != null) {
         publishTransition(level, parent, op, LivingEventType.SETTLEMENT_DEVELOPMENT, "failed:" + op.reason(), "colonize-fail:" + op.operationId());
      }
   }

   private static void tryRefund(ServerLevel level, ColonizationSavedData store, ColonizationOperation op) {
      tryRefundBudgeted(level, store, op, MAX_REFUND_STACKS_PER_TICK);
   }

   /**
    * @return number of stack insert attempts performed (successful or not)
    */
   private static int tryRefundBudgeted(ServerLevel level, ColonizationSavedData store, ColonizationOperation op, int stackBudget) {
      if (stackBudget <= 0) {
         return 0;
      }
      if (op.costsConsumed()) {
         op.reservedStacks().clear();
         if (op.refundOutstanding().isEmpty()) {
            op.setResourcesReserved(false);
         }
         return 1;
      }
      Village parent = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(op.parentVillageId()));
      if (parent == null) {
         return 1;
      }
      List<ItemStack> pending = new ArrayList<>();
      if (!op.refundOutstanding().isEmpty()) {
         pending.addAll(op.refundOutstanding());
      } else {
         pending.addAll(op.reservedStacks());
         op.reservedStacks().clear();
      }
      if (pending.isEmpty()) {
         op.setResourcesReserved(false);
         return 1;
      }
      int n = pending.size();
      int start = Math.floorMod(op.refundStackCursor(), n);
      List<ItemStack> leftover = new ArrayList<>();
      int attempts = 0;
      int nextCursor = 0;
      for (int i = 0; i < n; i++) {
         int idx = Math.floorMod(start + i, n);
         ItemStack stack = pending.get(idx);
         if (stack == null || stack.isEmpty()) {
            continue;
         }
         if (attempts >= stackBudget) {
            leftover.add(stack.copy());
            if (leftover.size() == 1) {
               nextCursor = leftover.size() - 1;
            }
            continue;
         }
         attempts++;
         int left = addExactStack(level, parent, stack.copy());
         if (left > 0) {
            ItemStack rem = stack.copy();
            rem.setCount(left);
            leftover.add(rem);
         }
      }
      op.refundOutstanding().clear();
      if (leftover.isEmpty()) {
         op.setRefundStackCursor(0);
         op.setResourcesReserved(false);
         if (op.reason().contains("refund_outstanding")) {
            op.setReason(op.reason().replace(";refund_outstanding", "").replace("refund_outstanding", "refund_complete"));
         }
      } else {
         op.refundOutstanding().addAll(leftover);
         // Advance so the next pass does not always retry the same full chest first.
         op.setRefundStackCursor(Math.floorMod(nextCursor + 1, leftover.size()));
         op.setResourcesReserved(true);
         if (!op.reason().contains("refund_outstanding")) {
            op.setReason((op.reason().isEmpty() ? "" : op.reason() + ";") + "refund_outstanding");
         }
      }
      store.put(op);
      return Math.max(1, attempts);
   }

   /** @return stacks that could not be reinserted */
   private static List<ItemStack> rollbackStacksExact(ServerLevel level, Village village, List<ItemStack> stacks) {
      List<ItemStack> leftover = new ArrayList<>();
      for (ItemStack stack : stacks) {
         int left = addExactStack(level, village, stack.copy());
         if (left > 0) {
            ItemStack rem = stack.copy();
            rem.setCount(left);
            leftover.add(rem);
         }
      }
      return leftover;
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

   private static void setWaitingReason(ColonizationOperation op, String reason) {
      if (reason != null && !reason.isEmpty()) {
         op.setReason(reason);
      }
   }

   private static void maybePublishReasonChange(ServerLevel level, Village village, ColonizationOperation op, String priorReason) {
      if (op.reason().isEmpty() || op.reason().equals(priorReason) || op.reason().equals(op.lastPublishedReason())) {
         return;
      }
      // Skip noisy per-tick chunk waits once already published for this reason.
      if (("chunk_not_loaded".equals(op.reason()) || "materializing".equals(op.reason()))
         && op.reason().equals(op.lastPublishedReason())) {
         return;
      }
      publishTransition(
         level,
         village,
         op,
         LivingEventType.SETTLEMENT_DEVELOPMENT,
         "status=" + op.statusCode() + " reason=" + op.reason(),
         "colonize-reason:" + op.operationId() + ":" + op.reason()
      );
   }

   private static void publishTransition(
      ServerLevel level,
      Village village,
      ColonizationOperation op,
      LivingEventType type,
      String details,
      String dedupeKey
   ) {
      LivingEvent event = LivingEventService.emit(
         level,
         type,
         LivingEventSource.SYSTEM,
         village,
         null,
         null,
         details,
         "public",
         dedupeKey
      );
      if (event != null) {
         op.setLastPublishedReason(op.reason());
         if (details != null && details.startsWith("proposal")) {
            op.setProposalEventPublished(true);
         }
         if (details != null && details.contains("reserved")) {
            op.setReservedEventPublished(true);
         }
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

   private static String safeVillageName(Village village) {
      if (village == null) {
         return "unknown";
      }
      String name = village.getVillageName();
      return name == null || name.isBlank() ? "village" : name;
   }

   /** Read-only helpers for presentation / actions. */
   @Nullable
   public static ColonizationOperation findActiveForParent(ServerLevel level, UUID parentVillageId) {
      if (level == null || parentVillageId == null) {
         return null;
      }
      ColonizationSavedData store = ColonizationSavedData.get(level);
      ColonizationOperation best = null;
      for (ColonizationOperation op : store.operations().values()) {
         if (!parentVillageId.equals(op.parentVillageId())) {
            continue;
         }
         if (op.isActive() || op.needsRefundWork()) {
            if (best == null || op.createdAtGameTime() > best.createdAtGameTime()) {
               best = op;
            }
         }
      }
      return best;
   }

   public static List<ColonizationOperation> listForVillage(ServerLevel level, UUID villageId, int limit) {
      List<ColonizationOperation> out = new ArrayList<>();
      if (level == null || villageId == null || limit <= 0) {
         return out;
      }
      ColonizationSavedData store = ColonizationSavedData.get(level);
      List<ColonizationOperation> ordered = new ArrayList<>(store.operations().values());
      ordered.sort(Comparator.comparingLong(ColonizationOperation::createdAtGameTime).reversed());
      for (ColonizationOperation op : ordered) {
         if (villageId.equals(op.parentVillageId()) || matchesChild(level, op, villageId)) {
            out.add(op);
            if (out.size() >= limit) {
               break;
            }
         }
      }
      return out;
   }

   private static boolean matchesChild(ServerLevel level, ColonizationOperation op, UUID villageId) {
      SettlementPlan plan = SettlementPlanSavedData.get(level).getPlan(op.planId());
      return plan != null && villageId.equals(plan.villageUuid());
   }
}
