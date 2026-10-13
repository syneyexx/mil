package org.millenaire.economy;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Authoritative village need. Expired/cancelled needs must not accept deliveries or rewards.
 */
public final class Need {
   private final UUID id;
   private final UUID villageId;
   private NeedCategory category;
   private Item item;
   private int amount;
   private int reservedAmount;
   private int deliveredAmount;
   private int priority;
   private long deadlineGameTime;
   private String sourceRef;
   private NeedState state;
   private String blockedReason;
   private long createdGameTime;

   public Need(
      UUID id,
      UUID villageId,
      NeedCategory category,
      Item item,
      int amount,
      int priority,
      long deadlineGameTime,
      String sourceRef,
      NeedState state,
      long createdGameTime
   ) {
      this.id = id;
      this.villageId = villageId;
      this.category = category;
      this.item = item;
      this.amount = Math.max(0, amount);
      this.priority = priority;
      this.deadlineGameTime = deadlineGameTime;
      this.sourceRef = sourceRef == null ? "" : sourceRef;
      this.state = state;
      this.createdGameTime = createdGameTime;
      this.blockedReason = "";
   }

   public UUID id() {
      return this.id;
   }

   public UUID villageId() {
      return this.villageId;
   }

   public NeedCategory category() {
      return this.category;
   }

   public Item item() {
      return this.item;
   }

   public int amount() {
      return this.amount;
   }

   public int remaining() {
      return Math.max(0, this.amount - this.deliveredAmount);
   }

   public int reservedAmount() {
      return this.reservedAmount;
   }

   public void setReservedAmount(int reservedAmount) {
      this.reservedAmount = Math.max(0, reservedAmount);
   }

   public int deliveredAmount() {
      return this.deliveredAmount;
   }

   public int priority() {
      return this.priority;
   }

   public long deadlineGameTime() {
      return this.deadlineGameTime;
   }

   public String sourceRef() {
      return this.sourceRef;
   }

   public NeedState state() {
      return this.state;
   }

   public void setState(NeedState state) {
      this.state = state;
   }

   public String blockedReason() {
      return this.blockedReason;
   }

   public void setBlockedReason(String blockedReason) {
      this.blockedReason = blockedReason == null ? "" : blockedReason;
   }

   public long createdGameTime() {
      return this.createdGameTime;
   }

   public boolean acceptsDelivery() {
      return this.state == NeedState.ACTIVE || this.state == NeedState.PARTIAL || this.state == NeedState.PROPOSED;
   }

   public void applyDelivery(int qty) {
      if (!acceptsDelivery() || qty <= 0) {
         return;
      }
      this.deliveredAmount = Math.min(this.amount, this.deliveredAmount + qty);
      if (this.deliveredAmount >= this.amount) {
         this.state = NeedState.SATISFIED;
         this.blockedReason = "";
      } else {
         this.state = NeedState.PARTIAL;
      }
   }

   public void tickExpiry(long gameTime) {
      if (!acceptsDelivery()) {
         return;
      }
      if (this.deadlineGameTime > 0L && gameTime >= this.deadlineGameTime) {
         this.state = NeedState.EXPIRED;
         this.blockedReason = "expired";
      }
   }

   public CompoundTag save(HolderLookup.Provider registries) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.id);
      tag.putUUID("villageId", this.villageId);
      tag.putString("category", this.category.name());
      tag.putString("item", BuiltInRegistries.ITEM.getKey(this.item).toString());
      tag.putInt("amount", this.amount);
      tag.putInt("reserved", this.reservedAmount);
      tag.putInt("delivered", this.deliveredAmount);
      tag.putInt("priority", this.priority);
      tag.putLong("deadline", this.deadlineGameTime);
      tag.putString("source", this.sourceRef);
      tag.putString("state", this.state.name());
      tag.putString("blocked", this.blockedReason);
      tag.putLong("created", this.createdGameTime);
      return tag;
   }

   public static Need load(CompoundTag tag, HolderLookup.Provider registries) {
      UUID id = tag.getUUID("id");
      UUID villageId = tag.getUUID("villageId");
      NeedCategory category = NeedCategory.valueOf(tag.getString("category"));
      ResourceLocation itemId = ResourceLocation.tryParse(tag.getString("item"));
      Item item = itemId != null ? BuiltInRegistries.ITEM.get(itemId) : Items.AIR;
      if (item == null) {
         item = Items.AIR;
      }
      Need need = new Need(
         id,
         villageId,
         category,
         item,
         tag.getInt("amount"),
         tag.getInt("priority"),
         tag.getLong("deadline"),
         tag.getString("source"),
         NeedState.valueOf(tag.getString("state")),
         tag.getLong("created")
      );
      need.reservedAmount = tag.getInt("reserved");
      need.deliveredAmount = tag.getInt("delivered");
      need.blockedReason = tag.contains("blocked") ? tag.getString("blocked") : "";
      return need;
   }

   @Nullable
   public static NeedCategory tryParseCategory(String raw) {
      try {
         return NeedCategory.valueOf(raw);
      } catch (Exception e) {
         return null;
      }
   }
}
