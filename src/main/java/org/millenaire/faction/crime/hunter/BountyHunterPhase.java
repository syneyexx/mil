package org.millenaire.faction.crime.hunter;

/**
 * Authoritative bounty-hunter behavioural phases.
 */
public enum BountyHunterPhase {
   INACTIVE,
   DISPATCH_PENDING,
   TRAVELLING,
   SEARCHING,
   OBSERVING,
   APPROACHING,
   PURSUING,
   RESTRAINING,
   CAPTURED,
   TRANSPORTING,
   JAIL_TRANSFER,
   BOUNTY_SETTLED,
   COMPLETE,
   TARGET_LOST,
   INVESTIGATING,
   RECOVERING,
   RETREATING,
   DESPAWNING;

   public boolean isActivePursuit() {
      return this == TRAVELLING
         || this == SEARCHING
         || this == OBSERVING
         || this == APPROACHING
         || this == PURSUING
         || this == RESTRAINING
         || this == TARGET_LOST
         || this == INVESTIGATING;
   }

   public boolean shouldDespawnWhenIdle() {
      return this == COMPLETE || this == DESPAWNING || this == INACTIVE || this == BOUNTY_SETTLED;
   }
}
