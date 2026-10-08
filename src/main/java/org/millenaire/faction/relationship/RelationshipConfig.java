package org.millenaire.faction.relationship;

import org.millenaire.TickConstants;

/**
 * Centralized constants for faction relationship scoring, history, decay, and query thresholds.
 * <p>
 * Adjust values here rather than scattering magic numbers through relationship code.
 * This is not a player-facing configuration GUI.
 */
public final class RelationshipConfig {
   /** Inclusive minimum relationship score. */
   public static final int SCORE_MIN = -100;
   /** Inclusive maximum relationship score. */
   public static final int SCORE_MAX = 100;
   /** Implicit score when no explicit relationship record exists. */
   public static final int DEFAULT_SCORE = 0;

   /** Maximum persisted history entries retained per directional relationship. */
   public static final int MAX_HISTORY_ENTRIES = 64;

   /**
    * Absolute delta required before a change is recorded in history.
    * Passive decay never creates history entries regardless of this value.
    */
   public static final int MIN_HISTORY_ABS_DELTA = 1;

   /** Game-time between decay steps (one Minecraft day). */
   public static final long DECAY_INTERVAL_TICKS = TickConstants.DAY_LENGTH;

   /** Score moved toward neutral per decay step. */
   public static final int DECAY_AMOUNT = 1;

   /**
    * After a meaningful (history-worthy) change, decay is suppressed for this many game ticks
    * so recent events remain politically relevant.
    */
   public static final long DECAY_GRACE_AFTER_EVENT_TICKS = TickConstants.DAY_LENGTH * 3L;

   /** Minimum absolute score before decay will move a relationship toward zero. */
   public static final int MIN_ABS_SCORE_FOR_DECAY = 1;

   /**
    * How often the server checks whether decay steps are due.
    * Decay itself is driven by stored game-time stamps, not by this interval alone.
    */
   public static final long DECAY_CHECK_INTERVAL_TICKS = 1200L;

   /** Cap on catch-up decay steps applied in one pass after long unload periods. */
   public static final int MAX_DECAY_STEPS_PER_PASS = 14;

   /** Absolute score at/above which {@link RelationshipCategory#POSITIVE} begins. */
   public static final int THRESHOLD_POSITIVE = 10;
   /** Absolute score at/above which {@link RelationshipCategory#FRIENDLY} begins. */
   public static final int THRESHOLD_FRIENDLY = 50;
   /** Absolute score at/above which {@link RelationshipCategory#VERY_FRIENDLY} begins. */
   public static final int THRESHOLD_VERY_FRIENDLY = 75;
   /** Absolute score at/below which {@link RelationshipCategory#NEGATIVE} begins. */
   public static final int THRESHOLD_NEGATIVE = -10;
   /** Absolute score at/below which {@link RelationshipCategory#HOSTILE} begins. */
   public static final int THRESHOLD_HOSTILE = -50;
   /** Absolute score at/below which {@link RelationshipCategory#VERY_HOSTILE} begins. */
   public static final int THRESHOLD_VERY_HOSTILE = -75;

   private RelationshipConfig() {
   }

   public static int clampScore(int score) {
      if (score < SCORE_MIN) {
         return SCORE_MIN;
      }

      return Math.min(score, SCORE_MAX);
   }

   /**
    * Adds {@code delta} to {@code score} using 64-bit arithmetic, then clamps to the valid range.
    */
   public static int applyDelta(int score, int delta) {
      long sum = (long)score + (long)delta;
      if (sum < SCORE_MIN) {
         return SCORE_MIN;
      }

      if (sum > SCORE_MAX) {
         return SCORE_MAX;
      }

      return (int)sum;
   }

   public static boolean isValidScore(int score) {
      return score >= SCORE_MIN && score <= SCORE_MAX;
   }
}
