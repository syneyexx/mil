package org.millenaire.worldboss;

/**
 * Canonical world-boss event FSM. Terminal states are DEFEATED, FINISHED, and FAILED
 * (FINISHED is the planner clear state after any terminal outcome).
 */
public enum WorldBossEventState {
   WAITING,
   SELECTING,
   ANNOUNCING,
   ARRIVING,
   NEUTRAL,
   ENGAGED,
   DISENGAGING,
   DEPARTING,
   DEFEATED,
   FAILED,
   FINISHED;

   public boolean isTerminal() {
      return this == DEFEATED || this == FAILED || this == FINISHED;
   }

   public boolean hasActiveEntity() {
      return this == ARRIVING || this == NEUTRAL || this == ENGAGED || this == DISENGAGING || this == DEPARTING;
   }

   public boolean isCombatVisible() {
      return this == ENGAGED || this == DISENGAGING;
   }
}
