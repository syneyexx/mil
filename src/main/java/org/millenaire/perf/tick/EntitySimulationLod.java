package org.millenaire.perf.tick;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.entity.MillVillager;

/**
 * Distance-based simulation fidelity for loaded villagers.
 * Far sleep aggressively skips AI scans when no player is nearby.
 */
public enum EntitySimulationLod {
   NEAR,
   MID,
   FAR;

   public static boolean enabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.entityLodEnabled.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   public static int midDistance() {
      try {
         return MillenaireServerConfig.SERVER.entityLodMidDistance.getAsInt();
      } catch (IllegalStateException e) {
         return 48;
      }
   }

   public static int farDistance() {
      try {
         return MillenaireServerConfig.SERVER.entityLodFarDistance.getAsInt();
      } catch (IllegalStateException e) {
         return 96;
      }
   }

   public static boolean farSleepEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.entityLodFarSleep.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   public static int midCombatInterval() {
      int base;
      try {
         base = Math.max(1, MillenaireServerConfig.SERVER.entityLodMidCombatInterval.getAsInt());
      } catch (IllegalStateException e) {
         base = 5;
      }
      return scaleInterval(base);
   }

   /** Near combat-urgent scan interval (always 1 when hurt / targeting). */
   public static int nearCombatInterval() {
      int base;
      try {
         base = Math.max(1, MillenaireServerConfig.SERVER.entityLodNearCombatInterval.getAsInt());
      } catch (IllegalStateException e) {
         base = 3;
      }
      return scaleInterval(base);
   }

   private static int scaleInterval(int base) {
      if (!pressureScalesAi()) {
         return base;
      }
      return switch (ServerTickGovernor.pressure()) {
         case HARD -> Math.min(40, base * 2);
         case SOFT -> Math.min(40, (base * 3 + 1) / 2);
         case HEALTHY -> base;
      };
   }

   private static boolean pressureScalesAi() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.tickPressureScalesAi.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   public static EntitySimulationLod resolve(ServerLevel level, Entity entity) {
      if (!enabled() || level == null || entity == null) {
         return NEAR;
      }
      // Hired / escort / active combat must stay responsive.
      if (entity instanceof MillVillager villager) {
         if (villager.isHired() || villager.isCompanion() || villager.getAttackTarget() != null) {
            return NEAR;
         }
      }
      double nearest = LevelTickCache.nearestPlayerDistSq(level, entity);
      double mid = midDistance();
      double far = Math.max(mid + 1.0, farDistance());
      if (nearest <= mid * mid) {
         return NEAR;
      }
      if (nearest <= far * far) {
         return MID;
      }
      return FAR;
   }

   /** Idle goal-pick / leisure scans. */
   public boolean allowGoalPick(long gameTime, int entityId) {
      if (this == NEAR) {
         return true;
      }
      if (this == FAR && farSleepEnabled()) {
         return false;
      }
      int interval = VillagerAiCadence.midInterval();
      if (pressureScalesAi() && ServerTickGovernor.pressure() == TickPressure.HARD) {
         interval = Math.min(40, interval * 2);
      }
      int stagger = Math.floorMod(entityId, interval);
      return Math.floorMod(gameTime, interval) == stagger;
   }

   /** Combat urgent canStart scans (not active combat ticks). */
   public boolean allowCombatScan(long gameTime, int entityId) {
      if (this == FAR && farSleepEnabled()) {
         return false;
      }
      int interval = this == NEAR ? nearCombatInterval() : midCombatInterval();
      if (interval <= 1) {
         return true;
      }
      int stagger = Math.floorMod(entityId, interval);
      return Math.floorMod(gameTime, interval) == stagger;
   }

   /**
    * Always allow combat scan when the villager is hurt or already has a target
    * (overrides Near/Mid cadence).
    */
   public static boolean forceCombatScan(MillVillager villager) {
      return villager != null && (villager.getAttackTarget() != null || villager.hurtTime > 0);
   }

   /** Expensive ambient work: leaf clear, mob aggro pulse, passive pickup. */
   public boolean allowAmbientWork() {
      if (this == FAR && farSleepEnabled()) {
         return false;
      }
      return true;
   }

   /**
    * Whether a Far-sleeping villager may skip ticking its current task this game tick.
    * Never completes or fails the task; callers must keep logical watchdog counters unchanged.
    *
    * @param urgentContext hired / companion / combat / hurt / selling / village under attack
    */
   public static boolean shouldPauseTask(boolean farSleep, boolean urgentContext, boolean combatUrgentGoal, boolean taskPausable) {
      return farSleep && !urgentContext && !combatUrgentGoal && taskPausable;
   }

   /** Navigation while idle (no current task). */
   public boolean allowIdleNavigation() {
      if (this == FAR && farSleepEnabled()) {
         return false;
      }
      return this != FAR;
   }
}
