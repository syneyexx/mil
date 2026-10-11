package org.millenaire.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPurpose;
import org.millenaire.building.SpecialPoint;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.entity.MillVillager;
import org.millenaire.item.ModItems;
import org.millenaire.item.MoneyHelper;

/**
 * Player-designated SHOP purchases by residents, merchants, and road travelers.
 */
public final class PlayerShopService {
   private static final Map<String, ShopDayStats> STATS = new ConcurrentHashMap<>();
   private static final int DEFAULT_PRICE = 8;
   private static final int BUY_QTY = 1;

   private PlayerShopService() {
   }

   public static boolean isEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.playerShopEnabled.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   @Nullable
   public static BuildingInstance findShop(Village village) {
      if (village == null) {
         return null;
      }
      for (BuildingInstance building : village.getBuildings()) {
         if (building != null && building.getPurpose() == BuildingPurpose.SHOP && building.isOperational()) {
            return building;
         }
      }
      return null;
   }

   @Nullable
   public static BlockPos shopPos(BuildingInstance shop) {
      if (shop == null) {
         return null;
      }
      BlockPos selling = shop.getFirstPointPos(SpecialPoint.SELLING_POS);
      return selling != null ? selling : shop.getOrigin();
   }

   /**
    * Attempt one purchase: take 1 stock item from shop chests, pay deniers into chests / traveler inventory.
    * @return deniers paid, or 0 if no trade
    */
   public static int tryPurchase(ServerLevel level, Village village, MillVillager buyer) {
      if (!isEnabled() || level == null || village == null || buyer == null) {
         return 0;
      }
      BuildingInstance shop = findShop(village);
      if (shop == null) {
         return 0;
      }
      BuildingInventory inv = shop.getInventory();
      if (inv == null) {
         shop.initInventory();
         inv = shop.getInventory();
      }
      if (inv == null) {
         return 0;
      }
      Map<Item, Integer> stock = inv.scanChests(level);
      Item chosen = pickBuyableItem(stock);
      if (chosen == null) {
         return 0;
      }
      int price = DEFAULT_PRICE;
      int deniersHeld = countDeniersInVillager(buyer);
      if (deniersHeld < price) {
         // Grant travelers a small purse so shops can function.
         buyer.getInventory().add(ModItems.DENIER.get(), price);
      }
      if (countDeniersInVillager(buyer) < price) {
         return 0;
      }
      int removed = inv.remove(level, chosen, BUY_QTY);
      if (removed <= 0) {
         return 0;
      }
      removeDeniersFromVillager(buyer, price);
      inv.add(level, ModItems.DENIER.get(), price);
      buyer.getInventory().add(chosen, removed);
      recordVisit(village, level.getDayTime() / 24000L, price);
      return price;
   }

   @Nullable
   private static Item pickBuyableItem(Map<Item, Integer> stock) {
      List<Item> candidates = new ArrayList<>();
      for (Entry<Item, Integer> e : stock.entrySet()) {
         if (e.getValue() == null || e.getValue() <= 0) {
            continue;
         }
         Item item = e.getKey();
         if (item == null || item == Items.AIR) {
            continue;
         }
         ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
         if (id != null && "millenaire".equals(id.getNamespace()) && id.getPath().startsWith("denier")) {
            continue;
         }
         candidates.add(item);
      }
      if (candidates.isEmpty()) {
         return null;
      }
      return candidates.get(Math.floorMod((int) System.nanoTime(), candidates.size()));
   }

   private static int countDeniersInVillager(MillVillager villager) {
      int total = 0;
      total += villager.getInventory().getCount(ModItems.DENIER.get());
      total += villager.getInventory().getCount(ModItems.DENIER_ARGENT.get()) * MoneyHelper.ARGENT_VALUE;
      total += villager.getInventory().getCount(ModItems.DENIER_OR.get()) * MoneyHelper.OR_VALUE;
      return total;
   }

   private static void removeDeniersFromVillager(MillVillager villager, int amount) {
      int remaining = amount;
      remaining -= villager.getInventory().remove(ModItems.DENIER.get(), remaining);
      while (remaining > 0 && villager.getInventory().getCount(ModItems.DENIER_ARGENT.get()) > 0) {
         villager.getInventory().remove(ModItems.DENIER_ARGENT.get(), 1);
         int change = MoneyHelper.ARGENT_VALUE - remaining;
         remaining = 0;
         if (change > 0) {
            villager.getInventory().add(ModItems.DENIER.get(), change);
         }
      }
      while (remaining > 0 && villager.getInventory().getCount(ModItems.DENIER_OR.get()) > 0) {
         villager.getInventory().remove(ModItems.DENIER_OR.get(), 1);
         int change = MoneyHelper.OR_VALUE - remaining;
         remaining = 0;
         if (change > 0) {
            // simplify change into argent/denier via bronze only for shop flow
            villager.getInventory().add(ModItems.DENIER.get(), change);
         }
      }
   }

   private static void recordVisit(Village village, long day, int deniers) {
      String key = village.getId().uuid() + "|" + day;
      STATS.compute(key, (k, prev) -> {
         ShopDayStats s = prev == null ? new ShopDayStats() : prev;
         s.visits++;
         s.deniers += deniers;
         return s;
      });
   }

   public static ShopDayStats statsForToday(Village village, ServerLevel level) {
      long day = level.getDayTime() / 24000L;
      return STATS.getOrDefault(village.getId().uuid() + "|" + day, new ShopDayStats());
   }

   public static final class ShopDayStats {
      public int visits;
      public int deniers;
   }
}
