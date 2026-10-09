package org.millenaire.worldgen;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.worldgen.activate.SettlementActivator;
import org.millenaire.worldgen.content.WorldgenContentSnapshot;
import org.millenaire.worldgen.materialize.ChunkSettlementMaterializer;
import org.millenaire.worldgen.materialize.MaterializationResumeQueue;
import org.millenaire.worldgen.materialize.MaterializationTickBudget;
import org.millenaire.worldgen.net.RegionalRoadMaterializer;
import org.millenaire.worldgen.plan.SettlementLayoutPlanner;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;
import org.millenaire.worldgen.plan.SettlementPlanStatus;
import org.millenaire.worldgen.plan.SettlementPlanner;
import org.slf4j.Logger;

/**
 * Orchestrates Phase 4 plan → materialize → activate on overworld chunks.
 * Exploration order cannot change settlement identity: region plans are seed-stable.
 * Materialization uses a shared per-tick budget and a resume queue for unfinished work.
 */
public final class WorldgenEngine {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static int activationRoundRobin;

   private WorldgenEngine() {
   }

   public static boolean isEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenEngineEnabled.get());
      } catch (IllegalStateException e) {
         return false;
      }
   }

   public static void onContentLoaded() {
      WorldgenContentSnapshot.rebuildFromLoadedCultures();
   }

   public static void onServerTick(ServerLevel overworld) {
      if (!isEnabled() || overworld == null) {
         return;
      }
      MaterializationTickBudget.ensureTick(overworld.getGameTime());
      SettlementPlanSavedData store = SettlementPlanSavedData.get(overworld);

      drainResumeQueue(overworld, store);
      RegionalRoadMaterializer.tick(overworld, store);
      activateDuePlans(overworld, store);
   }

   private static void drainResumeQueue(ServerLevel level, SettlementPlanSavedData store) {
      int limit = MillenaireServerConfig.SERVER.worldgenResumeQueueBudget.getAsInt();
      List<MaterializationResumeQueue.Item> items = store.resumeQueue().peekRoundRobin(limit);
      for (MaterializationResumeQueue.Item item : items) {
         if (!MaterializationTickBudget.hasOps()) {
            break;
         }
         SettlementPlan plan = store.getPlan(item.planId());
         if (plan == null || plan.status().isTerminalFailure() || plan.activated()) {
            store.resumeQueue().remove(item.planId(), item.chunkPacked());
            continue;
         }
         ChunkPos chunk = item.chunkPos();
         if (!level.hasChunk(chunk.x, chunk.z)) {
            // Wait for natural load — never force-load.
            plan.progressFor(item.chunkPacked()).setBlockReason("chunk_not_loaded");
            continue;
         }
         ChunkSettlementMaterializer.PassResult result = ChunkSettlementMaterializer.materializeChunk(level, chunk, plan);
         if (result == ChunkSettlementMaterializer.PassResult.COMPLETE) {
            maybeQueueActivation(level, plan);
         }
      }
   }

   private static void activateDuePlans(ServerLevel overworld, SettlementPlanSavedData store) {
      List<SettlementPlan> candidates = new ArrayList<>();
      for (SettlementPlan plan : store.allPlans()) {
         if (plan.status() == SettlementPlanStatus.PHYSICAL_COMPLETE || plan.status() == SettlementPlanStatus.ACTIVATING) {
            if (plan.isPhysicallyComplete()) {
               candidates.add(plan);
            }
         }
      }
      if (candidates.isEmpty()) {
         return;
      }
      candidates.sort(Comparator.comparing(SettlementPlan::planId));
      int n = candidates.size();
      int start = Math.floorMod(activationRoundRobin, n);
      for (int i = 0; i < n; i++) {
         if (!MaterializationTickBudget.hasActivationBudget()) {
            break;
         }
         SettlementPlan plan = candidates.get((start + i) % n);
         if (!MaterializationTickBudget.tryConsumeActivationAttempt()) {
            break;
         }
         SettlementActivator.tryActivate(overworld, plan);
         activationRoundRobin = start + i + 1;
      }
   }

   /**
    * Called for newly generated overworld chunks. Must run on the server thread.
    * Existing loaded chunks with unfinished work are handled via the resume queue on tick.
    */
   public static void onNewChunk(ServerLevel level, ChunkPos chunk) {
      if (!isEnabled()) {
         return;
      }
      if (level.dimension() != Level.OVERWORLD) {
         return;
      }
      MaterializationTickBudget.ensureTick(level.getGameTime());
      if (WorldgenContentSnapshot.current() == null) {
         WorldgenContentSnapshot.rebuildFromLoadedCultures();
      }

      SettlementPlan plan = SettlementPlanner.ensureRegionPlan(level, chunk);
      if (plan == null || plan.status().isTerminalFailure()) {
         return;
      }

      if (plan.status() == SettlementPlanStatus.PLANNED || plan.buildings().isEmpty()) {
         if (!plan.status().isTerminalFailure()) {
            boolean laidOut = SettlementLayoutPlanner.ensureLayout(level, plan);
            if (!laidOut && plan.status().isTerminalFailure()) {
               SettlementPlanSavedData.get(level).markDirtyPlan();
               return;
            }
         }
      }

      if (!plan.intersectsChunk(chunk) && !plan.reservedChunks().contains(chunk.toLong())) {
         // Still allow resume for plans already tracking this chunk.
         SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
         for (SettlementPlan touching : store.plansTouchingChunk(chunk)) {
            scheduleOrMaterialize(level, chunk, touching);
         }
         return;
      }

      scheduleOrMaterialize(level, chunk, plan);

      // Expand hamlet plans as child region candidates when parent is reserved/active
      if (!plan.hamlets().isEmpty()
         && (plan.status() == SettlementPlanStatus.RESERVED
            || plan.status() == SettlementPlanStatus.MATERIALIZING
            || plan.status() == SettlementPlanStatus.PHYSICAL_COMPLETE
            || plan.status() == SettlementPlanStatus.ACTIVE)) {
         expandHamlets(level, plan);
      }
   }

   /**
    * Hook for existing chunks that reload with unfinished settlement work.
    */
   public static void onChunkAvailable(ServerLevel level, ChunkPos chunk) {
      if (!isEnabled() || level.dimension() != Level.OVERWORLD) {
         return;
      }
      MaterializationTickBudget.ensureTick(level.getGameTime());
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      for (SettlementPlan plan : store.plansTouchingChunk(chunk)) {
         if (plan.status().isTerminalFailure() || plan.activated()) {
            continue;
         }
         if (plan.isChunkFullyMaterialized(chunk.toLong())) {
            continue;
         }
         if (plan.status() == SettlementPlanStatus.MATERIALIZING
            || plan.status() == SettlementPlanStatus.RESERVED
            || plan.status() == SettlementPlanStatus.PLANNED
            || !plan.chunkProgress().isEmpty()) {
            scheduleOrMaterialize(level, chunk, plan);
         }
      }
   }

   /**
    * Explicitly drive layout + queue/materialize for already-loaded footprint chunks.
    * Used by colonization so progress does not wait for a fresh chunk-load event.
    */
   public static void kickPlanExecution(ServerLevel level, SettlementPlan plan) {
      if (level == null || plan == null || plan.status().isTerminalFailure() || plan.activated()) {
         return;
      }
      MaterializationTickBudget.ensureTick(level.getGameTime());
      if (plan.status() == SettlementPlanStatus.PLANNED || plan.buildings().isEmpty()) {
         SettlementLayoutPlanner.ensureLayout(level, plan);
      }
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      int queued = 0;
      for (Long packed : plan.reservedChunks()) {
         ChunkPos chunk = new ChunkPos(packed);
         if (!level.hasChunk(chunk.x, chunk.z)) {
            continue;
         }
         if (plan.isChunkFullyMaterialized(packed)) {
            continue;
         }
         scheduleOrMaterialize(level, chunk, plan);
         queued++;
         if (!MaterializationTickBudget.hasOps()) {
            break;
         }
      }
      // Also touch the centre chunk if reserved set is empty after a failed layout.
      if (queued == 0 && plan.center() != null) {
         ChunkPos centre = new ChunkPos(plan.center());
         if (level.hasChunk(centre.x, centre.z) && !plan.isChunkFullyMaterialized(centre.toLong())) {
            scheduleOrMaterialize(level, centre, plan);
         }
      }
      store.markDirtyPlan();
   }

   private static void scheduleOrMaterialize(ServerLevel level, ChunkPos chunk, SettlementPlan plan) {
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      long packed = chunk.toLong();
      if (plan.isChunkFullyMaterialized(packed)) {
         store.resumeQueue().remove(plan.planId(), packed);
         maybeQueueActivation(level, plan);
         return;
      }
      store.resumeQueue().offer(plan.planId(), packed);
      if (!MaterializationTickBudget.hasOps()) {
         plan.progressFor(packed).setBlockReason("budget_exhausted");
         store.markDirtyPlan();
         return;
      }
      ChunkSettlementMaterializer.PassResult result = ChunkSettlementMaterializer.materializeChunk(level, chunk, plan);
      ChunkSettlementMaterializer.refreshBuildingCompletion(plan);
      if (result == ChunkSettlementMaterializer.PassResult.COMPLETE) {
         maybeQueueActivation(level, plan);
      }
   }

   private static void maybeQueueActivation(ServerLevel level, SettlementPlan plan) {
      if (!plan.isPhysicallyComplete()) {
         return;
      }
      if (plan.status() != SettlementPlanStatus.MATERIALIZING && plan.status() != SettlementPlanStatus.PHYSICAL_COMPLETE) {
         return;
      }
      if (plan.status() != SettlementPlanStatus.PHYSICAL_COMPLETE) {
         plan.forceStatus(SettlementPlanStatus.PHYSICAL_COMPLETE);
         SettlementPlanSavedData.get(level).markDirtyPlan();
      }
      // Activation is budgeted on the tick path — do not bypass via direct onNewChunk activation.
   }

   private static void expandHamlets(ServerLevel level, SettlementPlan parent) {
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      for (var hamlet : parent.hamlets()) {
         if (hamlet.status().isTerminalFailure() || hamlet.status() == SettlementPlanStatus.ACTIVE) {
            continue;
         }
         SettlementPlan existing = store.getPlan(hamlet.hamletPlanId());
         if (existing != null) {
            continue;
         }
         int regionSize = MillenaireServerConfig.SERVER.worldgenRegionSizeChunks.getAsInt();
         var region = org.millenaire.worldgen.plan.SettlementRegionKey.fromBlock(
            hamlet.center().getX(), hamlet.center().getZ(), regionSize
         );
         if (store.getByRegion(region) != null) {
            continue;
         }
         SettlementPlan child = new SettlementPlan(
            hamlet.hamletPlanId(),
            hamlet.hamletPlanId(),
            region,
            parent.worldSeed(),
            parent.cultureId(),
            hamlet.villageTypeId(),
            hamlet.center(),
            parent.orientation(),
            40,
            Math.max(0, parent.initialCompletionPercent() / 2),
            false,
            parent.planId(),
            parent.plannedName()
         );
         child.reserveFootprintChunks();
         child.trySetStatus(SettlementPlanStatus.PLANNED);
         store.putPlan(child);
         hamlet.setStatus(SettlementPlanStatus.PLANNED);
         if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenDetailedDiagnostics.get())) {
            LOGGER.info("[Millenaire] Queued hamlet plan {} for parent {}", child.planId(), parent.planId());
         }
      }
   }
}
