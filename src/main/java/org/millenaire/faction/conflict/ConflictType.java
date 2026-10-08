package org.millenaire.faction.conflict;

import java.util.Locale;
import javax.annotation.Nullable;

public enum ConflictType {
   REBELLION,
   CIVIL_WAR,
   REVOLUTION,
   SECESSION,
   COUP_CONFLICT,
   SUCCESSION_CONFLICT,
   REGIONAL_UPRISING;

   @Nullable
   public static ConflictType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return ConflictType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public boolean seeksIndependence() {
      return this == SECESSION;
   }

   public boolean seeksGovernmentChange() {
      return this == REVOLUTION || this == COUP_CONFLICT || this == CIVIL_WAR;
   }

   public boolean isLocalized() {
      return this == REGIONAL_UPRISING || this == REBELLION;
   }
}
