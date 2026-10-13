package org.millenaire.perf;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight counters for Phase 0 meetpunten. Reset/console via debug command.
 */
public final class TickPerfCounters {
   public static final AtomicLong IMMIGRANT_CHECKS = new AtomicLong();
   public static final AtomicLong IMMIGRANT_SPAWNS = new AtomicLong();
   public static final AtomicLong SLOT_RECOUNTS = new AtomicLong();
   public static final AtomicLong RAID_TICKS = new AtomicLong();
   public static final AtomicLong ILLAGER_SPAWNS = new AtomicLong();
   public static final AtomicLong RAID_PLANS_SKIPPED_CAP = new AtomicLong();
   public static final AtomicLong CITY_STATS_REFRESHES = new AtomicLong();

   private TickPerfCounters() {
   }

   public static void reset() {
      IMMIGRANT_CHECKS.set(0L);
      IMMIGRANT_SPAWNS.set(0L);
      SLOT_RECOUNTS.set(0L);
      RAID_TICKS.set(0L);
      ILLAGER_SPAWNS.set(0L);
      RAID_PLANS_SKIPPED_CAP.set(0L);
      CITY_STATS_REFRESHES.set(0L);
   }

   public static String snapshot() {
      return "immigrantChecks="
         + IMMIGRANT_CHECKS.get()
         + " immigrantSpawns="
         + IMMIGRANT_SPAWNS.get()
         + " slotRecounts="
         + SLOT_RECOUNTS.get()
         + " raidTicks="
         + RAID_TICKS.get()
         + " illagerSpawns="
         + ILLAGER_SPAWNS.get()
         + " raidPlansSkippedCap="
         + RAID_PLANS_SKIPPED_CAP.get()
         + " cityStatsRefreshes="
         + CITY_STATS_REFRESHES.get();
   }
}
