package org.millenaire.faction.conflict;

import java.util.Locale;
import javax.annotation.Nullable;

public enum MilitaryLoyalty {
   LOYAL_TO_GOVERNMENT,
   NEUTRAL,
   SUPPORTS_REBELS,
   SPLIT,
   LOCAL_COMMAND;

   @Nullable
   public static MilitaryLoyalty tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return MilitaryLoyalty.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
