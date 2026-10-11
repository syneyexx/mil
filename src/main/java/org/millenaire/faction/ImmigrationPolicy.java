package org.millenaire.faction;

import javax.annotation.Nullable;

/**
 * Controls whether a PLAYER faction accepts civilian immigration into its settlements.
 */
public enum ImmigrationPolicy {
   OPEN,
   RESTRICTED,
   CLOSED;

   public static ImmigrationPolicy tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return OPEN;
      }
      try {
         return valueOf(raw.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
         return OPEN;
      }
   }
}
