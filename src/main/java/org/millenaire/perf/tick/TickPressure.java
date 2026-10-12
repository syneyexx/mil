package org.millenaire.perf.tick;

/**
 * Soft load pressure for adaptive Post-tick budgets.
 * Published from the previous tick so the current tick does not feedback-loop mid-frame.
 */
public enum TickPressure {
   HEALTHY,
   SOFT,
   HARD;

   public double materializationScale() {
      return switch (this) {
         case HEALTHY -> 1.0;
         case SOFT -> 0.62;
         case HARD -> 0.34;
      };
   }

   public double discoveryScale() {
      return switch (this) {
         case HEALTHY -> 1.0;
         case SOFT -> 0.5;
         case HARD -> 0.25;
      };
   }
}
