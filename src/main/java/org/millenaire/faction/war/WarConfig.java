package org.millenaire.faction.war;

/**
 * Centralized Phase 6 warfare balance. No permanent conquest parameters.
 */
public final class WarConfig {
   public static final long WAR_UPDATE_INTERVAL_TICKS = 200L;
   public static final long BATTLE_ROUND_INTERVAL_TICKS = 100L;
   public static final long ARMY_MOVE_INTERVAL_TICKS = 40L;
   public static final long AI_EVALUATION_INTERVAL_TICKS = 200L;
   public static final long REINFORCEMENT_INTERVAL_TICKS = 1200L;

   public static final int WAR_SCORE_MIN = -100;
   public static final int WAR_SCORE_MAX = 100;
   public static final int WAR_SCORE_BATTLE_WIN = 12;
   public static final int WAR_SCORE_BATTLE_LOSS = -12;
   public static final int WAR_SCORE_PER_100_CASUALTIES_INFLICTED = 4;
   public static final int WAR_SCORE_PER_100_CASUALTIES_SUFFERED = -3;
   public static final int WAR_SCORE_ARMY_DESTROYED = 18;
   public static final int WAR_SCORE_SUCCESSFUL_DEFENSE = 8;
   public static final int WAR_SCORE_GOAL_PROGRESS = 5;

   public static final int MAX_WAR_HISTORY_ENTRIES = 256;
   public static final int MAX_BATTLE_HISTORY_ENTRIES = 64;
   public static final int MAX_ACTIVE_WARS_PER_FACTION_PAIR = 1;

   public static final int ENGAGEMENT_RANGE_BLOCKS = 48;
   public static final int BATTLE_JOIN_RANGE_BLOCKS = 64;
   public static final int SETTLEMENT_THREAT_RANGE_BLOCKS = 96;
   public static final int RETREAT_DISTANCE_BLOCKS = 80;
   public static final int ARMY_MOVE_BLOCKS_PER_UPDATE = 8;

   public static final int MORALE_BASE = 70;
   public static final int MORALE_MIN = 0;
   public static final int MORALE_MAX = 100;
   public static final int MORALE_RETREAT_THRESHOLD = 25;
   public static final int MORALE_COLLAPSE_THRESHOLD = 10;

   public static final int SUPPLY_BASE = 80;
   public static final int SUPPLY_MIN = 0;
   public static final int SUPPLY_MAX = 100;

   public static final double COMBAT_RANDOMNESS_FRACTION = 0.08;
   public static final int MIN_CASUALTIES_PER_ROUND = 0;
   public static final int MAX_CASUALTY_FRACTION_PER_ROUND_PERMILLE = 120;

   public static final int RELATIONSHIP_WAR_DECLARATION = -40;
   public static final int RELATIONSHIP_ALLY_JOIN = 15;
   public static final int RELATIONSHIP_PEACE = 10;
   public static final int RELATIONSHIP_BETRAYAL = -50;
   public static final int RELATIONSHIP_MAJOR_VICTORY = -10;
   public static final int RELATIONSHIP_MAJOR_DEFEAT = -5;

   public static final int WAR_EXHAUSTION_PER_DAY = 1;
   public static final int WAR_EXHAUSTION_PER_100_CASUALTIES = 3;
   public static final int WAR_EXHAUSTION_PER_LOST_BATTLE = 4;
   public static final int WAR_EXHAUSTION_MAX = 100;
   public static final int PEACE_PRESSURE_THRESHOLD = 60;
   public static final int DECISIVE_WAR_SCORE = 75;

   public static final int MAX_PHYSICAL_SOLDIER_REPRESENTATIVES = 12;
   public static final int REINFORCEMENT_SOLDIERS_PER_PULSE = 2;

   public static final long DEFAULT_CEASEFIRE_DURATION_TICKS = 24000L;
   public static final long MOBILIZATION_DURATION_TICKS = 600L;

   private WarConfig() {
   }

   public static int clampWarScore(int value) {
      return Math.max(WAR_SCORE_MIN, Math.min(WAR_SCORE_MAX, value));
   }

   public static int clampMorale(int value) {
      return Math.max(MORALE_MIN, Math.min(MORALE_MAX, value));
   }

   public static int clampSupply(int value) {
      return Math.max(SUPPLY_MIN, Math.min(SUPPLY_MAX, value));
   }

   public static int clampPercent(int value) {
      return Math.max(0, Math.min(100, value));
   }

   public static int clampNonNegative(int value) {
      return Math.max(0, value);
   }
}
