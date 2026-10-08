package org.millenaire.faction.military;

import java.util.Locale;
import javax.annotation.Nullable;

public enum UnitStatus {
   RESERVE,
   IN_ARMY,
   GARRISONED,
   FORMING,
   DISABLED;

   @Nullable
   public static UnitStatus tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return UnitStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
