package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.building.ClearMargins;
import org.millenaire.building.ConstructionTask;
import org.millenaire.building.PlacementStep;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.world.BuildingPlacer;
import org.millenaire.world.TerrainPreparer;
import org.slf4j.Logger;

public final class WallGrowthManager {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final double RUSH_PLACEMENT_FLOOR_RATIO = 0.33;
   private static final String TAG_NO_UPGRADE_TILL_WALL_INITIALIZED = "no_upgrade_till_wall_initialized";

   private WallGrowthManager() {
   }

   static boolean isWallGrowthEligible(@Nullable VillageType villageType) {
      return villageType == null ? false : !villageType.loneBuilding();
   }

   public static void evaluate(ServerLevel level, Village village) {
      VillageType villageType = ModCultures.getVillageType(village.getVillageTypeId());
      if (isWallGrowthEligible(villageType)) {
         int maxSlots = computeMaxSlots(village, villageType);
         if (maxSlots > 0) {
            int ongoing = countOngoing(village);
            if (ongoing < maxSlots) {
               int available = maxSlots - ongoing;

               for (BuildingInstance b : village.getBuildings()) {
                  if (available <= 0) {
                     return;
                  }

                  if (b.getStatus() == BuildingInstance.Status.PLANNED && b.isWallSegment() && b.getPlanSetId() != null && b.getVariant() != null) {
                     BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(b.getPlanSetId());
                     if (planSet != null) {
                        BuildingPlanSet.LevelDef levelDef = planSet.getLevel(b.getVariant(), b.getLevel());
                        if (levelDef != null
                           && VillageGrowthManager.hasResourcesForWall(level, village, levelDef)
                           && launchPlanned(level, village, b, planSet, levelDef)) {
                           available--;
                        }
                     }
                  }
               }

               for (BuildingInstance b : village.getBuildings()) {
                  if (available <= 0) {
                     return;
                  }

                  if (b.getStatus() == BuildingInstance.Status.COMPLETE
                     && b.isWallSegment()
                     && b.isUpgradesAllowed()
                     && b.getPlanSetId() != null
                     && b.getVariant() != null) {
                     BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(b.getPlanSetId());
                     if (planSet != null && planSet.hasNextLevel(b.getVariant(), b.getLevel())) {
                        BuildingPlanSet.LevelDef nextLevel = planSet.getLevel(b.getVariant(), b.getLevel() + 1);
                        if (nextLevel != null
                           && !isBlockedByUninitializedWall(village, nextLevel)
                           && VillageGrowthManager.checkBuildConditions(village, b, nextLevel)
                           && VillageGrowthManager.hasResourcesForWall(level, village, nextLevel)) {
                           VillageGrowthManager.GrowthCandidate candidate = new VillageGrowthManager.GrowthCandidate(
                              planSet, nextLevel, b.getVariant(), Math.max(1, nextLevel.priority()), true, b, null
                           );
                           VillageGrowthManager.launchUpgrade(level, village, candidate);
                           available--;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   static boolean isBlockedByUninitializedWall(Village village, BuildingPlanSet.LevelDef nextLevel) {
      if (nextLevel.level() <= 0) {
         return false;
      }

      BuildingPlan nextPlan = ModCultures.getBuildingPlan(nextLevel.planId());
      if (nextPlan != null && nextPlan.hasTag("no_upgrade_till_wall_initialized")) {
         for (BuildingInstance other : village.getBuildings()) {
            if (other.isWallSegment()) {
               boolean awaitingLevelZero = other.getLevel() == 0
                  && (other.getStatus() == BuildingInstance.Status.PLANNED || other.getStatus() == BuildingInstance.Status.UNDER_CONSTRUCTION);
               if (awaitingLevelZero) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static int rush(ServerLevel level, Village village, double placementRatio, int maxUpgradePasses) {
      int count = 0;

      for (BuildingInstance b : new ArrayList<>(village.getBuildings())) {
         if (b.getStatus() == BuildingInstance.Status.PLANNED && b.isWallSegment()) {
            boolean shouldPlace = level.getRandom().nextDouble() < placementRatio || placementRatio > 0.33;
            if (shouldPlace && rushPlace(level, village, b)) {
               count++;
            }
         }
      }

      for (int pass = 0; pass < maxUpgradePasses; pass++) {
         boolean anyUpgrade = false;

         for (BuildingInstance b : new ArrayList<>(village.getBuildings())) {
            if (b.getStatus() == BuildingInstance.Status.COMPLETE && b.isWallSegment() && b.getPlanSetId() != null && b.getVariant() != null) {
               BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(b.getPlanSetId());
               if (planSet != null && planSet.hasNextLevel(b.getVariant(), b.getLevel())) {
                  BuildingPlanSet.LevelDef nextLevel = planSet.getLevel(b.getVariant(), b.getLevel() + 1);
                  if (nextLevel != null) {
                     BuildingPlan newPlan = ModCultures.getBuildingPlan(nextLevel.planId());
                     if (newPlan != null) {
                        BlockPos upgradeOrigin = VillageGrowthManager.recalculateOriginForUpgrade(b, newPlan);
                        if (!upgradeOrigin.equals(b.getOrigin())) {
                           b.setOrigin(upgradeOrigin);
                        }

                        BuildingPlacer.placeUpgradeInstantly(level, newPlan, upgradeOrigin, b.getRotation(), b);
                        b.startUpgrade(nextLevel.planId(), nextLevel.level());
                        b.markComplete();
                        BuildingFinalizer.applyPostPlacement(level, village, b, newPlan);
                        count++;
                        anyUpgrade = true;
                     }
                  }
               }
            }
         }

         if (!anyUpgrade) {
            break;
         }
      }

      if (count > 0) {
         BuildingFinalizer.applyVillageUpdates(level, village);
      }

      return count;
   }

   private static boolean rushPlace(ServerLevel level, Village village, BuildingInstance instance) {
      if (instance.getPlanSetId() != null && instance.getVariant() != null) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(instance.getPlanSetId());
         if (planSet == null) {
            return false;
         }

         BuildingPlan plan = ModCultures.getBuildingPlan(instance.getPlanId());
         if (plan == null) {
            return false;
         }

         BlockPos adjustedOrigin = instance.getOrigin();
         int baseY = adjustedOrigin.getY() - plan.groundLevel();
         BlockPos requestedOrigin = new BlockPos(adjustedOrigin.getX(), baseY, adjustedOrigin.getZ());
         Rotation rotation = instance.getRotation();
         VillageGrowthManager.initBrickColours(instance, planSet, village, level.getRandom());
         ClearMargins margins = planSet.clearMargins();
         boolean[][] snowMap = TerrainPreparer.checkForSnow(level, requestedOrigin, plan.width(), plan.depth(), rotation, margins);
         TerrainPreparer.clearAndFlattenAtY(level, requestedOrigin, plan.width(), plan.height(), plan.depth(), rotation, plan.groundLevel(), baseY, margins);
         TerrainPreparer.decayOrphanedLeaves(level, requestedOrigin, plan.width(), plan.depth(), rotation, baseY, margins);
         BuildingPlacer.placeInstantly(level, plan, adjustedOrigin, rotation, instance);
         TerrainPreparer.restoreSnow(level, requestedOrigin, plan.width(), plan.depth(), rotation, snowMap, margins);
         instance.markComplete();
         instance.setConstructionTask(null);
         BuildingFinalizer.applyPostPlacement(level, village, instance, plan);
         BuildingFinalizer.applyCompletionEffects(level, village, instance);
         SubBuildingHelper.spawnStartingSubBuildings(level, village, planSet, instance, false);
         BuildingPlanSet.LevelDef placedLevelDef = planSet.getLevel(instance.getVariant(), instance.getLevel());
         if (placedLevelDef != null) {
            SubBuildingHelper.spawnUpgradeSubBuildings(level, village, planSet, placedLevelDef, instance, false);
         }

         LOGGER.info("[Millénaire] Rush wall: placed {} L{} at {}", new Object[]{planSet.id(), instance.getLevel(), adjustedOrigin.toShortString()});
         return true;
      } else {
         return false;
      }
   }

   public static int computeMaxSlots(Village village, VillageType villageType) {
      int base = villageType.maxSimultaneousWallConstructions();
      int extras = 0;

      for (BuildingInstance b : village.getBuildings()) {
         if (b.getStatus() == BuildingInstance.Status.COMPLETE && b.getPlanSetId() != null) {
            BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(b.getPlanSetId());
            if (planSet != null) {
               extras += planSet.extraWallConstructionSlots();
            }
         }
      }

      return base + extras;
   }

   static int countOngoing(Village village) {
      int count = 0;

      for (BuildingInstance b : village.getBuildings()) {
         if (b.isBeingBuilt() && b.isWallSegment()) {
            count++;
         }
      }

      return count;
   }

   public static int countActiveBuilders(Village village) {
      int count = 0;

      for (BuildingInstance b : village.getBuildings()) {
         if (b.isBeingBuilt() && b.isWallSegment()) {
            ConstructionTask task = b.getConstructionTask();
            if (task != null && task.isReserved()) {
               count++;
            }
         }
      }

      return count;
   }

   private static boolean launchPlanned(
      ServerLevel level, Village village, BuildingInstance instance, BuildingPlanSet planSet, BuildingPlanSet.LevelDef levelDef
   ) {
      BuildingPlan plan = ModCultures.getBuildingPlan(levelDef.planId());
      if (plan == null) {
         return false;
      }

      BlockPos adjustedOrigin = instance.getOrigin();
      int baseY = adjustedOrigin.getY() - plan.groundLevel();
      BlockPos requestedOrigin = new BlockPos(adjustedOrigin.getX(), baseY, adjustedOrigin.getZ());
      Rotation rotation = instance.getRotation();
      VillageGrowthManager.initBrickColours(instance, planSet, village, level.getRandom());
      List<PlacementStep> terrainSteps = BuildingPlacer.compileTerrainPrepSteps(
         level, requestedOrigin, plan.width(), plan.height(), plan.depth(), baseY, adjustedOrigin, rotation, plan.groundLevel(), planSet.clearMargins()
      );
      List<PlacementStep> buildingSteps = BuildingPlacer.compilePlacementSteps(level, plan, adjustedOrigin, rotation, instance);
      List<PlacementStep> dedupedTerrainSteps = VillageGrowthManager.deduplicateTerrainSteps(terrainSteps, buildingSteps, plan.groundLevel());
      List<PlacementStep> allSteps = new ArrayList<>(dedupedTerrainSteps.size() + buildingSteps.size());
      allSteps.addAll(dedupedTerrainSteps);
      allSteps.addAll(buildingSteps);
      instance.markUnderConstruction();
      if (!allSteps.isEmpty()) {
         instance.setConstructionTask(new ConstructionTask(allSteps, 0));
      }

      SubBuildingHelper.spawnStartingSubBuildings(level, village, planSet, instance, false);
      SubBuildingHelper.spawnUpgradeSubBuildings(level, village, planSet, levelDef, instance, false);
      village.recordEvent(
         level, "Wall segment: " + planSet.id().getPath() + " (" + instance.getVariant() + "L" + instance.getLevel() + ") at " + adjustedOrigin.toShortString()
      );
      LOGGER.info("[Millénaire] Wall growth: launching {} L{} at {}", new Object[]{planSet.id(), instance.getLevel(), adjustedOrigin.toShortString()});
      return true;
   }
}
