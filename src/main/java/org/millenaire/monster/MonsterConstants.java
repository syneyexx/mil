package org.millenaire.monster;

/** Tunable combat, spawn and reward values for all native monsters. */
public final class MonsterConstants {
   private MonsterConstants() {
   }

   public static final int SPAWN_WEIGHT = 3;
   public static final int SPAWN_MIN = 1;
   public static final int SPAWN_MAX = 1;

   public static final class Drakenkoning {
      public static final double HEALTH = 1024;
      public static final double DAMAGE = 26;
      public static final double ARMOR = 20;
      public static final double SPEED = 0.22;
      public static final float WIDTH = 4.8F;
      public static final float HEIGHT = 9.0F;
      public static final float SHADOW = 2.40F;
      public static final int XP = 500;
      public static final int FIRE_BREATH_COOLDOWN = 240;
      public static final int FIRE_BREATH_RANGE = 6;
      public static final int FIRE_BREATH_BURN_SECONDS = 3;
      public static final int WING_SLAM_COOLDOWN = 300;
      public static final int WING_SLAM_RADIUS = 8;
      public static final float WING_SLAM_DAMAGE = 12;
      public static final float ENRAGE_HEALTH_FRACTION = 0.25F;
      public static final float SALVO_HEALTH_FRACTION = 0.5F;
      public static final double ENRAGE_SPEED_BONUS = 0.20;
      public static final double ENRAGE_COOLDOWN_FACTOR = 0.70;
   }

   public static final class DeAlziende {
      public static final double HEALTH = 900;
      public static final double DAMAGE = 18;
      public static final double ARMOR = 16;
      public static final double SPEED = 0.2;
      public static final float WIDTH = 3.2F;
      public static final float HEIGHT = 8.4F;
      public static final float SHADOW = 1.60F;
      public static final int XP = 450;
      public static final int LASER_COOLDOWN = 200;
      public static final float LASER_DAMAGE_PER_TICK = 8;
      public static final int LASER_CHARGE_TICKS = 40;
      public static final int FEATHER_RAIN_COOLDOWN = 280;
      public static final int FEATHER_COUNT = 12;
      public static final float FEATHER_DAMAGE = 6;
      public static final float HELPER_HEALTH_FRACTION = 0.4F;
      public static final float HOVER_MIN = 6;
      public static final float HOVER_MAX = 10;
   }

   public static final class DrievoudigeVorst {
      public static final double HEALTH = 1024;
      public static final double DAMAGE = 18;
      public static final double ARMOR = 22;
      public static final double SPEED = 0.2;
      public static final float WIDTH = 5.6F;
      public static final float HEIGHT = 6.6F;
      public static final float SHADOW = 2.80F;
      public static final int XP = 500;
      public static final int HEAD_ABILITY_COOLDOWN = 100;
      public static final int ROYAL_COMMAND_RADIUS = 12;
      public static final float CAT_LEAP_DAMAGE = 16;
      public static final float DUAL_HEAD_HEALTH_FRACTION = 0.5F;
   }

   public static final class GevallenSeraf {
      public static final double HEALTH = 960;
      public static final double DAMAGE = 22;
      public static final double ARMOR = 18;
      public static final double SPEED = 0.23;
      public static final float WIDTH = 2.8F;
      public static final float HEIGHT = 8.2F;
      public static final float SHADOW = 1.40F;
      public static final int XP = 480;
      public static final int DIVE_COOLDOWN = 240;
      public static final float DIVE_DAMAGE = 20;
      public static final int WING_CUT_COOLDOWN = 160;
      public static final float WING_CUT_DAMAGE = 8;
      public static final int WING_CUT_WAVES = 4;
      public static final float FALLEN_LIGHT_HEALTH_FRACTION = 0.5F;
      public static final float FALLEN_LIGHT_DAMAGE = 14;
      public static final int FALLEN_LIGHT_WARN_TICKS = 30;
      public static final float PERMA_FLY_HEALTH_FRACTION = 0.25F;
   }

   public static final class ScharlakenKoningin {
      public static final double HEALTH = 1000;
      public static final double DAMAGE = 24;
      public static final double ARMOR = 20;
      public static final double SPEED = 0.18;
      public static final float WIDTH = 4.8F;
      public static final float HEIGHT = 7.8F;
      public static final float SHADOW = 2.40F;
      public static final int XP = 520;
      public static final int STING_DAMAGE = 10;
      public static final int PINCER_HOLD_TICKS = 60;
      public static final float PINCER_DPS = 6;
      public static final int MIST_COOLDOWN = 320;
      public static final float SPIDER_HEALTH_FRACTION = 0.5F;
      public static final float EXTRA_ARMOR_HEALTH_FRACTION = 0.25F;
      public static final double EXTRA_ARMOR = 8;
   }

   public static final class ZesarmigeRazernij {
      public static final double HEALTH = 800;
      public static final double DAMAGE = 20;
      public static final double ARMOR = 12;
      public static final double SPEED = 0.3;
      public static final float WIDTH = 3.2F;
      public static final float HEIGHT = 5.8F;
      public static final float SHADOW = 1.60F;
      public static final int XP = 400;
      public static final int SIX_STRIKE_COOLDOWN = 120;
      public static final float SIX_STRIKE_DAMAGE = 6;
      public static final int SIX_STRIKE_HITS = 6;
      public static final float RAGE_STEP = 0.20F;
      public static final double RAGE_BONUS = 0.10;
      public static final int RAGE_MAX_STACKS = 4;
      public static final float BOUNCE_DAMAGE = 14;
      public static final int ROAR_COOLDOWN = 360;
   }
}
