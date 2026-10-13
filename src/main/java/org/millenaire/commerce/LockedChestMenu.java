package org.millenaire.commerce;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.millenaire.building.BuildingId;
import org.millenaire.crime.CrimeService;

public class LockedChestMenu extends AbstractContainerMenu {
   private final Container chestInventory;
   private final int numRows;
   private final boolean locked;
   @Nullable
   private final BuildingId buildingId;
   @Nullable
   private final BlockPos chestPos;

   public LockedChestMenu(int containerId, Inventory playerInventory, Container chestInventory, boolean locked) {
      this(containerId, playerInventory, chestInventory, locked, null, null);
   }

   public LockedChestMenu(
      int containerId,
      Inventory playerInventory,
      Container chestInventory,
      boolean locked,
      @Nullable BuildingId buildingId,
      @Nullable BlockPos chestPos
   ) {
      super(ModMenuTypes.LOCKED_CHEST.get(), containerId);
      this.chestInventory = chestInventory;
      this.numRows = chestInventory.getContainerSize() / 9;
      this.locked = locked;
      this.buildingId = buildingId;
      this.chestPos = chestPos;
      chestInventory.startOpen(playerInventory.player);
      int yOffset = (this.numRows - 4) * 18;

      for (int row = 0; row < this.numRows; row++) {
         for (int col = 0; col < 9; col++) {
            int slotIndex = col + row * 9;
            int x = 8 + col * 18;
            int y = 18 + row * 18;
            if (locked) {
               this.addSlot(new LockedChestMenu.LockedSlot(chestInventory, slotIndex, x, y));
            } else {
               this.addSlot(new Slot(chestInventory, slotIndex, x, y));
            }
         }
      }

      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 103 + row * 18 + yOffset));
         }
      }

      for (int col = 0; col < 9; col++) {
         this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 161 + yOffset));
      }
   }

   public static LockedChestMenu fromNetwork(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
      int rows = buf.readByte();
      boolean locked = buf.readBoolean();
      SimpleContainer container = new SimpleContainer(rows * 9);
      return new LockedChestMenu(containerId, playerInventory, container, locked);
   }

   public void writeToBuffer(FriendlyByteBuf buf) {
      buf.writeByte(this.numRows);
      buf.writeBoolean(this.locked);
   }

   public boolean isLocked() {
      return this.locked;
   }

   public int getNumRows() {
      return this.numRows;
   }

   public Container getContainer() {
      return this.chestInventory;
   }

   public void clicked(int slotId, int button, ClickType clickType, Player player) {
      ItemStack before = ItemStack.EMPTY;
      if (!this.locked && slotId >= 0 && slotId < this.numRows * 9 && slotId < this.slots.size()) {
         before = ((Slot)this.slots.get(slotId)).getItem().copy();
      }

      if (this.locked && slotId >= 0 && slotId < this.slots.size()) {
         Slot slot = (Slot)this.slots.get(slotId);
         if (slot instanceof LockedChestMenu.LockedSlot) {
            ItemStack carried = this.getCarried();
            if (!carried.isEmpty() && slot.mayPlace(carried)) {
               super.clicked(slotId, button, clickType, player);
               return;
            }

            return;
         }
      }

      super.clicked(slotId, button, clickType, player);
      if (!this.locked && !before.isEmpty() && player instanceof ServerPlayer serverPlayer && slotId >= 0 && slotId < this.numRows * 9) {
         ItemStack after = ((Slot)this.slots.get(slotId)).getItem();
         if (after.getCount() < before.getCount() || after.isEmpty()) {
            this.notifyTheft(serverPlayer);
         }
      }
   }

   public ItemStack quickMoveStack(Player player, int index) {
      ItemStack result = ItemStack.EMPTY;
      Slot slot = (Slot)this.slots.get(index);
      if (slot != null && slot.hasItem()) {
         ItemStack slotStack = slot.getItem();
         result = slotStack.copy();
         if (index < this.numRows * 9) {
            if (slot instanceof LockedChestMenu.LockedSlot) {
               return ItemStack.EMPTY;
            }

            if (!this.moveItemStackTo(slotStack, this.numRows * 9, this.slots.size(), true)) {
               return ItemStack.EMPTY;
            }

            if (player instanceof ServerPlayer serverPlayer) {
               this.notifyTheft(serverPlayer);
            }
         } else if (!this.moveItemStackTo(slotStack, 0, this.numRows * 9, false)) {
            return ItemStack.EMPTY;
         }

         if (slotStack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
         } else {
            slot.setChanged();
         }
      }

      return result;
   }

   private void notifyTheft(ServerPlayer player) {
      if (this.buildingId != null && this.chestPos != null) {
         CrimeService.onChestItemTaken(player, this.buildingId, this.chestPos);
      }
   }

   public boolean stillValid(Player player) {
      return this.chestInventory.stillValid(player);
   }

   public void removed(Player player) {
      super.removed(player);
      this.chestInventory.stopOpen(player);
   }

   private static class LockedSlot extends Slot {
      public LockedSlot(Container container, int index, int x, int y) {
         super(container, index, x, y);
      }

      public boolean mayPickup(Player player) {
         return false;
      }

      public boolean mayPlace(ItemStack stack) {
         return true;
      }
   }
}
