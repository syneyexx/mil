package org.millenaire.perf.plan;

/**
 * Per-tick shared commit units for applying planner results on the server thread.
 * Reset at the start of each Millénaire post-tick; domains spend units as they apply.
 */
public final class CommitBudget {
   private static final CommitBudget INSTANCE = new CommitBudget();

   public static final int DEFAULT_UNITS_PER_TICK = 8;

   private int remaining;
   private int candidatesRemaining;

   private CommitBudget() {
   }

   public static CommitBudget get() {
      return INSTANCE;
   }

   public void resetTick(int commitUnits, int candidateUnits) {
      this.remaining = Math.max(0, commitUnits);
      this.candidatesRemaining = Math.max(0, candidateUnits);
   }

   public boolean trySpendCommit(int units) {
      if (units <= 0) {
         return true;
      }
      if (this.remaining < units) {
         return false;
      }
      this.remaining -= units;
      return true;
   }

   public boolean trySpendCandidate(int units) {
      if (units <= 0) {
         return true;
      }
      if (this.candidatesRemaining < units) {
         return false;
      }
      this.candidatesRemaining -= units;
      return true;
   }

   public int remainingCommits() {
      return this.remaining;
   }

   public int remainingCandidates() {
      return this.candidatesRemaining;
   }
}
