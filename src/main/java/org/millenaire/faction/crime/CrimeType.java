package org.millenaire.faction.crime;

/**
 * Player crimes that raise a culture's bounty score.
 */
public enum CrimeType {
   ASSAULT(15),
   MURDER(80),
   THEFT(40),
   PICKPOCKET(25),
   RAID_AGAINST_CULTURE(120);

   private final int bountyPoints;

   CrimeType(int bountyPoints) {
      this.bountyPoints = bountyPoints;
   }

   public int bountyPoints() {
      return this.bountyPoints;
   }
}
