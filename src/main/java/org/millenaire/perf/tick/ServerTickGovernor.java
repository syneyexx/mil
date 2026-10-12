package org.millenaire.perf.tick;

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

   public static boolean enabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.tickGovernorEnabled.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   public static boolean adaptiveBudgetEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.tickAdaptiveBudgetEnabled.get());
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

   public static int effectiveGlobalBudgetMs() {
      return Math.max(1, effectiveGlobalMs);
   }

   public static TickPressure pressure() {
      return publishedPressure;
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

   public static int entityGoalPickBudgetPerTick() {
      try {
         return Math.max(1, MillenaireServerConfig.SERVER.entityGoalPickBudgetPerTick.getAsInt());
      } catch (IllegalStateException e) {
         return 32;
      }
   }

   /** Wall-clock mark at {@code ServerTickEvent.Pre} for remaining-tick awareness. */
   public static void markServerTickStart() {
      serverTickStartNanos = System.nanoTime();
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
      int minMs;
      try {
         minMs = Math.max(1, MillenaireServerConfig.SERVER.tickAdaptiveMinGlobalMs.getAsInt());
      } catch (IllegalStateException e) {
         minMs = 6;
      }
      minMs = Math.min(minMs, configured);

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
      currentPhase = phase;
      phaseStartNanos = now;
      phaseDeadlineNanos = phaseQuotaDeadline(phase, now);
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
      int softMs;
      int hardMs;
      try {
         softMs = MillenaireServerConfig.SERVER.tickSoftMsptThreshold.getAsInt();
         hardMs = MillenaireServerConfig.SERVER.tickHardMsptThreshold.getAsInt();
      } catch (IllegalStateException e) {
         softMs = 40;
         hardMs = 48;
      }
      hardMs = Math.max(softMs + 1, hardMs);

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
      PathRequestQueue.resetForTest();
      MaterializationTickBudget.reset();
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
