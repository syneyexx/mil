package org.millenaire.world;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.ClearMargins;
import org.millenaire.culture.ModCultures;
import org.slf4j.Logger;

public final class BuildingLocationFinder {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int NEIGHBOUR_CLIFF_SCAN_RADIUS = 8;
   private static final int NEIGHBOUR_CLIFF_SLACK = 3;

   private BuildingLocationFinder() {
   }

   @Nullable
   public static PlacedLocation findLocation(
      VillageTerrainMap map,
      BuildingPlan plan,
      BlockPos villageCenter,
      PlacementConstraints constraints,
      ClearMargins clearMargins,
      List<BuildingInstance> existingBuildings
   ) {
      return findLocation(map, plan, villageCenter, constraints, clearMargins, existingBuildings, null);
   }

   @Nullable
   public static PlacedLocation findLocation(
      VillageTerrainMap map,
      BuildingPlan plan,
      BlockPos villageCenter,
      PlacementConstraints constraints,
      ClearMargins clearMargins,
      List<BuildingInstance> existingBuildings,
      @Nullable TerrainReachability reachability
   ) {
      int centerX = villageCenter.getX();
      int centerZ = villageCenter.getZ();
      int minDist = constraints.minDistance();
      int maxDist = constraints.maxDistance();
      int sideOffset = ThreadLocalRandom.current().nextInt(4);

      for (int r = minDist; r <= maxDist; r++) {
         for (int sideIdx = 0; sideIdx < 4; sideIdx++) {
            int side = (sideIdx + sideOffset) % 4;
            PlacedLocation result = scanSide(side, r, centerX, centerZ, map, plan, villageCenter, constraints, clearMargins, existingBuildings, reachability);
            if (result != null) {
               return result;
            }
         }
      }

      return null;
   }

   @Nullable
   private static PlacedLocation scanSide(
      int side,
      int r,
      int centerX,
      int centerZ,
      VillageTerrainMap map,
      BuildingPlan plan,
      BlockPos villageCenter,
      PlacementConstraints constraints,
      ClearMargins clearMargins,
      List<BuildingInstance> existingBuildings,
      @Nullable TerrainReachability reachability
   ) {
      switch (side) {
         case 0:
            for (int x = -r; x <= r; x++) {
               PlacedLocation result = tryPosition(
                  centerX + x, centerZ - r, map, plan, villageCenter, constraints, clearMargins, existingBuildings, reachability
               );
               if (result != null) {
                  return result;
               }
            }
            break;
         case 1:
            for (int z = -r + 1; z <= r; z++) {
               PlacedLocation result = tryPosition(
                  centerX + r, centerZ + z, map, plan, villageCenter, constraints, clearMargins, existingBuildings, reachability
               );
               if (result != null) {
                  return result;
               }
            }
            break;
         case 2:
            for (int x = r - 1; x >= -r; x--) {
               PlacedLocation result = tryPosition(
                  centerX + x, centerZ + r, map, plan, villageCenter, constraints, clearMargins, existingBuildings, reachability
               );
               if (result != null) {
                  return result;
               }
            }
            break;
         case 3:
            for (int z = r - 1; z >= -r + 1; z--) {
               PlacedLocation result = tryPosition(
                  centerX - r, centerZ + z, map, plan, villageCenter, constraints, clearMargins, existingBuildings, reachability
               );
               if (result != null) {
                  return result;
               }
            }
      }

      return null;
   }

