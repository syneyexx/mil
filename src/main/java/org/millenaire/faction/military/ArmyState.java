package org.millenaire.faction.military;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Organizational army states for Phase 5. No battle/siege/war states.
 */
public enum ArmyState {
   DISBANDED,
   FORMING,
   READY,
   GARRISONED,
   RESERVE,
   MUSTERED,
   MOVING,
   STATIONED,
   DISABLED;

   @Nullable
   public static ArmyState tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return ArmyState.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public boolean isActiveOrganization() {
      return this != DISBANDED && this != DISABLED;
   }
}
