package org.millenaire.faction.territory;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Faction-level territorial posture. Landless factions remain historically valid.
 */
public enum FactionTerritorialStatus {
   SETTLED,
   DISPLACED,
   EXILED,
   TERRITORIALLY_DEFEATED,
   LANDLESS;

   @Nullable
   public static FactionTerritorialStatus tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return FactionTerritorialStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public boolean isLandless() {
      return this == LANDLESS || this == EXILED || this == DISPLACED || this == TERRITORIALLY_DEFEATED;
   }
}
