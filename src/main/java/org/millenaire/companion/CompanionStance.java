package org.millenaire.companion;

import java.util.Locale;
import javax.annotation.Nullable;

public enum CompanionStance {
   STICK_CLOSE,
   FREE_ROAM,
   VILLAGE_DAY_JOB;

   @Nullable
   public static CompanionStance parse(@Nullable String raw, @Nullable CompanionStance fallback) {
      if (raw == null || raw.isBlank()) {
         return fallback;
      }
      try {
         return CompanionStance.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return fallback;
      }
   }
}
