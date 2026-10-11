package org.millenaire.perf.tick;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
      try {
         return Math.max(1, MillenaireServerConfig.SERVER.entityLodMidCombatInterval.getAsInt());
      } catch (IllegalStateException e) {
         return 5;
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
      double nearest = Double.MAX_VALUE;
      for (ServerPlayer player : level.players()) {
         double distSq = player.distanceToSqr(entity);
         if (distSq < nearest) {
            nearest = distSq;
         }
      }
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
      int stagger = Math.floorMod(entityId, interval);
      return Math.floorMod(gameTime, interval) == stagger;
   }

   /** Combat urgent canStart scans (not active combat ticks). */
   public boolean allowCombatScan(long gameTime, int entityId) {
      if (this == NEAR) {
         return true;
      }
      if (this == FAR && farSleepEnabled()) {
         return false;
      }
      int interval = midCombatInterval();
      int stagger = Math.floorMod(entityId, interval);
      return Math.floorMod(gameTime, interval) == stagger;
   }

   /** Expensive ambient work: leaf clear, mob aggro pulse, passive pickup. */
   public boolean allowAmbientWork() {
      if (this == FAR && farSleepEnabled()) {
         return false;
      }
      return true;
   }

   /** Navigation while idle (no current task). */
   public boolean allowIdleNavigation() {
      if (this == FAR && farSleepEnabled()) {
         return false;
      }
      return this != FAR;
   }
}
