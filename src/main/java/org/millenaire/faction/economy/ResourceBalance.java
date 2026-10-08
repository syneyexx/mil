package org.millenaire.faction.economy;

/**
 * Per-resource supply/demand snapshot for a faction, derived from Millénaire settlement data.
 * {@code surplus} is positive when stock exceeds demand; negative values are deficits.
 */
public record ResourceBalance(EconomicResourceId resourceId, int stock, int demand, int surplus) {
   public ResourceBalance {
      if (resourceId == null) {
         throw new IllegalArgumentException("resourceId");
      }
   }

   public static ResourceBalance of(EconomicResourceId resourceId, int stock, int demand) {
      int safeStock = Math.max(0, stock);
      int safeDemand = Math.max(0, demand);
      return new ResourceBalance(resourceId, safeStock, safeDemand, safeStock - safeDemand);
   }

   public boolean hasSurplus() {
      return this.surplus > 0;
   }

   public boolean hasDeficit() {
      return this.surplus < 0;
   }

   public int deficitAmount() {
      return this.surplus < 0 ? -this.surplus : 0;
   }

   public int surplusAmount() {
      return Math.max(this.surplus, 0);
   }
}
