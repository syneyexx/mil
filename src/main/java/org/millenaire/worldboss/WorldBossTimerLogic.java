package org.millenaire.worldboss;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.util.RandomSource;

/**
 * Pure helpers for world-boss cadence.
 * Day mode uses overworld day index; legacy active-playtime helpers remain for migration/config.
 */
public final class WorldBossTimerLogic {
   public static final long TICKS_PER_DAY = 24000L;

   private WorldBossTimerLogic() {
   }

   public static long dayIndex(long dayTime) {
      return Math.floorDiv(Math.max(0L, dayTime), TICKS_PER_DAY);
   }

   public static long nextDueDay(long currentDay, int intervalDays) {
      int interval = Math.max(1, intervalDays);
      return currentDay + interval;
   }

   public static boolean isDayDue(long currentDay, long nextDueDay) {
      return nextDueDay >= 0L && currentDay >= nextDueDay;
   }

   /** Days remaining until due (0 when due). */
   public static long daysRemaining(long currentDay, long nextDueDay) {
      if (nextDueDay < 0L) {
         return 0L;
      }
      return Math.max(0L, nextDueDay - currentDay);
   }

   /**
    * Migrate legacy tick-timer progress into a day due index.
    * If already due (or nearly), schedule for current day; otherwise schedule one interval out.
    */
   public static long migrateTickTimerToDueDay(long currentDay, int elapsed, int interval, int intervalDays) {
      int days = Math.max(1, intervalDays);
      if (interval <= 0 || isDue(elapsed, interval)) {
         return currentDay;
      }
      double progress = Math.max(0.0, Math.min(1.0, (double)elapsed / (double)interval));
      if (progress >= 0.9) {
         return currentDay;
      }
      return currentDay + days;
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
