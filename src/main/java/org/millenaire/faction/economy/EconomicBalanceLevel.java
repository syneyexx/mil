package org.millenaire.faction.economy;

/**
 * Deterministic summary of a faction's overall economic condition.
 * Not a random score and not a geopolitical power rating.
 */
public enum EconomicBalanceLevel {
   /** Comfortable stock relative to demand; limited import dependence. */
   HEALTHY,
   /** Roughly matched supply and demand. */
   BALANCED,
   /** Notable unmet demand or thin stock value. */
   UNDER_PRESSURE,
   /** Relies heavily on imports for key goods. */
   IMPORT_DEPENDENT;

   public static EconomicBalanceLevel fromMetrics(int surplusResources, int deficitResources, int significantDependencies, int stockValue, int demandPressure) {
      if (significantDependencies > 0 && deficitResources > 0) {
         return IMPORT_DEPENDENT;
      }

      if (deficitResources > surplusResources || demandPressure > 0 && stockValue < EconomicConfig.PRESSURE_STOCK_VALUE_THRESHOLD) {
         return UNDER_PRESSURE;
      }

      if (surplusResources > 0 && deficitResources == 0) {
         return HEALTHY;
      }

      return BALANCED;
   }
}
