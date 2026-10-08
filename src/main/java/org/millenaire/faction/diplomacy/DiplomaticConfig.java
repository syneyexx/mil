package org.millenaire.faction.diplomacy;

import org.millenaire.TickConstants;

/**
 * Centralized constants for diplomatic agreements, history, expiration checks, and
 * diplomatic→relationship influence.
 */
public final class DiplomaticConfig {
   /** How often active agreements are checked for expiration (game ticks). */
   public static final long EXPIRATION_CHECK_INTERVAL_TICKS = 1200L;

   /** Bounded diplomatic history entries retained worldwide (recent first). */
   public static final int MAX_DIPLOMATIC_HISTORY_ENTRIES = 128;

   /** Bounded history entries retained on each agreement. */
   public static final int MAX_AGREEMENT_HISTORY_ENTRIES = 32;

   public static final int RELATIONSHIP_DELTA_PEACE = 1;
   public static final int RELATIONSHIP_DELTA_NON_AGGRESSION = 1;
   public static final int RELATIONSHIP_DELTA_TRADE_AGREEMENT = 1;
   public static final int RELATIONSHIP_DELTA_DEFENSIVE_PACT = 2;
   public static final int RELATIONSHIP_DELTA_ALLIANCE = 3;
   public static final int RELATIONSHIP_DELTA_EMBARGO = -2;
   public static final int RELATIONSHIP_DELTA_TERMINATION = -1;

   /** Cap applied per diplomatic event so scores never jump wildly. */
   public static final int MAX_RELATIONSHIP_DELTA_PER_EVENT = 3;

   public static final long DAY_LENGTH = TickConstants.DAY_LENGTH;

   private DiplomaticConfig() {
   }

   public static int clampRelationshipDelta(int delta) {
      if (delta > MAX_RELATIONSHIP_DELTA_PER_EVENT) {
         return MAX_RELATIONSHIP_DELTA_PER_EVENT;
      }

      return Math.max(-MAX_RELATIONSHIP_DELTA_PER_EVENT, delta);
   }
}
