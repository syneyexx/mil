package org.millenaire.faction.war;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Strategic objectives for wartime AI. Settlement attack objectives support Phase 7 occupation/capture.
 */
public enum MilitaryObjectiveType {
   DEFEND_SETTLEMENT,
   ATTACK_SETTLEMENT,
   ATTACK_ARMY,
   GATHER_ARMY,
   PATROL,
   REINFORCE,
   RETREAT,
   HOLD_POSITION,
   RESPOND_TO_THREAT;

   @Nullable
   public static MilitaryObjectiveType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return MilitaryObjectiveType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   /**
    * Lower value = higher priority. Centralized priority ordering for AI.
    */
   public int basePriority() {
      return switch (this) {
         case RETREAT -> 10;
         case RESPOND_TO_THREAT -> 20;
         case DEFEND_SETTLEMENT -> 30;
         case ATTACK_SETTLEMENT -> 35;
         case REINFORCE -> 40;
         case ATTACK_ARMY -> 50;
         case HOLD_POSITION -> 60;
         case GATHER_ARMY -> 70;
         case PATROL -> 80;
      };
   }
}
