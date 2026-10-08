package org.millenaire.faction.politics;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Discrete stance levels for government policies (not a free-form law simulator).
 */
public enum PolicyStance {
   LOW,
   NORMAL,
   HIGH,
   AGGRESSIVE,
   PASSIVE,
   OPEN,
   RESTRICTIVE,
   TRADITIONAL,
   REFORMIST;

   @Nullable
   public static PolicyStance tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return PolicyStance.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public int intensity() {
      return switch (this) {
         case LOW, PASSIVE, RESTRICTIVE, TRADITIONAL -> 25;
         case NORMAL, OPEN -> 50;
         case HIGH, REFORMIST -> 75;
         case AGGRESSIVE -> 90;
      };
   }
}
