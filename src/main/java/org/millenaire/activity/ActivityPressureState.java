package org.millenaire.activity;

public enum ActivityPressureState {
   HEALTHY,
   SOFT,
   HARD;

   public double targetCoordinatorMs() {
      return switch (this) {
         case HEALTHY -> 0.50;
         case SOFT -> 0.25;
         case HARD -> 0.10;
      };
   }

   public int candidateScalePercent() {
      return switch (this) {
         case HEALTHY -> 100;
         case SOFT -> 50;
         case HARD -> 0;
      };
   }

   public boolean allowsNewGroups() {
      return this != HARD;
   }

   public boolean allowsNewVillageRaids() {
      return this == HEALTHY;
   }

   public boolean allowsMaterialization() {
      return this != HARD;
   }
}
