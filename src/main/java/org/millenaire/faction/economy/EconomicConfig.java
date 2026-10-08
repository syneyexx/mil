package org.millenaire.faction.economy;

import org.millenaire.TickConstants;

/**
 * Centralized constants for faction economy, trade activity, and economic→political influence.
 */
public final class EconomicConfig {
   /** How often faction economic snapshots are refreshed (game ticks). */
   public static final long ECONOMY_UPDATE_INTERVAL_TICKS = 6000L;

   /** How often trade-activity decay is evaluated. */
   public static final long TRADE_ACTIVITY_DECAY_CHECK_TICKS = 1200L;

   /** Game-time between trade-activity decay steps. */
   public static final long TRADE_ACTIVITY_DECAY_INTERVAL_TICKS = TickConstants.DAY_LENGTH;

   /** Activity points removed per decay step. */
   public static final int TRADE_ACTIVITY_DECAY_AMOUNT = 8;

   /** Maximum trade activity on a directional route. */
   public static final int TRADE_ACTIVITY_MAX = 1000;

   /** Activity gained per denier of successful trade value (scaled). */
   public static final int TRADE_VALUE_PER_ACTIVITY_POINT = 32;

   /** Maximum activity gained from a single successful trade event. */
   public static final int MAX_ACTIVITY_GAIN_PER_TRADE = 40;

   /** Minimum denier value before a trade may influence political relations. */
   public static final int MIN_TRADE_VALUE_FOR_RELATIONSHIP = 64;

   /** Denier value required for +1 political relationship from trade. */
   public static final int TRADE_VALUE_PER_RELATIONSHIP_POINT = 512;

   /** Hard cap on relationship delta from one trade event. */
   public static final int MAX_RELATIONSHIP_DELTA_PER_TRADE = 2;

   /** Hard cap on relationship delta from trade per faction pair per Minecraft day. */
   public static final int MAX_RELATIONSHIP_DELTA_PER_DAY_PER_PAIR = 5;

   /** Bounded trade history entries per directional route. */
   public static final int MAX_TRADE_HISTORY_ENTRIES = 48;

   /** Bounded economic history entries per faction. */
   public static final int MAX_ECONOMIC_HISTORY_ENTRIES = 64;

   /** Minimum cumulative import volume before dependency is tracked. */
   public static final int MIN_VOLUME_FOR_DEPENDENCY = 16;

   /** Maximum tracked distinct resources in cumulative import/export maps per faction. */
   public static final int MAX_TRACKED_RESOURCES_PER_FACTION = 64;

   /** Stock value (deniers) below which a faction is considered under pressure when demand exists. */
   public static final int PRESSURE_STOCK_VALUE_THRESHOLD = 200;

   /** Import share (0–100) of a resource from one partner considered significant dependency. */
   public static final int SIGNIFICANT_DEPENDENCY_PERCENT = 50;

   /** Catch-up cap for trade-activity decay steps after long unload. */
   public static final int MAX_ACTIVITY_DECAY_STEPS_PER_PASS = 14;

   private EconomicConfig() {
   }

   public static int clampActivity(int activity) {
      if (activity < 0) {
         return 0;
      }

      return Math.min(activity, TRADE_ACTIVITY_MAX);
   }

   public static int activityGainForValue(int tradeValue) {
      if (tradeValue <= 0) {
         return 0;
      }

      int gain = Math.max(1, tradeValue / TRADE_VALUE_PER_ACTIVITY_POINT);
      return Math.min(gain, MAX_ACTIVITY_GAIN_PER_TRADE);
   }

   public static int relationshipDeltaForValue(int tradeValue) {
      if (tradeValue < MIN_TRADE_VALUE_FOR_RELATIONSHIP) {
         return 0;
      }

      int delta = tradeValue / TRADE_VALUE_PER_RELATIONSHIP_POINT;
      if (delta <= 0) {
         return 1;
      }

      return Math.min(delta, MAX_RELATIONSHIP_DELTA_PER_TRADE);
   }
}
