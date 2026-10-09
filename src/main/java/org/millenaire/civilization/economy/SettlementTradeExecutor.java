package org.millenaire.civilization.economy;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.civilization.CivilizationMetrics;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.diplomacy.DiplomaticManager;
import org.millenaire.faction.economy.EconomicResourceId;
import org.millenaire.faction.economy.EconomySavedData;
import org.millenaire.faction.economy.TradeManager;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.events.LivingEventSource;
import org.millenaire.living.events.LivingEventType;
import org.millenaire.village.Village;
import org.slf4j.Logger;

/**
 * Conserved physical settlement-to-settlement transfer.
 * Success dedupe is written only after a conserved commit so failed attempts remain retryable.
 * Intra-faction movement is logistics (profile refresh), not diplomatic trade booking.
 */
public final class SettlementTradeExecutor {
   private static final Logger LOGGER = LogUtils.getLogger();

   private SettlementTradeExecutor() {
   }

   public static boolean tryTransferDeficitItem(
      ServerLevel level,
      Village exporter,
      Village importer,
      EconomicResourceId resourceId,
      int requested
   ) {
      if (level == null || exporter == null || importer == null || resourceId == null || requested <= 0) {
         return false;
      }
      Optional<Holder.Reference<Item>> holder = BuiltInRegistries.ITEM.getHolder(resourceId.id());
      if (holder.isEmpty()) {
         return false;
      }
      Item item = holder.get().value();

      FactionId exportFaction = exporter.getFactionId();
      FactionId importFaction = importer.getFactionId();
      if (exportFaction == null || importFaction == null) {
         return false;
      }
      boolean sameFaction = exportFaction.equals(importFaction);
      if (!sameFaction) {
         DiplomaticManager diplomacy = DiplomacySavedData.get(level).getDiplomaticManager();
         if (!diplomacy.canTrade(exportFaction, importFaction)) {
            return false;
         }
         if (WarSavedData.get(level).getWarManager().areAtWar(exportFaction, importFaction)) {
            return false;
         }
      }

      int available = countExportable(level, exporter, item);
      int amount = Math.min(requested, available);
      if (amount <= 0) {
         return false;
      }
      if (importer.getTownhall() == null || importer.getTownhall().getInventory() == null) {
         return false;
      }

      long day = level.getGameTime() / 24000L;
      String successKey = "trade-ok:" + exporter.getId().uuid() + ":" + importer.getId().uuid() + ":" + resourceId.id() + ":" + day;
      CivilizationSavedData civ = CivilizationSavedData.get(level);
      if (civ.isPublished(successKey, level.getGameTime(), 24000L)) {
         return false;
      }

      int removed = removeFromVillage(level, exporter, item, amount);
      if (removed <= 0) {
         return false;
      }
      int added = addToVillage(level, importer, item, removed);
      if (added < removed) {
         int undelivered = removed - added;
         int compensated = addToVillage(level, exporter, item, undelivered);
         if (compensated < undelivered) {
            LOGGER.warn(
               "[Millenaire] Trade compensation incomplete {}/{} of {} from {} to {}",
               compensated,
               undelivered,
               resourceId.id(),
               exporter.getId().uuid(),
               importer.getId().uuid()
            );
         }
         if (added <= 0) {
            return false;
         }
         removed = added;
      }

      civ.tryMarkPublished(successKey, level.getGameTime(), 24000L);

      Map<Item, Integer> transferred = new LinkedHashMap<>();
      transferred.put(item, removed);
      TradeManager trade = EconomySavedData.get(level).getTradeManager();
      if (sameFaction) {
         Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(exportFaction);
         if (faction != null) {
            trade.refreshProfile(level, faction, level.getGameTime());
         }
      } else {
         trade.recordSettlementGoodsTransfer(level, exporter, importer, transferred);
      }
      CivilizationMetrics.TRADE_OPS.incrementAndGet();

      LivingEventService.emit(
         level,
         LivingEventType.TRADE_COMPLETED,
         LivingEventSource.SYSTEM,
         importer,
         null,
         null,
         (sameFaction ? "logistics=" : "trade=") + resourceId.id() + " qty=" + removed + " from=" + exporter.getId().uuid(),
         "public",
         successKey
      );
      return true;
   }

   /**
    * Exportable stock: village total minus a local reserve so home needs are not stripped bare.
    */
   public static int countExportable(ServerLevel level, Village village, Item item) {
      int total = village.getVillageItemCount(level, item);
      int reserve = Math.min(8, Math.max(0, total / 4));
      return Math.max(0, total - reserve);
   }

   private static int removeFromVillage(ServerLevel level, Village village, Item item, int amount) {
      int remaining = amount;
      int removed = 0;
      for (BuildingInstance building : village.getBuildings()) {
         if (remaining <= 0) {
            break;
         }
         BuildingInventory inv = building.getInventory();
         if (inv == null) {
            continue;
         }
         inv.invalidateCacheOncePerTick(level.getGameTime());
         int took = inv.remove(level, item, remaining);
         removed += took;
         remaining -= took;
      }
      return removed;
   }

   private static int addToVillage(ServerLevel level, Village village, Item item, int amount) {
      int remaining = amount;
      int added = 0;
      for (BuildingInstance building : village.getBuildings()) {
         if (remaining <= 0) {
            break;
         }
         BuildingInventory inv = building.getInventory();
         if (inv == null) {
            continue;
         }
         inv.invalidateCacheOncePerTick(level.getGameTime());
         int before = inv.getCount(level, item);
         ItemStack stack = new ItemStack(item, remaining);
         if (!inv.addStack(level, stack)) {
            continue;
         }
         inv.invalidateCacheOncePerTick(level.getGameTime());
         int after = inv.getCount(level, item);
         int placed = Math.min(remaining, Math.max(0, after - before));
         if (placed <= 0) {
            continue;
         }
         added += placed;
         remaining -= placed;
      }
      return added;
   }
}
