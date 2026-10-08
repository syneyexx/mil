package org.millenaire.faction.war;

import java.util.Locale;
import javax.annotation.Nullable;

public enum ArmyOrderType {
   MOVE,
   ATTACK,
   DEFEND,
   RETREAT,
   FOLLOW,
   GATHER,
   HOLD,
   NONE;

   @Nullable
   public static ArmyOrderType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return ArmyOrderType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
