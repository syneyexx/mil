package org.millenaire.faction.military;

import java.util.Locale;
import javax.annotation.Nullable;

public enum TrainingLevel {
   UNTRAINED(0),
   PARTIAL(1),
   TRAINED(2),
   HIGHLY_PREPARED(3);

   private final int rank;

   TrainingLevel(int rank) {
      this.rank = rank;
   }

   public int getRank() {
      return this.rank;
   }

   public static TrainingLevel fromReadiness(int readinessPercent) {
      int value = MilitaryConfig.clampPercent(readinessPercent);
      if (value >= MilitaryConfig.READINESS_HIGH) {
         return HIGHLY_PREPARED;
      }

      if (value >= MilitaryConfig.READINESS_TRAINED) {
         return TRAINED;
      }

      if (value >= MilitaryConfig.READINESS_PARTIAL) {
         return PARTIAL;
      }

      return UNTRAINED;
   }

   @Nullable
   public static TrainingLevel tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return TrainingLevel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
