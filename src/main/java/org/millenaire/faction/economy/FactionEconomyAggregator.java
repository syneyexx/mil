package org.millenaire.faction.economy;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.millenaire.building.GoodAvailabilityHelper;
import org.millenaire.commerce.TradeGood;
import org.millenaire.commerce.TradeGoodsLoader;
import org.millenaire.faction.Faction;
import org.millenaire.faction.territory.Territory;
import org.millenaire.faction.territory.TerritorySavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;

/**
 * Aggregates Millénaire settlement stock/demand into faction economic views.
 * Does not copy chest inventories into faction storage.
 */
public final class FactionEconomyAggregator {
   private FactionEconomyAggregator() {
   }

   public static Map<EconomicResourceId, ResourceBalance> aggregateBalances(ServerLevel level, Faction faction) {
      Map<EconomicResourceId, int[]> accum = new LinkedHashMap<>();
      Set<ResourceLocation> cultures = new HashSet<>();
      VillageSavedData villageData = VillageSavedData.get(level);

      for (VillageId settlementId : faction.getSettlementIds()) {
         Village village = villageData.getVillageManager().getVillage(settlementId);
         if (village == null) {
            continue;
         }

         cultures.add(village.getCultureId());
         int efficiencyPermille = settlementEconomicEfficiencyPermille(level, settlementId);
         Set<Item> items = collectRelevantItems(village.getCultureId());

         for (Item item : items) {
            int stock = village.getVillageItemCount(level, item);
            int demand = GoodAvailabilityHelper.nbGoodNeeded(level, village, item);
            EconomicResourceId resourceId = EconomicResourceId.of(item);
            int[] pair = accum.computeIfAbsent(resourceId, ignored -> new int[2]);
            // Occupation/integration modifiers reduce effective economic output without mutating chests.
            pair[0] += Math.max(0, stock) * efficiencyPermille / 1000;
            pair[1] += Math.max(0, demand);
         }
      }

      Map<EconomicResourceId, ResourceBalance> balances = new LinkedHashMap<>();

      for (Map.Entry<EconomicResourceId, int[]> entry : accum.entrySet()) {
         balances.put(entry.getKey(), ResourceBalance.of(entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
      }

      return balances;
   }

   public static int estimateStockValue(ServerLevel level, Faction faction, Map<EconomicResourceId, ResourceBalance> balances) {
      ResourceLocation cultureHint = faction.getCultureId();
      int total = 0;

      for (ResourceBalance balance : balances.values()) {
         int value = TradeValueCalculator.estimateValue(cultureHint, balance.resourceId(), balance.stock());
         long sum = (long)total + value;
         total = sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)sum;
      }

      return total;
   }

   public static int estimateDemandPressure(Map<EconomicResourceId, ResourceBalance> balances) {
      int pressure = 0;

      for (ResourceBalance balance : balances.values()) {
         if (balance.hasDeficit()) {
            long sum = (long)pressure + balance.deficitAmount();
            pressure = sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)sum;
         }
      }

      return pressure;
   }

   public static int countSurplusResources(Map<EconomicResourceId, ResourceBalance> balances) {
      int count = 0;

      for (ResourceBalance balance : balances.values()) {
         if (balance.hasSurplus()) {
            count++;
         }
      }

      return count;
   }

   public static int countDeficitResources(Map<EconomicResourceId, ResourceBalance> balances) {
      int count = 0;

      for (ResourceBalance balance : balances.values()) {
         if (balance.hasDeficit()) {
            count++;
         }
      }

      return count;
   }

   /**
    * Centralized Phase 7 economic efficiency for a settlement (permille, 1000 = full).
    */
   public static int settlementEconomicEfficiencyPermille(ServerLevel level, VillageId settlementId) {
      if (level == null || settlementId == null) {
         return 1000;
      }

      Territory territory = TerritorySavedData.get(level).getTerritoryManager().getTerritoryForSettlement(settlementId);
      return territory == null ? 1000 : territory.getEconomicEfficiencyPermille();
   }

   private static Set<Item> collectRelevantItems(ResourceLocation cultureId) {
      Set<Item> items = new HashSet<>();

      for (TradeGood good : TradeGoodsLoader.getGoods(cultureId)) {
         Item item = good.resolveItem();
         if (item != null) {
            items.add(item);
         }
      }

      for (Item item : TradeGoodsLoader.getTargetQuantities(cultureId).keySet()) {
         if (item != null && BuiltInRegistries.ITEM.getKey(item) != null) {
            items.add(item);
         }
      }

      return items;
   }
}
