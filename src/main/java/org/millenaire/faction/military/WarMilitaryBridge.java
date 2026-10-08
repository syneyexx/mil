package org.millenaire.faction.military;

/**
 * Narrow package-local bridge so Phase 6 warfare can apply battle casualties without
 * exposing mutable unit APIs broadly.
 */
public final class WarMilitaryBridge {
   private WarMilitaryBridge() {
   }

   public static void reduceSoldiers(MilitaryUnit unit, int loss, long gameTime) {
      if (unit == null || loss <= 0) {
         return;
      }

      int next = Math.max(0, unit.getSoldierCount() - loss);
      unit.setSoldierCount(next, gameTime);
      if (next <= 0) {
         unit.assignToReserve(gameTime);
      }
   }
}
