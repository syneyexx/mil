package org.millenaire.living.events;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class LivingEvent {
   public static final int SCHEMA_VERSION = 1;

   private final String eventId;
   private final LivingEventType eventType;
   private final LivingEventSource sourceSystem;
   private final UUID villageId;
   @Nullable
   private final UUID actorId;
   @Nullable
   private final UUID targetId;
   private final long gameTime;
   @Nullable
   private final ResourceLocation dimension;
   private final String details;
   private final String visibilityPolicy;
   private final int schemaVersion;
   private final String dedupeKey;

   public LivingEvent(
      String eventId,
      LivingEventType eventType,
      LivingEventSource sourceSystem,
      UUID villageId,
      @Nullable UUID actorId,
      @Nullable UUID targetId,
      long gameTime,
      @Nullable ResourceLocation dimension,
      String details,
      String visibilityPolicy,
      int schemaVersion,
      String dedupeKey
   ) {
      this.eventId = Objects.requireNonNull(eventId);
      this.eventType = Objects.requireNonNull(eventType);
      this.sourceSystem = Objects.requireNonNull(sourceSystem);
      this.villageId = Objects.requireNonNull(villageId);
      this.actorId = actorId;
      this.targetId = targetId;
      this.gameTime = gameTime;
      this.dimension = dimension;
      this.details = details == null ? "" : details;
      this.visibilityPolicy = visibilityPolicy == null ? "public" : visibilityPolicy;
      this.schemaVersion = schemaVersion;
      this.dedupeKey = dedupeKey == null ? "" : dedupeKey;
   }

   public String eventId() {
      return this.eventId;
   }

   public LivingEventType eventType() {
      return this.eventType;
   }

   public LivingEventSource sourceSystem() {
      return this.sourceSystem;
   }

   public UUID villageId() {
      return this.villageId;
   }

   @Nullable
   public UUID actorId() {
      return this.actorId;
   }

   @Nullable
   public UUID targetId() {
      return this.targetId;
   }

   public long gameTime() {
      return this.gameTime;
   }

   @Nullable
   public ResourceLocation dimension() {
      return this.dimension;
   }

   public String details() {
      return this.details;
   }

   public String visibilityPolicy() {
      return this.visibilityPolicy;
   }

   public int schemaVersion() {
      return this.schemaVersion;
   }

   public String dedupeKey() {
      return this.dedupeKey;
   }

   public boolean isPublic() {
      return !"private".equalsIgnoreCase(this.visibilityPolicy);
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putString("id", this.eventId);
      tag.putString("type", this.eventType.name());
      tag.putString("source", this.sourceSystem.name());
      tag.putUUID("village", this.villageId);
      if (this.actorId != null) {
         tag.putUUID("actor", this.actorId);
      }
      if (this.targetId != null) {
         tag.putUUID("target", this.targetId);
      }
      tag.putLong("gameTime", this.gameTime);
      if (this.dimension != null) {
         tag.putString("dimension", this.dimension.toString());
      }
      tag.putString("details", this.details);
      tag.putString("visibility", this.visibilityPolicy);
      tag.putInt("schema", this.schemaVersion);
      tag.putString("dedupe", this.dedupeKey);
      return tag;
   }

   @Nullable
   public static LivingEvent load(CompoundTag tag) {
      try {
         String id = tag.getString("id");
         if (id.isBlank()) {
            return null;
         }
         LivingEventType type = LivingEventType.valueOf(tag.getString("type"));
         LivingEventSource source = LivingEventSource.valueOf(tag.getString("source"));
         UUID village = tag.getUUID("village");
         UUID actor = tag.hasUUID("actor") ? tag.getUUID("actor") : null;
         UUID target = tag.hasUUID("target") ? tag.getUUID("target") : null;
         long gameTime = tag.getLong("gameTime");
         ResourceLocation dimension = null;
         if (tag.contains("dimension")) {
            dimension = ResourceLocation.tryParse(tag.getString("dimension"));
         }
         return new LivingEvent(
            id,
            type,
            source,
            village,
            actor,
            target,
            gameTime,
            dimension,
            tag.getString("details"),
            tag.getString("visibility"),
            tag.contains("schema") ? tag.getInt("schema") : 1,
            tag.getString("dedupe")
         );
      } catch (Exception ignored) {
         return null;
      }
   }
}
