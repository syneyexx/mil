package org.millenaire.perf.tick;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.millenaire.config.MillenaireServerConfig;

/**
 * Amortizes heavy goal-pick / leisure scans for villagers far from players.
 * Combat and active tasks still tick every frame via {@link org.millenaire.goal.GoalScheduler}.
 * <p>
 * When {@link EntitySimulationLod} is enabled, Mid/Far staggering is handled there;
 * this helper is only applied from {@code MillVillager} as a fallback when entity LOD is off.
 */
public final class VillagerAiCadence {
   private VillagerAiCadence() {
   }

   public static boolean enabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.villagerAiCadenceEnabled.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   public static int midDistance() {
      try {
         return MillenaireServerConfig.SERVER.villagerAiMidDistance.getAsInt();
      } catch (IllegalStateException e) {
         return 48;
      }
   }

   public static int midInterval() {
      try {
         return Math.max(1, MillenaireServerConfig.SERVER.villagerAiMidInterval.getAsInt());
      } catch (IllegalStateException e) {
         return 3;
      }
   }

   /**
    * When false, skip expensive goal picking this tick (active task + combat still run).
    */
   public static boolean allowGoalPick(ServerLevel level, Entity entity) {
      if (!enabled() || level == null || entity == null) {
         return true;
      }
      double mid = midDistance();
      double midSq = mid * mid;
      double nearest = Double.MAX_VALUE;
      for (ServerPlayer player : level.players()) {
         double distSq = player.distanceToSqr(entity);
         if (distSq < nearest) {
            nearest = distSq;
         }
         if (nearest <= midSq) {
            return true;
         }
      }
      if (nearest <= midSq) {
         return true;
      }
      int interval = midInterval();
      int stagger = Math.floorMod(entity.getId(), interval);
      return Math.floorMod(level.getGameTime(), interval) == stagger;
   }
}
