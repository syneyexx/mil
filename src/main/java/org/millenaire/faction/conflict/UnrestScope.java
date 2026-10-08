package org.millenaire.faction.conflict;

import java.util.Locale;
import javax.annotation.Nullable;

public enum UnrestScope {
   FACTION,
   POLITICAL_GROUP,
   SETTLEMENT,
   TERRITORY,
   REGION;

   @Nullable
   public static UnrestScope tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return UnrestScope.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
