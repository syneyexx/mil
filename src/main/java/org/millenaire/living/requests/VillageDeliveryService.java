package org.millenaire.living.requests;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
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
 * Allowed quantities from need revalidation bound each item (never blind request.remaining()).
 */
public final class VillageDeliveryService {
   private static final Logger LOGGER = LogUtils.getLogger();

   private VillageDeliveryService() {
   }

   public static DeliveryResult deliver(ServerLevel level, ServerPlayer player, Village village, VillageRequest request) {
      return deliver(level, player, village, request, request.remaining());
   }

   public static DeliveryResult deliver(
      ServerLevel level,
      ServerPlayer player,
      Village village,
      VillageRequest request,
      Map<ResourceLocation, Integer> allowedQuantities
   ) {
      if (level == null || player == null || village == null || request == null) {
         return DeliveryResult.fail("living.millenaire.request.error.invalid");
      }
      if (!player.getUUID().equals(request.playerId())) {
         return DeliveryResult.fail("living.millenaire.request.error.not_owner");
      }
      if (request.state() != VillageRequestState.ACCEPTED && request.state() != VillageRequestState.IN_PROGRESS) {
         return DeliveryResult.fail("living.millenaire.request.error.bad_state");
      }
      if (request.state() == VillageRequestState.EXPIRED || request.state() == VillageRequestState.CANCELLED) {
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
      Map<ResourceLocation, Integer> caps = allowedQuantities == null ? Map.of() : allowedQuantities;

      for (Entry<ResourceLocation, Integer> entry : remaining.entrySet()) {
         ResourceLocation itemId = entry.getKey();
         int want = Math.min(entry.getValue(), caps.getOrDefault(itemId, 0));
         if (want <= 0) {
            continue;
         }
         TransferDetail detail = transferOneItem(level, playerInv, inv, itemId, want);
         if (detail.added() > 0) {
            int applied = request.applyDelivery(itemId, detail.added(), level.getGameTime());
            if (applied < detail.added()) {
               // Over-delivery relative to request accounting — pull excess back from destination first.
               int excess = detail.added() - applied;
               int pulled = pullExactFromDest(level, inv, detail.concreteStacks(), excess);
               int still = excess - pulled;
               if (still > 0) {
                  returnExact(level, player, detail.concreteStacks(), still);
               }
            }
            if (applied > 0) {
               transferred.put(itemId, applied);
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

   private static TransferDetail transferOneItem(
      ServerLevel level,
      Inventory playerInv,
      BuildingInventory dest,
      ResourceLocation itemId,
      int want
   ) {
      if (want <= 0) {
         return TransferDetail.empty();
      }
      if (AnywoodHelper.isAnywood(itemId)) {
         return transferAnywood(level, playerInv, dest, want);
      }
      Item item = ItemHelper.resolve(itemId);
      if (item == null) {
         return TransferDetail.empty();
      }
      int available = countPlayerItem(playerInv, item);
      if (available <= 0) {
         return TransferDetail.empty();
      }
      int attempt = Math.min(want, available);
      List<ItemStack> removedStacks = removeExactFromPlayer(playerInv, item, attempt);
      int removed = removedStacks.stream().mapToInt(ItemStack::getCount).sum();
      if (removed <= 0) {
         return TransferDetail.empty();
      }
      int added = dest.add(level, item, removed);
      if (added < removed) {
         returnExactStacks(playerInv, item, removed - added, level, null);
      }
      List<ItemStack> concrete = new ArrayList<>();
      if (added > 0) {
         concrete.add(new ItemStack(item, added));
      }
      return new TransferDetail(added, concrete);
   }

   private static TransferDetail transferAnywood(ServerLevel level, Inventory playerInv, BuildingInventory dest, int want) {
      int remaining = want;
      int totalAdded = 0;
      List<ItemStack> concrete = new ArrayList<>();
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
         if (added > 0) {
            concrete.add(new ItemStack(item, added));
         }
         if (added < extracted.getCount()) {
            returnExactStacks(playerInv, item, extracted.getCount() - added, level, null);
            break;
         }
      }
      return new TransferDetail(totalAdded, concrete);
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

   private static List<ItemStack> removeExactFromPlayer(Inventory inv, Item item, int amount) {
      List<ItemStack> taken = new ArrayList<>();
      int remaining = amount;
      for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
         ItemStack stack = inv.getItem(i);
         if (stack.isEmpty() || !stack.is(item)) {
            continue;
         }
         int take = Math.min(remaining, stack.getCount());
         ItemStack split = stack.split(take);
         if (stack.isEmpty()) {
            inv.setItem(i, ItemStack.EMPTY);
         }
         taken.add(split.copy());
         remaining -= take;
      }
      return taken;
   }

   private static int pullExactFromDest(ServerLevel level, BuildingInventory dest, List<ItemStack> concrete, int amount) {
      int remaining = amount;
      for (ItemStack sample : concrete) {
         if (remaining <= 0) {
            break;
         }
         int took = dest.remove(level, sample.getItem(), Math.min(remaining, sample.getCount()));
         remaining -= took;
      }
      return amount - remaining;
   }

   private static void returnExact(ServerLevel level, ServerPlayer player, List<ItemStack> concrete, int amount) {
      int remaining = amount;
      Inventory inv = player.getInventory();
      for (ItemStack sample : concrete) {
         if (remaining <= 0) {
            break;
         }
         int give = Math.min(remaining, sample.getCount());
         returnExactStacks(inv, sample.getItem(), give, level, player);
         remaining -= give;
      }
   }

   private static void returnExactStacks(Inventory inv, Item item, int amount, ServerLevel level, @Nullable ServerPlayer player) {
      int remaining = amount;
      while (remaining > 0) {
         int stackSize = Math.min(item.getDefaultMaxStackSize(), remaining);
         ItemStack stack = new ItemStack(item, stackSize);
         if (!inv.add(stack)) {
            for (int i = 0; i < inv.getContainerSize() && !stack.isEmpty(); i++) {
               if (inv.getItem(i).isEmpty()) {
                  inv.setItem(i, stack.copy());
                  stack.setCount(0);
               }
            }
            if (!stack.isEmpty()) {
               // Explicit recovery: drop at player feet rather than silently deleting items.
               if (player != null && level != null) {
                  ItemEntity dropped = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), stack.copy());
                  dropped.setPickUpDelay(40);
                  level.addFreshEntity(dropped);
                  LOGGER.warn("[Living] Returned overflow {} x{} as ground drop after failed inventory insert", item, stack.getCount());
               } else {
                  LOGGER.warn("[Living] Could not fully return {} x{} to player inventory after failed village deposit", item, stack.getCount());
               }
            }
            break;
         }
         remaining -= stackSize;
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

   private record TransferDetail(int added, List<ItemStack> concreteStacks) {
      static TransferDetail empty() {
         return new TransferDetail(0, List.of());
      }
   }
}
