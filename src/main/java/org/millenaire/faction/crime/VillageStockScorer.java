package org.millenaire.faction.crime;

import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.village.Village;

/**
 * Estimates settlement "stock" wealth used to size bounty raid waves.
 */
public final class VillageStockScorer {
   private VillageStockScorer() {
   }

   public static int score(@Nullable ServerLevel level, @Nullable Village village) {
      if (level == null || village == null) {
         return 0;
      }
      int total = 0;
      BuildingInstance th = village.getTownhall();
      if (th != null) {
         total += scoreBuilding(level, th);
      }
      for (BuildingInstance building : village.getBuildings()) {
         if (th != null && building.getId().equals(th.getId())) {
            continue;
         }
         total += scoreBuilding(level, building) / 4;
      }
      return total;
   }

   private static int scoreBuilding(ServerLevel level, BuildingInstance building) {
      BuildingInventory inv = building.getInventory();
      if (inv == null) {
         return 0;
      }
      Map<Item, Integer> contents = inv.getCachedContents();
      if (contents == null) {
         contents = inv.scanChests(level);
      }
      if (contents == null || contents.isEmpty()) {
         return 0;
      }
      int sum = 0;
      for (int count : contents.values()) {
         sum += Math.max(0, count);
      }
      return sum;
   }
}
