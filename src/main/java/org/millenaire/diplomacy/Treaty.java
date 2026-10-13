package org.millenaire.diplomacy;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class Treaty {
   private final UUID id;
   private final UUID villageA;
   private final UUID villageB;
   private final TreatyType type;
   private final long createdGameTime;
   private final long expiresGameTime;
   private int trust;
   private boolean breached;
   private boolean active;
   @Nullable
   private Item tradeItem;
   private int tradeAmount;
   private String lastReason = "";

   public Treaty(
      UUID id,
      UUID villageA,
      UUID villageB,
      TreatyType type,
      long createdGameTime,
      long expiresGameTime
   ) {
      this.id = id;
      this.villageA = villageA;
      this.villageB = villageB;
      this.type = type;
      this.createdGameTime = createdGameTime;
      this.expiresGameTime = expiresGameTime;
      this.trust = 50;
      this.active = true;
   }

   public UUID id() {
      return this.id;
   }

   public UUID villageA() {
      return this.villageA;
   }

   public UUID villageB() {
      return this.villageB;
   }

   public TreatyType type() {
      return this.type;
   }

   public boolean active() {
      return this.active && !this.breached;
   }

   public int trust() {
      return this.trust;
   }

   public void adjustTrust(int delta) {
      this.trust = Math.max(0, Math.min(100, this.trust + delta));
   }

   public void markBreached(String reason) {
      this.breached = true;
      this.active = false;
      this.lastReason = reason == null ? "breached" : reason;
   }

   public void expire() {
      this.active = false;
      this.lastReason = "expired";
   }

   public boolean involves(UUID villageId) {
      return this.villageA.equals(villageId) || this.villageB.equals(villageId);
   }

   public boolean isBetween(UUID a, UUID b) {
      return involves(a) && involves(b) && !a.equals(b);
   }

   @Nullable
   public Item tradeItem() {
      return this.tradeItem;
   }

   public void setTradeObligation(@Nullable Item item, int amount) {
      this.tradeItem = item;
      this.tradeAmount = amount;
   }

   public int tradeAmount() {
      return this.tradeAmount;
   }

   public String lastReason() {
      return this.lastReason;
   }

   public void setLastReason(String reason) {
      this.lastReason = reason == null ? "" : reason;
   }

   public long expiresGameTime() {
      return this.expiresGameTime;
   }

   public CompoundTag save(HolderLookup.Provider registries) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.id);
      tag.putUUID("a", this.villageA);
      tag.putUUID("b", this.villageB);
      tag.putString("type", this.type.name());
      tag.putLong("created", this.createdGameTime);
      tag.putLong("expires", this.expiresGameTime);
      tag.putInt("trust", this.trust);
      tag.putBoolean("breached", this.breached);
      tag.putBoolean("active", this.active);
      if (this.tradeItem != null) {
         tag.putString("item", BuiltInRegistries.ITEM.getKey(this.tradeItem).toString());
         tag.putInt("amount", this.tradeAmount);
      }
      tag.putString("reason", this.lastReason);
      return tag;
   }

   public static Treaty load(CompoundTag tag, HolderLookup.Provider registries) {
      Treaty t = new Treaty(
         tag.getUUID("id"),
         tag.getUUID("a"),
         tag.getUUID("b"),
         TreatyType.valueOf(tag.getString("type")),
         tag.getLong("created"),
         tag.getLong("expires")
      );
      t.trust = tag.getInt("trust");
      t.breached = tag.getBoolean("breached");
      t.active = tag.getBoolean("active");
      if (tag.contains("item")) {
         ResourceLocation id = ResourceLocation.tryParse(tag.getString("item"));
         Item item = id != null ? BuiltInRegistries.ITEM.get(id) : Items.AIR;
         t.tradeItem = item == null ? Items.AIR : item;
         t.tradeAmount = tag.getInt("amount");
      }
      t.lastReason = tag.contains("reason") ? tag.getString("reason") : "";
      return t;
   }
}
