package org.millenaire.faction.territory;

import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.military.MilitaryInfrastructureScanner;
import org.millenaire.faction.military.ManpowerCalculator;
import org.millenaire.village.Village;

/**
 * Centralized strategic/economic territory valuation.
 */
public final class TerritoryValueCalculator {
   private TerritoryValueCalculator() {
   }

   public static int strategicValue(ServerLevel level, Village village) {
      if (village == null) {
         return 1;
      }

      int population = ManpowerCalculator.countSettlementPopulation(village);
      int infra = MilitaryInfrastructureScanner.scoreSettlement(village);
      int buildings = village.getBuildings().size();
      return Math.max(1, population / 2 + infra * 3 + buildings);
   }

   public static int economicValue(ServerLevel level, Village village) {
      if (village == null) {
         return 1;
      }

      int population = ManpowerCalculator.countSettlementPopulation(village);
      int buildings = village.getBuildings().size();
      return Math.max(1, population + buildings * 2);
   }

   public static int garrisonRequirement(Territory territory) {
      if (territory == null) {
         return TerritoryConfig.MIN_OCCUPATION_GARRISON;
      }

      int base = TerritoryConfig.MIN_OCCUPATION_GARRISON + territory.getStrategicValue() / TerritoryConfig.GARRISON_SOLDIERS_PER_VALUE;
      if (territory.getOccupation() != null) {
         base = base * territory.getOccupation().getPolicy().garrisonRequirementMultiplierPermille() / 1000;
      }

      return Math.min(TerritoryConfig.MAX_OCCUPATION_GARRISON, Math.max(TerritoryConfig.MIN_OCCUPATION_GARRISON, base));
   }
}
