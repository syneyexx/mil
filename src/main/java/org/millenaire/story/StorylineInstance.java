package org.millenaire.story;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

public final class StorylineInstance {
   private final UUID id;
   private final UUID villageId;
   private final String kind;
   private final String summary;
   private final long startedGameTime;
   private final long expiresGameTime;
   @Nullable
   private final UUID needId;
   @Nullable
   private final UUID contractId;
   private boolean finished;
   private String outcome = "";

   public StorylineInstance(
      UUID id,
      UUID villageId,
      String kind,
      String summary,
      long startedGameTime,
      long expiresGameTime,
      @Nullable UUID needId,
      @Nullable UUID contractId
   ) {
      this.id = id;
      this.villageId = villageId;
      this.kind = kind;
      this.summary = summary;
      this.startedGameTime = startedGameTime;
      this.expiresGameTime = expiresGameTime;
      this.needId = needId;
      this.contractId = contractId;
   }

   public UUID id() {
      return this.id;
   }

   public UUID villageId() {
      return this.villageId;
   }

   public String kind() {
      return this.kind;
   }

   public String summary() {
      return this.summary;
   }

   public long startedGameTime() {
      return this.startedGameTime;
   }

   public long expiresGameTime() {
      return this.expiresGameTime;
   }

   public boolean finished() {
      return this.finished;
   }

   public void finish(String outcome) {
      this.finished = true;
      this.outcome = outcome == null ? "" : outcome;
   }

   public String outcome() {
      return this.outcome;
   }

   public CompoundTag save(HolderLookup.Provider registries) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.id);
      tag.putUUID("village", this.villageId);
      tag.putString("kind", this.kind);
      tag.putString("summary", this.summary);
      tag.putLong("started", this.startedGameTime);
      tag.putLong("expires", this.expiresGameTime);
      if (this.needId != null) {
         tag.putUUID("need", this.needId);
      }
      if (this.contractId != null) {
         tag.putUUID("contract", this.contractId);
      }
      tag.putBoolean("finished", this.finished);
      tag.putString("outcome", this.outcome);
      return tag;
   }

   public static StorylineInstance load(CompoundTag tag, HolderLookup.Provider registries) {
      StorylineInstance s = new StorylineInstance(
         tag.getUUID("id"),
         tag.getUUID("village"),
         tag.getString("kind"),
         tag.getString("summary"),
         tag.getLong("started"),
         tag.getLong("expires"),
         tag.hasUUID("need") ? tag.getUUID("need") : null,
         tag.hasUUID("contract") ? tag.getUUID("contract") : null
      );
      s.finished = tag.getBoolean("finished");
      s.outcome = tag.contains("outcome") ? tag.getString("outcome") : "";
      return s;
   }
}
