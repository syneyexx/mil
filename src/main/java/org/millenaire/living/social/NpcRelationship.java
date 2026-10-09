package org.millenaire.living.social;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * Directed relationship from one NPC toward another.
 * Ranges: familiarity 0..100; trust/affection/respect/rivalry -100..100.
 */
public final class NpcRelationship {
   private final UUID fromId;
   private final UUID toId;
   private NpcRelationshipType type;
   private int familiarity;
   private int trust;
   private int affection;
   private int respect;
   private int rivalry;
   private long lastInteractionGameTime;
   private long lastUpdateGameTime;

   public NpcRelationship(UUID fromId, UUID toId, NpcRelationshipType type) {
      this.fromId = fromId;
      this.toId = toId;
      this.type = type == null ? NpcRelationshipType.UNKNOWN : type;
   }

   public UUID fromId() {
      return this.fromId;
   }

   public UUID toId() {
      return this.toId;
   }

   public NpcRelationshipType type() {
      return this.type;
   }

   public void setType(NpcRelationshipType type) {
      if (type != null) {
         this.type = type;
      }
   }

   public int familiarity() {
      return this.familiarity;
   }

   public int trust() {
      return this.trust;
   }

   public int affection() {
      return this.affection;
   }

   public int respect() {
      return this.respect;
   }

   public int rivalry() {
      return this.rivalry;
   }

   public long lastInteractionGameTime() {
      return this.lastInteractionGameTime;
   }

   public long lastUpdateGameTime() {
      return this.lastUpdateGameTime;
   }

   public void setLastInteractionGameTime(long t) {
      this.lastInteractionGameTime = t;
   }

   public static int clampFamiliarity(int v) {
      return Math.max(0, Math.min(100, v));
   }

   public static int clampSigned(int v) {
      return Math.max(-100, Math.min(100, v));
   }

   public void adjust(int familiarityDelta, int trustDelta, int affectionDelta, int respectDelta, int rivalryDelta, long gameTime) {
      this.familiarity = clampFamiliarity(this.familiarity + familiarityDelta);
      this.trust = clampSigned(this.trust + trustDelta);
      this.affection = clampSigned(this.affection + affectionDelta);
      this.respect = clampSigned(this.respect + respectDelta);
      this.rivalry = clampSigned(this.rivalry + rivalryDelta);
      this.lastUpdateGameTime = gameTime;
      this.lastInteractionGameTime = gameTime;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("from", this.fromId);
      tag.putUUID("to", this.toId);
      tag.putString("type", this.type.name());
      tag.putInt("familiarity", this.familiarity);
      tag.putInt("trust", this.trust);
      tag.putInt("affection", this.affection);
      tag.putInt("respect", this.respect);
      tag.putInt("rivalry", this.rivalry);
      tag.putLong("lastInteraction", this.lastInteractionGameTime);
      tag.putLong("lastUpdate", this.lastUpdateGameTime);
      return tag;
   }

   public static NpcRelationship load(CompoundTag tag) {
      UUID from = tag.getUUID("from");
      UUID to = tag.getUUID("to");
      NpcRelationshipType type;
      try {
         type = NpcRelationshipType.valueOf(tag.getString("type"));
      } catch (Exception e) {
         type = NpcRelationshipType.UNKNOWN;
      }
      NpcRelationship rel = new NpcRelationship(from, to, type);
      rel.familiarity = clampFamiliarity(tag.getInt("familiarity"));
      rel.trust = clampSigned(tag.getInt("trust"));
      rel.affection = clampSigned(tag.getInt("affection"));
      rel.respect = clampSigned(tag.getInt("respect"));
      rel.rivalry = clampSigned(tag.getInt("rivalry"));
      rel.lastInteractionGameTime = tag.getLong("lastInteraction");
      rel.lastUpdateGameTime = tag.getLong("lastUpdate");
      return rel;
   }
}
