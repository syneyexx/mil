package org.millenaire.faction.war;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Opposing camps in a war. Multiple factions may share a side.
 */
public enum WarSideId {
   ATTACKER,
   DEFENDER;

   @Nullable
   public static WarSideId tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return WarSideId.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public WarSideId opposite() {
      return this == ATTACKER ? DEFENDER : ATTACKER;
   }
}
