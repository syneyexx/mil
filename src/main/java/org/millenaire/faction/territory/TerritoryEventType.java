package org.millenaire.faction.territory;

import java.util.Locale;
import javax.annotation.Nullable;

public enum TerritoryEventType {
   TERRITORY_CREATED,
   TERRITORY_CAPTURED,
   OCCUPATION_STARTED,
   OCCUPATION_ENDED,
   OCCUPATION_POLICY_CHANGED,
   RESISTANCE_CHANGED,
   STABILITY_CHANGED,
   LIBERATION,
   PEACE_SETTLEMENT,
   PERMANENT_TRANSFER,
   INTEGRATION_STARTED,
   INTEGRATION_COMPLETED,
   CLAIM_CREATED,
   CLAIM_REMOVED,
   CLAIM_RESOLVED,
   DISPUTE_CREATED,
   DISPUTE_RESOLVED,
   BORDER_UPDATED,
   CONTROL_COLLAPSED,
   FACTION_LANDLESS;

   @Nullable
   public static TerritoryEventType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return TerritoryEventType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
