package org.millenaire.worldgen;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.millenaire.civilization.settlement.ColonizationMaterializationGate;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.world.VillageTerrainMapCache;
import org.millenaire.worldgen.activate.SettlementActivator;
import org.millenaire.worldgen.content.WorldgenContentSnapshot;
import org.millenaire.worldgen.materialize.ChunkSettlementMaterializer;
import org.millenaire.worldgen.materialize.CompiledPlacementStepsCache;
import org.millenaire.worldgen.materialize.MaterializationResumeQueue;
import org.millenaire.worldgen.materialize.MaterializationTickBudget;
import org.millenaire.worldgen.net.RegionalRoadMaterializer;
import org.millenaire.worldgen.plan.SettlementLayoutPlanner;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;
import org.millenaire.worldgen.plan.SettlementPlanStatus;
import org.millenaire.worldgen.plan.SettlementPlanner;
import org.millenaire.worldgen.plan.SettlementRegionKey;
import org.millenaire.worldgen.plan.WorldgenLayoutSessions;
import org.millenaire.worldgen.plan.WorldgenPlanningQueue;
import org.millenaire.worldgen.terrain.WorldgenChunkAccess;
import org.slf4j.Logger;

/**
 * Orchestrates Phase 4 plan → materialize → activate on overworld chunks.
 * Chunk loads only enqueue compact work keys; the server tick drains planning,
 * layout, and materialization under shared budgets so exploration stays responsive.
 */
public final class WorldgenEngine {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static int activationRoundRobin;
   private static final Set<UUID> activationWatch = new LinkedHashSet<>();
   /** Chunks already materialized this game tick — avoids TOUCH + resume double work. */
   private static final Set<Long> materializedThisTick = new LinkedHashSet<>();
   private static long materializeDedupeTick = Long.MIN_VALUE;
   /** Cached near-spawn kick list; rebuilt when spawn moves >16 or plan count changes. */
   private static List<SettlementPlan> nearSpawnCache = List.of();
   private static int nearSpawnCacheX = Integer.MIN_VALUE;
   private static int nearSpawnCacheZ = Integer.MIN_VALUE;
   private static int nearSpawnCachePlanCount = -1;
   private static int nearSpawnCacheRadius = -1;
   private static long lastActivationFullScanTick = Long.MIN_VALUE;

   private WorldgenEngine() {
   }

   private static void beginMaterializeTick(long gameTime) {
      if (materializeDedupeTick != gameTime) {
         materializeDedupeTick = gameTime;
         materializedThisTick.clear();
      }
   }

   private static long materializeKey(UUID planId, long chunkPacked) {
      long a = planId.getMostSignificantBits();
      long b = planId.getLeastSignificantBits();
      // Mix plan bits with chunk packed — less collision-prone than plain XOR.
      long h = a * 31L + b;
      h = h * 31L + chunkPacked;
      return h ^ (h >>> 32);
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
      WorldgenContentSnapshot.markReady();
      CompiledPlacementStepsCache.invalidateAll();
      LOGGER.info(
         "[Millenaire] Worldgen engine ready: snapshot villages={} lone={} invalid={}",
         WorldgenContentSnapshot.require().naturalVillages().size(),
         WorldgenContentSnapshot.require().naturalLoneBuildings().size(),
         WorldgenContentSnapshot.require().invalidCount()
      );
   }

   /**
    * After content is ready, drop sticky INVALID plans created before the snapshot had natural types
    * (start-region race) so those regions can be re-planned.
    */
   public static void recoverPrematureRejects(ServerLevel level) {
      if (!isEnabled() || level == null || !WorldgenContentSnapshot.isReady()) {
         return;
      }
      WorldgenContentSnapshot snap = WorldgenContentSnapshot.require();
      if (!snap.hasNaturalContent()) {
         return;
      }
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      int cleared = store.clearPrematureEmptyRejects();
      if (cleared > 0) {
         LOGGER.info(
            "[Millenaire] Cleared {} premature worldgen reject(s) stored before content was ready — regions will re-plan",
            cleared
         );
      }
      int spawnCleared = store.clearSpawnProtectionRejects();
      if (spawnCleared > 0) {
         LOGGER.info(
            "[Millenaire] Cleared {} spawn-protection worldgen reject(s) — regions near spawn will re-plan",
            spawnCleared
         );
      }
   }

