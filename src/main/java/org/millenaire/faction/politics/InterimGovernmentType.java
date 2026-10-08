package org.millenaire.faction.politics;

import java.util.Locale;
import javax.annotation.Nullable;

public enum InterimGovernmentType {
   NONE,
   REGENCY,
   INTERIM_COUNCIL,
   MILITARY_COMMAND,
   TEMPORARY_LEADERSHIP;

   @Nullable
   public static InterimGovernmentType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return InterimGovernmentType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public boolean isActive() {
      return this != NONE;
   }
}
