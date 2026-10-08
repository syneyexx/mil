package org.millenaire.faction.politics;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Phase 8 foundation for internal conflict. Does not spawn rebel armies (Phase 9).
 */
public enum InternalConflictState {
   STABLE,
   POLITICAL_TENSION,
   CIVIL_UNREST,
   REBELLION_PRESSURE,
   INTERNAL_CONFLICT;

   @Nullable
   public static InternalConflictState tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return InternalConflictState.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public boolean isUnstable() {
      return this != STABLE;
   }
}
