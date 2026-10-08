package org.millenaire.faction.conflict;

import java.util.Locale;
import javax.annotation.Nullable;

public enum PostConflictModifier {
   RECENT_CIVIL_WAR,
   RECENT_REVOLUTION,
   RECENT_REBELLION,
   RECENT_SECESSION,
   RECENT_DEFEAT,
   RECENT_REPRESSION,
   RECENT_REINTEGRATION;

   @Nullable
   public static PostConflictModifier tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return PostConflictModifier.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public int stabilityPenalty() {
      return switch (this) {
         case RECENT_CIVIL_WAR, RECENT_REVOLUTION -> 20;
         case RECENT_REBELLION, RECENT_SECESSION -> 12;
         case RECENT_DEFEAT, RECENT_REPRESSION -> 10;
         case RECENT_REINTEGRATION -> 8;
      };
   }
}
