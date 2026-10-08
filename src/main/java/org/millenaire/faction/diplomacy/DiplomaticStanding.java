package org.millenaire.faction.diplomacy;

/**
 * Derived summary of formal diplomatic status between two factions.
 * Distinct from Phase 2 relationship score and Phase 3 trade activity.
 */
public enum DiplomaticStanding {
   NEUTRAL,
   FRIENDLY,
   HOSTILE,
   PEACE,
   NON_AGGRESSION,
   TRADE_AGREEMENT,
   DEFENSIVE_PACT,
   ALLIANCE,
   EMBARGO;

   /**
    * Priority order for summarizing multiple concurrent agreements (highest wins).
    * Embargo is reported when present even alongside weaker cooperative states that should not coexist.
    */
   public static int priority(DiplomaticStanding standing) {
      return switch (standing) {
         case ALLIANCE -> 80;
         case DEFENSIVE_PACT -> 70;
         case TRADE_AGREEMENT -> 60;
         case NON_AGGRESSION -> 50;
         case PEACE -> 40;
         case EMBARGO -> 90;
         case FRIENDLY -> 20;
         case HOSTILE -> 15;
         case NEUTRAL -> 0;
      };
   }
}
