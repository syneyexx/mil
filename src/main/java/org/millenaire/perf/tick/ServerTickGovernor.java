package org.millenaire.perf.tick;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.perf.path.PathRequestQueue;
import org.millenaire.worldgen.materialize.MaterializationTickBudget;

/**
 * Tick budget board: shared soft deadline with per-phase quotas, a reserved
 * WORLDGEN/HIGHWAY slice, adaptive allowance under load, and entity-loop attribution.
 */
public final class ServerTickGovernor {
   private static final double EMA_ALPHA = 0.08;
   private static final long IDEAL_TICK_NANOS = 50_000_000L;

   /** Immutable config view refreshed on ModConfigEvent / first use. */
   private record ConfigSnapshot(
      boolean enabled,
      boolean adaptiveBudget,
      int globalBudgetMs,
      int adaptiveMinGlobalMs,
      int phaseApplyMs,
      int phaseVillageNearMs,
      int phaseVillageMidMs,
      int phaseAiSupportMs,
      int phaseCivMs,
      int phaseWorldgenMs,
      int phaseHighwayMs,
      int phaseTailMs,
      int entityGoalPickBudget,
      int softMspt,
      int hardMspt
   ) {
      static ConfigSnapshot defaults() {
         return new ConfigSnapshot(true, true, 12, 6, 2, 4, 2, 1, 1, 2, 1, 1, 32, 40, 48);
      }

      static ConfigSnapshot readLive() {
         try {
            return new ConfigSnapshot(
               Boolean.TRUE.equals(MillenaireServerConfig.SERVER.tickGovernorEnabled.get()),
               Boolean.TRUE.equals(MillenaireServerConfig.SERVER.tickAdaptiveBudgetEnabled.get()),
               Math.max(1, MillenaireServerConfig.SERVER.tickGlobalBudgetMs.getAsInt()),
               Math.max(1, MillenaireServerConfig.SERVER.tickAdaptiveMinGlobalMs.getAsInt()),
               Math.max(1, MillenaireServerConfig.SERVER.tickPhaseApplyMs.getAsInt()),
               Math.max(1, MillenaireServerConfig.SERVER.tickPhaseVillageNearMs.getAsInt()),
               Math.max(1, MillenaireServerConfig.SERVER.tickPhaseVillageMidMs.getAsInt()),
               Math.max(1, MillenaireServerConfig.SERVER.tickPhaseAiSupportMs.getAsInt()),
               Math.max(1, MillenaireServerConfig.SERVER.tickPhaseCivMs.getAsInt()),
               Math.max(0, MillenaireServerConfig.SERVER.tickPhaseWorldgenMs.getAsInt()),
               Math.max(0, MillenaireServerConfig.SERVER.tickPhaseHighwayMs.getAsInt()),
               Math.max(1, MillenaireServerConfig.SERVER.tickPhaseTailMs.getAsInt()),
               Math.max(1, MillenaireServerConfig.SERVER.entityGoalPickBudgetPerTick.getAsInt()),
               MillenaireServerConfig.SERVER.tickSoftMsptThreshold.getAsInt(),
               MillenaireServerConfig.SERVER.tickHardMsptThreshold.getAsInt()
            );
         } catch (IllegalStateException e) {
            return defaults();
         }
      }
   }

   private static volatile ConfigSnapshot config = ConfigSnapshot.defaults();
   private static boolean profilerSectionOpen;
   @Nullable
   private static ProfilerFiller activeProfiler;

   private static long tickGameTime = Long.MIN_VALUE;
   private static TickPhase currentPhase = TickPhase.APPLY;
   private static long lastPhaseNanos;
   private static final long[] phaseNanos = new long[TickPhase.values().length];
   private static final int[] deferCounts = new int[TickPhase.values().length];
   private static final double[] phaseEmaMs = new double[TickPhase.values().length];
   private static final long[] phasePeakMs = new long[TickPhase.values().length];
   private static final int[] deferStreak = new int[TickPhase.values().length];
   private static final int[] deferStreakMax = new int[TickPhase.values().length];

