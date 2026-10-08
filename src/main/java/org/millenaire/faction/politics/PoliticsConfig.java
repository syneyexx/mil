package org.millenaire.faction.politics;

/**
 * Centralized Phase 8 internal politics balance constants.
 */
public final class PoliticsConfig {
   public static final long POLITICS_UPDATE_INTERVAL_TICKS = 1200L;
   public static final long SUCCESSION_INTERIM_TICKS = 2400L;

   public static final int BOUNDED_MIN = 0;
   public static final int BOUNDED_MAX = 100;

   public static final int LEGITIMACY_BASE = 55;
   public static final int POPULARITY_BASE = 50;
   public static final int STABILITY_BASE = 60;
   public static final int PRESSURE_BASE = 10;
   public static final int POLARIZATION_BASE = 20;
   public static final int REBELLION_PRESSURE_BASE = 0;
   public static final int CENTRALIZATION_BASE = 50;

   public static final int RULING_SUPPORT_SAFE = 55;
   public static final int RULING_SUPPORT_LOW = 35;
   public static final int CRISIS_PRESSURE_THRESHOLD = 60;
   public static final int SEVERE_CRISIS_PRESSURE_THRESHOLD = 80;
   public static final int COUP_PRESSURE_THRESHOLD = 75;
   public static final int COUP_LEGITIMACY_MAX = 35;
   public static final int COUP_STABILITY_MAX = 40;
   public static final int COUP_MIN_GROUP_POWER = 40;
   public static final int REBELLION_PRESSURE_THRESHOLD = 85;
   public static final int POLARIZATION_CRISIS_THRESHOLD = 70;

   public static final int LEGITIMACY_WAR_VICTORY = 8;
   public static final int LEGITIMACY_WAR_DEFEAT = -12;
   public static final int LEGITIMACY_CONQUEST = 6;
   public static final int LEGITIMACY_TERRITORIAL_LOSS = -10;
   public static final int LEGITIMACY_ECONOMIC_HEALTHY = 3;
   public static final int LEGITIMACY_ECONOMIC_PRESSURE = -5;
   public static final int LEGITIMACY_SUCCESSION_HEREDITARY = 10;
   public static final int LEGITIMACY_SUCCESSION_ELECTIVE = 5;
   public static final int LEGITIMACY_COUP = -25;
   public static final int LEGITIMACY_INTERIM = -8;

   public static final int POPULARITY_PROSPERITY = 4;
   public static final int POPULARITY_SHORTAGE = -6;
   public static final int POPULARITY_WAR_EXHAUSTION_PER_20 = -3;
   public static final int POPULARITY_POLICY_MATCH = 2;
   public static final int POPULARITY_POLICY_MISMATCH = -3;

   public static final int PRESSURE_LOW_LEGITIMACY = 4;
   public static final int PRESSURE_LOW_STABILITY = 3;
   public static final int PRESSURE_OPPOSITION = 2;
   public static final int PRESSURE_DECAY = -2;

   public static final int RELATIONSHIP_LEADERSHIP_CHANGE = -5;
   public static final int RELATIONSHIP_COUP = -15;
   public static final int RELATIONSHIP_REFORM = 3;

   public static final int MAX_POLITICAL_HISTORY = 512;
   public static final int MAX_FACTION_HISTORY = 256;
   public static final int MAX_ACTIVE_CRISES_PER_FACTION = 3;
   public static final int DECISION_COOLDOWN_TICKS = 600;

   public static final int LOCAL_AUTONOMY_LOW_CENTRALIZATION = 70;
   public static final int LOCAL_AUTONOMY_HIGH_CENTRALIZATION = 30;

   private PoliticsConfig() {
   }

   public static int clamp(int value) {
      return Math.max(BOUNDED_MIN, Math.min(BOUNDED_MAX, value));
   }

   public static int clampNonNegative(int value) {
      return Math.max(0, value);
   }
}
