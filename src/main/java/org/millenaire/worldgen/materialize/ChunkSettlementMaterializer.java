package org.millenaire.worldgen.materialize;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ClearMargins;
import org.millenaire.building.PlacementPhase;
import org.millenaire.building.PlacementStep;
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

      MaterializationTickBudget.ensureTick(level.getGameTime());
      if (!MaterializationTickBudget.hasOps()) {
         SettlementPlanSavedData.get(level).resumeQueue().offer(plan.planId(), packed);
         ChunkMaterializationProgress progress = plan.progressFor(packed);
         progress.setStatus(ChunkMaterializationProgress.Status.BLOCKED);
         progress.setBlockReason("budget_exhausted");
         return PassResult.BUDGET_EXHAUSTED;
      }

      if (!plan.trySetStatus(SettlementPlanStatus.MATERIALIZING) && plan.status() != SettlementPlanStatus.MATERIALIZING) {
         if (plan.status() == SettlementPlanStatus.RESERVED || plan.status() == SettlementPlanStatus.PLANNED) {
            plan.forceStatus(SettlementPlanStatus.MATERIALIZING);
         }
      }

      ChunkMaterializationProgress progress = plan.progressFor(packed);
      if (progress.status() != ChunkMaterializationProgress.Status.COMPLETE) {
         progress.setStatus(ChunkMaterializationProgress.Status.IN_PROGRESS);
         progress.setBlockReason("");
      }

      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      store.resumeQueue().offer(plan.planId(), packed);

      try {
         boolean budgetHit = false;
         boolean anyProgress = false;

         for (PlannedBuilding building : plan.buildings()) {
            if (!MaterializationTickBudget.hasScanCandidates()) {
               budgetHit = true;
               break;
            }
            MaterializationTickBudget.tryConsumeScan();
            if (building.materialized() || progress.isPlacementCompleteBuilding(building.buildingId())) {
               continue;
            }
            if (!building.intersectsChunk(chunk)) {
               continue;
            }
            WorkOutcome outcome = materializeBuildingInChunk(level, chunk, building, progress);
            if (outcome.ops() > 0) {
               anyProgress = true;
            }
            if (outcome.budgetExhausted()) {
               budgetHit = true;
               break;
            }
         }

         if (!budgetHit && Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenGenerateStartingWalls.get())) {
            for (PlannedWall wall : plan.walls()) {
               if (!MaterializationTickBudget.hasScanCandidates()) {
                  budgetHit = true;
                  break;
               }
               MaterializationTickBudget.tryConsumeScan();
               if (wall.materialized() || progress.isPlacementCompleteWall(wall.segmentId())) {
                  continue;
               }
               if (!wallIntersectsChunk(wall, chunk)) {
                  continue;
               }
               WorkOutcome outcome = materializeWallInChunk(level, chunk, wall, progress);
               if (outcome.ops() > 0) {
                  anyProgress = true;
               }
               if (outcome.budgetExhausted()) {
                  budgetHit = true;
                  break;
               }
            }
         }

         if (!budgetHit && Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenGenerateInitialRoads.get())) {
            for (PlannedRoad road : plan.roads()) {
               if (!MaterializationTickBudget.hasScanCandidates()) {
                  budgetHit = true;
                  break;
               }
               MaterializationTickBudget.tryConsumeScan();
               if (road.materialized() || progress.isRoadComplete(road.roadId())) {
                  continue;
               }
               if (!road.intersectsChunk(chunk)) {
                  continue;
               }
               WorkOutcome outcome = materializeRoadInChunk(level, chunk, plan, road, progress);
               if (outcome.ops() > 0) {
                  anyProgress = true;
               }
               if (outcome.budgetExhausted()) {
                  budgetHit = true;
                  break;
               }
            }
         }

         boolean chunkDone = isChunkWorkComplete(plan, chunk, progress);
         if (chunkDone) {
            plan.markChunkFullyMaterialized(packed);
            WorldgenMetrics.CHUNKS_MATERIALIZED.incrementAndGet();
            store.resumeQueue().remove(plan.planId(), packed);
            refreshBuildingCompletion(plan);
            refreshWallCompletion(plan);
            maybeCompleteRoads(plan);
            if (plan.allBuildingsMaterialized() && wallsDone(plan) && roadsDone(plan)) {
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

         progress.setStatus(ChunkMaterializationProgress.Status.IN_PROGRESS);
         progress.setBlockReason(anyProgress ? "partial" : "waiting_units");
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

   private static boolean wallsDone(SettlementPlan plan) {
      if (!Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenGenerateStartingWalls.get())) {
         return true;
      }
      for (PlannedWall w : plan.walls()) {
         if (!w.materialized()) {
            return false;
         }
      }
      return true;
   }

   private static boolean roadsDone(SettlementPlan plan) {
      if (!Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenGenerateInitialRoads.get())) {
         return true;
      }
      for (PlannedRoad r : plan.roads()) {
         if (!r.materialized()) {
            return false;
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
         progress.placementCompleteBuildings().add(building.buildingId());
         progress.terrainPreparedBuildings().add(building.buildingId());
         building.setMaterialized(true);
         building.setChunksComplete(true);
         return WorkOutcome.done(0);
      }

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
      boolean budgetExhausted = false;

      if (!progress.isTerrainPreparedBuilding(building.buildingId())) {
         WorkOutcome terrain = flattenFootprintInChunk(level, chunk, origin, plan, building.rotation(), baseY, margins);
         ops += terrain.ops();
         if (terrain.budgetExhausted()) {
            return WorkOutcome.budget(ops);
         }
         progress.terrainPreparedBuildings().add(building.buildingId());
      }

      List<PlacementStep> steps = BuildingPlacer.compilePlacementSteps(level, plan, origin, building.rotation());
      int minX = chunk.getMinBlockX();
      int maxX = chunk.getMaxBlockX();
      int minZ = chunk.getMinBlockZ();
      int maxZ = chunk.getMaxBlockZ();
      int cursor = progress.placementCursor();
      int index = 0;

      for (PlacementStep step : steps) {
         if (index++ < cursor) {
            continue;
         }
         if (!MaterializationTickBudget.hasOps()) {
            progress.setPlacementCursor(index - 1);
            budgetExhausted = true;
            break;
         }
         BlockPos worldPos = step.relativePos().offset(origin);
         if (worldPos.getX() < minX || worldPos.getX() > maxX || worldPos.getZ() < minZ || worldPos.getZ() > maxZ) {
            progress.setPlacementCursor(index);
            continue;
         }
         if (step.phase() == PlacementPhase.DELETION) {
            if (!level.getBlockState(worldPos).isAir()) {
               if (!MaterializationTickBudget.tryConsumeOp()) {
                  progress.setPlacementCursor(index - 1);
                  budgetExhausted = true;
                  break;
               }
               level.setBlock(worldPos, Blocks.AIR.defaultBlockState(), 2);
               ops++;
            }
            progress.setPlacementCursor(index);
            continue;
         }
         BlockState state = step.blockState();
         if (state.isAir()) {
            progress.setPlacementCursor(index);
            continue;
         }
         if (!MaterializationTickBudget.tryConsumeOp()) {
            progress.setPlacementCursor(index - 1);
            budgetExhausted = true;
            break;
         }
         BlockState fixed = BuildingPlacer.applyFacingGuess(
            level, worldPos, state, step.guessChestFacing(), step.guessTorchFacing(), step.guessFurnaceFacing()
         );
         level.setBlock(worldPos, fixed, 2);
         ops++;
         progress.setPlacementCursor(index);
      }

      if (!budgetExhausted) {
         progress.placementCompleteBuildings().add(building.buildingId());
         progress.setPlacementCursor(0);
         if (buildingChunksCovered(building, progress, null) || building.minChunkX() == building.maxChunkX() && building.minChunkZ() == building.maxChunkZ()) {
            // Single-chunk buildings complete immediately; multi-chunk wait for refreshBuildingCompletion.
            if (building.minChunkX() == building.maxChunkX() && building.minChunkZ() == building.maxChunkZ()) {
               building.setMaterialized(true);
               building.setChunksComplete(true);
            }
         }
         return WorkOutcome.done(ops);
      }
      return WorkOutcome.budget(ops);
   }

   private static WorkOutcome flattenFootprintInChunk(
      ServerLevel level,
      ChunkPos chunk,
      BlockPos origin,
      BuildingPlan plan,
      net.minecraft.world.level.block.Rotation rotation,
      int baseY,
      ClearMargins margins
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
      int maxMod = MillenaireServerConfig.SERVER.worldgenMaximumTerrainModification.getAsInt();
      int ops = 0;
      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            if (!MaterializationTickBudget.hasOps()) {
               return WorkOutcome.budget(ops);
            }
            int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            if (Math.abs(surface - baseY) > maxMod) {
               continue;
            }
            for (int y = baseY + 1; y <= Math.min(surface + plan.height(), baseY + plan.height() + 4); y++) {
               BlockPos p = new BlockPos(x, y, z);
               if (!level.getBlockState(p).isAir()) {
                  if (!MaterializationTickBudget.tryConsumeOp()) {
                     return WorkOutcome.budget(ops);
                  }
                  level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                  ops++;
               }
            }
            BlockPos ground = new BlockPos(x, baseY, z);
            if (level.getBlockState(ground).isAir() || level.getBlockState(ground).canBeReplaced()) {
               if (!MaterializationTickBudget.tryConsumeOp()) {
                  return WorkOutcome.budget(ops);
               }
               BlockState filler = level.getBlockState(new BlockPos(x, Math.max(level.getMinBuildHeight(), baseY - 1), z));
               if (filler.isAir()) {
                  filler = Blocks.DIRT.defaultBlockState();
               }
               level.setBlock(ground, filler, 2);
               ops++;
            }
         }
      }
      return WorkOutcome.done(ops);
   }

   private static boolean buildingChunksCovered(
      PlannedBuilding building,
      ChunkMaterializationProgress current,
      SettlementPlan plan
   ) {
      if (plan == null) {
         return false;
      }
      for (int cx = building.minChunkX(); cx <= building.maxChunkX(); cx++) {
         for (int cz = building.minChunkZ(); cz <= building.maxChunkZ(); cz++) {
            long packed = ChunkPos.asLong(cx, cz);
            if (plan.isChunkFullyMaterialized(packed)) {
               continue;
            }
            ChunkMaterializationProgress progress = packed == current.chunkPacked()
               ? current
               : plan.chunkProgress().get(packed);
            if (progress == null || !progress.isPlacementCompleteBuilding(building.buildingId())) {
               return false;
            }
         }
      }
      return true;
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
         progress.placementCompleteWalls().add(wall.segmentId());
         progress.terrainPreparedWalls().add(wall.segmentId());
         wall.setMaterialized(true);
         return WorkOutcome.done(0);
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
         WorkOutcome terrain = flattenFootprintInChunk(level, chunk, origin, plan, wall.rotation(), baseY, margins);
         ops += terrain.ops();
         if (terrain.budgetExhausted()) {
            return WorkOutcome.budget(ops);
         }
         progress.terrainPreparedWalls().add(wall.segmentId());
      }

      // Temporarily map wall into building progress by using a nested local progress mirror via building methods.
      // Instead place blocks directly with budget.
      List<PlacementStep> steps = BuildingPlacer.compilePlacementSteps(level, plan, origin, wall.rotation());
      int minX = chunk.getMinBlockX();
      int maxX = chunk.getMaxBlockX();
      int minZ = chunk.getMinBlockZ();
      int maxZ = chunk.getMaxBlockZ();
      for (PlacementStep step : steps) {
         BlockPos worldPos = step.relativePos().offset(origin);
         if (worldPos.getX() < minX || worldPos.getX() > maxX || worldPos.getZ() < minZ || worldPos.getZ() > maxZ) {
            continue;
         }
         if (!MaterializationTickBudget.hasOps()) {
            return WorkOutcome.budget(ops);
         }
         if (step.phase() == PlacementPhase.DELETION) {
            if (!level.getBlockState(worldPos).isAir()) {
               if (!MaterializationTickBudget.tryConsumeOp()) {
                  return WorkOutcome.budget(ops);
               }
               level.setBlock(worldPos, Blocks.AIR.defaultBlockState(), 2);
               ops++;
            }
            continue;
         }
         BlockState state = step.blockState();
         if (state.isAir()) {
            continue;
         }
         if (!MaterializationTickBudget.tryConsumeOp()) {
            return WorkOutcome.budget(ops);
         }
         BlockState fixed = BuildingPlacer.applyFacingGuess(
            level, worldPos, state, step.guessChestFacing(), step.guessTorchFacing(), step.guessFurnaceFacing()
         );
         level.setBlock(worldPos, fixed, 2);
         ops++;
      }
      progress.placementCompleteWalls().add(wall.segmentId());
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
      for (BlockPos p : pts) {
         if (p.getX() < minX || p.getX() > maxX || p.getZ() < minZ || p.getZ() > maxZ) {
            continue;
         }
         if (!MaterializationTickBudget.hasOps()) {
            return WorkOutcome.budget(ops);
         }
         int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, p.getX(), p.getZ()) - 1;
         BlockPos surface = new BlockPos(p.getX(), surfaceY, p.getZ());
         BlockState cur = level.getBlockState(surface);
         if (!cur.getFluidState().isEmpty()) {
            if (!bridges) {
               continue;
            }
            ops += placeBridgeSegment(level, surface, pathState);
         } else if (!cur.is(Blocks.BEDROCK)) {
            if (!MaterializationTickBudget.tryConsumeOp()) {
               return WorkOutcome.budget(ops);
            }
            level.setBlock(surface, pathState, 2);
            ops++;
         }
         for (BlockPos side : List.of(surface.north(), surface.south(), surface.east(), surface.west())) {
            if (side.getX() < minX || side.getX() > maxX || side.getZ() < minZ || side.getZ() > maxZ) {
               continue;
            }
            if (!MaterializationTickBudget.hasOps()) {
               return WorkOutcome.budget(ops);
            }
            BlockState s = level.getBlockState(side);
            if (!s.getFluidState().isEmpty()) {
               if (bridges) {
                  ops += placeBridgeSegment(level, side, pathState);
               }
               continue;
            }
            if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL)) {
               if (!MaterializationTickBudget.tryConsumeOp()) {
                  return WorkOutcome.budget(ops);
               }
               level.setBlock(side, pathState, 2);
               ops++;
            }
         }
      }
      progress.roadsCompleteInChunk().add(road.roadId());
      return WorkOutcome.done(ops);
   }

   /**
    * Deterministic short water crossing: stone support pillars + path deck.
    * Does not invent missing template files.
    */
   private static int placeBridgeSegment(ServerLevel level, BlockPos surface, BlockState deck) {
      int ops = 0;
      int y = surface.getY();
      while (y > level.getMinBuildHeight() + 1 && !level.getBlockState(new BlockPos(surface.getX(), y, surface.getZ())).canOcclude()) {
         if (!MaterializationTickBudget.tryConsumeOp()) {
            return ops;
         }
         level.setBlock(new BlockPos(surface.getX(), y, surface.getZ()), Blocks.STONE.defaultBlockState(), 2);
         ops++;
         y--;
      }
      if (MaterializationTickBudget.tryConsumeOp()) {
         level.setBlock(surface, deck.isAir() ? Blocks.OAK_PLANKS.defaultBlockState() : deck, 2);
         ops++;
      }
      BlockPos above = surface.above();
      if (level.getBlockState(above).canBeReplaced() && MaterializationTickBudget.tryConsumeOp()) {
         // leave air for passage — counted as scanned work already
      }
      return ops;
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

   private record WorkOutcome(int ops, boolean budgetExhausted) {
      static WorkOutcome done(int ops) {
         return new WorkOutcome(ops, false);
      }

      static WorkOutcome budget(int ops) {
         return new WorkOutcome(ops, true);
      }
   }
}
