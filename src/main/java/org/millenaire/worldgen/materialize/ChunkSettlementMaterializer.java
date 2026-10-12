package org.millenaire.worldgen.materialize;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ClearMargins;
import org.millenaire.building.PlacementPhase;
import org.millenaire.building.PlacementStep;
import org.millenaire.civilization.settlement.ColonizationMaterializationGate;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.world.BuildingPlacer;
import org.millenaire.world.PlacementConstraints;
import org.millenaire.world.TerrainPreparer;
import org.millenaire.worldgen.WorldgenMetrics;
import org.millenaire.worldgen.plan.PlannedBuilding;
import org.millenaire.worldgen.plan.PlannedRoad;
import org.millenaire.worldgen.plan.PlannedWall;
import org.millenaire.worldgen.plan.SettlementFailureReason;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;
import org.millenaire.worldgen.plan.SettlementPlanStatus;
import org.slf4j.Logger;

/**
 * Stage B — chunk-safe physical materialization with resumable per-unit progress.
 * A chunk is marked fully materialized only when all required work for that chunk finished.
 * Budget exhaustion is a deferral, not a terminal failure.
 */
public final class ChunkSettlementMaterializer {
   private static final Logger LOGGER = LogUtils.getLogger();
   /** Caps scanning of placement steps outside the current cursor window per unit pass. */
   private static final int MAX_STEP_SCAN_PER_PASS = 512;

   public enum PassResult {
      IDLE,
      PROGRESS,
      BUDGET_EXHAUSTED,
      COMPLETE,
      FAILED
   }

   private ChunkSettlementMaterializer() {
   }

   public static PassResult materializeChunk(ServerLevel level, ChunkPos chunk, SettlementPlan plan) {
      if (plan.status().isTerminalFailure() || plan.activated()) {
         return PassResult.IDLE;
      }
      if (!plan.status().allowsMaterialization() && plan.status() != SettlementPlanStatus.PHYSICAL_COMPLETE) {
         if (plan.status() != SettlementPlanStatus.PLANNED
            && plan.status() != SettlementPlanStatus.RESERVED
            && plan.status() != SettlementPlanStatus.MATERIALIZING) {
            return PassResult.IDLE;
         }
      }

      long packed = chunk.toLong();
      if (plan.isChunkFullyMaterialized(packed)) {
         SettlementPlanSavedData.get(level).resumeQueue().remove(plan.planId(), packed);
         return PassResult.COMPLETE;
      }

      // Indispensable check at the real physical mutation entry — all callers share this gate.
      if (!ColonizationMaterializationGate.allowPhysicalMutation(level, plan)) {
         ChunkMaterializationProgress blocked = plan.progressFor(packed);
         blocked.setStatus(ChunkMaterializationProgress.Status.BLOCKED);
         blocked.setBlockReason("colonization_unpaid_or_blocked");
         SettlementPlanSavedData.get(level).markDirtyPlan();
         return PassResult.IDLE;
      }

      MaterializationTickBudget.ensureTick(level.getGameTime());
      if (!MaterializationTickBudget.hasTimeAndOps()) {
         SettlementPlanSavedData.get(level).resumeQueue().offer(plan.planId(), packed);
         ChunkMaterializationProgress progress = plan.progressFor(packed);
         progress.setStatus(ChunkMaterializationProgress.Status.BLOCKED);
         progress.setBlockReason("budget_exhausted");
         SettlementPlanSavedData.get(level).markDirtyPlan();
         return PassResult.BUDGET_EXHAUSTED;
      }

      if (!plan.trySetStatus(SettlementPlanStatus.MATERIALIZING) && plan.status() != SettlementPlanStatus.MATERIALIZING) {
         if (plan.status() == SettlementPlanStatus.RESERVED || plan.status() == SettlementPlanStatus.PLANNED) {
            plan.forceStatus(SettlementPlanStatus.MATERIALIZING);
         }
      }

      ChunkMaterializationProgress progress = plan.progressFor(packed);
      if (progress.status() != ChunkMaterializationProgress.Status.COMPLETE
         && progress.status() != ChunkMaterializationProgress.Status.FAILED) {
         progress.setStatus(ChunkMaterializationProgress.Status.IN_PROGRESS);
         if (!"legacy_materialized_unverified".equals(progress.blockReason())) {
            progress.setBlockReason("");
         }
      }

      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      store.resumeQueue().offer(plan.planId(), packed);

      try {
         boolean budgetHit = false;
         boolean anyProgress = false;
         boolean unitBlocked = false;

         for (PlannedBuilding building : plan.buildings()) {
            if (building.materialized() || progress.isPlacementCompleteBuilding(building.buildingId())) {
               continue;
            }
            if (!building.intersectsChunk(chunk)) {
               continue;
            }
            if (!MaterializationTickBudget.hasScanCandidates()) {
               budgetHit = true;
               break;
            }
            MaterializationTickBudget.tryConsumeScan();
            WorkOutcome outcome = materializeBuildingInChunk(level, chunk, building, progress);
            if (outcome.ops() > 0) {
               anyProgress = true;
            }
            if (outcome.blocked()) {
               unitBlocked = true;
               progress.setStatus(ChunkMaterializationProgress.Status.BLOCKED);
               progress.setBlockReason(outcome.reason());
            }
            if (outcome.budgetExhausted()) {
               budgetHit = true;
               break;
            }
         }

         if (!budgetHit && Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenGenerateStartingWalls.get())) {
            for (PlannedWall wall : plan.walls()) {
               if (wall.materialized() || progress.isPlacementCompleteWall(wall.segmentId())) {
                  continue;
               }
               if (!wallIntersectsChunk(wall, chunk)) {
                  continue;
               }
               if (!MaterializationTickBudget.hasScanCandidates()) {
                  budgetHit = true;
                  break;
               }
               MaterializationTickBudget.tryConsumeScan();
               WorkOutcome outcome = materializeWallInChunk(level, chunk, wall, progress);
               if (outcome.ops() > 0) {
                  anyProgress = true;
               }
               if (outcome.blocked()) {
                  unitBlocked = true;
                  progress.setStatus(ChunkMaterializationProgress.Status.BLOCKED);
                  progress.setBlockReason(outcome.reason());
               }
               if (outcome.budgetExhausted()) {
                  budgetHit = true;
                  break;
               }
            }
         }

         if (!budgetHit && Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenGenerateInitialRoads.get())) {
            for (PlannedRoad road : plan.roads()) {
               if (road.materialized() || progress.isRoadComplete(road.roadId())) {
                  continue;
               }
               if (!road.intersectsChunk(chunk)) {
                  continue;
               }
               if (!MaterializationTickBudget.hasScanCandidates()) {
                  budgetHit = true;
                  break;
               }
               MaterializationTickBudget.tryConsumeScan();
               WorkOutcome outcome = materializeRoadInChunk(level, chunk, plan, road, progress);
               if (outcome.ops() > 0) {
                  anyProgress = true;
               }
               if (outcome.blocked()) {
                  unitBlocked = true;
                  progress.setStatus(ChunkMaterializationProgress.Status.BLOCKED);
                  progress.setBlockReason(outcome.reason());
               }
               if (outcome.budgetExhausted()) {
                  budgetHit = true;
                  break;
               }
            }
         }

