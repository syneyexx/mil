package org.millenaire.worldgen.highway.travel;

public enum RoadJourneyKind {
   TRADER,
   WARRIOR,
   BANDIT,
   MOUNTED_TRADER,
   MOUNTED_WARRIOR;

   public boolean mounted() {
      return this == MOUNTED_TRADER || this == MOUNTED_WARRIOR;
   }

   public boolean hostile() {
      return this == BANDIT;
   }

   public static RoadJourneyKind fromId(String id) {
      try {
         return RoadJourneyKind.valueOf(id);
      } catch (Exception e) {
         return TRADER;
      }
   }
}
