package org.millenaire.village;

import javax.annotation.Nullable;

/**
 * Player-assigned building function for controlled villages.
 * Plan residents remain the source of which professions are possible.
 */
public enum BuildingPurpose {
   AUTO,
   HOUSING,
   PRODUCTION,
   MILITARY,
   TRADE,
   STORAGE,
   CULTURE;

   public static BuildingPurpose fromName(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return AUTO;
      }
      try {
         return valueOf(raw.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
         return AUTO;
      }
   }

   public String serializationName() {
      return this.name().toLowerCase();
   }
}
