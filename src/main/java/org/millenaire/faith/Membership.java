package org.millenaire.faith;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/**
 * Canonical membership of a player or villager record in a chapter.
 * Villager membership also mirrors onto {@link org.millenaire.village.VillagerRecord} fields.
 */
public final class Membership {
   public enum SubjectType {
      PLAYER,
      VILLAGER
   }

   private final UUID subjectId;
   private final SubjectType subjectType;
   private final UUID chapterId;
   private OrderRole role;
   private int devotion;
   private long joinedGameTime;

   public Membership(UUID subjectId, SubjectType subjectType, UUID chapterId, OrderRole role, int devotion, long joinedGameTime) {
      this.subjectId = subjectId;
      this.subjectType = subjectType;
      this.chapterId = chapterId;
      this.role = role;
      this.devotion = clamp(devotion);
      this.joinedGameTime = joinedGameTime;
   }

   public UUID subjectId() {
      return this.subjectId;
   }

   public SubjectType subjectType() {
      return this.subjectType;
   }

   public UUID chapterId() {
      return this.chapterId;
   }

   public OrderRole role() {
      return this.role;
   }

   public void setRole(OrderRole role) {
      this.role = role;
   }

   public int devotion() {
      return this.devotion;
   }

   public void setDevotion(int devotion) {
      this.devotion = clamp(devotion);
   }

   public void addDevotion(int delta) {
      setDevotion(this.devotion + delta);
   }

   public long joinedGameTime() {
      return this.joinedGameTime;
   }

   private static int clamp(int v) {
      return Math.max(0, Math.min(100, v));
   }

   public String storageKey() {
      return this.subjectType.name() + ":" + this.subjectId;
   }

   public static String keyFor(SubjectType type, UUID id) {
      return type.name() + ":" + id;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("subject", this.subjectId);
      tag.putString("stype", this.subjectType.name());
      tag.putUUID("chapter", this.chapterId);
      tag.putString("role", this.role.name());
      tag.putInt("devotion", this.devotion);
      tag.putLong("joined", this.joinedGameTime);
      return tag;
   }

   @Nullable
   public static Membership load(CompoundTag tag) {
      try {
         SubjectType st = SubjectType.valueOf(tag.getString("stype"));
         OrderRole role = OrderRole.fromId(tag.getString("role"));
         if (role == null) {
            role = OrderRole.INTERESTED;
         }
         return new Membership(
            tag.getUUID("subject"),
            st,
            tag.getUUID("chapter"),
            role,
            tag.getInt("devotion"),
            tag.getLong("joined")
         );
      } catch (Exception e) {
         return null;
      }
   }
}
