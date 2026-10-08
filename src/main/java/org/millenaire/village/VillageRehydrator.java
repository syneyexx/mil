package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.ConstructionTask;
import org.millenaire.building.PlacementStep;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.world.BuildingPlacer;
import org.slf4j.Logger;

public final class VillageRehydrator {
   private static final Logger LOGGER = LogUtils.getLogger();

   private VillageRehydrator() {
   }

   public static void rehydrateAll(ServerLevel level) {
      VillageSavedData data = VillageSavedData.get(level);

      for (Village village : data.getVillageManager().getAllVillages()) {
         VillageType villageType = ModCultures.getVillageType(village.getVillageTypeId());
         if (villageType != null) {
            village.resolveBrickTheme(villageType);
         }

         for (BuildingInstance building : village.getBuildings()) {
            if (building.getStatus() == BuildingInstance.Status.UNDER_CONSTRUCTION && building.getLevel() > 0) {
               building.setStatus(BuildingInstance.Status.UPGRADING);
               LOGGER.info("Migrated building {} from UNDER_CONSTRUCTION to UPGRADING (level {})", building.getPlanId(), building.getLevel());
            }

            BuildingPlan plan = ModCultures.getBuildingPlan(building.getPlanId());
            if (plan == null) {
               LOGGER.warn("Plan not found for rehydration: {}", building.getPlanId());
            } else {
               building.addRuntimeTags(plan.tags());
               building.resolveSpecialPoints(plan);
               building.initInventory();
               building.linkChestsToBuilding(level);
               if (building.isBeingBuilt()) {
                  if (building.getConstructionTask() != null) {
                     LOGGER.debug(
                        "Rehydration: {} — {} steps loaded from NBT, cursor at {}",
                        new Object[]{plan.id(), building.getConstructionTask().totalSteps(), building.getConstructionTask().getNextStepIndex()}
                     );
                  } else {
                     List<PlacementStep> steps = BuildingPlacer.compilePlacementSteps(level, plan, building.getOrigin(), building.getRotation(), building);
                     if (steps.isEmpty()) {
                        LOGGER.warn("No steps compiled for plan {} — missing template?", plan.id());
                     } else {
                        building.setConstructionTask(new ConstructionTask(steps, 0));
                        LOGGER.debug("Rehydration (legacy save): {} — {} steps recompiled from scratch", plan.id(), steps.size());
                     }
                  }
               }
            }
         }

         if (allVillageChunksLoaded(level, village)) {
            village.rebuildWaypointGraph(level);
         } else {
            LOGGER.info("Skipping waypoint graph rebuild for village {} — some chunks not loaded yet", village.getId());
         }
      }
   }

   private static boolean allVillageChunksLoaded(ServerLevel level, Village village) {
      for (ChunkPos cp : village.computeVillageChunks()) {
         if (!level.hasChunk(cp.x, cp.z)) {
            return false;
         }
      }

      return true;
   }
}
