package org.millenaire.faction.territory;

import java.util.Locale;
import javax.annotation.Nullable;

public enum ClaimType {
   HISTORICAL,
   CULTURAL,
   DIPLOMATIC,
   WAR_GOAL,
   CORE;

   @Nullable
   public static ClaimType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return ClaimType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
