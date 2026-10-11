package org.millenaire.perf.compute;

import org.millenaire.config.MillenaireServerConfig;

/**
 * Config-backed kill switches for compute offload phases.
 */
public final class ComputeOffload {
   private ComputeOffload() {
   }

   public static boolean masterEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.computeOffloadEnabled.get())
            && MillenaireComputePool.isRunning();
      } catch (IllegalStateException e) {
         return false;
      }
   }

   public static boolean villagePaths() {
      try {
         return masterEnabled() && Boolean.TRUE.equals(MillenaireServerConfig.SERVER.computeOffloadVillagePaths.get());
      } catch (IllegalStateException e) {
         return false;
      }
   }

   public static boolean highwayPlan() {
      try {
         return masterEnabled() && Boolean.TRUE.equals(MillenaireServerConfig.SERVER.computeOffloadHighwayPlan.get());
      } catch (IllegalStateException e) {
         return false;
      }
   }

   public static boolean worldgenPlan() {
      try {
         return masterEnabled() && Boolean.TRUE.equals(MillenaireServerConfig.SERVER.computeOffloadWorldgenPlan.get());
      } catch (IllegalStateException e) {
         return false;
      }
   }

   public static boolean waypointGraph() {
      try {
         return masterEnabled() && Boolean.TRUE.equals(MillenaireServerConfig.SERVER.computeOffloadWaypointGraph.get());
      } catch (IllegalStateException e) {
         return false;
      }
   }

   public static boolean entityPaths() {
      try {
         return masterEnabled() && Boolean.TRUE.equals(MillenaireServerConfig.SERVER.computeOffloadEntityPaths.get());
      } catch (IllegalStateException e) {
         return false;
      }
   }

   public static int applyBudgetPerTick() {
      try {
         return MillenaireServerConfig.SERVER.computeApplyBudgetPerTick.getAsInt();
      } catch (IllegalStateException e) {
         return 8;
      }
   }

   public static int configuredWorkerThreads() {
      try {
         return MillenaireServerConfig.SERVER.computeWorkerThreads.getAsInt();
      } catch (IllegalStateException e) {
         return 0;
      }
   }
}
