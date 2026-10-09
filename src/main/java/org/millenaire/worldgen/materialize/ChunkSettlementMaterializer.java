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
import org.millenaire.worldgen.WorldgenMetrics;
import org.millenaire.world.PlacementConstraints;
import org.millenaire.world.TerrainPreparer;
import org.millenaire.worldgen.plan.PlannedBuilding;
import org.millenaire.worldgen.plan.PlannedRoad;
import org.millenaire.worldgen.plan.PlannedWall;
import org.millenaire.worldgen.plan.SettlementFailureReason;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;
import org.millenaire.worldgen.plan.SettlementPlanStatus;
import org.slf4j.Logger;

/**
 * Stage B — chunk-safe physical materialization.
 * Places only blocks belonging to the newly generated chunk. Idempotent per chunk/building.
 */
public final class ChunkSettlementMaterializer {
   private static final Logger LOGGER = LogUtils.getLogger();

   private ChunkSettlementMaterializer() {
   }

   public static void materializeChunk(ServerLevel level, ChunkPos chunk, SettlementPlan plan) {
      if (plan.status().isTerminalFailure() || plan.activated()) {
         return;
      }
      if (!plan.status().allowsMaterialization() && plan.status() != SettlementPlanStatus.PHYSICAL_COMPLETE) {
         if (plan.status() == SettlementPlanStatus.PLANNED || plan.status() == SettlementPlanStatus.RESERVED) {
            // ok
         } else if (plan.status() != SettlementPlanStatus.MATERIALIZING) {
            return;
         }
      }

      long packed = chunk.toLong();
      if (plan.materializedChunks().contains(packed)) {
         return;
      }

      if (!plan.trySetStatus(SettlementPlanStatus.MATERIALIZING) && plan.status() != SettlementPlanStatus.MATERIALIZING) {
         if (plan.status() == SettlementPlanStatus.RESERVED || plan.status() == SettlementPlanStatus.PLANNED) {
            plan.forceStatus(SettlementPlanStatus.MATERIALIZING);
         }
      }

      int budget = MillenaireServerConfig.SERVER.worldgenMaterializationBudget.getAsInt();
      int placed = 0;

      try {
         for (PlannedBuilding building : plan.buildings()) {
            if (building.materialized()) {
               continue;
            }
            if (!building.intersectsChunk(chunk)) {
               continue;
            }
            placed += materializeBuildingInChunk(level, chunk, building);
            if (placed >= budget) {
               break;
            }
            if (buildingChunksComplete(level, building)) {
               building.setMaterialized(true);
               building.setChunksComplete(true);
            }
         }

         if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenGenerateStartingWalls.get())) {
            for (PlannedWall wall : plan.walls()) {
               if (wall.materialized()) {
                  continue;
               }
               if (wallIntersectsChunk(wall, chunk)) {
                  materializeWallInChunk(level, chunk, wall);
                  if (wallChunksComplete(wall)) {
                     wall.setMaterialized(true);
                  }
               }
            }
         }

