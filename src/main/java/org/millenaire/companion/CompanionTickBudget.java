package org.millenaire.companion;

import org.millenaire.perf.tick.ServerTickGovernor;
import org.millenaire.perf.tick.TickPhase;

/**
 * Gates companion main-thread work under the server tick governor.
 */
public final class CompanionTickBudget {
   private static final long MAX_NANOS_PER_COMPANION = 500_000L; // 0.5 ms
   private static long sliceStartNanos;
   private static boolean active;

   private CompanionTickBudget() {
   }

   public static void beginSlice() {
      ServerTickGovernor.enter(TickPhase.AI_SUPPORT);
      sliceStartNanos = System.nanoTime();
      active = true;
   }

   public static void endSlice() {
      active = false;
   }

   public static boolean hasTime() {
      if (!ServerTickGovernor.hasTime()) {
         return false;
      }
      if (!active) {
         return ServerTickGovernor.hasTime();
      }
      return System.nanoTime() - sliceStartNanos < MAX_NANOS_PER_COMPANION * 4L;
   }

   public static boolean hasTimeForCompanion() {
      if (!hasTime()) {
         ServerTickGovernor.noteDefer(TickPhase.AI_SUPPORT);
         return false;
      }
      return System.nanoTime() - sliceStartNanos < MAX_NANOS_PER_COMPANION * 8L;
   }
}
