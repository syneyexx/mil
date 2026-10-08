package org.millenaire.faction.war;

import java.util.Locale;
import javax.annotation.Nullable;

public enum WarGoalStatus {
   ACTIVE,
   PROGRESSING,
   COMPLETED,
   FAILED,
   CANCELLED;

   @Nullable
   public static WarGoalStatus tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return WarGoalStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public boolean isTerminal() {
      return this == COMPLETED || this == FAILED || this == CANCELLED;
   }
}
