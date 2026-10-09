package org.millenaire.living.knowledge;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/**
 * Structured knowledge record with provenance. Not LLM-generated authority.
 */
public final class NpcKnowledgeItem {
   private final String knowledgeId;
   private final NpcKnowledgeCategory category;
   private final String summary;
   @Nullable
   private final UUID originalSourceNpcId;
   @Nullable
   private final UUID currentSourceNpcId;
   @Nullable
   private final String eventOrRumorId;
   private final long observationTime;
   private final long transferTime;
   private final boolean firsthand;
   private final int hopCount;
   private final long expirationTime;
   private final int trustStatus;

   public NpcKnowledgeItem(
      String knowledgeId,
      NpcKnowledgeCategory category,
      String summary,
      @Nullable UUID originalSourceNpcId,
      @Nullable UUID currentSourceNpcId,
      @Nullable String eventOrRumorId,
      long observationTime,
      long transferTime,
      boolean firsthand,
      int hopCount,
      long expirationTime,
      int trustStatus
   ) {
      this.knowledgeId = knowledgeId;
      this.category = category;
      this.summary = summary == null ? "" : summary;
      this.originalSourceNpcId = originalSourceNpcId;
      this.currentSourceNpcId = currentSourceNpcId;
      this.eventOrRumorId = eventOrRumorId;
      this.observationTime = observationTime;
      this.transferTime = transferTime;
      this.firsthand = firsthand;
      this.hopCount = hopCount;
      this.expirationTime = expirationTime;
      this.trustStatus = trustStatus;
   }

   public String knowledgeId() {
      return this.knowledgeId;
   }

   public NpcKnowledgeCategory category() {
      return this.category;
   }

   public String summary() {
      return this.summary;
   }

   @Nullable
   public UUID originalSourceNpcId() {
      return this.originalSourceNpcId;
   }

   @Nullable
   public UUID currentSourceNpcId() {
      return this.currentSourceNpcId;
   }

   @Nullable
   public String eventOrRumorId() {
      return this.eventOrRumorId;
   }

   public long observationTime() {
      return this.observationTime;
   }

   public long transferTime() {
      return this.transferTime;
   }

   public boolean firsthand() {
      return this.firsthand;
   }

   public int hopCount() {
      return this.hopCount;
   }

   public long expirationTime() {
      return this.expirationTime;
   }

   public int trustStatus() {
      return this.trustStatus;
   }

   public boolean isExpired(long now) {
      return this.expirationTime > 0 && now > this.expirationTime;
   }

   public NpcKnowledgeItem transferTo(UUID newHolderFrom, long now, int maxHops) {
      if (this.hopCount + 1 > maxHops) {
         return null;
      }
      return new NpcKnowledgeItem(
         this.knowledgeId,
         this.category,
         this.summary,
         this.originalSourceNpcId,
         newHolderFrom,
         this.eventOrRumorId,
         this.observationTime,
         now,
         false,
         this.hopCount + 1,
         this.expirationTime,
         Math.max(0, this.trustStatus - 10)
      );
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("id", this.knowledgeId);
      tag.putString("category", this.category.name());
      tag.putString("summary", this.summary);
      if (this.originalSourceNpcId != null) {
         tag.putUUID("original", this.originalSourceNpcId);
      }
      if (this.currentSourceNpcId != null) {
         tag.putUUID("current", this.currentSourceNpcId);
      }
      if (this.eventOrRumorId != null) {
         tag.putString("event", this.eventOrRumorId);
      }
      tag.putLong("observed", this.observationTime);
      tag.putLong("transferred", this.transferTime);
      tag.putBoolean("firsthand", this.firsthand);
      tag.putInt("hops", this.hopCount);
      tag.putLong("expires", this.expirationTime);
      tag.putInt("trust", this.trustStatus);
      return tag;
   }

   @Nullable
   public static NpcKnowledgeItem load(CompoundTag tag) {
      try {
         String id = tag.getString("id");
         if (id.isBlank()) {
            return null;
         }
         NpcKnowledgeCategory category = NpcKnowledgeCategory.valueOf(tag.getString("category"));
         return new NpcKnowledgeItem(
            id,
            category,
            tag.getString("summary"),
            tag.hasUUID("original") ? tag.getUUID("original") : null,
            tag.hasUUID("current") ? tag.getUUID("current") : null,
            tag.contains("event") ? tag.getString("event") : null,
            tag.getLong("observed"),
            tag.getLong("transferred"),
            tag.getBoolean("firsthand"),
            tag.getInt("hops"),
            tag.getLong("expires"),
            tag.getInt("trust")
         );
      } catch (Exception e) {
         return null;
      }
   }
}
