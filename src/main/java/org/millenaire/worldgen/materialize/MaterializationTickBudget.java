package org.millenaire.worldgen.materialize;

import org.millenaire.config.MillenaireServerConfig;

/**
 * Shared per-server-tick materialization budget. Multiple chunk-load events in the same tick
 * consume the same pool so they cannot each reset an unlimited allowance.
 */
public final class MaterializationTickBudget {
   private static long budgetGameTime = Long.MIN_VALUE;
   private static int remainingOps;
   private static int remainingScanCandidates;
   private static int remainingActivations;

   private MaterializationTickBudget() {
   }

   public static void ensureTick(long gameTime) {
      if (budgetGameTime != gameTime) {
         budgetGameTime = gameTime;
         remainingOps = MillenaireServerConfig.SERVER.worldgenMaterializationBudget.getAsInt();
         remainingScanCandidates = MillenaireServerConfig.SERVER.worldgenMaterializationScanBudget.getAsInt();
         remainingActivations = MillenaireServerConfig.SERVER.worldgenActivationBudget.getAsInt();
      }
   }

   public static int remainingOps() {
      return remainingOps;
   }

   public static boolean hasOps() {
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
      return consumeOps(1) == 1;
   }

   public static boolean hasScanCandidates() {
      return remainingScanCandidates > 0;
   }

   public static boolean tryConsumeScan() {
      if (remainingScanCandidates <= 0) {
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

   public static String diagnostic() {
      return "ops=" + remainingOps + " scans=" + remainingScanCandidates + " activations=" + remainingActivations;
   }
}
