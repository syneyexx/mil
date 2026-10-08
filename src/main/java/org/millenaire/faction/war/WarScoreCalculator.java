package org.millenaire.faction.war;

/**
 * Centralized war-score deltas. Positive favors the attacker side.
 */
public final class WarScoreCalculator {
   private WarScoreCalculator() {
   }

   public static int battleOutcomeDelta(WarSideId victorSide) {
      if (victorSide == WarSideId.ATTACKER) {
         return WarConfig.WAR_SCORE_BATTLE_WIN;
      }

      if (victorSide == WarSideId.DEFENDER) {
         return WarConfig.WAR_SCORE_BATTLE_LOSS;
      }

      return 0;
   }

   public static int casualtyDelta(int inflictedByAttacker, int sufferedByAttacker) {
      int gain = inflictedByAttacker / 100 * WarConfig.WAR_SCORE_PER_100_CASUALTIES_INFLICTED;
      int loss = sufferedByAttacker / 100 * WarConfig.WAR_SCORE_PER_100_CASUALTIES_SUFFERED;
      return gain + loss;
   }

   public static int armyDestroyedDelta(WarSideId destroyedSide) {
      return destroyedSide == WarSideId.DEFENDER ? WarConfig.WAR_SCORE_ARMY_DESTROYED : -WarConfig.WAR_SCORE_ARMY_DESTROYED;
   }

   public static int successfulDefenseDelta() {
      return -WarConfig.WAR_SCORE_SUCCESSFUL_DEFENSE;
   }

   public static int goalProgressDelta(WarSideId goalOwnerSide) {
      return goalOwnerSide == WarSideId.ATTACKER ? WarConfig.WAR_SCORE_GOAL_PROGRESS : -WarConfig.WAR_SCORE_GOAL_PROGRESS;
   }
}
