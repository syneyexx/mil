package org.millenaire.civilization.population;

/**
 * Derived spare capacity that attracts immigrants into a player settlement.
 */
public record AttractionDemand(int housingDemand, int workDemand) {
   public int totalSlots() {
      return Math.max(0, this.housingDemand) + Math.max(0, this.workDemand);
   }

   /** Weighted score: housing always matters; work boosts when workshops need people. */
   public int attractionScore() {
      return Math.max(0, this.housingDemand) * 2 + Math.max(0, this.workDemand) * 3;
   }

   public boolean hasDemand() {
      return this.housingDemand > 0 || this.workDemand > 0;
   }
}
