package org.millenaire.activity;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public final class ActivityOutcomeSummary {
   private final UUID operationId;
   private final ActivityKind kind;
   private final String result;
   private final long gameTime;
   private final String sourceKey;
   private final String targetKey;

   public ActivityOutcomeSummary(UUID operationId, ActivityKind kind, String result, long gameTime, String sourceKey, String targetKey) {
      this.operationId = operationId;
      this.kind = kind;
      this.result = result == null ? "" : result;
      this.gameTime = gameTime;
      this.sourceKey = sourceKey == null ? "" : sourceKey;
      this.targetKey = targetKey == null ? "" : targetKey;
   }

   public UUID operationId() {
      return this.operationId;
   }

   public ActivityKind kind() {
      return this.kind;
   }

   public String result() {
      return this.result;
   }

   public long gameTime() {
      return this.gameTime;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.operationId);
      tag.putString("kind", this.kind.name());
      tag.putString("result", this.result);
      tag.putLong("t", this.gameTime);
      tag.putString("src", this.sourceKey);
      tag.putString("tgt", this.targetKey);
      return tag;
   }

   public static ActivityOutcomeSummary load(CompoundTag tag) {
      return new ActivityOutcomeSummary(
         tag.getUUID("id"),
         ActivityKind.fromId(tag.getString("kind")),
         tag.getString("result"),
         tag.getLong("t"),
         tag.getString("src"),
         tag.getString("tgt")
      );
   }
}
