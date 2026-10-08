package org.millenaire.faction.military;

import javax.annotation.Nullable;

/**
 * Centralized military strength/power formulas for Phase 5 organization metrics.
 */
public final class MilitaryStrengthCalculator {
   private MilitaryStrengthCalculator() {
   }

   public static int unitStrength(MilitaryUnit unit, @Nullable UnitTypeDefinition definition) {
      if (unit == null || unit.getSoldierCount() <= 0) {
         return 0;
      }

      int perSoldier = definition == null ? MilitaryConfig.STRENGTH_PER_SOLDIER_BASE : definition.baseStrengthPerSoldier();
      int readinessFactor = Math.max(1, unit.getReadinessPercent());
      int equipmentBonus = unit.getEquipmentLevel().getRank() * MilitaryConfig.STRENGTH_EQUIPMENT_BONUS_PER_LEVEL;
      int trainingBonus = unit.getTrainingLevel().getRank() * MilitaryConfig.STRENGTH_TRAINING_BONUS_PER_LEVEL;
      long raw = (long)unit.getSoldierCount() * (perSoldier + equipmentBonus + trainingBonus);
      raw = raw * readinessFactor / MilitaryConfig.STRENGTH_READINESS_DIVISOR;
      return raw > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)raw;
   }

   public static int armyStrength(Army army, java.util.function.Function<UnitId, MilitaryUnit> unitResolver, boolean hasCommander) {
      if (army == null) {
         return 0;
      }

      long total = 0L;

      for (UnitId unitId : army.getUnitIds()) {
         MilitaryUnit unit = unitResolver.apply(unitId);
         if (unit != null) {
            total += unitStrength(unit, UnitTypeRegistry.get(unit.getUnitTypeId()));
         }
      }

      if (hasCommander) {
         total += MilitaryConfig.STRENGTH_COMMANDER_BONUS;
      }

      return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)total;
   }

   public static int armyReadiness(Army army, java.util.function.Function<UnitId, MilitaryUnit> unitResolver) {
      if (army == null || army.getUnitIds().isEmpty()) {
         return 0;
      }

      int totalSoldiers = 0;
      long weighted = 0L;

      for (UnitId unitId : army.getUnitIds()) {
         MilitaryUnit unit = unitResolver.apply(unitId);
         if (unit != null && unit.getSoldierCount() > 0) {
            totalSoldiers += unit.getSoldierCount();
            weighted += (long)unit.getSoldierCount() * unit.getReadinessPercent();
         }
      }

      return totalSoldiers <= 0 ? 0 : (int)(weighted / totalSoldiers);
   }

   public static int factionMilitaryPower(MilitaryOrganization organization, java.util.function.Function<UnitId, MilitaryUnit> unitResolver) {
      if (organization == null) {
         return 0;
      }

      long total = 0L;

      for (UnitId unitId : organization.getUnitIds()) {
         MilitaryUnit unit = unitResolver.apply(unitId);
         if (unit != null) {
            total += unitStrength(unit, UnitTypeRegistry.get(unit.getUnitTypeId()));
         }
      }

      total += organization.getAvailableManpower() / 4;
      total += organization.getInfrastructureScore() * 3;
      return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)total;
   }

   public static int foodMaintenanceRequirement(MilitaryOrganization organization, java.util.function.Function<UnitId, MilitaryUnit> unitResolver) {
      int food = 0;

      for (UnitId unitId : organization.getUnitIds()) {
         MilitaryUnit unit = unitResolver.apply(unitId);
         if (unit == null) {
            continue;
         }

         UnitTypeDefinition definition = UnitTypeRegistry.get(unit.getUnitTypeId());
         int per = definition == null ? MilitaryConfig.FOOD_PER_SOLDIER_MAINTENANCE : definition.foodMaintenancePerSoldier();
         food += unit.getSoldierCount() * per;
      }

      return food;
   }

   public static int equipmentValueRequirement(MilitaryOrganization organization, java.util.function.Function<UnitId, MilitaryUnit> unitResolver) {
      int value = 0;

      for (UnitId unitId : organization.getUnitIds()) {
         MilitaryUnit unit = unitResolver.apply(unitId);
         if (unit == null) {
            continue;
         }

         UnitTypeDefinition definition = UnitTypeRegistry.get(unit.getUnitTypeId());
         int per = definition == null ? MilitaryConfig.EQUIPMENT_VALUE_PER_SOLDIER : definition.equipmentValuePerSoldier();
         value += unit.getSoldierCount() * per;
      }

      return value;
   }
}