         if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenGenerateInitialRoads.get())) {
            for (PlannedRoad road : plan.roads()) {
               if (road.materialized()) {
                  continue;
               }
               if (road.intersectsChunk(chunk)) {
                  materializeRoadInChunk(level, chunk, plan, road);
               }
            }
            maybeCompleteRoads(plan);
         }

         plan.materializedChunks().add(packed);
         WorldgenMetrics.CHUNKS_MATERIALIZED.incrementAndGet();
         refreshBuildingCompletion(plan);
         refreshWallCompletion(plan);

         if (plan.allBuildingsMaterialized() && wallsDone(plan) && roadsDone(plan)) {
            plan.trySetStatus(SettlementPlanStatus.PHYSICAL_COMPLETE);
            if (plan.status() != SettlementPlanStatus.PHYSICAL_COMPLETE) {
               plan.forceStatus(SettlementPlanStatus.PHYSICAL_COMPLETE);
            }
         }
         SettlementPlanSavedData.get(level).markDirtyPlan();
      } catch (Exception e) {
         LOGGER.error("[Millenaire] Materialization failed for plan {} chunk {}", plan.planId(), chunk, e);
         plan.markFailed(SettlementFailureReason.CHUNK_MATERIALIZATION_FAILURE, e.getClass().getSimpleName() + ": " + e.getMessage());
         SettlementPlanSavedData.get(level).markDirtyPlan();
      }
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
         boolean all = true;
         for (BlockPos p : road.waypoints()) {
            // waypoints are considered placed once their chunks were visited
            ChunkPos cp = new ChunkPos(p);
            if (!plan.materializedChunks().contains(cp.toLong()) && !plan.materializedChunks().contains(ChunkPos.asLong(cp.x, cp.z))) {
               // still waiting for chunk — keep unfinished unless empty
               all = false;
               break;
            }
         }
         // Mark complete when plan has processed every waypoint chunk at least once after road paint attempts
         if (all || road.waypoints().isEmpty()) {
            road.setMaterialized(true);
         }
      }
   }

   private static int materializeBuildingInChunk(ServerLevel level, ChunkPos chunk, PlannedBuilding building) {
      BuildingPlan plan = ModCultures.getBuildingPlan(building.planId());
      if (plan == null) {
         building.setMaterialized(true);
         return 0;
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
      // Terrain prep clipped to this chunk
      flattenFootprintInChunk(level, chunk, origin, plan, building.rotation(), baseY, margins);

      List<PlacementStep> steps = BuildingPlacer.compilePlacementSteps(level, plan, origin, building.rotation());
      int count = 0;
      int minX = chunk.getMinBlockX();
      int maxX = chunk.getMaxBlockX();
      int minZ = chunk.getMinBlockZ();
      int maxZ = chunk.getMaxBlockZ();

      for (PlacementStep step : steps) {
         BlockPos worldPos = step.relativePos().offset(origin);
         if (worldPos.getX() < minX || worldPos.getX() > maxX || worldPos.getZ() < minZ || worldPos.getZ() > maxZ) {
            continue;
         }
         if (step.phase() == PlacementPhase.DELETION) {
            if (!level.getBlockState(worldPos).isAir()) {
               level.setBlock(worldPos, Blocks.AIR.defaultBlockState(), 2);
               count++;
            }
            continue;
         }
         BlockState state = step.blockState();
         if (state.isAir()) {
            continue;
         }
         BlockState fixed = BuildingPlacer.applyFacingGuess(
            level, worldPos, state, step.guessChestFacing(), step.guessTorchFacing(), step.guessFurnaceFacing()
         );
         level.setBlock(worldPos, fixed, 2);
         count++;
      }
      return count;
   }

   private static void flattenFootprintInChunk(
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
      int maxX = Math.min(chunk.getMaxBlockX(), origin.getX() + effW + padX1);
      int minZ = Math.max(chunk.getMinBlockZ(), origin.getZ() - padZ0);
      int maxZ = Math.min(chunk.getMaxBlockZ(), origin.getZ() + effD + padZ1);
      int maxMod = MillenaireServerConfig.SERVER.worldgenMaximumTerrainModification.getAsInt();
      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            if (Math.abs(surface - baseY) > maxMod) {
               continue;
            }
            // Clear above platform
            for (int y = baseY + 1; y <= Math.min(surface + plan.height(), baseY + plan.height() + 4); y++) {
               BlockPos p = new BlockPos(x, y, z);
               if (!level.getBlockState(p).isAir()) {
                  level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
               }
            }
            // Ensure support at base
            BlockPos ground = new BlockPos(x, baseY, z);
            if (level.getBlockState(ground).isAir() || level.getBlockState(ground).canBeReplaced()) {
               BlockState filler = level.getBlockState(new BlockPos(x, Math.max(level.getMinBuildHeight(), baseY - 1), z));
               if (filler.isAir()) {
                  filler = Blocks.DIRT.defaultBlockState();
               }
               level.setBlock(ground, filler, 2);
            }
         }
      }
   }

   private static boolean buildingChunksComplete(ServerLevel level, PlannedBuilding building) {
      // Conservative: mark complete after first successful intersecting-chunk placement pass when
      // all footprint chunks are in the plan's materialized set — checked by caller after add.
      // Here we check geometric coverage expectation: single-chunk buildings complete immediately.
      int minCx = building.origin().getX() >> 4;
      int maxCx = (building.origin().getX() + Math.max(building.width(), 1) - 1) >> 4;
      int minCz = building.origin().getZ() >> 4;
      int maxCz = (building.origin().getZ() + Math.max(building.depth(), 1) - 1) >> 4;
      return minCx == maxCx && minCz == maxCz;
   }

   /**
    * After a chunk is recorded, mark multi-chunk buildings complete if all their chunks are done.
    */
   public static void refreshBuildingCompletion(SettlementPlan plan) {
      for (PlannedBuilding building : plan.buildings()) {
         if (building.materialized()) {
            continue;
         }
         int minCx = building.origin().getX() >> 4;
         int maxCx = (building.origin().getX() + Math.max(building.width(), 1) - 1) >> 4;
         int minCz = building.origin().getZ() >> 4;
         int maxCz = (building.origin().getZ() + Math.max(building.depth(), 1) - 1) >> 4;
         boolean all = true;
         for (int cx = minCx; cx <= maxCx && all; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
               if (!plan.materializedChunks().contains(ChunkPos.asLong(cx, cz))) {
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
               if (!plan.materializedChunks().contains(ChunkPos.asLong(cx, cz))) {
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
      int maxX = minX + Math.max(wall.width(), 1);
      int maxZ = minZ + Math.max(wall.depth(), 1);
      return minX <= chunk.getMaxBlockX() && maxX >= chunk.getMinBlockX() && minZ <= chunk.getMaxBlockZ() && maxZ >= chunk.getMinBlockZ();
   }

   private static boolean wallChunksComplete(PlannedWall wall) {
      int minCx = wall.origin().getX() >> 4;
      int maxCx = (wall.origin().getX() + Math.max(wall.width(), 1) - 1) >> 4;
      int minCz = wall.origin().getZ() >> 4;
      int maxCz = (wall.origin().getZ() + Math.max(wall.depth(), 1) - 1) >> 4;
      return minCx == maxCx && minCz == maxCz;
   }

   private static void materializeWallInChunk(ServerLevel level, ChunkPos chunk, PlannedWall wall) {
      BuildingPlan plan = ModCultures.getBuildingPlan(wall.planId());
      if (plan == null) {
         wall.setMaterialized(true);
         return;
      }
      PlannedBuilding proxy = new PlannedBuilding(
         wall.segmentId(),
         wall.planSetId(),
         wall.planId(),
         "default",
         0,
         wall.gate() ? "gate" : "wall",
         wall.origin(),
         wall.rotation(),
         wall.width(),
         wall.depth(),
         plan.groundLevel()
      );
      materializeBuildingInChunk(level, chunk, proxy);
      if (buildingChunksComplete(level, proxy)) {
         wall.setMaterialized(true);
      }
   }

   private static void materializeRoadInChunk(ServerLevel level, ChunkPos chunk, SettlementPlan plan, PlannedRoad road) {
      List<String> materials = List.of("dirt_path", "gravel", "cobblestone");
      ResourceLocation typeId = plan.villageTypeId();
      if (typeId != null) {
         var vt = ModCultures.getVillageType(typeId);
         if (vt != null && !vt.pathMaterials().isEmpty()) {
            materials = vt.pathMaterials();
         }
      }
      BlockState pathState = resolvePathState(materials.isEmpty() ? "dirt_path" : materials.getFirst());
      int minX = chunk.getMinBlockX();
      int maxX = chunk.getMaxBlockX();
      int minZ = chunk.getMinBlockZ();
      int maxZ = chunk.getMaxBlockZ();
      List<BlockPos> pts = road.waypoints();
      for (int i = 0; i < pts.size(); i++) {
         BlockPos p = pts.get(i);
         if (p.getX() < minX || p.getX() > maxX || p.getZ() < minZ || p.getZ() > maxZ) {
            continue;
         }
         int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, p.getX(), p.getZ()) - 1;
         BlockPos surface = new BlockPos(p.getX(), y, p.getZ());
         BlockState cur = level.getBlockState(surface);
         if (cur.getFluidState().isEmpty() && !cur.is(Blocks.BEDROCK)) {
            level.setBlock(surface, pathState, 2);
         }
         // Widen one block for primary roads
         for (BlockPos side : List.of(surface.north(), surface.south(), surface.east(), surface.west())) {
            if (side.getX() < minX || side.getX() > maxX || side.getZ() < minZ || side.getZ() > maxZ) {
               continue;
            }
            BlockState s = level.getBlockState(side);
            if (s.getFluidState().isEmpty() && (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL))) {
               level.setBlock(side, pathState, 2);
            }
         }
      }
   }

   private static BlockState resolvePathState(String material) {
      return switch (material) {
         case "gravel" -> Blocks.GRAVEL.defaultBlockState();
         case "cobblestone", "cobble" -> Blocks.COBBLESTONE.defaultBlockState();
         case "stone_bricks", "stonebrick" -> Blocks.STONE_BRICKS.defaultBlockState();
         case "sandstone" -> Blocks.SANDSTONE.defaultBlockState();
         case "packed_mud" -> Blocks.PACKED_MUD.defaultBlockState();
         default -> Blocks.DIRT_PATH.defaultBlockState();
      };
   }
}
