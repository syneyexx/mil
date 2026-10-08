package org.millenaire.faction.territory;

/**
 * Centralized Phase 7 territory/occupation balance.
 */
public final class TerritoryConfig {
   public static final long TERRITORY_UPDATE_INTERVAL_TICKS = 1200L;
   public static final long INTEGRATION_TICK_INTERVAL = 2400L;

   public static final int RESISTANCE_MIN = 0;
   public static final int RESISTANCE_MAX = 100;
   public static final int STABILITY_MIN = 0;
   public static final int STABILITY_MAX = 100;
   public static final int INTEGRATION_MIN = 0;
   public static final int INTEGRATION_MAX = 100;
   public static final int CLAIM_STRENGTH_MIN = 0;
   public static final int CLAIM_STRENGTH_MAX = 100;

   public static final int CAPTURE_RESISTANCE_BASE = 55;
   public static final int CAPTURE_STABILITY_BASE = 35;
   public static final int INTEGRATION_START = 0;
   public static final int INTEGRATION_COMPLETE = 100;

   public static final int GARRISON_SOLDIERS_PER_VALUE = 2;
   public static final int MIN_OCCUPATION_GARRISON = 4;
   public static final int MAX_OCCUPATION_GARRISON = 40;

   public static final int MAX_TERRITORY_HISTORY = 256;
   public static final int MAX_GLOBAL_HISTORY = 512;
   public static final int MAX_PEACE_TREATY_HISTORY = 128;

   public static final int RELATIONSHIP_CONQUEST = -35;
   public static final int RELATIONSHIP_LIBERATION = 20;
   public static final int RELATIONSHIP_RETURN = 15;
   public static final int RELATIONSHIP_ANNEX_NEIGHBOR = -10;

   public static final int ECONOMIC_OCCUPATION_PENALTY_PERMILLE = 350;
   public static final int ECONOMIC_INTEGRATING_PENALTY_PERMILLE = 150;

   public static final int OVEREXTENSION_PER_OCCUPIED = 8;
   public static final int OVEREXTENSION_PER_INTEGRATING = 4;
   public static final int OVEREXTENSION_MAX = 100;
   public static final int ADMIN_CAPACITY_BASE = 3;
   public static final int ADMIN_CAPACITY_PER_STOCK_1000 = 1;

   public static final int RESISTANCE_COLLAPSE_CONTROL = 85;
   public static final int REBELLION_PRESSURE_THRESHOLD = 80;

   public static final int NEIGHBOR_DISTANCE_BLOCKS = 512;

   private TerritoryConfig() {
   }

   public static int clampResistance(int value) {
      return Math.max(RESISTANCE_MIN, Math.min(RESISTANCE_MAX, value));
   }

   public static int clampStability(int value) {
      return Math.max(STABILITY_MIN, Math.min(STABILITY_MAX, value));
   }

   public static int clampIntegration(int value) {
      return Math.max(INTEGRATION_MIN, Math.min(INTEGRATION_MAX, value));
   }

   public static int clampClaimStrength(int value) {
      return Math.max(CLAIM_STRENGTH_MIN, Math.min(CLAIM_STRENGTH_MAX, value));
   }

   public static int clampPercent(int value) {
      return Math.max(0, Math.min(100, value));
   }

   public static int clampNonNegative(int value) {
      return Math.max(0, value);
   }
}
