package org.millenaire.religion.model;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class FaithAffiliation {
   private ResourceLocation traditionId;
   private int devotion;
   private long joinedGameTime;

   public FaithAffiliation(ResourceLocation traditionId, int devotion, long joinedGameTime) {
      this.traditionId = traditionId;
      this.devotion = clamp(devotion);
      this.joinedGameTime = joinedGameTime;
   }

   public ResourceLocation traditionId() {
      return this.traditionId;
   }

   public int devotion() {
      return this.devotion;
   }

   public long joinedGameTime() {
      return this.joinedGameTime;
   }

   public void setDevotion(int devotion) {
      this.devotion = clamp(devotion);
   }

   public void adjustDevotion(int delta) {
      this.devotion = clamp(this.devotion + delta);
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("tradition", this.traditionId.toString());
      tag.putInt("devotion", this.devotion);
      tag.putLong("joined", this.joinedGameTime);
      return tag;
   }

   public static FaithAffiliation load(CompoundTag tag) {
      ResourceLocation id = ResourceLocation.parse(tag.getString("tradition"));
      return new FaithAffiliation(id, tag.getInt("devotion"), tag.getLong("joined"));
   }

   private static int clamp(int v) {
      return Math.max(0, Math.min(100, v));
   }

   @Override
   public String toString() {
      return this.traditionId + "@" + this.devotion;
   }
}
