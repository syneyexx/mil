package org.millenaire.worldboss;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.util.RandomSource;

/**
 * Pure helpers for the world-boss active-playtime timer.
 * Advances only when eligible players are online; never catches up multiple events.
 */
public final class WorldBossTimerLogic {
   private WorldBossTimerLogic() {
   }

   public static int rollInterval(int minTicks, int maxTicks, RandomSource random) {
      int min = Math.max(1, Math.min(minTicks, maxTicks));
      int max = Math.max(min, Math.max(minTicks, maxTicks));
      if (max == min) {
         return min;
      }
      return min + random.nextInt(max - min + 1);
   }

   public static int rollInterval(int minTicks, int maxTicks) {
      return rollInterval(minTicks, maxTicks, RandomSource.create(ThreadLocalRandom.current().nextLong()));
   }

   /**
    * @return new elapsed ticks (clamped to interval); does not produce backlog beyond one due event
    */
   public static int advance(int elapsed, int interval, boolean eligiblePlayersOnline) {
      if (!eligiblePlayersOnline || interval <= 0) {
         return elapsed;
      }
      if (elapsed >= interval) {
         return interval;
      }
      return elapsed + 1;
   }

   public static boolean isDue(int elapsed, int interval) {
      return interval > 0 && elapsed >= interval;
   }

   public static int remaining(int elapsed, int interval) {
      return Math.max(0, interval - elapsed);
   }
}