         if (anyProgress) {
            // First real block mutation consumes founding escrow (not merely MATERIALIZING status).
            ColonizationMaterializationGate.notePhysicalMutation(level, plan);
         }

         boolean chunkDone = isChunkWorkComplete(plan, chunk, progress);
         if (chunkDone) {
            plan.markChunkFullyMaterialized(packed);
            WorldgenMetrics.CHUNKS_MATERIALIZED.incrementAndGet();
            store.resumeQueue().remove(plan.planId(), packed);
            refreshBuildingCompletion(plan);
            refreshWallCompletion(plan);
            maybeCompleteRoads(plan);
            if (plan.isPhysicallyComplete()) {
               plan.trySetStatus(SettlementPlanStatus.PHYSICAL_COMPLETE);
               if (plan.status() != SettlementPlanStatus.PHYSICAL_COMPLETE) {
                  plan.forceStatus(SettlementPlanStatus.PHYSICAL_COMPLETE);
               }
            }
            store.markDirtyPlan();
            return PassResult.COMPLETE;
         }

         if (budgetHit) {
            progress.setStatus(ChunkMaterializationProgress.Status.BLOCKED);
            progress.setBlockReason("budget_exhausted");
            if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenDetailedDiagnostics.get())) {
               LOGGER.debug(
                  "[Millenaire] Materialization deferred plan={} chunk={} ({}) — {}",
                  plan.planId(),
                  chunk,
                  progress.blockReason(),
                  MaterializationTickBudget.diagnostic()
               );
            }
            store.markDirtyPlan();
            return PassResult.BUDGET_EXHAUSTED;
         }

         progress.setStatus(unitBlocked ? ChunkMaterializationProgress.Status.BLOCKED : ChunkMaterializationProgress.Status.IN_PROGRESS);
         if (!unitBlocked) {
            progress.setBlockReason(anyProgress ? "partial" : "waiting_units");
         }
         store.markDirtyPlan();
         return anyProgress ? PassResult.PROGRESS : PassResult.IDLE;
      } catch (Exception e) {
         LOGGER.error("[Millenaire] Materialization failed for plan {} chunk {}", plan.planId(), chunk, e);
         progress.setStatus(ChunkMaterializationProgress.Status.FAILED);
         progress.setBlockReason(e.getClass().getSimpleName() + ": " + e.getMessage());
         plan.markFailed(SettlementFailureReason.CHUNK_MATERIALIZATION_FAILURE, e.getClass().getSimpleName() + ": " + e.getMessage());
         store.markDirtyPlan();
         return PassResult.FAILED;
      }
   }

   private static boolean isChunkWorkComplete(SettlementPlan plan, ChunkPos chunk, ChunkMaterializationProgress progress) {
      for (PlannedBuilding building : plan.buildings()) {
         if (!building.intersectsChunk(chunk)) {
            continue;
         }
         if (!building.materialized() && !progress.isPlacementCompleteBuilding(building.buildingId())) {
            return false;
         }
      }
      if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenGenerateStartingWalls.get())) {
         for (PlannedWall wall : plan.walls()) {
            if (!wallIntersectsChunk(wall, chunk)) {
               continue;
            }
            if (!wall.materialized() && !progress.isPlacementCompleteWall(wall.segmentId())) {
               return false;
            }
         }
      }
      if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenGenerateInitialRoads.get())) {
         for (PlannedRoad road : plan.roads()) {
            if (!road.intersectsChunk(chunk)) {
               continue;
            }
            if (!road.materialized() && !progress.isRoadComplete(road.roadId())) {
               return false;
            }
         }
      }
      return true;
   }

   private static void maybeCompleteRoads(SettlementPlan plan) {
      for (PlannedRoad road : plan.roads()) {
         if (road.materialized()) {
            continue;
         }
         if (road.waypoints().isEmpty()) {
            road.setMaterialized(true);
            continue;
         }
         boolean all = true;
         for (BlockPos p : road.waypoints()) {
            ChunkPos cp = new ChunkPos(p);
            long packed = cp.toLong();
            if (!plan.isChunkFullyMaterialized(packed)) {
               ChunkMaterializationProgress progress = plan.chunkProgress().get(packed);
               if (progress == null || !progress.isRoadComplete(road.roadId())) {
                  all = false;
                  break;
               }
            }
         }
         if (all) {
            road.setMaterialized(true);
         }
      }
   }

   private static WorkOutcome materializeBuildingInChunk(
      ServerLevel level,
      ChunkPos chunk,
      PlannedBuilding building,
      ChunkMaterializationProgress progress
   ) {
      BuildingPlan plan = ModCultures.getBuildingPlan(building.planId());
      if (plan == null) {
         progress.setBlockReason("missing_building_plan:" + building.planId());
         progress.setStatus(ChunkMaterializationProgress.Status.BLOCKED);
         return WorkOutcome.blocked("missing_building_plan:" + building.planId());
      }

      // Ambiguous legacy chunks: never re-run destructive terrain clear; resume placement only.
      ClearMargins margins = ClearMargins.symmetric(PlacementConstraints.getDefaultClearMargin());
      if (building.planSetId() != null) {
         BuildingPlanSet set = ModCultures.getBuildingPlanSet(building.planSetId());
         if (set != null) {
            margins = set.clearMargins();
         }
      }

      BlockPos origin = building.origin();
      int baseY = origin.getY() - building.groundLevel();
      int ops = 0;

      progress.setActiveBuilding(building.buildingId());

      if (!progress.isTerrainPreparedBuilding(building.buildingId())) {
         if (progress.legacyAmbiguous()) {
            progress.terrainPreparedBuildings().add(building.buildingId());
            progress.clearTerrainBuildingCursor(building.buildingId());
         } else {
            WorkOutcome terrain = flattenFootprintInChunk(
               level, chunk, origin, plan, building.rotation(), baseY, margins,
               progress, building.buildingId(), true
            );
            ops += terrain.ops();
            if (terrain.budgetExhausted()) {
               return WorkOutcome.budget(ops);
            }
            if (terrain.blocked()) {
               return WorkOutcome.blocked(terrain.reason(), ops);
            }
            progress.terrainPreparedBuildings().add(building.buildingId());
            progress.clearTerrainBuildingCursor(building.buildingId());
         }
      }

      ResolvedSteps resolved = resolveBuildingSteps(level, plan, origin, building.rotation());
      List<PlacementStep> steps = resolved.steps();
      int minX = chunk.getMinBlockX();
      int maxX = chunk.getMaxBlockX();
      int minZ = chunk.getMinBlockZ();
      int maxZ = chunk.getMaxBlockZ();
      int cursor = progress.buildingPlacementCursor(building.buildingId());
      int scanned = 0;
      boolean budgetExhausted = false;
      int[] chunkIndices = indicesForWorldChunk(resolved, origin, chunk);

      if (chunkIndices != null) {
         int pos = 0;
         while (pos < chunkIndices.length && chunkIndices[pos] < cursor) {
            pos++;
         }
         for (; pos < chunkIndices.length; pos++) {
            int index = chunkIndices[pos];
            if (++scanned > MAX_STEP_SCAN_PER_PASS) {
               progress.setBuildingPlacementCursor(building.buildingId(), index);
               budgetExhausted = true;
               break;
            }
            if (!MaterializationTickBudget.hasTimeAndOps()) {
               progress.setBuildingPlacementCursor(building.buildingId(), index);
               budgetExhausted = true;
               break;
            }
            PlacementStep step = steps.get(index);
            BlockPos worldPos = step.relativePos().offset(origin);
            if (worldPos.getX() < minX || worldPos.getX() > maxX || worldPos.getZ() < minZ || worldPos.getZ() > maxZ) {
               progress.setBuildingPlacementCursor(building.buildingId(), index + 1);
               continue;
            }
            WorkOutcome stepOut = applyPlacementStep(
               level, origin, plan, progress, building.buildingId(), true, index, step, worldPos
            );
            ops += stepOut.ops();
            if (stepOut.budgetExhausted()) {
               budgetExhausted = true;
               break;
            }
         }
      } else {
         // Direct index access — list order is stable (no world-dependent filtering).
         for (int index = cursor; index < steps.size(); index++) {
            if (++scanned > MAX_STEP_SCAN_PER_PASS) {
               progress.setBuildingPlacementCursor(building.buildingId(), index);
               budgetExhausted = true;
               break;
            }
            if (!MaterializationTickBudget.hasTimeAndOps()) {
               progress.setBuildingPlacementCursor(building.buildingId(), index);
               budgetExhausted = true;
               break;
            }
            PlacementStep step = steps.get(index);
            BlockPos worldPos = step.relativePos().offset(origin);
            if (worldPos.getX() < minX || worldPos.getX() > maxX || worldPos.getZ() < minZ || worldPos.getZ() > maxZ) {
               progress.setBuildingPlacementCursor(building.buildingId(), index + 1);
               continue;
            }
            WorkOutcome stepOut = applyPlacementStep(
               level, origin, plan, progress, building.buildingId(), true, index, step, worldPos
            );
            ops += stepOut.ops();
            if (stepOut.budgetExhausted()) {
               budgetExhausted = true;
               break;
            }
         }
      }

      if (!budgetExhausted) {
         progress.placementCompleteBuildings().add(building.buildingId());
         progress.clearBuildingPlacementCursor(building.buildingId());
         progress.setActiveBuilding(null);
         if (building.minChunkX() == building.maxChunkX() && building.minChunkZ() == building.maxChunkZ()) {
            building.setMaterialized(true);
            building.setChunksComplete(true);
         }
         return WorkOutcome.done(ops);
      }
      return WorkOutcome.budget(ops);
   }

   private static WorkOutcome applyPlacementStep(
      ServerLevel level,
      BlockPos origin,
      BuildingPlan plan,
      ChunkMaterializationProgress progress,
      java.util.UUID unitId,
      boolean building,
      int index,
      PlacementStep step,
      BlockPos worldPos
   ) {
      int ops = 0;
      // Runtime world check — must not remove steps from the compiled list.
      if (BuildingPlacer.isNoOpAirStep(level, origin, step, plan.groundLevel())) {
         if (building) {
            progress.setBuildingPlacementCursor(unitId, index + 1);
         } else {
            progress.setWallPlacementCursor(unitId, index + 1);
         }
         return WorkOutcome.done(0);
      }
      if (step.phase() == PlacementPhase.DELETION) {
         if (progress.legacyAmbiguous()) {
            if (building) {
               progress.setBuildingPlacementCursor(unitId, index + 1);
            } else {
               progress.setWallPlacementCursor(unitId, index + 1);
            }
            return WorkOutcome.done(0);
         }
         if (!level.getBlockState(worldPos).isAir()) {
            if (!MaterializationTickBudget.tryConsumeOp()) {
               if (building) {
                  progress.setBuildingPlacementCursor(unitId, index);
               } else {
                  progress.setWallPlacementCursor(unitId, index);
               }
               return WorkOutcome.budget(0);
            }
            level.setBlock(worldPos, Blocks.AIR.defaultBlockState(), 2);
            ops++;
         }
         if (building) {
            progress.setBuildingPlacementCursor(unitId, index + 1);
         } else {
            progress.setWallPlacementCursor(unitId, index + 1);
         }
         return WorkOutcome.done(ops);
      }
      BlockState state = step.blockState();
      if (state.isAir()) {
         if (building) {
            progress.setBuildingPlacementCursor(unitId, index + 1);
         } else {
            progress.setWallPlacementCursor(unitId, index + 1);
         }
         return WorkOutcome.done(0);
      }
      BlockState fixed = BuildingPlacer.applyFacingGuess(
         level, worldPos, state, step.guessChestFacing(), step.guessTorchFacing(), step.guessFurnaceFacing()
      );
      BlockState current = level.getBlockState(worldPos);
      if (alreadyCorrect(level, worldPos, current, fixed)) {
         if (building) {
            progress.setBuildingPlacementCursor(unitId, index + 1);
         } else {
            progress.setWallPlacementCursor(unitId, index + 1);
         }
         return WorkOutcome.done(0);
      }
      if (!MaterializationTickBudget.tryConsumeOp()) {
         if (building) {
            progress.setBuildingPlacementCursor(unitId, index);
         } else {
            progress.setWallPlacementCursor(unitId, index);
         }
         return WorkOutcome.budget(0);
      }
      level.setBlock(worldPos, fixed, 2);
      if (building) {
         progress.setBuildingPlacementCursor(unitId, index + 1);
      } else {
         progress.setWallPlacementCursor(unitId, index + 1);
      }
      return WorkOutcome.done(1);
   }

   private record ResolvedSteps(List<PlacementStep> steps, @javax.annotation.Nullable java.util.Map<Long, int[]> chunkIndexRanges) {
   }

   private static ResolvedSteps resolveBuildingSteps(
      ServerLevel level, BuildingPlan plan, BlockPos origin, net.minecraft.world.level.block.Rotation rotation
   ) {
      CompiledPlacementStepsCache.Key key = new CompiledPlacementStepsCache.Key(plan.id(), rotation, plan.groundLevel());
      CompiledPlacementStepsCache.CompiledSteps compiled = CompiledPlacementStepsCache.getOrCompute(
         key, () -> BuildingPlacer.compileTemplateStepsStable(level, plan, rotation)
      );
      List<PlacementStep> merged = BuildingPlacer.mergePreserveGround(level, plan, origin, rotation, compiled.steps());
      // Ranges index the cached template list; only valid when merge did not prepend ground steps.
      java.util.Map<Long, int[]> ranges = merged == compiled.steps() ? compiled.chunkIndexRanges() : null;
      return new ResolvedSteps(merged, ranges);
   }

   /**
    * Build a dense ascending index list of steps that may land in {@code chunk}, using cached
    * relative-chunk buckets when available; otherwise returns null (caller falls back to linear scan).
    */
   @javax.annotation.Nullable
   private static int[] indicesForWorldChunk(
      ResolvedSteps resolved, BlockPos origin, ChunkPos chunk
   ) {
      if (resolved.chunkIndexRanges() == null || resolved.chunkIndexRanges().isEmpty()) {
         return null;
      }
      int minX = chunk.getMinBlockX();
      int maxX = chunk.getMaxBlockX();
      int minZ = chunk.getMinBlockZ();
      int maxZ = chunk.getMaxBlockZ();
      int relMinX = minX - origin.getX();
      int relMaxX = maxX - origin.getX();
      int relMinZ = minZ - origin.getZ();
      int relMaxZ = maxZ - origin.getZ();
      java.util.ArrayList<Integer> gathered = new java.util.ArrayList<>();
      for (int rcx = relMinX >> 4; rcx <= relMaxX >> 4; rcx++) {
         for (int rcz = relMinZ >> 4; rcz <= relMaxZ >> 4; rcz++) {
            int[] part = resolved.chunkIndexRanges().get(ChunkPos.asLong(rcx, rcz));
            if (part == null) {
               continue;
            }
            for (int idx : part) {
               gathered.add(idx);
            }
         }
      }
      if (gathered.isEmpty()) {
         return new int[0];
      }
      gathered.sort(Integer::compare);
      // Deduplicate (overlapping relative buckets are rare but cheap to clean).
      int[] out = new int[gathered.size()];
      int n = 0;
      int prev = Integer.MIN_VALUE;
      for (int idx : gathered) {
         if (idx != prev) {
            out[n++] = idx;
            prev = idx;
         }
      }
      if (n == out.length) {
         return out;
      }
      int[] trimmed = new int[n];
      System.arraycopy(out, 0, trimmed, 0, n);
      return trimmed;
   }

   private static boolean alreadyCorrect(ServerLevel level, BlockPos pos, BlockState current, BlockState desired) {
      if (current.getBlock() != desired.getBlock()) {
         return false;
      }
      // Do not re-init block entities / inventories on retry.
      BlockEntity be = level.getBlockEntity(pos);
      return be != null || current.equals(desired) || BridgePlacementHelper.statesMatch(current, desired);
   }

   private static WorkOutcome flattenFootprintInChunk(
      ServerLevel level,
      ChunkPos chunk,
      BlockPos origin,
      BuildingPlan plan,
      net.minecraft.world.level.block.Rotation rotation,
      int baseY,
      ClearMargins margins,
      ChunkMaterializationProgress progress,
      java.util.UUID unitId,
      boolean building
   ) {
      int effW = TerrainPreparer.effectiveWidth(plan.width(), plan.depth(), rotation);
      int effD = TerrainPreparer.effectiveDepth(plan.width(), plan.depth(), rotation);
      ClearMargins oriented = margins.forRotation(rotation);
      int padX0 = oriented.widthBefore();
      int padX1 = oriented.widthAfter();
      int padZ0 = oriented.lengthBefore();
      int padZ1 = oriented.lengthAfter();
      int minX = Math.max(chunk.getMinBlockX(), origin.getX() - padX0);
      int maxX = Math.min(chunk.getMaxBlockX(), origin.getX() + effW + padX1 - 1);
      int minZ = Math.max(chunk.getMinBlockZ(), origin.getZ() - padZ0);
      int maxZ = Math.min(chunk.getMaxBlockZ(), origin.getZ() + effD + padZ1 - 1);
      int width = Math.max(1, maxX - minX + 1);
      int depth = Math.max(1, maxZ - minZ + 1);
      int total = width * depth;
      int cursor = building ? progress.terrainBuildingCursor(unitId) : progress.terrainWallCursor(unitId);
      int maxMod = MillenaireServerConfig.SERVER.worldgenMaximumTerrainModification.getAsInt();
      int ops = 0;
      BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

      for (int cell = cursor; cell < total; cell++) {
         if (!MaterializationTickBudget.hasTimeAndOps()) {
            if (building) {
               progress.setTerrainBuildingCursor(unitId, cell);
            } else {
               progress.setTerrainWallCursor(unitId, cell);
            }
            return WorkOutcome.budget(ops);
         }
         int x = minX + (cell % width);
         int z = minZ + (cell / width);
         int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
         if (Math.abs(surface - baseY) > maxMod) {
            continue;
         }
         for (int y = baseY + 1; y <= Math.min(surface + plan.height(), baseY + plan.height() + 4); y++) {
            mutable.set(x, y, z);
            if (!level.getBlockState(mutable).isAir()) {
               if (level.getBlockEntity(mutable) != null) {
                  continue;
               }
               if (!MaterializationTickBudget.tryConsumeOp()) {
                  if (building) {
                     progress.setTerrainBuildingCursor(unitId, cell);
                  } else {
                     progress.setTerrainWallCursor(unitId, cell);
                  }
                  return WorkOutcome.budget(ops);
               }
               level.setBlock(mutable, Blocks.AIR.defaultBlockState(), 2);
               ops++;
            }
         }
         mutable.set(x, baseY, z);
         BlockState groundState = level.getBlockState(mutable);
         if (groundState.isAir() || groundState.canBeReplaced()) {
            if (!MaterializationTickBudget.tryConsumeOp()) {
               if (building) {
                  progress.setTerrainBuildingCursor(unitId, cell);
               } else {
                  progress.setTerrainWallCursor(unitId, cell);
               }
               return WorkOutcome.budget(ops);
            }
            BlockState filler = level.getBlockState(mutable.set(x, Math.max(level.getMinBuildHeight(), baseY - 1), z));
            if (filler.isAir()) {
               filler = Blocks.DIRT.defaultBlockState();
            }
            level.setBlock(mutable.set(x, baseY, z), filler, 2);
            ops++;
         }
         if (building) {
            progress.setTerrainBuildingCursor(unitId, cell + 1);
         } else {
            progress.setTerrainWallCursor(unitId, cell + 1);
         }
      }
      return WorkOutcome.done(ops);
   }

   public static void refreshBuildingCompletion(SettlementPlan plan) {
      for (PlannedBuilding building : plan.buildings()) {
         if (building.materialized()) {
            continue;
         }
         boolean all = true;
         for (int cx = building.minChunkX(); cx <= building.maxChunkX() && all; cx++) {
            for (int cz = building.minChunkZ(); cz <= building.maxChunkZ(); cz++) {
               long packed = ChunkPos.asLong(cx, cz);
               if (plan.isChunkFullyMaterialized(packed)) {
                  continue;
               }
               ChunkMaterializationProgress progress = plan.chunkProgress().get(packed);
               if (progress == null || !progress.isPlacementCompleteBuilding(building.buildingId())) {
                  all = false;
                  break;
               }
            }
         }
         if (all) {
            building.setMaterialized(true);
            building.setChunksComplete(true);
         }
      }
   }

   private static void refreshWallCompletion(SettlementPlan plan) {
      for (PlannedWall wall : plan.walls()) {
         if (wall.materialized()) {
            continue;
         }
         int minCx = wall.origin().getX() >> 4;
         int maxCx = (wall.origin().getX() + Math.max(wall.width(), 1) - 1) >> 4;
         int minCz = wall.origin().getZ() >> 4;
         int maxCz = (wall.origin().getZ() + Math.max(wall.depth(), 1) - 1) >> 4;
         boolean all = true;
         for (int cx = minCx; cx <= maxCx && all; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
               long packed = ChunkPos.asLong(cx, cz);
               if (plan.isChunkFullyMaterialized(packed)) {
                  continue;
               }
               ChunkMaterializationProgress progress = plan.chunkProgress().get(packed);
               if (progress == null || !progress.isPlacementCompleteWall(wall.segmentId())) {
                  all = false;
                  break;
               }
            }
         }
         if (all) {
            wall.setMaterialized(true);
         }
      }
   }

   private static boolean wallIntersectsChunk(PlannedWall wall, ChunkPos chunk) {
      int minX = wall.origin().getX();
      int minZ = wall.origin().getZ();
      int maxX = minX + Math.max(wall.width(), 1) - 1;
      int maxZ = minZ + Math.max(wall.depth(), 1) - 1;
      return minX <= chunk.getMaxBlockX() && maxX >= chunk.getMinBlockX()
         && minZ <= chunk.getMaxBlockZ() && maxZ >= chunk.getMinBlockZ();
   }

   private static WorkOutcome materializeWallInChunk(
      ServerLevel level,
      ChunkPos chunk,
      PlannedWall wall,
      ChunkMaterializationProgress progress
   ) {
      BuildingPlan plan = ModCultures.getBuildingPlan(wall.planId());
      if (plan == null) {
         progress.setBlockReason("missing_wall_plan:" + wall.planId());
         progress.setStatus(ChunkMaterializationProgress.Status.BLOCKED);
         return WorkOutcome.blocked("missing_wall_plan:" + wall.planId());
      }
      ClearMargins margins = ClearMargins.symmetric(PlacementConstraints.getDefaultClearMargin());
      if (wall.planSetId() != null) {
         BuildingPlanSet set = ModCultures.getBuildingPlanSet(wall.planSetId());
         if (set != null) {
            margins = set.clearMargins();
         }
      }
      BlockPos origin = wall.origin();
      int baseY = origin.getY() - plan.groundLevel();
      int ops = 0;
      if (!progress.isTerrainPreparedWall(wall.segmentId())) {
         if (progress.legacyAmbiguous()) {
            progress.terrainPreparedWalls().add(wall.segmentId());
            progress.clearTerrainWallCursor(wall.segmentId());
         } else {
            WorkOutcome terrain = flattenFootprintInChunk(
               level, chunk, origin, plan, wall.rotation(), baseY, margins,
               progress, wall.segmentId(), false
            );
            ops += terrain.ops();
            if (terrain.budgetExhausted()) {
               return WorkOutcome.budget(ops);
            }
            progress.terrainPreparedWalls().add(wall.segmentId());
            progress.clearTerrainWallCursor(wall.segmentId());
         }
      }

      ResolvedSteps resolved = resolveBuildingSteps(level, plan, origin, wall.rotation());
      List<PlacementStep> steps = resolved.steps();
      int minX = chunk.getMinBlockX();
      int maxX = chunk.getMaxBlockX();
      int minZ = chunk.getMinBlockZ();
      int maxZ = chunk.getMaxBlockZ();
      int cursor = progress.wallPlacementCursor(wall.segmentId());
      int scanned = 0;
      boolean budgetExhausted = false;
      int[] chunkIndices = indicesForWorldChunk(resolved, origin, chunk);

      if (chunkIndices != null) {
         int pos = 0;
         while (pos < chunkIndices.length && chunkIndices[pos] < cursor) {
            pos++;
         }
         for (; pos < chunkIndices.length; pos++) {
            int index = chunkIndices[pos];
            if (++scanned > MAX_STEP_SCAN_PER_PASS) {
               progress.setWallPlacementCursor(wall.segmentId(), index);
               budgetExhausted = true;
               break;
            }
            if (!MaterializationTickBudget.hasTimeAndOps()) {
               progress.setWallPlacementCursor(wall.segmentId(), index);
               return WorkOutcome.budget(ops);
            }
            PlacementStep step = steps.get(index);
            BlockPos worldPos = step.relativePos().offset(origin);
            if (worldPos.getX() < minX || worldPos.getX() > maxX || worldPos.getZ() < minZ || worldPos.getZ() > maxZ) {
               progress.setWallPlacementCursor(wall.segmentId(), index + 1);
               continue;
            }
            WorkOutcome stepOut = applyPlacementStep(
               level, origin, plan, progress, wall.segmentId(), false, index, step, worldPos
            );
            ops += stepOut.ops();
            if (stepOut.budgetExhausted()) {
               return WorkOutcome.budget(ops);
            }
         }
      } else {
         for (int index = cursor; index < steps.size(); index++) {
            if (++scanned > MAX_STEP_SCAN_PER_PASS) {
               progress.setWallPlacementCursor(wall.segmentId(), index);
               budgetExhausted = true;
               break;
            }
            if (!MaterializationTickBudget.hasTimeAndOps()) {
               progress.setWallPlacementCursor(wall.segmentId(), index);
               return WorkOutcome.budget(ops);
            }
            PlacementStep step = steps.get(index);
            BlockPos worldPos = step.relativePos().offset(origin);
            if (worldPos.getX() < minX || worldPos.getX() > maxX || worldPos.getZ() < minZ || worldPos.getZ() > maxZ) {
               progress.setWallPlacementCursor(wall.segmentId(), index + 1);
               continue;
            }
            WorkOutcome stepOut = applyPlacementStep(
               level, origin, plan, progress, wall.segmentId(), false, index, step, worldPos
            );
            ops += stepOut.ops();
            if (stepOut.budgetExhausted()) {
               return WorkOutcome.budget(ops);
            }
         }
      }

      if (budgetExhausted) {
         return WorkOutcome.budget(ops);
      }
      progress.placementCompleteWalls().add(wall.segmentId());
      progress.clearWallPlacementCursor(wall.segmentId());
      int minCx = wall.origin().getX() >> 4;
      int maxCx = (wall.origin().getX() + Math.max(wall.width(), 1) - 1) >> 4;
      int minCz = wall.origin().getZ() >> 4;
      int maxCz = (wall.origin().getZ() + Math.max(wall.depth(), 1) - 1) >> 4;
      if (minCx == maxCx && minCz == maxCz) {
         wall.setMaterialized(true);
      }
      return WorkOutcome.done(ops);
   }

   private static WorkOutcome materializeRoadInChunk(
      ServerLevel level,
      ChunkPos chunk,
      SettlementPlan plan,
      PlannedRoad road,
      ChunkMaterializationProgress progress
   ) {
      List<String> materials = List.of("dirt_path", "gravel", "cobblestone");
      ResourceLocation typeId = plan.villageTypeId();
      if (typeId != null) {
         var vt = ModCultures.getVillageType(typeId);
         if (vt != null && !vt.pathMaterials().isEmpty()) {
            materials = vt.pathMaterials();
         }
      }
      BlockState pathState = resolvePathState(materials.isEmpty() ? "dirt_path" : materials.getFirst());
      boolean bridges = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenGenerateBridges.get());
      int minX = chunk.getMinBlockX();
      int maxX = chunk.getMaxBlockX();
      int minZ = chunk.getMinBlockZ();
      int maxZ = chunk.getMaxBlockZ();
      int ops = 0;
      List<BlockPos> pts = road.waypoints();
      int cursor = progress.roadWaypointCursor(road.roadId());

      for (int i = cursor; i < pts.size(); i++) {
         BlockPos p = pts.get(i);
         if (p.getX() < minX || p.getX() > maxX || p.getZ() < minZ || p.getZ() > maxZ) {
            progress.setRoadWaypointCursor(road.roadId(), i + 1);
            continue;
         }
         if (!MaterializationTickBudget.hasTimeAndOps()) {
            progress.setRoadWaypointCursor(road.roadId(), i);
            return WorkOutcome.budget(ops);
         }
         int surfaceY = walkableSurfaceY(level, p.getX(), p.getZ());
         BlockPos surface = new BlockPos(p.getX(), surfaceY, p.getZ());
         BlockState cur = level.getBlockState(surface);
         if (!cur.getFluidState().isEmpty()) {
            if (!bridges) {
               progress.setRoadWaypointCursor(road.roadId(), i);
               return WorkOutcome.blocked("water_no_bridges", ops);
            }
            java.util.UUID bridgeKey = BridgePlacementHelper.columnKey(road.roadId(), surface);
            BridgePlacementHelper.Outcome bridge = BridgePlacementHelper.placeBridge(
               level, surface, pathState, progress, bridgeKey, true, plan
            );
            ops += bridge.ops();
            if (!bridge.finished()) {
               progress.setRoadWaypointCursor(road.roadId(), i);
               if (bridge.budgetExhausted()) {
                  return WorkOutcome.budget(ops);
               }
               return WorkOutcome.blocked(bridge.reason(), ops);
            }
         } else if (!cur.is(Blocks.BEDROCK) && !BridgePlacementHelper.statesMatch(cur, pathState)) {
            RoadPlacementGuard.Result permit = RoadPlacementGuard.evaluate(
               level, surface, RoadPlacementGuard.Intent.PATH_SURFACE, plan, pathState
            );
            if (permit.alreadyPath()) {
               // already matching path — continue
            } else if (!permit.allowed()) {
               progress.setRoadWaypointCursor(road.roadId(), i);
               return WorkOutcome.blocked(permit.reason().isEmpty() ? "road_protected" : permit.reason(), ops);
            } else {
               if (!MaterializationTickBudget.tryConsumeOp()) {
                  progress.setRoadWaypointCursor(road.roadId(), i);
                  return WorkOutcome.budget(ops);
               }
               level.setBlock(surface, pathState, 2);
               ops++;
            }
         }
         for (BlockPos side : List.of(surface.north(), surface.south(), surface.east(), surface.west())) {
            if (side.getX() < minX || side.getX() > maxX || side.getZ() < minZ || side.getZ() > maxZ) {
               continue;
            }
            if (!MaterializationTickBudget.hasTimeAndOps()) {
               progress.setRoadWaypointCursor(road.roadId(), i);
               return WorkOutcome.budget(ops);
            }
            BlockState s = level.getBlockState(side);
            if (!s.getFluidState().isEmpty()) {
               if (bridges) {
                  java.util.UUID sideKey = BridgePlacementHelper.columnKey(road.roadId(), side);
                  BridgePlacementHelper.Outcome sideBridge = BridgePlacementHelper.placeBridge(
                     level, side, pathState, progress, sideKey, true, plan
                  );
                  ops += sideBridge.ops();
                  if (!sideBridge.finished()) {
                     progress.setRoadWaypointCursor(road.roadId(), i);
                     if (sideBridge.budgetExhausted()) {
                        return WorkOutcome.budget(ops);
                     }
                     return WorkOutcome.blocked(sideBridge.reason(), ops);
                  }
               }
               continue;
            }
            RoadPlacementGuard.Result sidePermit = RoadPlacementGuard.evaluate(
               level, side, RoadPlacementGuard.Intent.PATH_SIDE, plan, pathState
            );
            if (!sidePermit.allowed() || sidePermit.alreadyPath()) {
               continue;
            }
            if (!MaterializationTickBudget.tryConsumeOp()) {
               progress.setRoadWaypointCursor(road.roadId(), i);
               return WorkOutcome.budget(ops);
            }
            level.setBlock(side, pathState, 2);
            ops++;
         }
         progress.setRoadWaypointCursor(road.roadId(), i + 1);
      }

      // Only complete when every waypoint belonging to this chunk has been advanced past.
      for (int i = 0; i < pts.size(); i++) {
         BlockPos p = pts.get(i);
         if (p.getX() < minX || p.getX() > maxX || p.getZ() < minZ || p.getZ() > maxZ) {
            continue;
         }
         if (progress.roadWaypointCursor(road.roadId()) <= i) {
            return WorkOutcome.budget(ops);
         }
      }
      progress.roadsCompleteInChunk().add(road.roadId());
      progress.clearRoadWaypointCursor(road.roadId());
      return WorkOutcome.done(ops);
   }

   private static int walkableSurfaceY(ServerLevel level, int x, int z) {
      int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
      int min = level.getMinBuildHeight() + 1;
      for (int y = top; y >= min; y--) {
         BlockState state = level.getBlockState(new BlockPos(x, y, z));
         if (state.isAir()) {
            continue;
         }
         if (!state.getFluidState().isEmpty()) {
            return y;
         }
         if (state.canOcclude() || state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND)) {
            return y;
         }
      }
      return Math.max(min, top);
   }

   private static BlockState resolvePathState(String material) {
      return switch (material) {
         case "gravel" -> Blocks.GRAVEL.defaultBlockState();
         case "cobblestone", "cobble" -> Blocks.COBBLESTONE.defaultBlockState();
         case "stone_bricks", "stonebrick" -> Blocks.STONE_BRICKS.defaultBlockState();
         case "sandstone" -> Blocks.SANDSTONE.defaultBlockState();
         case "packed_mud" -> Blocks.PACKED_MUD.defaultBlockState();
         case "oak_planks", "planks" -> Blocks.OAK_PLANKS.defaultBlockState();
         default -> Blocks.DIRT_PATH.defaultBlockState();
      };
   }

   private record WorkOutcome(int ops, boolean budgetExhausted, boolean blocked, String reason) {
      static WorkOutcome done(int ops) {
         return new WorkOutcome(ops, false, false, "");
      }

      static WorkOutcome budget(int ops) {
         return new WorkOutcome(ops, true, false, "budget_exhausted");
      }

      static WorkOutcome blocked(String reason) {
         return new WorkOutcome(0, false, true, reason == null ? "" : reason);
      }

      static WorkOutcome blocked(String reason, int ops) {
         return new WorkOutcome(ops, false, true, reason == null ? "" : reason);
      }
   }
}
