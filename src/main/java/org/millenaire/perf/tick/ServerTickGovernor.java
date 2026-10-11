package org.millenaire.perf.tick;

import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.perf.path.PathRequestQueue;
import org.millenaire.worldgen.materialize.MaterializationTickBudget;

/**
 * Tick budget board: shared soft deadline with per-phase quotas and a reserved
 * WORLDGEN/HIGHWAY slice that earlier phases cannot consume.
 */
public final class ServerTickGovernor {
   private static long tickGameTime = Long.MIN_VALUE;
   private static TickPhase currentPhase = TickPhase.APPLY;
   private static long lastPhaseNanos;
   private static final long[] phaseNanos = new long[TickPhase.values().length];
   private static final int[] deferCounts = new int[TickPhase.values().length];
   private static int nearVillages;
   private static int midVillages;
   private static int farVillages;
   private static int midDeferred;
   private static int aiCadenceSkipped;
   private static int activeVillages;
   private static int dataOnlyVillages;
   private static int backgroundTickSkipped;

   private static long globalDeadlineNanos;
   private static long sharedDeadlineNanos;
   private static long phaseDeadlineNanos;
   private static long phaseStartNanos;

   private ServerTickGovernor() {
   }

   public static boolean enabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.tickGovernorEnabled.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   public static int globalBudgetMs() {
      try {
         return Math.max(1, MillenaireServerConfig.SERVER.tickGlobalBudgetMs.getAsInt());
      } catch (IllegalStateException e) {
         return 12;
      }
   }

   public static int phaseWorldgenMs() {
      try {
         return Math.max(0, MillenaireServerConfig.SERVER.tickPhaseWorldgenMs.getAsInt());
      } catch (IllegalStateException e) {
         return 2;
      }
   }

   public static int phaseHighwayMs() {
      try {
         return Math.max(0, MillenaireServerConfig.SERVER.tickPhaseHighwayMs.getAsInt());
      } catch (IllegalStateException e) {
         return 1;
      }
   }

   public static void beginTick(long gameTime) {
      PathRequestQueue.ensureTick(gameTime);
      try {
         MaterializationTickBudget.ensureTick(gameTime);
      } catch (IllegalStateException e) {
         // Config not loaded yet
      }
      if (tickGameTime != gameTime) {
         tickGameTime = gameTime;
         lastPhaseNanos = System.nanoTime();
         currentPhase = TickPhase.APPLY;
         for (int i = 0; i < phaseNanos.length; i++) {
            phaseNanos[i] = 0L;
            deferCounts[i] = 0;
         }
         nearVillages = 0;
         midVillages = 0;
         farVillages = 0;
         midDeferred = 0;
         aiCadenceSkipped = 0;
         activeVillages = 0;
         dataOnlyVillages = 0;
         backgroundTickSkipped = 0;

         long now = lastPhaseNanos;
         int globalMs = globalBudgetMs();
         int wgMs = phaseWorldgenMs();
         int hwMs = phaseHighwayMs();
         int reserveMs = Math.min(globalMs - 1, wgMs + hwMs);
         globalDeadlineNanos = now + (long)globalMs * 1_000_000L;
         sharedDeadlineNanos = now + (long)Math.max(1, globalMs - reserveMs) * 1_000_000L;
         phaseStartNanos = now;
         phaseDeadlineNanos = sharedDeadlineNanos;
         // Align materialization soft deadline with global budget so worldgen can use the reserve.
         MaterializationTickBudget.setDeadlineNanos(globalDeadlineNanos);
      }
   }

   public static void enter(TickPhase phase) {
      if (phase == null) {
         return;
      }
      long now = System.nanoTime();
      phaseNanos[currentPhase.ordinal()] += Math.max(0L, now - lastPhaseNanos);
      lastPhaseNanos = now;
      currentPhase = phase;
      phaseStartNanos = now;
      phaseDeadlineNanos = phaseQuotaDeadline(phase, now);
   }

   private static long phaseQuotaDeadline(TickPhase phase, long now) {
      int quotaMs = phaseQuotaMs(phase);
      long quotaDeadline = now + (long)quotaMs * 1_000_000L;
      if (usesReservedPie(phase)) {
         return Math.min(globalDeadlineNanos, quotaDeadline);
      }
      return Math.min(sharedDeadlineNanos, quotaDeadline);
   }

   private static boolean usesReservedPie(TickPhase phase) {
      return phase == TickPhase.WORLDGEN
         || phase == TickPhase.HIGHWAY
         || phase == TickPhase.BOSS_QUEST_MAP;
   }

