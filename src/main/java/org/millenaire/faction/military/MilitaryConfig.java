package org.millenaire.faction.military;

import org.millenaire.TickConstants;

/**
 * Centralized military balance constants for Phase 5 organization (not warfare).
 */
public final class MilitaryConfig {
   public static final long MILITARY_UPDATE_INTERVAL_TICKS = 6000L;
   public static final long READINESS_CHECK_INTERVAL_TICKS = 1200L;

   /** Fraction of settlement population counted as potential manpower (per mille). */
   public static final int MANPOWER_PER_MILLE_OF_POPULATION = 200;

   /** Extra manpower capacity per military infrastructure point. */
   public static final int MANPOWER_PER_INFRASTRUCTURE_POINT = 2;

   public static final int DEFAULT_UNIT_MAX_STRENGTH = 20;
   public static final int MIN_UNIT_SIZE_ON_RECRUIT = 1;
   public static final int MAX_UNIT_SIZE_ON_RECRUIT = 40;

   public static final int BASE_GARRISON_CAPACITY = 10;
   public static final int GARRISON_PER_INFRASTRUCTURE_POINT = 4;

   public static final int MAX_MILITARY_HISTORY_ENTRIES = 128;
   public static final int MAX_ARMY_HISTORY_ENTRIES = 32;

   public static final int READINESS_UNTRAINED = 0;
   public static final int READINESS_PARTIAL = 35;
   public static final int READINESS_TRAINED = 70;
   public static final int READINESS_HIGH = 90;

   public static final int STRENGTH_PER_SOLDIER_BASE = 8;
   public static final int STRENGTH_READINESS_DIVISOR = 100;
   public static final int STRENGTH_EQUIPMENT_BONUS_PER_LEVEL = 2;
   public static final int STRENGTH_COMMANDER_BONUS = 15;
   public static final int STRENGTH_TRAINING_BONUS_PER_LEVEL = 3;

   public static final int FOOD_PER_SOLDIER_MAINTENANCE = 1;
   public static final int EQUIPMENT_VALUE_PER_SOLDIER = 8;

   public static final long DAY_LENGTH = TickConstants.DAY_LENGTH;

   private MilitaryConfig() {
   }

   public static int clampPercent(int value) {
      if (value < 0) {
         return 0;
      }

      return Math.min(100, value);
   }

   public static int clampNonNegative(int value) {
      return Math.max(0, value);
   }
}
