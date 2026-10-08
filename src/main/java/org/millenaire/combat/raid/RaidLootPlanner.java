package org.millenaire.combat.raid;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;

public final class RaidLootPlanner {
   private RaidLootPlanner() {
   }

   public static Map<Item, Integer> plan(List<RaidLootPlanner.GoodNeed> needs, Map<Item, Integer> targetContents, int budget) {
      Map<Item, Integer> toSteal = new LinkedHashMap<>();
      int remaining = budget;

      for (RaidLootPlanner.GoodNeed need : needs) {
         if (remaining <= 0) {
            break;
         }

         int demand = need.targetQuantity();
         if (demand > 0) {
            int present = targetContents.getOrDefault(need.item(), 0);
            if (present > 0) {
               int take = Math.min(demand, Math.min(remaining, present));
               if (take > 0) {
                  toSteal.merge(need.item(), take, Integer::sum);
                  remaining -= take;
               }
            }
         }
      }

      return toSteal;
   }

   public record GoodNeed(Item item, int targetQuantity) {
   }
}