   @Nullable
   private static PlacedLocation tryPosition(
      int worldX,
      int worldZ,
      VillageTerrainMap map,
      BuildingPlan plan,
      BlockPos villageCenter,
      PlacementConstraints constraints,
      ClearMargins clearMargins,
      List<BuildingInstance> existingBuildings,
      @Nullable TerrainReachability reachability
   ) {
      int halfFootprint = (plan.width() + plan.depth()) / 4;
      int candidateCenterX = worldX + halfFootprint;
      int candidateCenterZ = worldZ + halfFootprint;
      int computedDir = directionToVillageCenter(candidateCenterX, candidateCenterZ, villageCenter);
      int targetDirection = constraints.fixedOrientation() != null ? constraints.fixedOrientation() : computedDir;
      int rotationOrdinal = Math.floorMod(targetDirection - plan.buildingOrientation(), 4);
      Rotation rotation = Rotation.values()[rotationOrdinal];
      BlockPos pivot = PlacedLocation.pivotForNwCorner(worldX, worldZ, plan.width(), plan.depth(), rotation);
      int pivotX = pivot.getX();
      int pivotZ = pivot.getZ();
      if (!checkTagConstraints(pivotX, pivotZ, constraints, existingBuildings)) {
         return null;
      }

      int result = map.testFootprint(pivotX, pivotZ, plan.width(), plan.depth(), clearMargins, rotation);
      if (result < 0) {
         return null;
      }

      if (reachability != null && !reachability.isFootprintReachable(pivotX, pivotZ, plan.width(), plan.depth(), rotation)) {
         return null;
      }

      BlockPos cliffCell = findNeighbourCliff(map, pivotX, pivotZ, plan.width(), plan.depth(), clearMargins, rotation);
      if (cliffCell != null) {
         return null;
      }

      BlockPos pos = new BlockPos(pivotX, villageCenter.getY(), pivotZ);
      if (LOGGER.isDebugEnabled()) {
         LOGGER.debug(
            "[Orient] plan={} bo={} fixed={} nwCorner=({},{}) wh={}x{} center=({},{}) thCenter=({},{}) dx={} dz={} target={} rot={} pivot=({},{})",
            new Object[]{
               plan.id(),
               plan.buildingOrientation(),
               constraints.fixedOrientation(),
               worldX,
               worldZ,
               plan.width(),
               plan.depth(),
               candidateCenterX,
               candidateCenterZ,
               villageCenter.getX(),
               villageCenter.getZ(),
               candidateCenterX - villageCenter.getX(),
               candidateCenterZ - villageCenter.getZ(),
               targetDirection,
               rotation,
               pivotX,
               pivotZ
            }
         );
      }

      return new PlacedLocation(pos, rotation);
   }

   public static BuildingLocationFinder.AnchorEvaluation evaluateAnchor(
      int worldX,
      int worldZ,
      VillageTerrainMap map,
      BuildingPlan plan,
      BlockPos villageCenter,
      PlacementConstraints constraints,
      ClearMargins clearMargins,
      List<BuildingInstance> existingBuildings,
      @Nullable TerrainReachability reachability,
      @Nullable Integer playerRotation
   ) {
      Rotation rotation;
      if (constraints.fixedOrientation() != null) {
         int rotationOrdinal = Math.floorMod(constraints.fixedOrientation() - plan.buildingOrientation(), 4);
         rotation = Rotation.values()[rotationOrdinal];
      } else if (playerRotation != null) {
         rotation = Rotation.values()[Math.floorMod(playerRotation, 4)];
      } else {
         int halfFootprint = (plan.width() + plan.depth()) / 4;
         int candidateCenterX = worldX + halfFootprint;
         int candidateCenterZ = worldZ + halfFootprint;
         int computedDir = directionToVillageCenter(candidateCenterX, candidateCenterZ, villageCenter);
         int rotationOrdinal = Math.floorMod(computedDir - plan.buildingOrientation(), 4);
         rotation = Rotation.values()[rotationOrdinal];
      }

      BlockPos pivot = PlacedLocation.pivotForNwCorner(worldX, worldZ, plan.width(), plan.depth(), rotation);
      int pivotX = pivot.getX();
      int pivotZ = pivot.getZ();
      if (!map.inBounds(map.toLocalX(pivotX), map.toLocalZ(pivotZ))) {
         return new BuildingLocationFinder.AnchorEvaluation(
            null, BuildingLocationFinder.FailureReason.OUTSIDE_RADIUS, new BlockPos(worldX, villageCenter.getY(), worldZ)
         );
      }

      if (!checkTagConstraints(pivotX, pivotZ, constraints, existingBuildings)) {
         return new BuildingLocationFinder.AnchorEvaluation(
            null, BuildingLocationFinder.FailureReason.GENERIC, new BlockPos(pivotX, villageCenter.getY(), pivotZ)
         );
      }

      BuildingLocationFinder.AnchorEvaluation footprintFailure = classifyFootprint(pivotX, pivotZ, plan, clearMargins, rotation, map, villageCenter);
      if (footprintFailure != null) {
         return footprintFailure;
      }

      if (reachability != null && !reachability.isFootprintReachable(pivotX, pivotZ, plan.width(), plan.depth(), rotation)) {
         return new BuildingLocationFinder.AnchorEvaluation(
            null, BuildingLocationFinder.FailureReason.NOT_REACHABLE, new BlockPos(pivotX, villageCenter.getY(), pivotZ)
         );
      }

      BlockPos cliffCell = findNeighbourCliff(map, pivotX, pivotZ, plan.width(), plan.depth(), clearMargins, rotation);
      if (cliffCell != null) {
         return new BuildingLocationFinder.AnchorEvaluation(null, BuildingLocationFinder.FailureReason.NOT_REACHABLE, cliffCell);
      }

      BlockPos pos = new BlockPos(pivotX, villageCenter.getY(), pivotZ);
      return new BuildingLocationFinder.AnchorEvaluation(new PlacedLocation(pos, rotation), null, null);
   }

