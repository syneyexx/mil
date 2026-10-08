package org.millenaire.faction.military;

import java.util.Locale;
import javax.annotation.Nullable;

public enum MilitaryAlertLevel {
   NORMAL,
   ELEVATED,
   HIGH,
   CRITICAL;

   @Nullable
   public static MilitaryAlertLevel tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return MilitaryAlertLevel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
