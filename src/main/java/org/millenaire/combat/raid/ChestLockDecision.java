package org.millenaire.combat.raid;

public final class ChestLockDecision {
   private ChestLockDecision() {
   }

   public static ChestLockDecision.Action decide(boolean currentlyLocked, int nbLiveDefenders) {
      if (currentlyLocked && nbLiveDefenders <= 0) {
         return ChestLockDecision.Action.UNLOCK;
      } else {
         return !currentlyLocked && nbLiveDefenders > 0 ? ChestLockDecision.Action.LOCK : ChestLockDecision.Action.NOOP;
      }
   }

   public enum Action {
      LOCK,
      UNLOCK,
      NOOP;
   }
}