   @Nullable
   static BlockPos findNeighbourCliff(VillageTerrainMap map, int pivotX, int pivotZ, int width, int depth, ClearMargins clearMargins, Rotation rotation) {
      int candidateAltitude = map.computeAverageAltitude(pivotX, pivotZ, width, depth, clearMargins, rotation);
      VillageTerrainMap.FootprintRect core = VillageTerrainMap.computeFootprintRect(pivotX, pivotZ, width, depth, ClearMargins.symmetric(0), rotation);
      int coreMinX = core.startX();
      int coreMaxX = core.startX() + core.width() - 1;
      int coreMinZ = core.startZ();
      int coreMaxZ = core.startZ() + core.depth() - 1;

      for (int wx = coreMinX - 8; wx <= coreMaxX + 8; wx++) {
         int gapX = wx < coreMinX ? coreMinX - wx : Math.max(wx - coreMaxX, 0);

         for (int wz = coreMinZ - 8; wz <= coreMaxZ + 8; wz++) {
            int gapZ = wz < coreMinZ ? coreMinZ - wz : Math.max(wz - coreMaxZ, 0);
            int gap = Math.max(gapX, gapZ);
            if (gap != 0) {
               int lx = map.toLocalX(wx);
               int lz = map.toLocalZ(wz);
               if (map.isOccupied(lx, lz)) {
                  int neighbourY = map.getTopGround(lx, lz);
                  if (Math.abs(candidateAltitude - neighbourY) > gap + 3) {
                     return new BlockPos(wx, neighbourY, wz);
                  }
               }
            }
         }
      }

      return null;
   }

