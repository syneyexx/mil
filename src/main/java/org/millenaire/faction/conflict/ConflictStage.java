package org.millenaire.faction.conflict;

import java.util.Locale;
import javax.annotation.Nullable;

public enum ConflictStage {
   POLITICAL_TENSION,
   UNREST,
   LOCAL_UPRISING,
   ORGANIZED_REBELLION,
   INTERNAL_WAR,
   CIVIL_WAR,
   REVOLUTION,
   SECESSION,
   RESOLUTION,
   POST_CONFLICT_SETTLEMENT,
   ENDED;

   @Nullable
   public static ConflictStage tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return ConflictStage.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public boolean isActive() {
      return this != ENDED && this != POST_CONFLICT_SETTLEMENT;
   }

   public boolean isArmed() {
      return this == ORGANIZED_REBELLION
         || this == INTERNAL_WAR
         || this == CIVIL_WAR
         || this == REVOLUTION
         || this == SECESSION;
   }

   public ConflictStage escalateToward(ConflictType type) {
      return switch (this) {
         case POLITICAL_TENSION -> UNREST;
         case UNREST -> LOCAL_UPRISING;
         case LOCAL_UPRISING -> ORGANIZED_REBELLION;
         case ORGANIZED_REBELLION -> INTERNAL_WAR;
         case INTERNAL_WAR -> switch (type) {
            case CIVIL_WAR -> CIVIL_WAR;
            case REVOLUTION -> REVOLUTION;
            case SECESSION -> SECESSION;
            default -> CIVIL_WAR;
         };
         default -> this;
      };
   }
}
