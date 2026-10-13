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
   public static final AtomicLong RELIGION_SNAPSHOTS = new AtomicLong();
   public static final AtomicLong RELIGION_DEADLINES = new AtomicLong();
   public static final AtomicLong RELIGION_COMMITS = new AtomicLong();
   public static final AtomicLong RELIGION_QUEUE_DEPTH = new AtomicLong();
   public static final AtomicLong RELIGION_QUEUE_REJECTS = new AtomicLong();
   public static final AtomicLong RELIGION_NAV_STARTS = new AtomicLong();
   public static final AtomicLong RELIGION_FESTIVAL_STARTS = new AtomicLong();
   public static final AtomicLong TREASURY_OPS = new AtomicLong();
   public static final AtomicLong WAR_COMMITS = new AtomicLong();
   public static final AtomicLong WAR_DAMAGE_BLOCKS = new AtomicLong();
   public static final AtomicLong PATH_ASTAR_NS = new AtomicLong();
   public static final AtomicLong PANEL_UPDATES = new AtomicLong();
   public static final AtomicLong SELLER_SCANS = new AtomicLong();
   public static final AtomicLong MOB_DESPAWN = new AtomicLong();
   public static final AtomicLong GOAL_PICK_NS = new AtomicLong();
   public static final AtomicLong PLAN_GATE_ACQUIRES = new AtomicLong();
   public static final AtomicLong PLAN_GATE_REJECTS = new AtomicLong();
   public static final AtomicLong PLAN_GATE_IN_FLIGHT = new AtomicLong();
   public static final AtomicLong ECONOMY_SNAPSHOTS = new AtomicLong();
   public static final AtomicLong ECONOMY_COMMITS = new AtomicLong();
   public static final AtomicLong LOGISTICS_COMMITS = new AtomicLong();
   public static final AtomicLong WAR_QUEUE_REJECTS = new AtomicLong();

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
      RELIGION_SNAPSHOTS.set(0L);
      RELIGION_DEADLINES.set(0L);
      RELIGION_COMMITS.set(0L);
      RELIGION_QUEUE_DEPTH.set(0L);
      RELIGION_QUEUE_REJECTS.set(0L);
      RELIGION_NAV_STARTS.set(0L);
      RELIGION_FESTIVAL_STARTS.set(0L);
      TREASURY_OPS.set(0L);
      WAR_COMMITS.set(0L);
      WAR_DAMAGE_BLOCKS.set(0L);
      PATH_ASTAR_NS.set(0L);
      PANEL_UPDATES.set(0L);
      SELLER_SCANS.set(0L);
      MOB_DESPAWN.set(0L);
      GOAL_PICK_NS.set(0L);
      PLAN_GATE_ACQUIRES.set(0L);
      PLAN_GATE_REJECTS.set(0L);
      PLAN_GATE_IN_FLIGHT.set(0L);
      ECONOMY_SNAPSHOTS.set(0L);
      ECONOMY_COMMITS.set(0L);
      LOGISTICS_COMMITS.set(0L);
      WAR_QUEUE_REJECTS.set(0L);
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
         + CITY_STATS_REFRESHES.get()
         + " religionSnapshots="
         + RELIGION_SNAPSHOTS.get()
         + " religionDeadlines="
         + RELIGION_DEADLINES.get()
         + " religionCommits="
         + RELIGION_COMMITS.get()
         + " religionQueueDepth="
         + RELIGION_QUEUE_DEPTH.get()
         + " religionQueueRejects="
         + RELIGION_QUEUE_REJECTS.get()
         + " religionNavStarts="
         + RELIGION_NAV_STARTS.get()
         + " religionFestivalStarts="
         + RELIGION_FESTIVAL_STARTS.get()
         + " treasuryOps="
         + TREASURY_OPS.get()
         + " warCommits="
         + WAR_COMMITS.get()
         + " warDamageBlocks="
         + WAR_DAMAGE_BLOCKS.get()
         + " pathAstarNs="
         + PATH_ASTAR_NS.get()
         + " panelUpdates="
         + PANEL_UPDATES.get()
         + " sellerScans="
         + SELLER_SCANS.get()
         + " mobDespawn="
         + MOB_DESPAWN.get()
         + " goalPickNs="
         + GOAL_PICK_NS.get()
         + " planGateAcquires="
         + PLAN_GATE_ACQUIRES.get()
         + " planGateRejects="
         + PLAN_GATE_REJECTS.get()
         + " planGateInFlight="
         + PLAN_GATE_IN_FLIGHT.get()
         + " economySnapshots="
         + ECONOMY_SNAPSHOTS.get()
         + " economyCommits="
         + ECONOMY_COMMITS.get()
         + " logisticsCommits="
         + LOGISTICS_COMMITS.get()
         + " warQueueRejects="
         + WAR_QUEUE_REJECTS.get();
   }
}
