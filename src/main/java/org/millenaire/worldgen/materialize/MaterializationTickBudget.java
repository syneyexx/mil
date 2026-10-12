package org.millenaire.worldgen.materialize;

import org.millenaire.config.MillenaireServerConfig;

/**
 * Shared per-server-tick materialization budget combining operation counts with a wall-clock
 * deadline. Multiple chunk-load events in the same tick consume the same pool.
 */
public final class MaterializationTickBudget {
   private static long budgetGameTime = Long.MIN_VALUE;
   private static int remainingOps;
   private static int remainingScanCandidates;
   private static int remainingActivations;
   private static int remainingPlanSteps;
   private static long deadlineNanos;
   private static long tickStartNanos;
   private static long lastPhaseNanos;
   private static long planningNanos;
   private static long layoutNanos;
   private static long materializeNanos;
   private static long activationNanos;

   private MaterializationTickBudget() {
   }

   public static void ensureTick(long gameTime) {
      if (budgetGameTime != gameTime) {
         budgetGameTime = gameTime;
         remainingOps = MillenaireServerConfig.SERVER.worldgenMaterializationBudget.getAsInt();
         remainingScanCandidates = MillenaireServerConfig.SERVER.worldgenMaterializationScanBudget.getAsInt();
         remainingActivations = MillenaireServerConfig.SERVER.worldgenActivationBudget.getAsInt();
         remainingPlanSteps = MillenaireServerConfig.SERVER.worldgenPlanningBudget.getAsInt();
         int timeMs;
         try {
            // Prefer tick governor global budget when present; fall back to legacy worldgen key.
            timeMs = MillenaireServerConfig.SERVER.tickGlobalBudgetMs.getAsInt();
         } catch (IllegalStateException e) {
            timeMs = MillenaireServerConfig.SERVER.worldgenTickTimeBudgetMs.getAsInt();
         }
         tickStartNanos = System.nanoTime();
         deadlineNanos = tickStartNanos + Math.max(1, timeMs) * 1_000_000L;
         lastPhaseNanos = tickStartNanos;
         planningNanos = 0L;
         layoutNanos = 0L;
         materializeNanos = 0L;
         activationNanos = 0L;
      }
   }

   /** Align soft deadline with {@link org.millenaire.perf.tick.ServerTickGovernor} reserved pie. */
   public static void setDeadlineNanos(long nanos) {
      deadlineNanos = nanos;
   }

   /**
    * Scale planning/discovery budgets under tick pressure while keeping materialize ops usable for apply progress.
    * Call after {@link #ensureTick(long)} when the governor publishes pressure for this tick.
    */
   public static void applyPressureScale(double materializeScale, double discoveryScale) {
      double mat = clamp01(materializeScale);
      double disc = clamp01(discoveryScale);
      if (mat >= 0.999 && disc >= 0.999) {
         return;
      }
      remainingOps = Math.max(1, (int)Math.floor(remainingOps * mat));
      remainingScanCandidates = Math.max(1, (int)Math.floor(remainingScanCandidates * disc));
      remainingPlanSteps = Math.max(1, (int)Math.floor(remainingPlanSteps * disc));
      // Activations stay available so catch-up can finish already-planned work.
      remainingActivations = Math.max(1, (int)Math.floor(remainingActivations * Math.max(mat, 0.5)));
   }

   private static double clamp01(double v) {
      if (v < 0.0) {
         return 0.0;
      }
      if (v > 1.0) {
         return 1.0;
      }
      return v;
   }

   public static boolean hasTime() {
      return System.nanoTime() < deadlineNanos;
   }

   /** Shared soft deadline for this server tick (nanos). */
   public static long deadlineNanos() {
      return deadlineNanos;
   }

   public static boolean hasTimeAndOps() {
      return hasTime() && remainingOps > 0;
   }

   public static void notePhase(Phase phase) {
      long now = System.nanoTime();
      long delta = Math.max(0L, now - lastPhaseNanos);
      lastPhaseNanos = now;
      switch (phase) {
         case PLANNING -> planningNanos += delta;
         case LAYOUT -> layoutNanos += delta;
         case MATERIALIZE -> materializeNanos += delta;
         case ACTIVATION -> activationNanos += delta;
      }
   }

   public enum Phase {
      PLANNING,
      LAYOUT,
      MATERIALIZE,
      ACTIVATION
   }

   public static int remainingOps() {
      return remainingOps;
   }

   public static boolean hasOps() {
      // Op count is independent of the wall-clock soft deadline so a short time budget cannot
      // zero out all materialization after planning. Callers still check hasTime() between units.
      return remainingOps > 0;
   }

   public static int consumeOps(int amount) {
      if (amount <= 0 || remainingOps <= 0) {
         return 0;
      }
      int take = Math.min(amount, remainingOps);
      remainingOps -= take;
      return take;
   }

   public static boolean tryConsumeOp() {
      if (!hasTime() || remainingOps <= 0) {
         return false;
      }
      return consumeOps(1) == 1;
   }

   public static boolean hasScanCandidates() {
      return remainingScanCandidates > 0 && hasTime();
   }

   public static boolean tryConsumeScan() {
      if (remainingScanCandidates <= 0 || !hasTime()) {
         return false;
      }
      remainingScanCandidates--;
      return true;
   }

   public static boolean hasActivationBudget() {
      return remainingActivations > 0;
   }

   /** Counts an activation attempt even when it does not succeed yet. */
   public static boolean tryConsumeActivationAttempt() {
      if (remainingActivations <= 0) {
         return false;
      }
      remainingActivations--;
      return true;
   }

   public static boolean hasPlanBudget() {
      return remainingPlanSteps > 0;
   }

   public static boolean tryConsumePlanStep() {
      if (remainingPlanSteps <= 0) {
         return false;
      }
      remainingPlanSteps--;
      return true;
   }

   public static long elapsedMillis() {
      return Math.max(0L, (System.nanoTime() - tickStartNanos) / 1_000_000L);
   }

   public static String diagnostic() {
      return "ops="
         + remainingOps
         + " scans="
         + remainingScanCandidates
         + " activations="
         + remainingActivations
         + " plans="
         + remainingPlanSteps
         + " elapsedMs="
         + elapsedMillis()
         + " phaseMs={plan="
         + planningNanos / 1_000_000L
         + ",layout="
         + layoutNanos / 1_000_000L
         + ",mat="
         + materializeNanos / 1_000_000L
         + ",act="
         + activationNanos / 1_000_000L
         + "}";
   }

   public static void reset() {
      budgetGameTime = Long.MIN_VALUE;
      remainingOps = 0;
      remainingScanCandidates = 0;
      remainingActivations = 0;
      remainingPlanSteps = 0;
      deadlineNanos = 0L;
   }
}
