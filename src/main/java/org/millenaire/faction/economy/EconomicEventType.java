package org.millenaire.faction.economy;

import java.util.Locale;
import javax.annotation.Nullable;

/** Meaningful faction-level economic events for bounded history. */
public enum EconomicEventType {
   MAJOR_TRADE,
   MAJOR_EXPORT,
   MAJOR_IMPORT,
   RESOURCE_SHORTAGE,
   RESOURCE_SURPLUS,
   TRADE_ROUTE_ESTABLISHED,
   TRADE_ROUTE_DISRUPTED,
   TRADE_ROUTE_RESTORED;

   @Nullable
   public static EconomicEventType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return EconomicEventType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
