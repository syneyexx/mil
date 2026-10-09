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
 * Exact stacks removed from the player are the only source of destination deposits and refunds.
 * Allowed quantities from need revalidation bound each item (never blind request.remaining()).
 */
public final class VillageDeliveryService {
   private static final Logger LOGGER = LogUtils.getLogger();

   private VillageDeliveryService() {
   }

   /**
    * Normal caller flow must supply live allowed quantities from {@link VillageRequestService}.
    * This overload refuses to use {@code request.remaining()} as an authoritative cap.
    */
   public static DeliveryResult deliver(ServerLevel level, ServerPlayer player, Village village, VillageRequest request) {
      return DeliveryResult.fail("living.millenaire.request.error.revalidation_required");
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
      if (level.getGameTime() > request.expiryGameTime()) {
         request.forceState(VillageRequestState.EXPIRED, level.getGameTime());
         VillageRequestSavedData.get(level).setDirty();
         return DeliveryResult.fail("living.millenaire.request.error.expired");
      }
      if (!village.getId().uuid().equals(request.villageId())) {
         return DeliveryResult.fail("living.millenaire.request.error.village_mismatch");
      }
      if (allowedQuantities == null || allowedQuantities.isEmpty()) {
         return DeliveryResult.fail("living.millenaire.request.error.no_longer_needed");
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
      Map<ResourceLocation, Integer> caps = allowedQuantities;

      for (Entry<ResourceLocation, Integer> entry : remaining.entrySet()) {
         ResourceLocation itemId = entry.getKey();
         int want = Math.min(entry.getValue(), caps.getOrDefault(itemId, 0));
         if (want <= 0) {
            continue;
         }
         TransferDetail detail = transferOneItem(level, player, playerInv, inv, itemId, want);
         if (detail.added() > 0) {
            int applied = request.applyDelivery(itemId, detail.added(), level.getGameTime());
            if (applied < detail.added()) {
               int excess = detail.added() - applied;
               List<ItemStack> pulled = pullExactStacksFromDest(level, inv, detail.concreteStacks(), excess);
               int pulledCount = pulled.stream().mapToInt(ItemStack::getCount).sum();
               returnStacksToPlayer(level, player, pulled);
               int still = excess - pulledCount;
               if (still > 0) {
                  // Do not fabricate replacements for goods still stuck in the destination.
                  LOGGER.warn(
                     "[Living] Overdelivery recovery incomplete for {} — {} still in destination; recorded as recovery",
                     itemId,
                     still
                  );
                  request.markRecoveryOutstanding(itemId, still);
               }
            }
            if (applied > 0) {
               transferred.put(itemId, applied);
            }
         } else if (detail.messageKey() != null) {
            // Prefer destination-full feedback when nothing moved for this item.
            if (transferred.isEmpty() && "living.millenaire.request.error.no_space".equals(detail.messageKey())) {
               return DeliveryResult.fail(detail.messageKey());
            }
         }
      }

      VillageRequestSavedData.get(level).setDirty();
      village.markDirty();
      inv.invalidateCache();

      if (transferred.isEmpty()) {
         return DeliveryResult.fail("living.millenaire.request.error.nothing_delivered");
      }
      return DeliveryResult.ok(transferred, request.remaining(), request.state());
   }

   private static TransferDetail transferOneItem(
      ServerLevel level,
      ServerPlayer player,
      Inventory playerInv,
      BuildingInventory dest,
      ResourceLocation itemId,
      int want
   ) {
      if (want <= 0) {
         return TransferDetail.empty();
      }
      if (AnywoodHelper.isAnywood(itemId)) {
         return transferAnywood(level, player, playerInv, dest, want);
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
      List<ItemStack> accepted = new ArrayList<>();
      List<ItemStack> rejected = new ArrayList<>();
      int added = 0;
      for (ItemStack stack : removedStacks) {
         BuildingInventory.AddStackResult result = dest.addExactStack(level, stack);
         if (result.placed() > 0) {
            ItemStack placed = stack.copy();
            placed.setCount(result.placed());
            accepted.add(placed);
            added += result.placed();
         }
         if (!result.remainder().isEmpty()) {
            rejected.add(result.remainder());
         }
      }
      if (!rejected.isEmpty()) {
         returnStacksToPlayer(level, player, rejected);
         if (added <= 0) {
            return new TransferDetail(0, List.of(), "living.millenaire.request.error.no_space");
         }
      }
      return new TransferDetail(added, accepted, null);
   }

   private static TransferDetail transferAnywood(
      ServerLevel level,
      ServerPlayer player,
      Inventory playerInv,
      BuildingInventory dest,
      int want
   ) {
      int remaining = want;
      int totalAdded = 0;
      List<ItemStack> concrete = new ArrayList<>();
      for (int slot = 0; slot < playerInv.getContainerSize() && remaining > 0; slot++) {
         ItemStack stack = playerInv.getItem(slot);
         if (stack.isEmpty() || !stack.is(AnywoodHelper.LOGS_TAG)) {
            continue;
         }
         int take = Math.min(remaining, stack.getCount());
         ItemStack extracted = stack.split(take).copy();
         if (stack.isEmpty()) {
            playerInv.setItem(slot, ItemStack.EMPTY);
         }
         BuildingInventory.AddStackResult result = dest.addExactStack(level, extracted);
         if (result.placed() > 0) {
            ItemStack placed = extracted.copy();
            placed.setCount(result.placed());
            concrete.add(placed);
            totalAdded += result.placed();
            remaining -= result.placed();
         }
         if (!result.remainder().isEmpty()) {
            returnStacksToPlayer(level, player, List.of(result.remainder()));
            if (totalAdded <= 0) {
               return new TransferDetail(0, List.of(), "living.millenaire.request.error.no_space");
            }
            break;
         }
      }
      return new TransferDetail(totalAdded, concrete, null);
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

   private static List<ItemStack> pullExactStacksFromDest(
      ServerLevel level,
      BuildingInventory dest,
      List<ItemStack> concrete,
      int amount
   ) {
      List<ItemStack> pulled = new ArrayList<>();
      int remaining = amount;
      for (ItemStack sample : concrete) {
         if (remaining <= 0) {
            break;
         }
         List<ItemStack> took = dest.removeExactStacks(level, sample, Math.min(remaining, sample.getCount()));
         for (ItemStack t : took) {
            pulled.add(t);
            remaining -= t.getCount();
         }
      }
      return pulled;
   }

   private static void returnStacksToPlayer(ServerLevel level, ServerPlayer player, List<ItemStack> stacks) {
      Inventory inv = player.getInventory();
      for (ItemStack stack : stacks) {
         if (stack == null || stack.isEmpty()) {
            continue;
         }
         ItemStack remaining = stack.copy();
         // Inventory.add returns false when nothing fit; otherwise mutates the passed stack to the leftover.
         if (!inv.add(remaining)) {
            remaining = stack.copy();
         }
         if (!remaining.isEmpty()) {
            for (int i = 0; i < inv.getContainerSize() && !remaining.isEmpty(); i++) {
               if (inv.getItem(i).isEmpty()) {
                  int place = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                  ItemStack slotStack = remaining.copy();
                  slotStack.setCount(place);
                  inv.setItem(i, slotStack);
                  remaining.shrink(place);
               }
            }
         }
         if (!remaining.isEmpty()) {
            ItemEntity dropped = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), remaining.copy());
            dropped.setPickUpDelay(40);
            level.addFreshEntity(dropped);
            LOGGER.warn(
               "[Living] Returned overflow {} x{} as ground drop after failed inventory insert",
               remaining.getItem(),
               remaining.getCount()
            );
         }
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

   private record TransferDetail(int added, List<ItemStack> concreteStacks, @Nullable String messageKey) {
      static TransferDetail empty() {
         return new TransferDetail(0, List.of(), null);
      }
   }
}