   public static void onServerStopping() {
      WorldgenPlanningQueue.get().clear();
      WorldgenLayoutSessions.clear();
      VillageTerrainMapCache.clear();
      CompiledPlacementStepsCache.invalidateAll();
      MaterializationTickBudget.reset();
      activationWatch.clear();
      activationRoundRobin = 0;
      nearSpawnCache = List.of();
      nearSpawnCachePlanCount = -1;
      lastActivationFullScanTick = Long.MIN_VALUE;
      WorldgenContentSnapshot.clearReady();
   }

   public static void onServerTick(ServerLevel overworld) {
      if (!isEnabled() || overworld == null) {
         return;
      }
      MaterializationTickBudget.ensureTick(overworld.getGameTime());
      beginMaterializeTick(overworld.getGameTime());
      SettlementPlanSavedData store = SettlementPlanSavedData.get(overworld);

      drainNearSpawnPriority(overworld, store);
      drainPlanningQueue(overworld, store);
      MaterializationTickBudget.notePhase(MaterializationTickBudget.Phase.PLANNING);
      drainResumeQueue(overworld, store);
      // Sand highways own the inter-settlement network when enabled — avoid dual dirt/sand corridors.
      if (!org.millenaire.worldgen.highway.HighwayEngine.isEnabled()) {
         RegionalRoadMaterializer.tick(overworld, store);
      }
      MaterializationTickBudget.notePhase(MaterializationTickBudget.Phase.MATERIALIZE);
      activateDuePlans(overworld, store);
      MaterializationTickBudget.notePhase(MaterializationTickBudget.Phase.ACTIVATION);

      if (overworld.getGameTime() % 200L == 0L) {
         LOGGER.debug(
            "[Millenaire] Worldgen status {} queue={} resume={} plans={} watch={} {}",
            MaterializationTickBudget.diagnostic(),
            WorldgenPlanningQueue.get().size(),
            store.resumeQueue().size(),
            store.planCount(),
            activationWatch.size(),
            WorldgenMetrics.snapshot()
         );
      }
      if (overworld.getGameTime() % 100L == 0L) {
         VillageTerrainMapCache.purgeStale(overworld.getGameTime());
      }
   }

   /**
    * Prefer finishing settlements near world spawn while their footprint chunks are already loaded.
    * Never force-loads chunks.
    */
   private static void drainNearSpawnPriority(ServerLevel level, SettlementPlanSavedData store) {
      int boostRadius;
      try {
         boostRadius = MillenaireServerConfig.SERVER.nearSpawnBoostRadius.getAsInt();
      } catch (IllegalStateException e) {
         return;
      }
      if (boostRadius <= 0 || !MaterializationTickBudget.hasTime()) {
         return;
      }
      var spawn = level.getSharedSpawnPos();
      List<SettlementPlan> near = nearSpawnPlans(level, store, spawn, boostRadius);
      if (near.isEmpty()) {
         return;
      }
      int kicks = 0;
      int maxKicks = Math.max(2, MillenaireServerConfig.SERVER.worldgenPlanningBudget.getAsInt());
      for (SettlementPlan plan : near) {
         if (kicks >= maxKicks || !MaterializationTickBudget.hasTime()) {
            break;
         }
         if (plan.status().isTerminalFailure() || plan.activated()) {
            continue;
         }
         if (plan.status() == SettlementPlanStatus.PHYSICAL_COMPLETE
            || plan.status() == SettlementPlanStatus.ACTIVATING) {
            watchActivation(plan.planId());
            continue;
         }
         kickPlanExecution(level, plan);
         kicks++;
      }
   }

