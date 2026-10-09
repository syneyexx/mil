package org.millenaire.living.requests;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.millenaire.building.AnywoodHelper;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.item.ItemHelper;
import org.millenaire.village.Village;
import org.slf4j.Logger;

/**
 * Server-only item delivery into authoritative townhall inventory.
 * Order: validate → remove from player → add to village → compensate leftovers → update request.
 */
public final class VillageDeliveryService {
   private static final Logger LOGGER = LogUtils.getLogger();

   private VillageDeliveryService() {
   }

   public static DeliveryResult deliver(ServerLevel level, ServerPlayer player, Village village, VillageRequest request) {
      if (level == null || player == null || village == null || request == null) {
         return DeliveryResult.fail("living.millenaire.request.error.invalid");
      }
      if (!player.getUUID().equals(request.playerId())) {
         return DeliveryResult.fail("living.millenaire.request.error.not_owner");
      }
      if (request.state() != VillageRequestState.ACCEPTED && request.state() != VillageRequestState.IN_PROGRESS) {
         return DeliveryResult.fail("living.millenaire.request.error.bad_state");
      }
      if (!village.getId().uuid().equals(request.villageId())) {
         return DeliveryResult.fail("living.millenaire.request.error.village_mismatch");
      }

      BuildingInstance townhall = village.getTownhall();
      if (townhall == null || townhall.getInventory() == null) {
         return DeliveryResult.fail("living.millenaire.request.error.no_inventory");
      }
      BuildingInventory inv = townhall.getInventory();
      inv.invalidateCacheOncePerTick(level.getGameTime());

      Map<ResourceLocation, Integer> remaining = request.remaining();
      if (remaining.isEmpty()) {
         request.transitionTo(VillageRequestState.READY_TO_COMPLETE, level.getGameTime());
         VillageRequestSavedData.get(level).setDirty();
         return DeliveryResult.fail("living.millenaire.request.error.already_complete");
      }

      Map<ResourceLocation, Integer> transferred = new LinkedHashMap<>();
      Inventory playerInv = player.getInventory();

      for (Entry<ResourceLocation, Integer> entry : remaining.entrySet()) {
         ResourceLocation itemId = entry.getKey();
         int want = entry.getValue();
         int moved = transferOneItem(level, playerInv, inv, itemId, want);
         if (moved > 0) {
            int applied = request.applyDelivery(itemId, moved, level.getGameTime());
            if (applied < moved) {
               // Over-delivery relative to request accounting — return excess to player.
               returnExcess(playerInv, itemId, moved - applied);
               moved = applied;
            }
            if (moved > 0) {
               transferred.put(itemId, moved);
            }
         }
      }

      VillageRequestSavedData.get(level).setDirty();
      village.markDirty();

      if (transferred.isEmpty()) {
         return DeliveryResult.fail("living.millenaire.request.error.nothing_delivered");
      }
      return DeliveryResult.ok(transferred, request.remaining(), request.state());
   }

   private static int transferOneItem(ServerLevel level, Inventory playerInv, BuildingInventory dest, ResourceLocation itemId, int want) {
      if (want <= 0) {
         return 0;
      }
      if (AnywoodHelper.isAnywood(itemId)) {
         return transferAnywood(level, playerInv, dest, want);
      }
      Item item = ItemHelper.resolve(itemId);
      if (item == null) {
         return 0;
      }
      int available = countPlayerItem(playerInv, item);
      if (available <= 0) {
         return 0;
      }
      int attempt = Math.min(want, available);
      // Remove from player first into a virtual count, then commit to dest; compensate on shortfall.
      int removed = removeFromPlayer(playerInv, item, attempt);
      if (removed <= 0) {
         return 0;
      }
      int added = dest.add(level, item, removed);
      if (added < removed) {
         giveBack(playerInv, item, removed - added);
      }
      return added;
   }

   private static int transferAnywood(ServerLevel level, Inventory playerInv, BuildingInventory dest, int want) {
      int remaining = want;
      int totalAdded = 0;
      for (int slot = 0; slot < playerInv.getContainerSize() && remaining > 0; slot++) {
         ItemStack stack = playerInv.getItem(slot);
         if (stack.isEmpty() || !stack.is(AnywoodHelper.LOGS_TAG)) {
            continue;
         }
         Item item = stack.getItem();
         int take = Math.min(remaining, stack.getCount());
         ItemStack extracted = stack.split(take);
         if (stack.isEmpty()) {
            playerInv.setItem(slot, ItemStack.EMPTY);
         }
         int added = dest.add(level, item, extracted.getCount());
         totalAdded += added;
         remaining -= added;
         if (added < extracted.getCount()) {
            giveBack(playerInv, item, extracted.getCount() - added);
            break;
         }
      }
      return totalAdded;
   }

   private static int countPlayerItem(Inventory inv, Item item) {
      int total = 0;
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);
         if (!stack.isEmpty() && stack.is(item)) {
            total += stack.getCount();
         }
      }
      return total;
   }

   private static int removeFromPlayer(Inventory inv, Item item, int amount) {
      int remaining = amount;
      for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
         ItemStack stack = inv.getItem(i);
         if (stack.isEmpty() || !stack.is(item)) {
            continue;
         }
         int take = Math.min(remaining, stack.getCount());
         stack.shrink(take);
         if (stack.isEmpty()) {
            inv.setItem(i, ItemStack.EMPTY);
         }
         remaining -= take;
      }
      return amount - remaining;
   }

   private static void giveBack(Inventory inv, Item item, int amount) {
      int remaining = amount;
      while (remaining > 0) {
         int stackSize = Math.min(item.getDefaultMaxStackSize(), remaining);
         ItemStack stack = new ItemStack(item, stackSize);
         if (!inv.add(stack)) {
            // Drop is intentionally avoided here; try to force into empty slots.
            for (int i = 0; i < inv.getContainerSize() && !stack.isEmpty(); i++) {
               if (inv.getItem(i).isEmpty()) {
                  inv.setItem(i, stack.copy());
                  stack.setCount(0);
               }
            }
            if (!stack.isEmpty()) {
               LOGGER.warn("[Living] Could not fully return {} x{} to player inventory after failed village deposit", item, stack.getCount());
            }
            break;
         }
         remaining -= stackSize;
      }
   }

   private static void returnExcess(Inventory inv, ResourceLocation itemId, int amount) {
      if (AnywoodHelper.isAnywood(itemId)) {
         // Best-effort: return as oak log if specific type unknown.
         Item oak = ItemHelper.resolve(ResourceLocation.parse("minecraft:oak_log"));
         if (oak != null) {
            giveBack(inv, oak, amount);
         }
         return;
      }
      Item item = ItemHelper.resolve(itemId);
      if (item != null) {
         giveBack(inv, item, amount);
      }
   }

   public record DeliveryResult(
      boolean success,
      String messageKey,
      Map<ResourceLocation, Integer> transferred,
      Map<ResourceLocation, Integer> remaining,
      @Nullable VillageRequestState newState
   ) {
      public static DeliveryResult ok(Map<ResourceLocation, Integer> transferred, Map<ResourceLocation, Integer> remaining, VillageRequestState state) {
         return new DeliveryResult(true, "living.millenaire.request.delivery.ok", Map.copyOf(transferred), Map.copyOf(remaining), state);
      }

      public static DeliveryResult fail(String key) {
         return new DeliveryResult(false, key, Map.of(), Map.of(), null);
      }
   }
}