   @Nullable
   private static BuildingLocationFinder.AnchorEvaluation classifyFootprint(
      int pivotX, int pivotZ, BuildingPlan plan, ClearMargins clearMargins, Rotation rotation, VillageTerrainMap map, BlockPos villageCenter
   ) {
      VillageTerrainMap.FootprintRect rect = VillageTerrainMap.computeFootprintRect(pivotX, pivotZ, plan.width(), plan.depth(), clearMargins, rotation);
      int surface = rect.width() * rect.depth();
      boolean hugeBuilding = surface > 2000;
      int allowedErrors;
      if (surface > 2000) {
         allowedErrors = surface / 10;
      } else if (surface > 200) {
         allowedErrors = surface / 20;
      } else {
         allowedErrors = 10;
      }

      int nbError = 0;

      for (int dx = 0; dx < rect.width(); dx++) {
         for (int dz = 0; dz < rect.depth(); dz++) {
            int wx = rect.startX() + dx;
            int wz = rect.startZ() + dz;
            int lx = map.toLocalX(wx);
            int lz = map.toLocalZ(wz);
            BlockPos errorPos = new BlockPos(wx, villageCenter.getY(), wz);
            if (!map.inBounds(lx, lz)) {
               return new BuildingLocationFinder.AnchorEvaluation(null, BuildingLocationFinder.FailureReason.OUTSIDE_RADIUS, errorPos);
            }

            if (map.isOccupied(lx, lz)) {
               return new BuildingLocationFinder.AnchorEvaluation(null, BuildingLocationFinder.FailureReason.LOCATION_CLASH, errorPos);
            }

            if (map.isBuildingForbiddenAt(lx, lz)) {
               if (!hugeBuilding || nbError > allowedErrors) {
                  return new BuildingLocationFinder.AnchorEvaluation(null, BuildingLocationFinder.FailureReason.CONSTRUCTION_FORBIDDEN, errorPos);
               }

               nbError++;
            } else if (map.isDangerAt(lx, lz)) {
               if (nbError > allowedErrors) {
                  return new BuildingLocationFinder.AnchorEvaluation(null, BuildingLocationFinder.FailureReason.DANGER, errorPos);
               }

               nbError++;
            } else if (!map.canBuildAt(lx, lz)) {
               if (nbError > allowedErrors) {
                  return new BuildingLocationFinder.AnchorEvaluation(null, BuildingLocationFinder.FailureReason.WRONG_ALTITUDE, errorPos);
               }

               nbError++;
            }
         }
      }

      return null;
   }

   static int directionToVillageCenter(int buildingX, int buildingZ, BlockPos villageCenter) {
      int relx = villageCenter.getX() - buildingX;
      int relz = villageCenter.getZ() - buildingZ;
      if (relx * relx > relz * relz) {
         return relx > 0 ? 1 : 3;
      } else {
         return relz > 0 ? 2 : 0;
      }
   }

   private static boolean checkTagConstraints(int worldX, int worldZ, PlacementConstraints constraints, List<BuildingInstance> existingBuildings) {
      for (Entry<String, Integer> entry : constraints.farFromTags().entrySet()) {
         String tag = entry.getKey();
         int distMin = entry.getValue();

         for (BuildingInstance building : existingBuildings) {
            BuildingPlan existingPlan = ModCultures.getBuildingPlan(building.getPlanId());
            if (existingPlan != null && existingPlan.hasTag(tag)) {
               double dist = horizontalDistance(worldX, worldZ, building.getOrigin());
               if (dist < distMin) {
                  return false;
               }
            }
         }
      }

      for (Entry<String, Integer> entry : constraints.closeToTags().entrySet()) {
         String tag = entry.getKey();
         int distMax = entry.getValue();
         boolean found = false;

         for (BuildingInstance building : existingBuildings) {
            BuildingPlan existingPlan = ModCultures.getBuildingPlan(building.getPlanId());
            if (existingPlan != null && existingPlan.hasTag(tag)) {
               double dist = horizontalDistance(worldX, worldZ, building.getOrigin());
               if (dist <= distMax) {
                  found = true;
                  break;
               }
            }
         }

         if (!found) {
            return false;
         }
      }

      return true;
   }

   private static double horizontalDistance(int x, int z, BlockPos pos) {
      int dx = x - pos.getX();
      int dz = z - pos.getZ();
      return Math.sqrt(dx * dx + dz * dz);
   }

   public record AnchorEvaluation(@Nullable PlacedLocation location, @Nullable BuildingLocationFinder.FailureReason reason, @Nullable BlockPos errorPos) {
      public boolean isSuccess() {
         return this.location != null;
      }
   }

   public enum FailureReason {
      OUTSIDE_RADIUS,
      LOCATION_CLASH,
      CONSTRUCTION_FORBIDDEN,
      WRONG_ALTITUDE,
      DANGER,
      NOT_REACHABLE,
      GENERIC;
   }
}