   private static List<SettlementPlan> nearSpawnPlans(
      ServerLevel level, SettlementPlanSavedData store, net.minecraft.core.BlockPos spawn, int boostRadius
   ) {
      int planCount = store.planCount();
      boolean spawnMoved = Math.abs(spawn.getX() - nearSpawnCacheX) > 16 || Math.abs(spawn.getZ() - nearSpawnCacheZ) > 16;
      boolean stale = level.getGameTime() % 100L == 0L;
      if (!spawnMoved
         && !stale
         && nearSpawnCacheRadius == boostRadius
         && nearSpawnCachePlanCount == planCount
         && nearSpawnCache != null) {
         return nearSpawnCache;
      }
      long radiusSq = (long)boostRadius * boostRadius;
      List<SettlementPlan> near = new ArrayList<>();
      for (SettlementPlan plan : store.plansView()) {
         if (plan.status().isTerminalFailure() || plan.activated()) {
            continue;
         }
         SettlementPlanStatus st = plan.status();
         if (st != SettlementPlanStatus.PLANNED
            && st != SettlementPlanStatus.RESERVED
            && st != SettlementPlanStatus.MATERIALIZING
            && st != SettlementPlanStatus.PHYSICAL_COMPLETE
            && st != SettlementPlanStatus.ACTIVATING) {
            continue;
         }
         long dx = (long)plan.center().getX() - spawn.getX();
         long dz = (long)plan.center().getZ() - spawn.getZ();
         if (dx * dx + dz * dz > radiusSq) {
            continue;
         }
         near.add(plan);
      }
      near.sort(Comparator.comparing(SettlementPlan::planId));
      nearSpawnCache = List.copyOf(near);
      nearSpawnCacheX = spawn.getX();
      nearSpawnCacheZ = spawn.getZ();
      nearSpawnCachePlanCount = planCount;
      nearSpawnCacheRadius = boostRadius;
      return nearSpawnCache;
   }

   /**
    * New chunk hook — enqueue region/layout/materialize work only (no heavy placement here).
    */
   public static void onNewChunk(ServerLevel level, ChunkPos chunk) {
      enqueueChunkWork(level, chunk);
   }

   public static void onChunkAvailable(ServerLevel level, ChunkPos chunk) {
      enqueueChunkWork(level, chunk);
   }

   public static void offerChunkWork(ServerLevel level, ChunkPos chunk, boolean isNewChunk) {
      enqueueChunkWork(level, chunk);
   }

   /**
    * Chunk-load path: offer queue keys only. Never runs layout/materialize on this path.
    */
   private static void enqueueChunkWork(ServerLevel level, ChunkPos chunk) {
      if (!isEnabled() || level == null || level.dimension() != Level.OVERWORLD) {
         return;
      }
      // Defer all planning until CultureLoader has built a real snapshot. Rebuilding early
      // with empty ModCultures permanently rejected start-region candidates (no_candidates).
      if (!WorldgenContentSnapshot.isReady()) {
         int regionSize = MillenaireServerConfig.SERVER.worldgenRegionSizeChunks.getAsInt();
         SettlementRegionKey region = SettlementRegionKey.fromChunk(chunk, regionSize);
         WorldgenPlanningQueue.get().markRegionWaiting(region.packed());
         WorldgenPlanningQueue.get().offerPlanRegion(region, chunk);
         return;
      }

      int regionSize = MillenaireServerConfig.SERVER.worldgenRegionSizeChunks.getAsInt();
      SettlementRegionKey region = SettlementRegionKey.fromChunk(chunk, regionSize);
      WorldgenPlanningQueue queue = WorldgenPlanningQueue.get();
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      SettlementPlan existing = store.getByRegion(region);

      if (existing == null || queue.isRegionWaiting(region.packed())) {
         queue.offerPlanRegion(region, chunk);
      }

      existing = store.getByRegion(region);
      if (existing != null && !existing.status().isTerminalFailure() && !existing.activated()) {
         enqueuePlanProgress(existing, chunk);
      }

      for (SettlementPlan touching : store.plansTouchingChunk(chunk)) {
         if (touching.status().isTerminalFailure() || touching.activated()) {
            continue;
         }
         if (touching.isChunkFullyMaterialized(chunk.toLong())) {
            maybeQueueActivation(level, touching);
            continue;
         }
         enqueueMaterialize(level, chunk, touching);
         if (touching.status() == SettlementPlanStatus.PHYSICAL_COMPLETE
            || touching.status() == SettlementPlanStatus.ACTIVATING) {
            watchActivation(touching.planId());
         }
      }
   }

   /** Enqueue layout or materialize work for a plan without performing heavy work. */
   private static void enqueuePlanProgress(SettlementPlan plan, ChunkPos triggerChunk) {
      if (plan.status() == SettlementPlanStatus.PLANNED || plan.buildings().isEmpty()) {
         WorldgenPlanningQueue.get().offerLayout(plan.region(), triggerChunk);
      } else if (plan.intersectsChunk(triggerChunk) || plan.reservedChunks().contains(triggerChunk.toLong())) {
         WorldgenPlanningQueue.get().offerMaterializeTouch(plan.region(), triggerChunk);
      }
   }

