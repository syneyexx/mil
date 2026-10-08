package org.millenaire.encyclopedia;

import java.util.List;

public record BuildingCostData(List<BuildingCostData.Variant> variants) {
   public record Level(int level, String kind, String size, List<BuildingCostData.Material> materials) {
      public static final String KIND_INITIAL = "initial";
      public static final String KIND_UPGRADE = "upgrade";
   }

   public record Material(String iconKey, LocalizedText label, int qty) {
   }

   public record Variant(String variant, List<BuildingCostData.Level> levels) {
   }
}
