package org.millenaire.logistics;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Soft lock against physical stock. Goods remain in chests until pulled into transit.
 */
public final class StockReservation {
   private final UUID id;
   private final UUID villageId;
   private final Item item;
   private final int amount;
   private final String ownerRef;
   private ReservationState state;

   public StockReservation(UUID id, UUID villageId, Item item, int amount, String ownerRef, ReservationState state) {
      this.id = id;
      this.villageId = villageId;
      this.item = item;
      this.amount = amount;
      this.ownerRef = ownerRef == null ? "" : ownerRef;
      this.state = state;
   }

   public UUID id() {
      return this.id;
   }

   public UUID villageId() {
      return this.villageId;
   }

   public Item item() {
      return this.item;
   }

   public int amount() {
      return this.amount;
   }

   public String ownerRef() {
      return this.ownerRef;
   }

   public ReservationState state() {
      return this.state;
   }

   public void setState(ReservationState state) {
      this.state = state;
   }

   public CompoundTag save(HolderLookup.Provider registries) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.id);
      tag.putUUID("villageId", this.villageId);
      tag.putString("item", BuiltInRegistries.ITEM.getKey(this.item).toString());
      tag.putInt("amount", this.amount);
      tag.putString("owner", this.ownerRef);
      tag.putString("state", this.state.name());
      return tag;
   }

   public static StockReservation load(CompoundTag tag, HolderLookup.Provider registries) {
      ResourceLocation itemId = ResourceLocation.tryParse(tag.getString("item"));
      Item item = itemId != null ? BuiltInRegistries.ITEM.get(itemId) : Items.AIR;
      if (item == null) {
         item = Items.AIR;
      }
      return new StockReservation(
         tag.getUUID("id"),
         tag.getUUID("villageId"),
         item,
         tag.getInt("amount"),
         tag.getString("owner"),
         ReservationState.valueOf(tag.getString("state"))
      );
   }
}
