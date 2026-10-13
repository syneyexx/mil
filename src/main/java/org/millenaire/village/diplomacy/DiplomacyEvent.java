package org.millenaire.village.diplomacy;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record DiplomacyEvent(
   UUID eventId,
   UUID playerId,
   UUID villageId,
   DiplomacyEventType type,
   ResourceLocation sourceCulture,
   @Nullable ResourceLocation secondaryCulture,
   long createdDay,
   long expiryDay,
   int amount,
   String itemKey,
   boolean pending
) {
   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.eventId);
      tag.putUUID("player", this.playerId);
      tag.putUUID("village", this.villageId);
      tag.putString("type", this.type.name());
      tag.putString("culture", this.sourceCulture.toString());
      if (this.secondaryCulture != null) {
         tag.putString("secondary", this.secondaryCulture.toString());
      }
      tag.putLong("created", this.createdDay);
      tag.putLong("expiry", this.expiryDay);
      tag.putInt("amount", this.amount);
      tag.putString("item", this.itemKey != null ? this.itemKey : "");
      tag.putBoolean("pending", this.pending);
      return tag;
   }

   public static DiplomacyEvent load(CompoundTag tag) {
      DiplomacyEventType type;
      try {
         type = DiplomacyEventType.valueOf(tag.getString("type"));
      } catch (IllegalArgumentException e) {
         type = DiplomacyEventType.CULTURE_PRAISE;
      }
      return new DiplomacyEvent(
         tag.getUUID("id"),
         tag.getUUID("player"),
         tag.getUUID("village"),
         type,
         ResourceLocation.parse(tag.getString("culture")),
         tag.contains("secondary") ? ResourceLocation.parse(tag.getString("secondary")) : null,
         tag.getLong("created"),
         tag.getLong("expiry"),
         tag.getInt("amount"),
         tag.getString("item"),
         tag.getBoolean("pending")
      );
   }

   public DiplomacyEvent withPending(boolean value) {
      return new DiplomacyEvent(
         this.eventId,
         this.playerId,
         this.villageId,
         this.type,
         this.sourceCulture,
         this.secondaryCulture,
         this.createdDay,
         this.expiryDay,
         this.amount,
         this.itemKey,
         value
      );
   }
}
