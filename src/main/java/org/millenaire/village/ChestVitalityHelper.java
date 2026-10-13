package org.millenaire.village;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;

/**
 * Keeps operational building chests from looking permanently empty.
 * Runs budgeted (few buildings per call) so it stays off the hot villager tick path.
 */
public final class ChestVitalityHelper {
   private static final int BUILDINGS_PER_PASS = 3;
   private static final int MIN_TOTAL_ITEMS = 8;
   private static final int TOP_UP_STACK = 6;

   private static final List<Item> STAPLES = List.of(
      Items.BREAD,
      Items.WHEAT,
      Items.APPLE,
      Items.STICK,
      Items.COBBLESTONE,
      Items.OAK_LOG,
      Items.IRON_INGOT,
      Items.COAL
   );

   private ChestVitalityHelper() {
   }

   /**
    * Tops up a rotating slice of buildings whose chests are nearly empty.
    * Call from village background tick (already throttled), not per-villager.
    */
   public static void tickSparseRestock(ServerLevel level, Village village) {
      List<BuildingInstance> buildings = village.getBuildings();
      if (buildings.isEmpty()) {
         return;
      }

      int start = (int)((level.getGameTime() / 200L) % Math.max(1, buildings.size()));
      int visited = 0;
      for (int i = 0; i < buildings.size() && visited < BUILDINGS_PER_PASS; i++) {
         BuildingInstance building = buildings.get((start + i) % buildings.size());
         if (!building.isOperational()) {
            continue;
         }
         BuildingInventory inv = building.getInventory();
         if (inv == null || inv.getChestCount() == 0) {
            continue;
         }
         visited++;
         int total = countItems(level, inv);
         if (total >= MIN_TOTAL_ITEMS) {
            continue;
         }
         topUp(level, inv, building);
      }
   }

   private static int countItems(ServerLevel level, BuildingInventory inv) {
      int total = 0;
      for (var entry : inv.scanChests(level).entrySet()) {
         total += entry.getValue();
      }
      return total;
   }

   private static void topUp(ServerLevel level, BuildingInventory inv, BuildingInstance building) {
      List<Item> pool = new ArrayList<>(STAPLES);
      BuildingPlanSet planSet = building.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(building.getPlanSetId()) : null;
      if (planSet != null) {
         for (BuildingPlanSet.StartingGood sg : planSet.startingGoods()) {
            Item item = org.millenaire.item.ItemHelper.resolve(sg.item());
            if (item != null && !pool.contains(item)) {
               pool.add(item);
            }
         }
      }

      ThreadLocalRandom rng = ThreadLocalRandom.current();
      int missing = MIN_TOTAL_ITEMS - countItems(level, inv);
      while (missing > 0 && !pool.isEmpty()) {
         Item item = pool.get(rng.nextInt(pool.size()));
         int amount = Math.min(TOP_UP_STACK, missing);
         int placed = inv.add(level, item, amount);
         if (placed <= 0) {
            break;
         }
         missing -= placed;
      }
   }
}
