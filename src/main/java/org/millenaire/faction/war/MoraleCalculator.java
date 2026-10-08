package org.millenaire.faction.war;

/**
 * Centralized morale updates for armies and battle sides.
 */
public final class MoraleCalculator {
   private MoraleCalculator() {
   }

   public static int initialArmyMorale(int readinessPercent, int supplyPercent, int commanderLeadership) {
      int morale = WarConfig.MORALE_BASE;
      morale += (readinessPercent - 50) / 4;
      morale += (supplyPercent - 50) / 5;
      morale += commanderLeadership / 5;
      return WarConfig.clampMorale(morale);
   }

   public static int afterCasualties(int currentMorale, int casualties, int soldierCountBefore, boolean numericalDisadvantage) {
      int morale = currentMorale;
      if (soldierCountBefore > 0 && casualties > 0) {
         morale -= Math.max(1, casualties * 40 / soldierCountBefore);
      }

      if (numericalDisadvantage) {
         morale -= 5;
      }

      return WarConfig.clampMorale(morale);
   }

   public static int afterVictory(int currentMorale) {
      return WarConfig.clampMorale(currentMorale + 8);
   }

   public static int afterDefeat(int currentMorale) {
      return WarConfig.clampMorale(currentMorale - 15);
   }

   public static boolean shouldRetreat(int morale) {
      return morale <= WarConfig.MORALE_RETREAT_THRESHOLD;
   }

   public static boolean isCollapsed(int morale) {
      return morale <= WarConfig.MORALE_COLLAPSE_THRESHOLD;
   }
}
