package org.millenaire.faction.war;

import java.util.Locale;
import javax.annotation.Nullable;

public enum WarState {
   DECLARED,
   MOBILIZING,
   ACTIVE,
   CEASEFIRE,
   PEACE_NEGOTIATION,
   ENDED;

   @Nullable
   public static WarState tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return WarState.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public boolean isOngoing() {
      return this != ENDED;
   }

   public boolean allowsCombat() {
      return this == ACTIVE || this == MOBILIZING;
   }

   public boolean allowsMovementOrders() {
      return this == ACTIVE || this == MOBILIZING || this == CEASEFIRE;
   }
}
