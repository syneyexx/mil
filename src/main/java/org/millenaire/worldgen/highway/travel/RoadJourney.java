package org.millenaire.worldgen.highway.travel;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class RoadJourney {
   public enum Phase {
      EN_ROUTE,
      ARRIVED,
      CANCELLED;

      static Phase fromId(String id) {
         try {
            return Phase.valueOf(id);
         } catch (Exception e) {
            return EN_ROUTE;
         }
      }
   }

   private final UUID journeyId;
   private final UUID routeId;
   private final RoadJourneyKind kind;
   private ResourceLocation villagerTypeId;
   private int waypointIndex;
   private BlockPos logicalPos;
   private Phase phase = Phase.EN_ROUTE;
   private boolean mounted;
   @Nullable
   private UUID entityUuid;
   @Nullable
   private UUID horseUuid;
   @Nullable
   private UUID hostVillageId;
   @Nullable
   private UUID srcVillageId;
   @Nullable
   private UUID destVillageId;
   private long createdAt;
   private long lastAdvanceAt;

   public RoadJourney(UUID journeyId, UUID routeId, RoadJourneyKind kind, ResourceLocation villagerTypeId, BlockPos start) {
      this.journeyId = journeyId;
      this.routeId = routeId;
      this.kind = kind;
      this.villagerTypeId = villagerTypeId;
      this.logicalPos = start;
      this.mounted = kind.mounted();
   }

   public UUID journeyId() {
      return this.journeyId;
   }

   public UUID routeId() {
      return this.routeId;
   }

   public RoadJourneyKind kind() {
      return this.kind;
   }

   public ResourceLocation villagerTypeId() {
      return this.villagerTypeId;
   }

   public void setVillagerTypeId(ResourceLocation id) {
      this.villagerTypeId = id;
   }

   public int waypointIndex() {
      return this.waypointIndex;
   }

   public void setWaypointIndex(int index) {
      this.waypointIndex = Math.max(0, index);
   }

   public BlockPos logicalPos() {
      return this.logicalPos;
   }

   public void setLogicalPos(BlockPos pos) {
      if (pos != null) {
         this.logicalPos = pos;
      }
   }

   public Phase phase() {
      return this.phase;
   }

   public void setPhase(Phase phase) {
      this.phase = phase == null ? Phase.EN_ROUTE : phase;
   }

   public boolean mounted() {
      return this.mounted;
   }

   public void setMounted(boolean mounted) {
      this.mounted = mounted;
   }

   @Nullable
   public UUID entityUuid() {
      return this.entityUuid;
   }

   public void setEntityUuid(@Nullable UUID entityUuid) {
      this.entityUuid = entityUuid;
   }

   @Nullable
   public UUID horseUuid() {
      return this.horseUuid;
   }

   public void setHorseUuid(@Nullable UUID horseUuid) {
      this.horseUuid = horseUuid;
   }

   @Nullable
   public UUID hostVillageId() {
      return this.hostVillageId;
   }

   public void setHostVillageId(@Nullable UUID hostVillageId) {
      this.hostVillageId = hostVillageId;
   }

   @Nullable
   public UUID srcVillageId() {
      return this.srcVillageId;
   }

   public void setSrcVillageId(@Nullable UUID srcVillageId) {
      this.srcVillageId = srcVillageId;
   }

   @Nullable
   public UUID destVillageId() {
      return this.destVillageId;
   }

   public void setDestVillageId(@Nullable UUID destVillageId) {
      this.destVillageId = destVillageId;
   }

   public long createdAt() {
      return this.createdAt;
   }

   public void setCreatedAt(long createdAt) {
      this.createdAt = createdAt;
   }

   public long lastAdvanceAt() {
      return this.lastAdvanceAt;
   }

   public void setLastAdvanceAt(long lastAdvanceAt) {
      this.lastAdvanceAt = lastAdvanceAt;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.journeyId);
      tag.putUUID("route", this.routeId);
      tag.putString("kind", this.kind.name());
      if (this.villagerTypeId != null) {
         tag.putString("type", this.villagerTypeId.toString());
      }
      tag.putInt("wp", this.waypointIndex);
      tag.putInt("x", this.logicalPos.getX());
      tag.putInt("y", this.logicalPos.getY());
      tag.putInt("z", this.logicalPos.getZ());
      tag.putString("phase", this.phase.name());
      tag.putBoolean("mounted", this.mounted);
      tag.putLong("created", this.createdAt);
      tag.putLong("advanced", this.lastAdvanceAt);
      if (this.entityUuid != null) {
         tag.putUUID("entity", this.entityUuid);
      }
      if (this.horseUuid != null) {
         tag.putUUID("horse", this.horseUuid);
      }
      if (this.hostVillageId != null) {
         tag.putUUID("host", this.hostVillageId);
      }
      if (this.srcVillageId != null) {
         tag.putUUID("src", this.srcVillageId);
      }
      if (this.destVillageId != null) {
         tag.putUUID("dest", this.destVillageId);
      }
      return tag;
   }

   public static RoadJourney load(CompoundTag tag) {
      UUID id = tag.getUUID("id");
      UUID route = tag.getUUID("route");
      RoadJourneyKind kind = RoadJourneyKind.fromId(tag.getString("kind"));
      ResourceLocation type = tag.contains("type") ? ResourceLocation.tryParse(tag.getString("type")) : null;
      BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
      RoadJourney journey = new RoadJourney(id, route, kind, type, pos);
      journey.waypointIndex = tag.getInt("wp");
      journey.phase = Phase.fromId(tag.getString("phase"));
      journey.mounted = tag.getBoolean("mounted");
      journey.createdAt = tag.getLong("created");
      journey.lastAdvanceAt = tag.getLong("advanced");
      if (tag.hasUUID("entity")) {
         journey.entityUuid = tag.getUUID("entity");
      }
      if (tag.hasUUID("horse")) {
         journey.horseUuid = tag.getUUID("horse");
      }
      if (tag.hasUUID("host")) {
         journey.hostVillageId = tag.getUUID("host");
      }
      if (tag.hasUUID("src")) {
         journey.srcVillageId = tag.getUUID("src");
      }
      if (tag.hasUUID("dest")) {
         journey.destVillageId = tag.getUUID("dest");
      }
      return journey;
   }
}
