package org.millenaire.religion.supply;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.millenaire.building.AnywoodHelper;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.item.ItemHelper;
import org.millenaire.religion.content.RitualDefinition;
import org.millenaire.religion.content.SupplyRequirement;
import org.millenaire.religion.model.CeremonyInstance;
import org.millenaire.village.Village;

/**
 * Checks and consumes ritual supplies from townhall inventory.
 */
public final class RitualSupplyService {
   public RitualSupplyService() {
   }

   public record Shortage(String itemId, int missing) {
   }

   public List<Shortage> missingSupplies(ServerLevel level, Village village, RitualDefinition ritual) {
      List<Shortage> out = new ArrayList<>();
      BuildingInventory inv = townhallInventory(village);
      if (inv == null) {
         for (SupplyRequirement req : ritual.supplies()) {
            if (!req.optional() && req.count() > 0) {
               out.add(new Shortage(req.itemId(), req.count()));
            }
         }
         return out;
      }
      inv.scanChests(level);
      for (SupplyRequirement req : ritual.supplies()) {
         if (req.optional() || req.count() <= 0 || req.itemId().isEmpty()) {
            continue;
         }
         int have = countItem(level, inv, req.itemId());
         if (have < req.count()) {
            out.add(new Shortage(req.itemId(), req.count() - have));
         }
      }
      return out;
   }

   public boolean hasRequiredSupplies(ServerLevel level, Village village, RitualDefinition ritual) {
      return missingSupplies(level, village, ritual).isEmpty();
   }

   /**
    * Consume required supplies once. Idempotent via ceremony.suppliesConsumed flag.
    */
   public boolean consumeForCeremony(ServerLevel level, Village village, CeremonyInstance ceremony, RitualDefinition ritual) {
      if (ceremony.suppliesConsumed()) {
         return true;
      }
      if (!hasRequiredSupplies(level, village, ritual)) {
         return false;
      }
      BuildingInventory inv = townhallInventory(village);
      if (inv == null) {
         return ritual.supplies().stream().allMatch(s -> s.optional() || s.count() <= 0);
      }
      Map<String, Integer> ledger = new HashMap<>();
      for (SupplyRequirement req : ritual.supplies()) {
         if (req.optional() || req.count() <= 0) {
            continue;
         }
         int removed = removeItem(level, inv, req.itemId(), req.count());
         if (removed < req.count()) {
            return false;
         }
         ledger.put(req.itemId(), removed);
      }
      ceremony.consumedSupplies().putAll(ledger);
      ceremony.setSuppliesConsumed(true);
      return true;
   }

   @Nullable
   private static BuildingInventory townhallInventory(Village village) {
      BuildingInstance th = village.getTownhall();
      return th != null ? th.getInventory() : null;
   }

   private static int countItem(ServerLevel level, BuildingInventory inv, String itemId) {
      if ("anywood".equalsIgnoreCase(itemId)) {
         return inv.getCountByTag(level, AnywoodHelper.LOGS_TAG);
      }
      Item item = ItemHelper.resolve(itemId);
      if (item == null) {
         return 0;
      }
      return inv.getCount(level, item);
   }

   private static int removeItem(ServerLevel level, BuildingInventory inv, String itemId, int count) {
      if ("anywood".equalsIgnoreCase(itemId)) {
         return inv.removeByTag(level, AnywoodHelper.LOGS_TAG, count, java.util.Set.of());
      }
      Item item = ItemHelper.resolve(itemId);
      if (item == null) {
         return 0;
      }
      return inv.remove(level, item, count);
   }
}
