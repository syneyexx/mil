package org.millenaire.faction.economy;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Why a trade route is disrupted. Labels only — Phase 3 does not implement the causing systems.
 */
public enum TradeDisruptionReason {
   NONE,
   HOSTILE_TERRITORY,
   WAR,
   INTERNAL_CONFLICT,
   EMBARGO,
   DESTROYED_ROUTE,
   RAID,
   BLOCKADE,
   MANUAL,
   UNKNOWN;

   @Nullable
   public static TradeDisruptionReason tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return TradeDisruptionReason.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
