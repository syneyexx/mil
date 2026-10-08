package org.millenaire.faction.war;

/**
 * Centralized war-exhaustion and peace-pressure evaluation.
 */
public final class WarExhaustionCalculator {
   private WarExhaustionCalculator() {
   }

   public static int compute(WarParticipant participant, long warDurationTicks, int lostBattles) {
      if (participant == null) {
         return 0;
      }

      int exhaustion = 0;
      exhaustion += (int)(warDurationTicks / 24000L) * WarConfig.WAR_EXHAUSTION_PER_DAY;
      exhaustion += participant.getCasualtiesSuffered() / 100 * WarConfig.WAR_EXHAUSTION_PER_100_CASUALTIES;
      exhaustion += lostBattles * WarConfig.WAR_EXHAUSTION_PER_LOST_BATTLE;
      return Math.min(WarConfig.WAR_EXHAUSTION_MAX, Math.max(0, exhaustion));
   }

   public static int peacePressure(War war, FactionIdView sidePerspective) {
      if (war == null) {
         return 0;
      }

      int pressure = 0;
      for (WarParticipant participant : war.getParticipants().values()) {
         if (!participant.isActive()) {
            continue;
         }

         if (sidePerspective == FactionIdView.ATTACKER && participant.getSide() != WarSideId.ATTACKER) {
            continue;
         }

         if (sidePerspective == FactionIdView.DEFENDER && participant.getSide() != WarSideId.DEFENDER) {
            continue;
         }

         pressure = Math.max(pressure, participant.getWarExhaustion());
      }

      int scorePressure = Math.abs(war.getWarScore());
      if (sidePerspective == FactionIdView.ATTACKER && war.getWarScore() < 0) {
         pressure = Math.max(pressure, scorePressure);
      }

      if (sidePerspective == FactionIdView.DEFENDER && war.getWarScore() > 0) {
         pressure = Math.max(pressure, scorePressure);
      }

      return WarConfig.clampPercent(pressure);
   }

   public static boolean shouldSeekPeace(int peacePressure) {
      return peacePressure >= WarConfig.PEACE_PRESSURE_THRESHOLD;
   }

   public enum FactionIdView {
      ATTACKER,
      DEFENDER,
      ANY
   }
}