   private static int nearVillages;
   private static int midVillages;
   private static int farVillages;
   private static int midDeferred;
   private static int aiCadenceSkipped;
   private static int activeVillages;
   private static int dataOnlyVillages;
   private static int backgroundTickSkipped;

   // Entity-loop accumulators (filled before Post beginTick); snapshotted for diagnostics.
   private static int entityNearAccum;
   private static int entityMidAccum;
   private static int entityFarAccum;
   private static int entityFarSleepAccum;
   private static int entityGoalPickDeferredAccum;
   private static long entityAiNanosAccum;
   private static int entityNearReported;
   private static int entityMidReported;
   private static int entityFarReported;
   private static int entityFarSleepReported;
   private static int entityGoalPickDeferredReported;
   private static long entityAiNanosReported;

   private static long entityBudgetGameTime = Long.MIN_VALUE;
   private static int entityGoalPickRemaining;

   private static long globalDeadlineNanos;
   private static long sharedDeadlineNanos;
   private static long phaseDeadlineNanos;
   private static long phaseStartNanos;
   private static long tickStartNanos;
   private static long serverTickStartNanos;
   private static int effectiveGlobalMs = 12;
   private static int configuredGlobalMs = 12;
   private static long alreadyUsedBeforePostNanos;
   private static TickPressure publishedPressure = TickPressure.HEALTHY;
   private static TickPressure nextPressure = TickPressure.HEALTHY;
   private static int pressureRecoveryTicks;
   private static double postSpentEmaMs;

   private ServerTickGovernor() {
   }

   /** Refresh cached config values (call from ModConfigEvent / server start). */
   public static void refreshConfigSnapshot() {
      config = ConfigSnapshot.readLive();
   }

   public static boolean enabled() {
      return config.enabled();
   }

   public static boolean adaptiveBudgetEnabled() {
      return config.adaptiveBudget();
   }

   public static int globalBudgetMs() {
      return config.globalBudgetMs();
   }

   public static int effectiveGlobalBudgetMs() {
      return Math.max(1, effectiveGlobalMs);
   }

   public static TickPressure pressure() {
      return publishedPressure;
   }

   public static int phaseWorldgenMs() {
      return config.phaseWorldgenMs();
   }

   public static int phaseHighwayMs() {
      return config.phaseHighwayMs();
   }

   public static int entityGoalPickBudgetPerTick() {
      return config.entityGoalPickBudget();
   }

   /** Wall-clock mark at {@code ServerTickEvent.Pre} for remaining-tick awareness. */
   public static void markServerTickStart() {
      serverTickStartNanos = System.nanoTime();
   }

   public static void beginTick(ServerLevel level) {
      if (level != null) {
         LevelTickCache.ensure(level);
         activeProfiler = level.getProfiler();
         beginTick(level.getGameTime());
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
         long now = System.nanoTime();
         lastPhaseNanos = now;
         tickStartNanos = now;
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

         // Entity loop for this gameTime already finished — snapshot then clear accumulators.
         entityNearReported = entityNearAccum;
         entityMidReported = entityMidAccum;
         entityFarReported = entityFarAccum;
         entityFarSleepReported = entityFarSleepAccum;
         entityGoalPickDeferredReported = entityGoalPickDeferredAccum;
         entityAiNanosReported = entityAiNanosAccum;
         entityNearAccum = 0;
         entityMidAccum = 0;
         entityFarAccum = 0;
         entityFarSleepAccum = 0;
         entityGoalPickDeferredAccum = 0;
         entityAiNanosAccum = 0L;

         publishedPressure = nextPressure;
         configuredGlobalMs = globalBudgetMs();
         alreadyUsedBeforePostNanos = serverTickStartNanos > 0L ? Math.max(0L, now - serverTickStartNanos) : 0L;
         effectiveGlobalMs = computeEffectiveGlobalMs(configuredGlobalMs, alreadyUsedBeforePostNanos, publishedPressure);

         int wgMs = phaseWorldgenMs();
         int hwMs = phaseHighwayMs();
         int reserveMs = Math.min(effectiveGlobalMs - 1, scaleReserve(wgMs + hwMs));
         globalDeadlineNanos = now + (long)effectiveGlobalMs * 1_000_000L;
         sharedDeadlineNanos = now + (long)Math.max(1, effectiveGlobalMs - reserveMs) * 1_000_000L;
         phaseStartNanos = now;
         phaseDeadlineNanos = sharedDeadlineNanos;
         MaterializationTickBudget.setDeadlineNanos(globalDeadlineNanos);
         MaterializationTickBudget.applyPressureScale(publishedPressure.materializationScale(), publishedPressure.discoveryScale());
      }
   }