   /**
    * Tick-path progress: may run layout/materialize under the shared tick budget.
    */
   private static void progressPlan(ServerLevel level, SettlementPlan plan, ChunkPos triggerChunk) {
      if (plan.status() == SettlementPlanStatus.PLANNED || plan.buildings().isEmpty()) {
         if (WorldgenChunkAccess.isColumnLoaded(level, plan.center().getX(), plan.center().getZ())) {
            boolean laidOut = SettlementLayoutPlanner.ensureLayout(level, plan);
            if (laidOut) {
               queueMaterializeLoadedFootprint(level, plan);
               maybeExpandHamlets(level, plan);
            } else if (!plan.status().isTerminalFailure()) {
               WorldgenPlanningQueue.get().offerLayout(plan.region(), triggerChunk);
            }
         } else {
            WorldgenPlanningQueue.get().offerLayout(plan.region(), triggerChunk);
         }
      } else if (plan.intersectsChunk(triggerChunk) || plan.reservedChunks().contains(triggerChunk.toLong())) {
         materializeNow(level, triggerChunk, plan);
      }
   }

   private static void queueMaterializeLoadedFootprint(ServerLevel level, SettlementPlan plan) {
      for (Long packed : plan.reservedChunks()) {
         ChunkPos cp = new ChunkPos(packed);
         if (level.hasChunk(cp.x, cp.z) && !plan.isChunkFullyMaterialized(packed)) {
            if (!MaterializationTickBudget.hasTimeAndOps()) {
               enqueueMaterialize(level, cp, plan);
               WorldgenPlanningQueue.get().offerMaterializeTouch(plan.region(), cp);
               continue;
            }
            materializeNow(level, cp, plan);
            if (!MaterializationTickBudget.hasTimeAndOps()) {
               break;
            }
         }
      }
   }

   private static void drainPlanningQueue(ServerLevel level, SettlementPlanSavedData store) {
      WorldgenPlanningQueue queue = WorldgenPlanningQueue.get();
      int planBudget = MillenaireServerConfig.SERVER.worldgenPlanningBudget.getAsInt();
      List<WorldgenPlanningQueue.Item> items = queue.drainPriority(
         Math.max(2, planBudget),
         Math.max(4, planBudget * 2),
         Math.max(2, planBudget)
      );
      for (WorldgenPlanningQueue.Item item : items) {
         if (!MaterializationTickBudget.hasTime()) {
            break;
         }
         switch (item.kind()) {
            case LAYOUT -> {
               SettlementPlan plan = store.getByRegion(item.regionKey());
               if (plan == null || plan.status().isTerminalFailure() || plan.activated()) {
                  queue.remove(item);
                  break;
               }
               if (!plan.buildings().isEmpty() && plan.status().ordinal() >= SettlementPlanStatus.RESERVED.ordinal()) {
                  queue.remove(item);
                  break;
               }
               boolean laidOut = SettlementLayoutPlanner.ensureLayout(level, plan);
               if (laidOut || plan.status().isTerminalFailure()) {
                  queue.remove(item);
               }
               if (laidOut) {
                  queueMaterializeLoadedFootprint(level, plan);
                  maybeExpandHamlets(level, plan);
               }
            }
            case MATERIALIZE_TOUCH -> {
               queue.remove(item);
               SettlementPlan plan = store.getByRegion(item.regionKey());
               if (plan == null || plan.status().isTerminalFailure() || plan.activated()) {
                  break;
               }
               if (plan.status() == SettlementPlanStatus.PLANNED || plan.buildings().isEmpty()) {
                  queue.offerLayout(plan.region(), item.chunkPos());
                  break;
               }
               if (!MaterializationTickBudget.hasTimeAndOps()) {
                  enqueueMaterialize(level, item.chunkPos(), plan);
                  break;
               }
               materializeNow(level, item.chunkPos(), plan);
            }
            case PLAN_REGION -> {
               SettlementRegionKey key = item.regionKey();
               int regionSize = MillenaireServerConfig.SERVER.worldgenRegionSizeChunks.getAsInt();
               ChunkPos centreChunk = new ChunkPos(key.centerChunkX(regionSize), key.centerChunkZ(regionSize));
               ChunkPos trigger = queue.planTriggerChunk(item.regionPacked());
               ChunkPos evalChunk = WorldgenChunkAccess.isChunkLoaded(level, centreChunk) ? centreChunk : trigger;
               // Ensure evaluation targets this region even if trigger map is stale.
               if (SettlementRegionKey.fromChunk(evalChunk, regionSize).packed() != key.packed()) {
                  evalChunk = centreChunk;
               }
               // WAITING backoff: do not burn a plan step while the evaluation column is still missing.
               if (queue.isRegionWaiting(key.packed()) && !WorldgenChunkAccess.isChunkLoaded(level, evalChunk)) {
                  queue.deferPlanRegion(item);
                  break;
               }
               if (!MaterializationTickBudget.tryConsumePlanStep()) {
                  queue.deferPlanRegion(item);
                  break;
               }
               SettlementPlanner.Outcome outcome = SettlementPlanner.ensureRegionPlanOutcome(level, evalChunk);
               if (outcome.result() == SettlementPlanner.PlanResult.WAITING
                  || outcome.result() == SettlementPlanner.PlanResult.IN_FLIGHT) {
                  queue.deferPlanRegion(item);
                  break;
               }
               queue.remove(item);
               if (outcome.plan() != null
                  && !outcome.plan().status().isTerminalFailure()
                  && (outcome.plan().status() == SettlementPlanStatus.PLANNED || outcome.plan().buildings().isEmpty())) {
                  progressPlan(level, outcome.plan(), evalChunk);
               }
            }
         }
      }
   }

