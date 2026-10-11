package org.millenaire.map.sync;

/**
 * Pure visibility rules for settlement map sync (unit-testable, no Minecraft types).
 */
public final class SettlementMapVisibility {
   private SettlementMapVisibility() {
   }

   /**
    * @param discovered whether the player has discovered this settlement instance
    * @param controlled whether the player owns/controls the settlement
    * @param sovereignPlayerCity whether this is the viewer's sovereign PLAYER-faction city
    * @param loneBuilding whether the settlement is a lone building
    * @param showLoneBuildings config: include lone buildings when otherwise visible
    */
   public static boolean shouldInclude(
      boolean discovered, boolean controlled, boolean sovereignPlayerCity, boolean loneBuilding, boolean showLoneBuildings
   ) {
      if (loneBuilding && !showLoneBuildings) {
         return false;
      }
      return discovered || controlled || sovereignPlayerCity;
   }

   public static boolean clientCategoryVisible(
      String developmentLabel,
      boolean sovereignPlayerCity,
      boolean showHamlets,
      boolean showVillages,
      boolean showTowns,
      boolean showCities,
      boolean showMyCity
   ) {
      if (sovereignPlayerCity) {
         return showMyCity;
      }
      return switch (developmentLabel == null ? "unknown" : developmentLabel) {
         case "hamlet" -> showHamlets;
         case "town" -> showTowns;
         case "regional_hub" -> showCities;
         case "village" -> showVillages;
         default -> showVillages;
      };
   }
}
