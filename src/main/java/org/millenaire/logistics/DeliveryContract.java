package org.millenaire.logistics;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Inter-village delivery. Authoritative goods path: source reserve → transit → destination.
 * Abstract advance when unloaded; visible merchants are a presentation of this record.
 */
public final class DeliveryContract {
   private final UUID id;
   private final UUID sourceVillageId;
   private final UUID destVillageId;
   private final Item item;
   private final int amount;
   private ContractState state;
   private @Nullable UUID reservationId;
   private @Nullable UUID linkedNeedId;
   private long createdGameTime;
   private long etaGameTime;
   private String mode; // ABSTRACT | VISIBLE
   private String reason;
   private int delivered;

   public DeliveryContract(
      UUID id,
      UUID sourceVillageId,
      UUID destVillageId,
      Item item,
      int amount,
      ContractState state,
      long createdGameTime,
      long etaGameTime
   ) {
      this.id = id;
      this.sourceVillageId = sourceVillageId;
      this.destVillageId = destVillageId;
      this.item = item;
      this.amount = amount;
      this.state = state;
      this.createdGameTime = createdGameTime;
      this.etaGameTime = etaGameTime;
      this.mode = "ABSTRACT";
      this.reason = "";
   }

   public UUID id() {
      return this.id;
   }

   public UUID sourceVillageId() {
      return this.sourceVillageId;
   }

   public UUID destVillageId() {
      return this.destVillageId;
   }

   public Item item() {
      return this.item;
   }

   public int amount() {
      return this.amount;
   }

   public ContractState state() {
      return this.state;
   }

   public void setState(ContractState state) {
      this.state = state;
   }

   @Nullable
   public UUID reservationId() {
      return this.reservationId;
   }

   public void setReservationId(@Nullable UUID reservationId) {
      this.reservationId = reservationId;
   }

   @Nullable
   public UUID linkedNeedId() {
      return this.linkedNeedId;
   }

   public void setLinkedNeedId(@Nullable UUID linkedNeedId) {
      this.linkedNeedId = linkedNeedId;
   }

   public long createdGameTime() {
      return this.createdGameTime;
   }

   public long etaGameTime() {
      return this.etaGameTime;
   }

   public void setEtaGameTime(long etaGameTime) {
      this.etaGameTime = etaGameTime;
   }

   public String mode() {
      return this.mode;
   }

   public void setMode(String mode) {
      this.mode = mode == null ? "ABSTRACT" : mode;
   }

   public String reason() {
      return this.reason;
   }

   public void setReason(String reason) {
      this.reason = reason == null ? "" : reason;
   }

   public int delivered() {
      return this.delivered;
   }

   public void setDelivered(int delivered) {
      this.delivered = delivered;
   }

   public boolean isOpen() {
      return this.state != ContractState.DONE
         && this.state != ContractState.CANCELLED;
   }

   public CompoundTag save(HolderLookup.Provider registries) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.id);
      tag.putUUID("source", this.sourceVillageId);
      tag.putUUID("dest", this.destVillageId);
      tag.putString("item", BuiltInRegistries.ITEM.getKey(this.item).toString());
      tag.putInt("amount", this.amount);
      tag.putString("state", this.state.name());
      if (this.reservationId != null) {
         tag.putUUID("reservation", this.reservationId);
      }
      if (this.linkedNeedId != null) {
         tag.putUUID("need", this.linkedNeedId);
      }
      tag.putLong("created", this.createdGameTime);
      tag.putLong("eta", this.etaGameTime);
      tag.putString("mode", this.mode);
      tag.putString("reason", this.reason);
      tag.putInt("delivered", this.delivered);
      return tag;
   }

   public static DeliveryContract load(CompoundTag tag, HolderLookup.Provider registries) {
      ResourceLocation itemId = ResourceLocation.tryParse(tag.getString("item"));
      Item item = itemId != null ? BuiltInRegistries.ITEM.get(itemId) : Items.AIR;
      if (item == null) {
         item = Items.AIR;
      }
      DeliveryContract c = new DeliveryContract(
         tag.getUUID("id"),
         tag.getUUID("source"),
         tag.getUUID("dest"),
         item,
         tag.getInt("amount"),
         ContractState.valueOf(tag.getString("state")),
         tag.getLong("created"),
         tag.getLong("eta")
      );
      if (tag.hasUUID("reservation")) {
         c.reservationId = tag.getUUID("reservation");
      }
      if (tag.hasUUID("need")) {
         c.linkedNeedId = tag.getUUID("need");
      }
      c.mode = tag.contains("mode") ? tag.getString("mode") : "ABSTRACT";
      c.reason = tag.contains("reason") ? tag.getString("reason") : "";
      c.delivered = tag.getInt("delivered");
      return c;
   }
}
