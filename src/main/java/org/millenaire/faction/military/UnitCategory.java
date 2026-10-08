package org.millenaire.faction.military;

import java.util.Locale;
import javax.annotation.Nullable;

public enum UnitCategory {
   INFANTRY,
   RANGED,
   CAVALRY,
   SPECIAL,
   MILITIA,
   GUARD,
   COMMAND;

   @Nullable
   public static UnitCategory tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return UnitCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
