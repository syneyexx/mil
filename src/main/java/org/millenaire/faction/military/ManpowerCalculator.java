package org.millenaire.faction.military;

import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.Faction;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;

/**
 * Derives military manpower capacity from Millénaire settlement populations and infrastructure.
 */
public final class ManpowerCalculator {
   private ManpowerCalculator() {
   }

   public static int countSettlementPopulation(Village village) {
      if (village == null) {
         return 0;
      }

      int living = 0;

      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (record != null && !record.isKilled()) {
            living++;
         }
      }

      return living;
   }

   public static int computeCapacity(ServerLevel level, Faction faction, int infrastructureScore) {
      int population = 0;
      VillageSavedData villages = VillageSavedData.get(level);

      for (VillageId settlementId : faction.getSettlementIds()) {
         Village village = villages.getVillageManager().getVillage(settlementId);
         if (village != null) {
            population += countSettlementPopulation(village);
         }
      }

      int fromPopulation = (population * MilitaryConfig.MANPOWER_PER_MILLE_OF_POPULATION) / 1000;
      int fromInfrastructure = infrastructureScore * MilitaryConfig.MANPOWER_PER_INFRASTRUCTURE_POINT;
      return Math.max(0, fromPopulation + fromInfrastructure);
   }

   public static int availableManpower(int capacity, int recruited) {
      return Math.max(0, capacity - Math.max(0, recruited));
   }
}