   private static int phaseQuotaMs(TickPhase phase) {
      try {
         return switch (phase) {
            case APPLY -> Math.max(1, MillenaireServerConfig.SERVER.tickPhaseApplyMs.getAsInt());
            case VILLAGE_NEAR -> Math.max(1, MillenaireServerConfig.SERVER.tickPhaseVillageNearMs.getAsInt());
            case VILLAGE_MID -> Math.max(1, MillenaireServerConfig.SERVER.tickPhaseVillageMidMs.getAsInt());
            case AI_SUPPORT -> Math.max(1, MillenaireServerConfig.SERVER.tickPhaseAiSupportMs.getAsInt());
            case CIV_FACTION -> Math.max(1, MillenaireServerConfig.SERVER.tickPhaseCivMs.getAsInt());
            case WORLDGEN -> Math.max(1, phaseWorldgenMs());
            case HIGHWAY -> Math.max(1, phaseHighwayMs());
            case BOSS_QUEST_MAP -> Math.max(1, MillenaireServerConfig.SERVER.tickPhaseTailMs.getAsInt());
         };
      } catch (IllegalStateException e) {
         return switch (phase) {
            case APPLY -> 2;
            case VILLAGE_NEAR -> 4;
            case VILLAGE_MID -> 2;
            case AI_SUPPORT, CIV_FACTION -> 1;
            case WORLDGEN -> 2;
            case HIGHWAY -> 1;
            case BOSS_QUEST_MAP -> 1;
         };
      }
   }

   public static boolean hasTime() {
      if (!enabled()) {
         return true;
      }
      long now = System.nanoTime();
      if (now >= phaseDeadlineNanos) {
         return false;
      }
      if (usesReservedPie(currentPhase)) {
         return now < globalDeadlineNanos;
      }
      return now < sharedDeadlineNanos;
   }

   /**
    * Soft gate: returns false when the shared deadline is spent (caller should defer).
    */
   public static boolean tryContinue(TickPhase phase) {
      enter(phase);
      return hasTime();
   }

   public static void noteDefer(TickPhase phase) {
      if (phase != null) {
         deferCounts[phase.ordinal()]++;
      }
   }

   public static void noteLod(VillageSimulationLod lod) {
      if (lod == null) {
         return;
      }
      switch (lod) {
         case NEAR -> nearVillages++;
         case MID -> midVillages++;
         case FAR -> farVillages++;
      }
   }

   public static void noteActiveVillage() {
      activeVillages++;
   }

   public static void noteDataOnlyVillage() {
      dataOnlyVillages++;
   }

   public static void noteBackgroundTickSkipped() {
      backgroundTickSkipped++;
   }

   public static void noteMidDeferred() {
      midDeferred++;
   }

   public static void noteAiCadenceSkipped() {
      aiCadenceSkipped++;
   }

   public static void endTick() {
      long now = System.nanoTime();
      phaseNanos[currentPhase.ordinal()] += Math.max(0L, now - lastPhaseNanos);
      lastPhaseNanos = now;
   }

   public static String diagnostic() {
      StringBuilder sb = new StringBuilder(320);
      sb.append("gov=")
         .append(enabled())
         .append(" globalMs=")
         .append(globalBudgetMs())
         .append(' ')
         .append(MaterializationTickBudget.diagnostic())
         .append(' ')
         .append(PathRequestQueue.diagnostic())
         .append(' ')
         .append(org.millenaire.perf.sense.CombatSenseCache.diagnostic())
         .append(" lod={near=")
         .append(nearVillages)
         .append(",mid=")
         .append(midVillages)
         .append(",far=")
         .append(farVillages)
         .append(",midDefer=")
         .append(midDeferred)
         .append("} villages={active=")
         .append(activeVillages)
         .append(",dataOnly=")
         .append(dataOnlyVillages)
         .append(",bgSkip=")
         .append(backgroundTickSkipped)
         .append("} aiSkip=")
         .append(aiCadenceSkipped)
         .append(" phaseMs={");
      TickPhase[] phases = TickPhase.values();
      for (int i = 0; i < phases.length; i++) {
         if (i > 0) {
            sb.append(',');
         }
         sb.append(phases[i].name().toLowerCase())
            .append('=')
            .append(phaseNanos[i] / 1_000_000L)
            .append("/d")
            .append(deferCounts[i]);
      }
      sb.append('}');
      return sb.toString();
   }

   /** Test / command helper — resets counters without advancing game time. */
   public static void resetForTest() {
      tickGameTime = Long.MIN_VALUE;
      currentPhase = TickPhase.APPLY;
      lastPhaseNanos = 0L;
      globalDeadlineNanos = 0L;
      sharedDeadlineNanos = 0L;
      phaseDeadlineNanos = 0L;
      phaseStartNanos = 0L;
      for (int i = 0; i < phaseNanos.length; i++) {
         phaseNanos[i] = 0L;
         deferCounts[i] = 0;
      }
      nearVillages = 0;
      midVillages = 0;
      farVillages = 0;
      midDeferred = 0;
      aiCadenceSkipped = 0;
      activeVillages = 0;
      dataOnlyVillages = 0;
      backgroundTickSkipped = 0;
      PathRequestQueue.resetForTest();
      MaterializationTickBudget.reset();
   }

   static int deferCount(TickPhase phase) {
      return deferCounts[phase.ordinal()];
   }

   static int nearCount() {
      return nearVillages;
   }

   static int midCount() {
      return midVillages;
   }

   static int farCount() {
      return farVillages;
   }
}
