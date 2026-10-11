package org.millenaire.civilization.interaction;

/**
 * Tunable constants for the civilization interaction layer.
 */
public final class InteractionConfig {
   public static final int CORRIDOR_RECOGNITION_THRESHOLD = 8;
   public static final int CORRIDOR_RECOGNITION_RELATIONSHIP_BOOST = 8;
   public static final int CORRIDOR_EXPORTER_SCORE_BONUS = 200;

   public static final long FESTIVAL_DURATION_TICKS = 24000L * 3L;
   public static final long FESTIVAL_ATTEMPT_INTERVAL_TICKS = 24000L * 7L;
   public static final long MARKET_DAY_INTERVAL_TICKS = 24000L * 5L;

   public static final int CLAIM_HEAT_NEAR_BLOCKS = 512;
   public static final int CLAIM_HEAT_HOT_BLOCKS = 256;
   public static final int CLAIM_HEAT_DECAY_PER_TICK_PASS = 1;
   public static final int CLAIM_HEAT_MAX = 100;

   public static final int LEGITIMACY_MIN = -100;
   public static final int LEGITIMACY_MAX = 100;
   public static final int LEGITIMACY_PASSIVE_DRIFT = 1;

   public static final long CALL_TO_ARMS_DURATION_TICKS = 24000L * 5L;
   public static final long ENVOY_DURATION_TICKS = 24000L * 7L;
   public static final long MERCENARY_CONTRACT_DURATION_TICKS = 24000L * 10L;
   public static final long FOSTERAGE_DURATION_TICKS = 24000L * 20L;
   public static final long PROTECTORATE_TRIBUTE_INTERVAL_TICKS = 24000L * 14L;
   public static final long RUMOR_TTL_TICKS = 24000L * 5L;

   public static final int REFUGEE_ACCEPT_CAP_PER_WAR = 3;
   public static final int DIASPORA_ASSIMILATION_THRESHOLD = 5;
   public static final int BLOOD_MONEY_BOUNTY_REDUCTION = 80;
   public static final int EMERGENCY_RELIEF_RELATIONSHIP = 12;
   public static final int EMERGENCY_RELIEF_LEGITIMACY = 5;

   public static final int MAX_THEATER_STORIES = 48;
   public static final int MAX_RUMORS = 32;
   public static final int MAX_CORRIDORS = 64;

   private InteractionConfig() {
   }
}
