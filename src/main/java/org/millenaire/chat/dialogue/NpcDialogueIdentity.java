package org.millenaire.chat.dialogue;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Stable identity for dialogue memory. Entity network ids are never used for persistence.
 */
public final class NpcDialogueIdentity {
   private final UUID entityUuid;
   private final ResourceLocation entityTypeId;
   private final ResourceKey<Level> dimension;
   private final boolean persistent;

   public NpcDialogueIdentity(UUID entityUuid, ResourceLocation entityTypeId, ResourceKey<Level> dimension, boolean persistent) {
      this.entityUuid = Objects.requireNonNull(entityUuid, "entityUuid");
      this.entityTypeId = Objects.requireNonNull(entityTypeId, "entityTypeId");
      this.dimension = Objects.requireNonNull(dimension, "dimension");
      this.persistent = persistent;
   }

   public static NpcDialogueIdentity from(Entity entity, boolean persistent) {
      ResourceLocation typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
      if (typeId == null) {
         typeId = ResourceLocation.fromNamespaceAndPath("minecraft", "unknown");
      }
      return new NpcDialogueIdentity(entity.getUUID(), typeId, entity.level().dimension(), persistent);
   }

   public UUID entityUuid() {
      return this.entityUuid;
   }

   public ResourceLocation entityTypeId() {
      return this.entityTypeId;
   }

   public ResourceKey<Level> dimension() {
      return this.dimension;
   }

   public boolean persistent() {
      return this.persistent;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      }
      if (!(o instanceof NpcDialogueIdentity other)) {
         return false;
      }
      return this.entityUuid.equals(other.entityUuid);
   }

   @Override
   public int hashCode() {
      return this.entityUuid.hashCode();
   }
}
