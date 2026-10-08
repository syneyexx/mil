package org.millenaire.faction.territory;

import java.util.Locale;
import javax.annotation.Nullable;

public enum TerritoryState {
   OWNED,
   CONTESTED,
   OCCUPIED,
   TRANSFER_PENDING,
   TRANSFERRED,
   INTEGRATING,
   INTEGRATED;

   @Nullable
   public static TerritoryState tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return TerritoryState.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public boolean isOccupiedControl() {
      return this == OCCUPIED || this == CONTESTED;
   }

   public boolean isIntegrating() {
      return this == INTEGRATING || this == TRANSFERRED;
   }
}
