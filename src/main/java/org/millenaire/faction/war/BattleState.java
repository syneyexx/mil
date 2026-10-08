package org.millenaire.faction.war;

import java.util.Locale;
import javax.annotation.Nullable;

public enum BattleState {
   PREPARING,
   ACTIVE,
   RETREATING,
   DECIDED,
   FINISHED;

   @Nullable
   public static BattleState tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return BattleState.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public boolean isOpen() {
      return this == PREPARING || this == ACTIVE || this == RETREATING;
   }

   public boolean isTerminal() {
      return this == DECIDED || this == FINISHED;
   }
}
