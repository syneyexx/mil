package org.millenaire.faction.territory;

import java.util.Locale;
import javax.annotation.Nullable;

public enum DisputeStatus {
   OPEN,
   SUSPENDED,
   RESOLVED,
   INVALIDATED;

   @Nullable
   public static DisputeStatus tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return DisputeStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public boolean isActive() {
      return this == OPEN || this == SUSPENDED;
   }
}