   private static int scaleReserve(int baseReserveMs) {
      if (configuredGlobalMs <= 0) {
         return baseReserveMs;
      }
      // Keep reserve proportional when adaptive allowance shrinks.
      return Math.max(1, (int)Math.round(baseReserveMs * (effectiveGlobalMs / (double)configuredGlobalMs)));
   }

   private static int computeEffectiveGlobalMs(int configured, long alreadyUsedNanos, TickPressure pressure) {
      int ms = configured;
      if (!adaptiveBudgetEnabled()) {
         return ms;
      }
      int minMs = Math.min(config.adaptiveMinGlobalMs(), configured);

      long remainingIdeal = IDEAL_TICK_NANOS - alreadyUsedNanos;
      int remainingMs = (int)Math.max(0L, remainingIdeal / 1_000_000L);
      // Leave a little headroom for vanilla tail / other mods after our Post work.
      int fromRemaining = Math.max(minMs, remainingMs - 2);
      ms = Math.min(ms, fromRemaining);

      if (pressure == TickPressure.SOFT) {
         ms = Math.min(ms, Math.max(minMs, (configured * 3) / 4));
      } else if (pressure == TickPressure.HARD) {
         ms = Math.min(ms, Math.max(minMs, configured / 2));
      }
      return Math.max(minMs, Math.min(configured, ms));
   }

   public static void enter(TickPhase phase) {
      if (phase == null) {
         return;
      }
      long now = System.nanoTime();
      phaseNanos[currentPhase.ordinal()] += Math.max(0L, now - lastPhaseNanos);
      lastPhaseNanos = now;
      popProfilerSection();
      currentPhase = phase;
      pushProfilerSection(phase);
      phaseStartNanos = now;
      phaseDeadlineNanos = phaseQuotaDeadline(phase, now);
   }

   private static void pushProfilerSection(TickPhase phase) {
      ProfilerFiller profiler = activeProfiler;
      if (profiler == null || phase == null) {
         return;
      }
      profiler.push("millenaire:" + phase.name().toLowerCase());
      profilerSectionOpen = true;
   }

   private static void popProfilerSection() {
      if (!profilerSectionOpen) {
         return;
      }
      ProfilerFiller profiler = activeProfiler;
      if (profiler != null) {
         profiler.pop();
      }
      profilerSectionOpen = false;
   }

   private static long phaseQuotaDeadline(TickPhase phase, long now) {
      int quotaMs = scalePhaseQuota(phaseQuotaMs(phase));
      long quotaDeadline = now + (long)quotaMs * 1_000_000L;
      if (usesReservedPie(phase)) {
         return Math.min(globalDeadlineNanos, quotaDeadline);
      }
      return Math.min(sharedDeadlineNanos, quotaDeadline);
   }

   private static int scalePhaseQuota(int quotaMs) {
      if (configuredGlobalMs <= 0 || effectiveGlobalMs >= configuredGlobalMs) {
         return Math.max(1, quotaMs);
      }
      int scaled = (int)Math.round(quotaMs * (effectiveGlobalMs / (double)configuredGlobalMs));
      return Math.max(1, scaled);
   }

   private static boolean usesReservedPie(TickPhase phase) {
      return phase == TickPhase.WORLDGEN
         || phase == TickPhase.HIGHWAY
         || phase == TickPhase.BOSS_QUEST_MAP;
   }

