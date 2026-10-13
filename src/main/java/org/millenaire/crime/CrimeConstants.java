package org.millenaire.crime;

public final class CrimeConstants {
   public static final int BOUNTY_MAX = 1000;
   public static final int TRADE_BAN_THRESHOLD = 50;
   public static final int HOSTILE_THRESHOLD = 100;
   public static final int RAID_CITY_THRESHOLD = 200;

   public static final int PICKPOCKET_BOUNTY = 25;
   public static final int PICKPOCKET_REP = -80;
   public static final int LOCKPICK_FAIL_BOUNTY = 15;
   public static final int LOCKPICK_FAIL_REP = -40;
   public static final int CHEST_CAUGHT_BOUNTY = 90;
   public static final int CHEST_CAUGHT_REP = -200;
   public static final int ASSAULT_BOUNTY = 15;
   public static final int ASSAULT_REP = -30;
   public static final int KILL_BOUNTY = 40;
   public static final int KILL_REP = -100;
   public static final int VANDALISM_BOUNTY_BASE = 10;
   public static final int VANDALISM_BOUNTY_MAX = 80;
   public static final int VANDALISM_REP_PAID = -5;
   public static final int VANDALISM_REP_UNPAID = -40;
   public static final int VANDALISM_FALLBACK_DENIERS = 8;
   public static final int VANDALISM_DENIERS_PER_COST_UNIT = 4;

   public static final double PICKPOCKET_RANGE = 2.5;
   public static final double BEHIND_DOT_MAX = -0.35;
   public static final int PICKPOCKET_COOLDOWN_TICKS = 600;
   public static final double WITNESS_RANGE = 16.0;
   public static final double WITNESS_FACING_DOT_MIN = 0.25;

   public static final int LOCKPICK_DURATION_TICKS = 1200;
   public static final float LOCKPICK_SUCCESS_CHANCE = 0.65F;
   public static final double CHEST_CATCH_RANGE = 8.0;
   public static final int INVESTIGATE_DEBOUNCE_TICKS = 200;
   public static final int CRIME_RAID_COOLDOWN_TICKS = 24000;

   private CrimeConstants() {
   }
}
