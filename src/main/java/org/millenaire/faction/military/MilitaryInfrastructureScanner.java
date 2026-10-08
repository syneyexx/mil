package org.millenaire.faction.military;

import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;
import org.millenaire.faction.Faction;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;

/**
 * Scores military-related Millénaire buildings without inventing a new building system.
 */
public final class MilitaryInfrastructureScanner {
   private MilitaryInfrastructureScanner() {
   }

   public static int scoreFaction(ServerLevel level, Faction faction) {
      int score = 0;
      VillageSavedData villages = VillageSavedData.get(level);

      for (VillageId settlementId : faction.getSettlementIds()) {
         Village village = villages.getVillageManager().getVillage(settlementId);
         if (village != null) {
            score += scoreSettlement(village);
         }
      }

      return score;
   }

   public static int scoreSettlement(Village village) {
      int score = 0;

      for (BuildingInstance building : village.getBuildings()) {
         score += scoreBuilding(building);
      }

      return score;
   }

   public static int garrisonCapacityForSettlement(Village village) {
      int infra = scoreSettlement(village);
      return MilitaryConfig.BASE_GARRISON_CAPACITY + infra * MilitaryConfig.GARRISON_PER_INFRASTRUCTURE_POINT;
   }

   private static int scoreBuilding(BuildingInstance building) {
      if (building == null) {
         return 0;
      }

      int score = 0;
      BuildingPlanSet planSet = building.getPlanSetId() == null ? null : ModCultures.getBuildingPlanSet(building.getPlanSetId());
      if (planSet != null) {
         if (planSet.isWallSegment()) {
            score += 1;
         }

         if (planSet.isBorderBuilding()) {
            score += 1;
         }

         String category = planSet.category();
         if (category != null && category.equalsIgnoreCase("military")) {
            score += 2;
         }

         if (planSet.tags() != null) {
            for (String tag : planSet.tags()) {
               score += tagScore(tag);
            }
         }
      }

      String path = building.getPlanId() == null ? "" : building.getPlanId().getPath().toLowerCase();
      if (path.contains("barrack") || path.contains("fort") || path.contains("armoury") || path.contains("armory") || path.contains("watchtower") || path.contains("guardhouse") || path.contains("soldier")) {
         score += 2;
      }

      return score;
   }

   private static int tagScore(String tag) {
      if (tag == null) {
         return 0;
      }

      String lower = tag.toLowerCase();
      if (lower.contains("barrack") || lower.contains("armoury") || lower.contains("armory") || lower.equals("soldier") || lower.equals("guard") || lower.equals("patrol")) {
         return 2;
      }

      if (lower.contains("wall") || lower.contains("fort")) {
         return 1;
      }

      return 0;
   }
}
