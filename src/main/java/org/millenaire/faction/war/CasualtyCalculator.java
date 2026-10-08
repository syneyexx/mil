package org.millenaire.faction.war;

/**
 * Centralized casualty formulas for battle rounds.
 */
public final class CasualtyCalculator {
   private CasualtyCalculator() {
   }

   public static int casualtiesForSide(int ownPower, int enemyPower, int ownSoldierCount, int roundsSeed) {
      if (ownSoldierCount <= 0 || enemyPower <= 0) {
         return 0;
      }

      double ratio = enemyPower / (double)Math.max(1, ownPower + enemyPower);
      int base = (int)Math.round(ownSoldierCount * ratio * WarConfig.MAX_CASUALTY_FRACTION_PER_ROUND_PERMILLE / 1000.0);
      int variance = (int)((roundsSeed & 3L) - 1L);
      int result = Math.max(WarConfig.MIN_CASUALTIES_PER_ROUND, base + variance);
      return Math.min(ownSoldierCount, Math.max(0, result));
   }
}
