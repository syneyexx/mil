package org.millenaire.faction.conflict;

/**
 * Centralized Phase 9 internal conflict balance constants.
 */
public final class ConflictConfig {
   public static final long CONFLICT_UPDATE_INTERVAL_TICKS = 1200L;
   public static final long STALE_CONFLICT_TICKS = 24000L * 7L;
   public static final long POST_CONFLICT_RECOVERY_TICKS = 24000L * 5L;
   public static final long IDENTICAL_CONFLICT_COOLDOWN_TICKS = 24000L * 3L;

   public static final int BOUNDED_MIN = 0;
   public static final int BOUNDED_MAX = 100;

   public static final int UNREST_ESCALATE_LOCAL = 35;
   public static final int UNREST_ESCALATE_UPRISING = 55;
   public static final int UNREST_ESCALATE_REBELLION = 70;
   public static final int UNREST_ESCALATE_INTERNAL_WAR = 85;

   public static final int REBELLION_PRESSURE_FORM = 80;
   public static final int CIVIL_WAR_PRESSURE = 90;
   public static final int SECESSION_AUTONOMY_MIN = 60;
   public static final int SECESSION_CENTRALIZATION_MAX = 40;
   public static final int MIN_REBEL_GROUP_POWER = 35;
   public static final int MIN_REBEL_SUPPORT = 40;
   public static final int MIN_GOV_LEGITIMACY_FOR_COUP_WAR = 30;

   public static final int CONFLICT_SCORE_VICTORY = 80;
   public static final int CONFLICT_SCORE_DEFEAT = -80;
   public static final int CONFLICT_SCORE_NEGOTIATE = 20;

   public static final int RELATIONSHIP_CIVIL_WAR = -40;
   public static final int RELATIONSHIP_SECESSION = -25;
   public static final int RELATIONSHIP_REVOLUTION = -30;
   public static final int RELATIONSHIP_RECOGNITION = 10;

   public static final int MAX_CONFLICT_HISTORY = 512;
   public static final int MAX_UNREST_PER_FACTION = 32;
   public static final int MAX_ACTIVE_CONFLICTS_PER_FACTION = 3;

   public static final double MILITARY_SPLIT_OPPOSITION_RATIO = 0.35;
   public static final int REBEL_RECRUIT_SOLDIERS = 8;

   public static final int SERIALIZATION_VERSION = 1;

   private ConflictConfig() {
   }

   public static int clamp(int value) {
      return Math.max(BOUNDED_MIN, Math.min(BOUNDED_MAX, value));
   }

   public static int clampNonNegative(int value) {
      return Math.max(0, value);
   }
}
