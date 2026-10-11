package org.millenaire.worldgen.highway;

public enum HighwayNodeKind {
   MILLENAIRE,
   MILLENAIRE_PLAN,
   VANILLA_VILLAGE,
   MOD_SETTLEMENT;

   public static HighwayNodeKind fromId(String id) {
      if (id == null || id.isEmpty()) {
         return VANILLA_VILLAGE;
      }
      try {
         return HighwayNodeKind.valueOf(id);
      } catch (IllegalArgumentException e) {
         return VANILLA_VILLAGE;
      }
   }
}
