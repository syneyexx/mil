package org.millenaire.faction.economy;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Why a trade attempt failed. Used for history; does not invent failures by itself.
 */
public enum TradeFailureReason {
   NONE,
   INSUFFICIENT_RESOURCES,
   INSUFFICIENT_DEMAND,
   ROUTE_DISRUPTED,
   SETTLEMENT_UNAVAILABLE,
   MERCHANT_FAILURE,
   STORAGE_FULL,
   /** Blocked by Phase 4 diplomatic embargo / political restriction. */
   EMBARGO,
   UNKNOWN;

   @Nullable
   public static TradeFailureReason tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return TradeFailureReason.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