   private static void drainResumeQueue(ServerLevel level, SettlementPlanSavedData store) {
      beginMaterializeTick(level.getGameTime());
      int limit = MillenaireServerConfig.SERVER.worldgenResumeQueueBudget.getAsInt();
      List<MaterializationResumeQueue.Item> items = store.resumeQueue().peekRoundRobin(limit);
      for (MaterializationResumeQueue.Item item : items) {
         if (!MaterializationTickBudget.hasTimeAndOps()) {
            break;
         }
         SettlementPlan plan = store.getPlan(item.planId());
         if (plan == null || plan.status().isTerminalFailure() || plan.activated()) {
            store.resumeQueue().remove(item.planId(), item.chunkPacked());
            continue;
         }
         long dedupeKey = materializeKey(item.planId(), item.chunkPacked());
         if (materializedThisTick.contains(dedupeKey)) {
            continue;
         }
         ChunkPos chunk = item.chunkPos();
         if (!level.hasChunk(chunk.x, chunk.z)) {
            plan.progressFor(item.chunkPacked()).setBlockReason("chunk_not_loaded");
            continue;
         }
         materializedThisTick.add(dedupeKey);
         ChunkSettlementMaterializer.PassResult result = ChunkSettlementMaterializer.materializeChunk(level, chunk, plan);
         if (result == ChunkSettlementMaterializer.PassResult.COMPLETE) {
            maybeQueueActivation(level, plan);
         }
      }
   }

