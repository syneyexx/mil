package org.millenaire.faction.war;

import java.util.Collection;
import javax.annotation.Nullable;
import org.millenaire.faction.military.Army;
import org.millenaire.faction.military.Commander;
import org.millenaire.faction.military.MilitaryStrengthCalculator;
import org.millenaire.faction.military.MilitaryUnit;
import org.millenaire.faction.military.UnitCategory;
import org.millenaire.faction.military.UnitTypeDefinition;
import org.millenaire.faction.military.UnitTypeRegistry;

/**
 * Centralized combat power evaluation. Deterministic factors dominate; bounded randomness is optional.
 */
public final class CombatCalculator {
   private CombatCalculator() {
   }

   public static int sideCombatPower(
      Collection<Army> armies,
      java.util.function.Function<org.millenaire.faction.military.UnitId, MilitaryUnit> unitResolver,
      java.util.function.Function<org.millenaire.faction.military.CommanderId, Commander> commanderResolver,
      int sideMorale,
      int supplyPercent,
      TerrainType terrain,
      boolean attacking
   ) {
      long total = 0L;
      for (Army army : armies) {
         if (army == null) {
            continue;
         }

         boolean hasCommander = army.getCommanderId() != null && commanderResolver.apply(army.getCommanderId()) != null;
         int base = MilitaryStrengthCalculator.armyStrength(army, unitResolver, hasCommander);
         base = applyUnitTypeModifiers(army, unitResolver, base);
         Commander commander = army.getCommanderId() == null ? null : commanderResolver.apply(army.getCommanderId());
         if (commander != null) {
            base += commander.getLeadership() / 4 + Math.min(20, commander.getExperience() / 50);
            if (commander.getRank() != null) {
               base += commander.getRank().getTier() * 4;
            }
         }

         total += base;
      }

      int moraleFactor = Math.max(40, sideMorale);
      int supplyFactor = Math.max(40, supplyPercent);
      total = total * moraleFactor / 100;
      total = total * supplyFactor / 100;
      int terrainMod = attacking ? terrain.attackerModifierPermille() : terrain.defenderModifierPermille();
      total = total * terrainMod / 1000;
      return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)Math.max(0L, total);
   }

   public static int applyBoundedRandomness(int power, long seed) {
      if (power <= 0) {
         return 0;
      }

      // Deterministic pseudo-variance from seed (not Math.random).
      long hash = seed ^ (seed >>> 33);
      hash *= 0xff51afd7ed558ccdL;
      hash ^= hash >>> 33;
      double fraction = (hash & 0xFFFFL) / 65535.0;
      double delta = (fraction * 2.0 - 1.0) * WarConfig.COMBAT_RANDOMNESS_FRACTION;
      return Math.max(0, (int)Math.round(power * (1.0 + delta)));
   }

   private static int applyUnitTypeModifiers(Army army, java.util.function.Function<org.millenaire.faction.military.UnitId, MilitaryUnit> unitResolver, int base) {
      int infantry = 0;
      int ranged = 0;
      int cavalry = 0;
      int militia = 0;
      int special = 0;
      int total = 0;

      for (org.millenaire.faction.military.UnitId unitId : army.getUnitIds()) {
         MilitaryUnit unit = unitResolver.apply(unitId);
         if (unit == null || unit.getSoldierCount() <= 0) {
            continue;
         }

         UnitTypeDefinition def = UnitTypeRegistry.get(unit.getUnitTypeId());
         UnitCategory category = def == null ? UnitCategory.INFANTRY : def.category();
         total += unit.getSoldierCount();
         switch (category) {
            case RANGED -> ranged += unit.getSoldierCount();
            case CAVALRY -> cavalry += unit.getSoldierCount();
            case MILITIA -> militia += unit.getSoldierCount();
            case SPECIAL, COMMAND -> special += unit.getSoldierCount();
            case GUARD, INFANTRY -> infantry += unit.getSoldierCount();
         }
      }

      if (total <= 0) {
         return base;
      }

      long adjusted = base;
      adjusted += (long)infantry * 1;
      adjusted += (long)ranged * 2;
      adjusted += (long)cavalry * 3;
      adjusted += (long)special * 2;
      adjusted -= (long)militia;
      return adjusted > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)Math.max(0L, adjusted);
   }
}
