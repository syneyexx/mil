package org.millenaire.faction.military;

import java.util.Locale;
import javax.annotation.Nullable;

public enum MobilizationState {
   DEMOBILIZED,
   PARTIAL,
   FULL;

   @Nullable
   public static MobilizationState tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return MobilizationState.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