   private static void activateDuePlans(ServerLevel overworld, SettlementPlanSavedData store) {
      List<SettlementPlan> candidates = new ArrayList<>();
      // Full scan only when watch is empty, and at most every 40 ticks (watchActivation fills it sooner).
      if (activationWatch.isEmpty()) {
         long t = overworld.getGameTime();
         if (lastActivationFullScanTick == Long.MIN_VALUE || t - lastActivationFullScanTick >= 40L) {
            lastActivationFullScanTick = t;
            for (SettlementPlan plan : store.plansView()) {
               if ((plan.status() == SettlementPlanStatus.PHYSICAL_COMPLETE || plan.status() == SettlementPlanStatus.ACTIVATING)
                  && plan.isPhysicallyComplete()) {
                  activationWatch.add(plan.planId());
               }
            }
         }
      }
      for (UUID id : new ArrayList<>(activationWatch)) {
         SettlementPlan plan = store.getPlan(id);
         if (plan == null || plan.activated() || plan.status().isTerminalFailure()) {
            activationWatch.remove(id);
            continue;
         }
         if ((plan.status() == SettlementPlanStatus.PHYSICAL_COMPLETE || plan.status() == SettlementPlanStatus.ACTIVATING)
            && plan.isPhysicallyComplete()) {
            candidates.add(plan);
         } else {
            activationWatch.remove(id);
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
         boolean done = SettlementActivator.tryActivate(overworld, plan);
         if (done || plan.status().isTerminalFailure() || plan.activated()) {
            activationWatch.remove(plan.planId());
         }
         activationRoundRobin = start + i + 1;
         if (!MaterializationTickBudget.hasTime()) {
            break;
         }
      }
   }

   public static void watchActivation(UUID planId) {
      if (planId != null) {
         activationWatch.add(planId);
      }
   }

   public static void kickPlanExecution(ServerLevel level, SettlementPlan plan) {
      if (level == null || plan == null || plan.status().isTerminalFailure() || plan.activated()) {
         return;
      }
      if (!ColonizationMaterializationGate.allowPhysicalMutation(level, plan)) {
         return;
      }
      MaterializationTickBudget.ensureTick(level.getGameTime());
      progressPlan(level, plan, new ChunkPos(plan.center()));
      SettlementPlanSavedData.get(level).markDirtyPlan();
   }

   /** Queue a chunk for later materialization (chunk-load safe). */
   private static void enqueueMaterialize(ServerLevel level, ChunkPos chunk, SettlementPlan plan) {
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      long packed = chunk.toLong();
      if (plan.isChunkFullyMaterialized(packed)) {
         store.resumeQueue().remove(plan.planId(), packed);
         maybeQueueActivation(level, plan);
         return;
      }
      if (!ColonizationMaterializationGate.allowPhysicalMutation(level, plan)) {
         plan.progressFor(packed).setBlockReason("colonization_unpaid_or_blocked");
         store.markDirtyPlan();
         return;
      }
      store.resumeQueue().offer(plan.planId(), packed);
      WorldgenPlanningQueue.get().offerMaterializeTouch(plan.region(), chunk);
   }

   /** Materialize one chunk now under the shared tick budget (server-tick path only). */
   private static void materializeNow(ServerLevel level, ChunkPos chunk, SettlementPlan plan) {
      beginMaterializeTick(level.getGameTime());
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      long packed = chunk.toLong();
      if (plan.isChunkFullyMaterialized(packed)) {
         store.resumeQueue().remove(plan.planId(), packed);
         maybeQueueActivation(level, plan);
         return;
      }
      if (!ColonizationMaterializationGate.allowPhysicalMutation(level, plan)) {
         plan.progressFor(packed).setBlockReason("colonization_unpaid_or_blocked");
         store.markDirtyPlan();
         return;
      }
      store.resumeQueue().offer(plan.planId(), packed);
      long dedupeKey = materializeKey(plan.planId(), packed);
      if (materializedThisTick.contains(dedupeKey)) {
         return;
      }
      if (!MaterializationTickBudget.hasTimeAndOps()) {
         plan.progressFor(packed).setBlockReason("budget_exhausted");
         store.markDirtyPlan();
         return;
      }
      materializedThisTick.add(dedupeKey);
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
      watchActivation(plan.planId());
   }

   private static void maybeExpandHamlets(ServerLevel level, SettlementPlan parent) {
      if (parent.hamlets().isEmpty()) {
         return;
      }
      if (parent.status() != SettlementPlanStatus.RESERVED
         && parent.status() != SettlementPlanStatus.MATERIALIZING
         && parent.status() != SettlementPlanStatus.PHYSICAL_COMPLETE
         && parent.status() != SettlementPlanStatus.ACTIVE) {
         return;
      }
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      for (var hamlet : parent.hamlets()) {
         if (hamlet.status().isTerminalFailure() || hamlet.status() == SettlementPlanStatus.ACTIVE) {
            continue;
         }
         if (store.getPlan(hamlet.hamletPlanId()) != null) {
            continue;
         }
         int regionSize = MillenaireServerConfig.SERVER.worldgenRegionSizeChunks.getAsInt();
         var region = SettlementRegionKey.fromBlock(hamlet.center().getX(), hamlet.center().getZ(), regionSize);
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
         WorldgenPlanningQueue.get().offerLayout(region, new ChunkPos(hamlet.center()));
      }
   }
}
