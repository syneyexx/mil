package org.millenaire.village;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.RuntimeTagHelper;
import org.millenaire.village.panel.PanelPlacer;
import org.millenaire.world.BuildingPlacer;

public final class BuildingFinalizer {
   private BuildingFinalizer() {
   }

   public static void applyPostPlacement(ServerLevel level, Village village, BuildingInstance instance, BuildingPlan plan) {
      instance.resolveSpecialPoints(plan);
      BuildingPlacer.clearPlantsAboveSoil(level, instance);
      instance.initInventory();
      instance.linkChestsToBuilding(level);
      VillageBannerApplier.applyBanners(level, instance, village);
   }

   public static void applyCompletionEffects(ServerLevel level, Village village, BuildingInstance instance) {
      RuntimeTagHelper.applyCompletionTagsForBuilding(village, instance);
      PanelPlacer.placePanels(level, village, instance);
      if (!instance.getPointsByType("animalSpawn").isEmpty()) {
         village.updatePens(level, true);
      }
      if (instance.getPlanSetId() != null) {
         org.millenaire.building.BuildingPlanSet planSet = org.millenaire.culture.ModCultures.getBuildingPlanSet(instance.getPlanSetId());
         if (planSet != null && planSet.isTownHall()) {
            org.millenaire.compat.WaystonePlacer.tryPlaceForMillenaireVillage(level, village);
         }
      }
   }

   public static void applyVillageUpdates(ServerLevel level, Village village) {
      applyVillageUpdates(level, village, null);
   }

   public static void applyVillageUpdates(ServerLevel level, Village village, @Nullable BuildingInstance completedBuilding) {
      if (!isReachabilityNeutralWallUpgrade(completedBuilding)) {
         village.markWaypointGraphDirtyStructural();
      }

      village.sendFireplacePositions(level);
   }

   static boolean isReachabilityNeutralWallUpgrade(@Nullable BuildingInstance completedBuilding) {
      return completedBuilding != null && completedBuilding.isWallSegment() && completedBuilding.getLevel() > 0;
   }
}
