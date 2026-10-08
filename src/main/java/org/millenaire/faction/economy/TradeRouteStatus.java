package org.millenaire.faction.economy;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Operational status of a directional faction trade connection.
 * Political systems in later phases may set {@link #DISRUPTED}; Phase 3 only provides the state.
 */
public enum TradeRouteStatus {
   ACTIVE,
   INACTIVE,
   DISRUPTED;

   @Nullable
   public static TradeRouteStatus tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return TradeRouteStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
