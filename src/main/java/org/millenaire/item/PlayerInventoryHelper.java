package org.millenaire.item;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class PlayerInventoryHelper {
   private PlayerInventoryHelper() {
   }

   public static int roomFor(Inventory inventory, ItemStack probe) {
      if (probe.isEmpty()) {
         return 0;
      }

      int max = inventory.getMaxStackSize(probe);
      int room = 0;

      for (ItemStack slot : inventory.items) {
         if (slot.isEmpty()) {
            room += max;
         } else if (slot.isStackable() && slot.getCount() < max && ItemStack.isSameItemSameComponents(slot, probe)) {
            room += max - slot.getCount();
         }
      }

      for (ItemStack slot : inventory.offhand) {
         if (!slot.isEmpty() && slot.isStackable() && slot.getCount() < max && ItemStack.isSameItemSameComponents(slot, probe)) {
            room += max - slot.getCount();
         }
      }

      return room;
   }

   public static int give(Inventory inventory, ItemStack stack) {
      if (stack.isEmpty()) {
         return 0;
      }

      int total = stack.getCount();
      int offered = Math.min(total, roomFor(inventory, stack));
      if (offered <= 0) {
         return total;
      }

      ItemStack toInsert = stack.copyWithCount(offered);
      inventory.add(toInsert);
      int refused = total - offered + toInsert.getCount();
      stack.setCount(refused);
      return refused;
   }

   public static void giveOrDrop(Player player, ItemStack stack) {
      if (give(player.getInventory(), stack) > 0) {
         player.drop(stack, false);
      }
   }
}
