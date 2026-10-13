package org.millenaire.village;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.item.ModItems;
import org.millenaire.item.MoneyHelper;
import org.millenaire.perf.TickPerfCounters;

/**
 * Village treasury: physical denier stacks in a Bank building (or town hall fallback).
 */
public final class VillageTreasuryService {
   public static final String REASON_PLAYER_DEPOSIT = "player_deposit";
   public static final String REASON_PLAYER_WITHDRAW = "player_withdraw";
   public static final String REASON_IMMIGRANT = "immigrant";
   public static final String REASON_SHOP_SURPLUS = "shop_surplus";
   public static final String REASON_CRIME_FINE = "crime_fine";
   public static final String REASON_DIPLOMACY_BUY = "diplomacy_buy";
   public static final String REASON_NPC_TAX = "npc_tax";
   public static final String REASON_WAR_LOOT = "war_loot";
   public static final String REASON_WAR_STOLEN = "war_stolen";

   private VillageTreasuryService() {
   }

   @Nullable
   public static BuildingInstance resolve(Village village) {
      for (BuildingInstance b : village.getBuildings()) {
         if (!b.isOperational()) {
            continue;
         }
         if (PlayerBuildingRole.BANK.id().equals(b.getPlayerBuildingRole())) {
            return b;
         }
      }
      for (BuildingInstance b : village.getBuildingsWithTag("bank")) {
         if (b.isOperational()) {
            return b;
         }
      }
      for (BuildingInstance b : village.getBuildingsWithTag("treasury")) {
         if (b.isOperational()) {
            return b;
         }
      }
      return village.getTownhall();
   }

   public static boolean hasDesignatedBank(Village village) {
      for (BuildingInstance b : village.getBuildings()) {
         if (b.isOperational() && PlayerBuildingRole.BANK.id().equals(b.getPlayerBuildingRole())) {
            return true;
         }
      }
      return !village.getBuildingsWithTag("bank").isEmpty();
   }

   public static int balance(ServerLevel level, Village village) {
      BuildingInstance building = resolve(village);
      if (building == null || building.getInventory() == null) {
         return 0;
      }
      return coinValueIn(level, building.getInventory());
   }

   public static boolean canAfford(ServerLevel level, Village village, int amount) {
      return amount <= 0 || balance(level, village) >= amount;
   }

   public static boolean withdraw(ServerLevel level, Village village, int amount, String reason) {
      if (amount <= 0) {
         return true;
      }
      BuildingInstance building = resolve(village);
      BuildingInventory inv = building != null ? building.getInventory() : null;
      if (inv == null) {
         return false;
      }
      int available = coinValueIn(level, inv);
      if (available < amount) {
         return false;
      }
      if (!removeCoins(level, inv, amount)) {
         return false;
      }
      appendLedger(village, level.getGameTime(), reason, -amount);
      TickPerfCounters.TREASURY_OPS.incrementAndGet();
      village.markDirty();
      return true;
   }

   public static int deposit(ServerLevel level, Village village, int amount, String reason) {
      if (amount <= 0) {
         return 0;
      }
      BuildingInstance building = resolve(village);
      BuildingInventory inv = building != null ? building.getInventory() : null;
      if (inv == null) {
         return 0;
      }
      int added = addCoins(level, inv, amount);
      if (added > 0) {
         appendLedger(village, level.getGameTime(), reason, added);
         TickPerfCounters.TREASURY_OPS.incrementAndGet();
         village.markDirty();
      }
      return added;
   }

   public static void applySimulatedDailyTax(ServerLevel level, Village village) {
      int tax = MillenaireServerConfig.SERVER.bankSimulatedNpcTax.getAsInt();
      if (tax <= 0 || !village.isPlayerControlled()) {
         return;
      }
      deposit(level, village, tax, REASON_NPC_TAX);
   }

   public static List<LedgerEntry> ledger(Village village) {
      return List.copyOf(village.getTreasuryLedger());
   }

   public static int coinValueIn(ServerLevel level, BuildingInventory inv) {
      inv.scanChests(level);
      int total = 0;
      total += inv.getCount(level, ModItems.DENIER.get());
      total += inv.getCount(level, ModItems.DENIER_ARGENT.get()) * MoneyHelper.ARGENT_VALUE;
      total += inv.getCount(level, ModItems.DENIER_OR.get()) * MoneyHelper.OR_VALUE;
      return total;
   }

   private static boolean removeCoins(ServerLevel level, BuildingInventory inv, int amount) {
      int available = coinValueIn(level, inv);
      if (available < amount) {
         return false;
      }
      int or = inv.getCount(level, ModItems.DENIER_OR.get());
      int argent = inv.getCount(level, ModItems.DENIER_ARGENT.get());
      int bronze = inv.getCount(level, ModItems.DENIER.get());
      if (or > 0) {
         inv.remove(level, ModItems.DENIER_OR.get(), or);
      }
      if (argent > 0) {
         inv.remove(level, ModItems.DENIER_ARGENT.get(), argent);
      }
      if (bronze > 0) {
         inv.remove(level, ModItems.DENIER.get(), bronze);
      }
      int change = available - amount;
      if (change > 0) {
         addCoins(level, inv, change);
      }
      return true;
   }

   private static int addCoins(ServerLevel level, BuildingInventory inv, int amount) {
      if (amount <= 0) {
         return 0;
      }
      int remaining = amount;
      int or = remaining / MoneyHelper.OR_VALUE;
      remaining %= MoneyHelper.OR_VALUE;
      int argent = remaining / MoneyHelper.ARGENT_VALUE;
      remaining %= MoneyHelper.ARGENT_VALUE;
      int bronze = remaining;
      int added = 0;
      if (or > 0) {
         added += inv.add(level, ModItems.DENIER_OR.get(), or) * MoneyHelper.OR_VALUE;
      }
      if (argent > 0) {
         added += inv.add(level, ModItems.DENIER_ARGENT.get(), argent) * MoneyHelper.ARGENT_VALUE;
      }
      if (bronze > 0) {
         added += inv.add(level, ModItems.DENIER.get(), bronze);
      }
      return added;
   }

   private static void appendLedger(Village village, long tick, String reason, int delta) {
      int max = MillenaireServerConfig.SERVER.bankMaxLedgerEntries.getAsInt();
      Deque<LedgerEntry> entries = new ArrayDeque<>(village.getTreasuryLedger());
      entries.addFirst(new LedgerEntry(tick, reason, delta));
      while (entries.size() > max) {
         entries.removeLast();
      }
      village.setTreasuryLedger(new ArrayList<>(entries));
   }

   public static void saveLedger(CompoundTag tag, Village village) {
      ListTag list = new ListTag();
      for (LedgerEntry e : village.getTreasuryLedger()) {
         CompoundTag entry = new CompoundTag();
         entry.putLong("t", e.tick());
         entry.putString("r", e.reason());
         entry.putInt("d", e.delta());
         list.add(entry);
      }
      tag.put("treasuryLedger", list);
   }

   public static void loadLedger(CompoundTag tag, Village village) {
      List<LedgerEntry> entries = new ArrayList<>();
      if (tag.contains("treasuryLedger", Tag.TAG_LIST)) {
         ListTag list = tag.getList("treasuryLedger", Tag.TAG_COMPOUND);
         for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            entries.add(new LedgerEntry(entry.getLong("t"), entry.getString("r"), entry.getInt("d")));
         }
      }
      village.setTreasuryLedger(entries);
   }

   public record LedgerEntry(long tick, String reason, int delta) {
   }
}
