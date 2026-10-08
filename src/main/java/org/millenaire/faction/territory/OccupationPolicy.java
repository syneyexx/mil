package org.millenaire.faction.territory;

import java.util.Locale;
import javax.annotation.Nullable;

public enum OccupationPolicy {
   LENIENT,
   STANDARD,
   HARSH,
   INTEGRATION;

   @Nullable
   public static OccupationPolicy tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return OccupationPolicy.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public int resistanceDeltaPerUpdate() {
      return switch (this) {
         case LENIENT -> -2;
         case STANDARD -> -1;
         case HARSH -> 2;
         case INTEGRATION -> -3;
      };
   }

   public int stabilityDeltaPerUpdate() {
      return switch (this) {
         case LENIENT -> 2;
         case STANDARD -> 1;
         case HARSH -> -1;
         case INTEGRATION -> 3;
      };
   }

   public int integrationDeltaPerUpdate() {
      return switch (this) {
         case LENIENT -> 1;
         case STANDARD -> 2;
         case HARSH -> 0;
         case INTEGRATION -> 4;
      };
   }

   public int garrisonRequirementMultiplierPermille() {
      return switch (this) {
         case LENIENT -> 800;
         case STANDARD -> 1000;
         case HARSH -> 1300;
         case INTEGRATION -> 1100;
      };
   }

   public int economicPenaltyPermille() {
      return switch (this) {
         case LENIENT -> 200;
         case STANDARD -> TerritoryConfig.ECONOMIC_OCCUPATION_PENALTY_PERMILLE;
         case HARSH -> 500;
         case INTEGRATION -> 250;
      };
   }
}