   private static int phaseQuotaMs(TickPhase phase) {
      return switch (phase) {
         case APPLY -> config.phaseApplyMs();
         case VILLAGE_NEAR -> config.phaseVillageNearMs();
         case VILLAGE_MID -> config.phaseVillageMidMs();
         case AI_SUPPORT -> config.phaseAiSupportMs();
         case CIV_FACTION -> config.phaseCivMs();
         case WORLDGEN -> Math.max(1, phaseWorldgenMs());
         case HIGHWAY -> Math.max(1, phaseHighwayMs());
         case BOSS_QUEST_MAP -> config.phaseTailMs();
      };
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

   public static void noteEntityLod(EntitySimulationLod lod) {
      if (lod == null) {
         return;
      }
      switch (lod) {
         case NEAR -> entityNearAccum++;
         case MID -> entityMidAccum++;
         case FAR -> entityFarAccum++;
      }
   }

   public static void noteEntityFarSleep() {
      entityFarSleepAccum++;
   }

   public static void noteEntityAiNanos(long nanos) {
      if (nanos > 0L) {
         entityAiNanosAccum += nanos;
      }
   }

   /**
    * Swarm fairness for goal-pick across loaded villagers (entity loop).
    * Urgent villagers (hired / companion / in combat) always pass.
    */
   public static boolean tryConsumeEntityGoalPick(long gameTime, boolean urgent) {
      ensureEntityBudgetTick(gameTime);
      if (urgent || !enabled()) {
         return true;
      }
      if (entityGoalPickRemaining <= 0) {
         entityGoalPickDeferredAccum++;
         return false;
      }
      entityGoalPickRemaining--;
      return true;
   }

   private static void ensureEntityBudgetTick(long gameTime) {
      if (entityBudgetGameTime != gameTime) {
         entityBudgetGameTime = gameTime;
         entityGoalPickRemaining = entityGoalPickBudgetPerTick();
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
      popProfilerSection();
      activeProfiler = null;

      long postSpentNanos = Math.max(0L, now - tickStartNanos);
      double postSpentMs = postSpentNanos / 1_000_000.0;
      postSpentEmaMs = postSpentEmaMs <= 0.0 ? postSpentMs : postSpentEmaMs * (1.0 - EMA_ALPHA) + postSpentMs * EMA_ALPHA;

      for (int i = 0; i < phaseNanos.length; i++) {
         double sampleMs = phaseNanos[i] / 1_000_000.0;
         phaseEmaMs[i] = phaseEmaMs[i] <= 0.0 ? sampleMs : phaseEmaMs[i] * (1.0 - EMA_ALPHA) + sampleMs * EMA_ALPHA;
         long sampleLong = phaseNanos[i] / 1_000_000L;
         if (sampleLong > phasePeakMs[i]) {
            phasePeakMs[i] = sampleLong;
         }
         if (deferCounts[i] > 0) {
            deferStreak[i]++;
            if (deferStreak[i] > deferStreakMax[i]) {
               deferStreakMax[i] = deferStreak[i];
            }
         } else {
            deferStreak[i] = 0;
         }
      }

      updateNextPressure(postSpentNanos, alreadyUsedBeforePostNanos);
   }

   private static void updateNextPressure(long postSpentNanos, long alreadyUsedNanos) {
      long totalApprox = postSpentNanos + alreadyUsedNanos;
      int softMs = config.softMspt();
      int hardMs = Math.max(softMs + 1, config.hardMspt());

      TickPressure measured;
      long allowanceNanos = (long)effectiveGlobalMs * 1_000_000L;
      if (totalApprox >= (long)hardMs * 1_000_000L || postSpentNanos >= allowanceNanos + 2_000_000L) {
         measured = TickPressure.HARD;
      } else if (totalApprox >= (long)softMs * 1_000_000L || postSpentNanos >= allowanceNanos) {
         measured = TickPressure.SOFT;
      } else {
         measured = TickPressure.HEALTHY;
      }

      // Also consider sustained Millenaire Post EMA vs configured budget.
      if (measured == TickPressure.HEALTHY && postSpentEmaMs >= configuredGlobalMs) {
         measured = TickPressure.SOFT;
      }
      if (postSpentEmaMs >= configuredGlobalMs * 1.5) {
         measured = TickPressure.HARD;
      }

      if (measured.ordinal() > nextPressure.ordinal()) {
         nextPressure = measured;
         pressureRecoveryTicks = measured == TickPressure.HARD ? 8 : 4;
      } else if (measured.ordinal() < nextPressure.ordinal()) {
         if (pressureRecoveryTicks > 0) {
            pressureRecoveryTicks--;
         } else {
            nextPressure = measured;
            pressureRecoveryTicks = measured == TickPressure.HEALTHY ? 0 : 2;
         }
      } else {
         pressureRecoveryTicks = measured == TickPressure.HARD ? 8 : measured == TickPressure.SOFT ? 4 : 0;
      }
   }

   public static String diagnostic() {
      StringBuilder sb = new StringBuilder(480);
      sb.append("gov=")
         .append(enabled())
         .append(" globalMs=")
         .append(configuredGlobalMs)
         .append(" effMs=")
         .append(effectiveGlobalMs)
         .append(" pressure=")
         .append(publishedPressure.name().toLowerCase())
         .append(" preMs=")
         .append(alreadyUsedBeforePostNanos / 1_000_000L)
         .append(" postEmaMs=")
         .append(String.format("%.1f", postSpentEmaMs))
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
         .append("} entityLod={near=")
         .append(entityNearReported)
         .append(",mid=")
         .append(entityMidReported)
         .append(",far=")
         .append(entityFarReported)
         .append(",sleep=")
         .append(entityFarSleepReported)
         .append(",goalDefer=")
         .append(entityGoalPickDeferredReported)
         .append("} entityAiMs=")
         .append(entityAiNanosReported / 1_000_000L)
         .append(" villages={active=")
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
            .append(deferCounts[i])
            .append("/ema")
            .append(String.format("%.1f", phaseEmaMs[i]))
            .append("/pk")
            .append(phasePeakMs[i])
            .append("/st")
            .append(deferStreakMax[i]);
      }
      sb.append('}');
      return sb.toString();
   }

   /** Compact dump for {@code /millenaire dev tick dump}. */
   public static String dumpSnapshot() {
      return diagnostic();
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
      tickStartNanos = 0L;
      serverTickStartNanos = 0L;
      effectiveGlobalMs = 12;
      configuredGlobalMs = 12;
      alreadyUsedBeforePostNanos = 0L;
      publishedPressure = TickPressure.HEALTHY;
      nextPressure = TickPressure.HEALTHY;
      pressureRecoveryTicks = 0;
      postSpentEmaMs = 0.0;
      entityBudgetGameTime = Long.MIN_VALUE;
      entityGoalPickRemaining = 0;
      entityNearAccum = entityMidAccum = entityFarAccum = entityFarSleepAccum = entityGoalPickDeferredAccum = 0;
      entityNearReported = entityMidReported = entityFarReported = entityFarSleepReported = entityGoalPickDeferredReported = 0;
      entityAiNanosAccum = entityAiNanosReported = 0L;
      for (int i = 0; i < phaseNanos.length; i++) {
         phaseNanos[i] = 0L;
         deferCounts[i] = 0;
         phaseEmaMs[i] = 0.0;
         phasePeakMs[i] = 0L;
         deferStreak[i] = 0;
         deferStreakMax[i] = 0;
      }
      nearVillages = 0;
      midVillages = 0;
      farVillages = 0;
      midDeferred = 0;
      aiCadenceSkipped = 0;
      activeVillages = 0;
      dataOnlyVillages = 0;
      backgroundTickSkipped = 0;
      popProfilerSection();
      activeProfiler = null;
      PathRequestQueue.resetForTest();
      MaterializationTickBudget.reset();
      LevelTickCache.resetForTest();
   }

   /** Test helper: simulate wall time already used before Post. */
   static void setServerTickStartForTest(long nanos) {
      serverTickStartNanos = nanos;
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

   static int entityNearReported() {
      return entityNearReported;
   }

   static long entityAiNanosReported() {
      return entityAiNanosReported;
   }
}
