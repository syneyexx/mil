package org.millenaire.worldgen.plan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;

/**
 * Immutable identity + mutable progress for one natural settlement plan.
 * Planning selects content; materialization writes blocks; activation registers gameplay.
 */
public final class SettlementPlan {
   public static final int SCHEMA_VERSION = WorldgenSeeds.PLANNER_VERSION;

   private final UUID planId;
   private final VillageIdHolder villageId;
   private final SettlementRegionKey region;
   private final long worldSeed;
   private final int plannerVersion;
   private final ResourceLocation cultureId;
   private final ResourceLocation villageTypeId;
   private final BlockPos center;
   private final Rotation orientation;
   private final int radius;
   private final int initialCompletionPercent;
   private final boolean loneBuilding;
   private final @Nullable UUID parentPlanId;
   private final String plannedName;
   private final List<PlannedBuilding> buildings;
   private final List<PlannedRoad> roads;
   private final List<PlannedWall> walls;
   private final List<PlannedHamlet> hamlets;
   private final Set<Long> reservedChunks;
   private final Set<Long> materializedChunks;
   private SettlementPlanStatus status;
   private SettlementFailureReason failureReason;
   private String failureDetail;
   private boolean activated;
   private boolean inventoriesInitialized;

   public SettlementPlan(
      UUID planId,
      UUID villageUuid,
      SettlementRegionKey region,
      long worldSeed,
      ResourceLocation cultureId,
      ResourceLocation villageTypeId,
      BlockPos center,
      Rotation orientation,
      int radius,
      int initialCompletionPercent,
      boolean loneBuilding,
      @Nullable UUID parentPlanId,
      String plannedName
   ) {
      this.planId = planId;
      this.villageId = new VillageIdHolder(villageUuid);
      this.region = region;
      this.worldSeed = worldSeed;
      this.plannerVersion = SCHEMA_VERSION;
      this.cultureId = cultureId;
      this.villageTypeId = villageTypeId;
      this.center = center;
      this.orientation = orientation == null ? Rotation.NONE : orientation;
      this.radius = Math.max(16, radius);
      this.initialCompletionPercent = Math.max(0, Math.min(100, initialCompletionPercent));
      this.loneBuilding = loneBuilding;
      this.parentPlanId = parentPlanId;
      this.plannedName = plannedName == null ? "" : plannedName;
      this.buildings = new ArrayList<>();
      this.roads = new ArrayList<>();
      this.walls = new ArrayList<>();
      this.hamlets = new ArrayList<>();
      this.reservedChunks = new HashSet<>();
      this.materializedChunks = new HashSet<>();
      this.status = SettlementPlanStatus.CANDIDATE;
      this.failureReason = SettlementFailureReason.NONE;
      this.failureDetail = "";
   }

   public UUID planId() {
      return this.planId;
   }

   public UUID villageUuid() {
      return this.villageId.uuid();
   }

   public SettlementRegionKey region() {
      return this.region;
   }

   public long worldSeed() {
      return this.worldSeed;
   }

   public int plannerVersion() {
      return this.plannerVersion;
   }

   public ResourceLocation cultureId() {
      return this.cultureId;
   }

   public ResourceLocation villageTypeId() {
      return this.villageTypeId;
   }

   public BlockPos center() {
      return this.center;
   }

   public Rotation orientation() {
      return this.orientation;
   }

   public int radius() {
      return this.radius;
   }

   public int initialCompletionPercent() {
      return this.initialCompletionPercent;
   }

   public boolean loneBuilding() {
      return this.loneBuilding;
   }

   @Nullable
   public UUID parentPlanId() {
      return this.parentPlanId;
   }

   public String plannedName() {
      return this.plannedName;
   }

   public List<PlannedBuilding> buildings() {
      return this.buildings;
   }

   public List<PlannedRoad> roads() {
      return this.roads;
   }

   public List<PlannedWall> walls() {
      return this.walls;
   }

   public List<PlannedHamlet> hamlets() {
      return this.hamlets;
   }

   public SettlementPlanStatus status() {
      return this.status;
   }

   public boolean trySetStatus(SettlementPlanStatus next) {
      if (this.status.canTransitionTo(next)) {
         this.status = next;
         return true;
      }
      return false;
   }

   public void forceStatus(SettlementPlanStatus status) {
      this.status = status == null ? SettlementPlanStatus.FAILED : status;
   }

   public SettlementFailureReason failureReason() {
      return this.failureReason;
   }

   public String failureDetail() {
      return this.failureDetail;
   }

   public void markFailed(SettlementFailureReason reason, String detail) {
      this.failureReason = reason == null ? SettlementFailureReason.UNKNOWN : reason;
      this.failureDetail = detail == null ? "" : detail;
      this.status = SettlementPlanStatus.FAILED;
   }

   public void markInvalid(SettlementFailureReason reason, String detail) {
      this.failureReason = reason == null ? SettlementFailureReason.UNKNOWN : reason;
      this.failureDetail = detail == null ? "" : detail;
      this.status = SettlementPlanStatus.INVALID;
   }

   public boolean activated() {
      return this.activated;
   }

   public void setActivated(boolean v) {
      this.activated = v;
   }

   public boolean inventoriesInitialized() {
      return this.inventoriesInitialized;
   }

   public void setInventoriesInitialized(boolean v) {
      this.inventoriesInitialized = v;
   }

   public Set<Long> reservedChunks() {
      return this.reservedChunks;
   }

   public Set<Long> materializedChunks() {
      return this.materializedChunks;
   }

   public void reserveFootprintChunks() {
      int minChunkX = (this.center.getX() - this.radius) >> 4;
      int maxChunkX = (this.center.getX() + this.radius) >> 4;
      int minChunkZ = (this.center.getZ() - this.radius) >> 4;
      int maxChunkZ = (this.center.getZ() + this.radius) >> 4;
      for (int cx = minChunkX; cx <= maxChunkX; cx++) {
         for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            this.reservedChunks.add(ChunkPos.asLong(cx, cz));
         }
      }
   }

   public boolean intersectsChunk(ChunkPos chunk) {
      if (this.reservedChunks.contains(chunk.toLong())) {
         return true;
      }
      for (PlannedBuilding b : this.buildings) {
         if (b.intersectsChunk(chunk)) {
            return true;
         }
      }
      return false;
   }

   public boolean allBuildingsMaterialized() {
      if (this.buildings.isEmpty()) {
         return false;
      }
      for (PlannedBuilding b : this.buildings) {
         if (!b.materialized()) {
            return false;
         }
      }
      return true;
   }

   public boolean hasMandatoryCentre() {
      for (PlannedBuilding b : this.buildings) {
         String role = b.role();
         if ("centre".equals(role) || "townhall".equals(role)) {
            return true;
         }
      }
      return this.loneBuilding && !this.buildings.isEmpty();
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putInt("schema", SCHEMA_VERSION);
      tag.putUUID("planId", this.planId);
      tag.putUUID("villageId", this.villageId.uuid());
      tag.putInt("rx", this.region.regionX());
      tag.putInt("rz", this.region.regionZ());
      tag.putLong("worldSeed", this.worldSeed);
      tag.putInt("plannerVersion", this.plannerVersion);
      if (this.cultureId != null) {
         tag.putString("culture", this.cultureId.toString());
      }
      if (this.villageTypeId != null) {
         tag.putString("villageType", this.villageTypeId.toString());
      }
      tag.putInt("cx", this.center.getX());
      tag.putInt("cy", this.center.getY());
      tag.putInt("cz", this.center.getZ());
      tag.putString("orient", this.orientation.name());
      tag.putInt("radius", this.radius);
      tag.putInt("completion", this.initialCompletionPercent);
      tag.putBoolean("lone", this.loneBuilding);
      if (this.parentPlanId != null) {
         tag.putUUID("parent", this.parentPlanId);
      }
      tag.putString("name", this.plannedName);
      tag.putString("status", this.status.name());
      tag.putString("failReason", this.failureReason.name());
      tag.putString("failDetail", this.failureDetail);
      tag.putBoolean("activated", this.activated);
      tag.putBoolean("inventories", this.inventoriesInitialized);

      ListTag buildingsTag = new ListTag();
      for (PlannedBuilding b : this.buildings) {
         buildingsTag.add(b.save());
      }
      tag.put("buildings", buildingsTag);

      ListTag roadsTag = new ListTag();
      for (PlannedRoad r : this.roads) {
         roadsTag.add(r.save());
      }
      tag.put("roads", roadsTag);

      ListTag wallsTag = new ListTag();
      for (PlannedWall w : this.walls) {
         wallsTag.add(w.save());
      }
      tag.put("walls", wallsTag);

      ListTag hamletsTag = new ListTag();
      for (PlannedHamlet h : this.hamlets) {
         hamletsTag.add(h.save());
      }
      tag.put("hamlets", hamletsTag);

      ListTag reserved = new ListTag();
      for (Long packed : this.reservedChunks) {
         reserved.add(LongTag.valueOf(packed));
      }
      tag.put("reservedChunks", reserved);

      ListTag mats = new ListTag();
      for (Long packed : this.materializedChunks) {
         mats.add(LongTag.valueOf(packed));
      }
      tag.put("materializedChunks", mats);
      return tag;
   }

   @Nullable
   public static SettlementPlan load(CompoundTag tag) {
      try {
         Rotation orient = Rotation.NONE;
         try {
            orient = Rotation.valueOf(tag.getString("orient"));
         } catch (Exception ignored) {
         }
         SettlementPlan plan = new SettlementPlan(
            tag.getUUID("planId"),
            tag.getUUID("villageId"),
            new SettlementRegionKey(tag.getInt("rx"), tag.getInt("rz")),
            tag.getLong("worldSeed"),
            tag.contains("culture") ? ResourceLocation.tryParse(tag.getString("culture")) : null,
            tag.contains("villageType") ? ResourceLocation.tryParse(tag.getString("villageType")) : null,
            new BlockPos(tag.getInt("cx"), tag.getInt("cy"), tag.getInt("cz")),
            orient,
            tag.getInt("radius"),
            tag.getInt("completion"),
            tag.getBoolean("lone"),
            tag.hasUUID("parent") ? tag.getUUID("parent") : null,
            tag.getString("name")
         );
         try {
            plan.status = SettlementPlanStatus.valueOf(tag.getString("status"));
         } catch (Exception ignored) {
         }
         try {
            plan.failureReason = SettlementFailureReason.valueOf(tag.getString("failReason"));
         } catch (Exception ignored) {
         }
         plan.failureDetail = tag.getString("failDetail");
         plan.activated = tag.getBoolean("activated");
         plan.inventoriesInitialized = tag.getBoolean("inventories");

         ListTag buildingsTag = tag.getList("buildings", Tag.TAG_COMPOUND);
         for (int i = 0; i < buildingsTag.size(); i++) {
            PlannedBuilding b = PlannedBuilding.load(buildingsTag.getCompound(i));
            if (b != null) {
               plan.buildings.add(b);
            }
         }
         ListTag roadsTag = tag.getList("roads", Tag.TAG_COMPOUND);
         for (int i = 0; i < roadsTag.size(); i++) {
            PlannedRoad r = PlannedRoad.load(roadsTag.getCompound(i));
            if (r != null) {
               plan.roads.add(r);
            }
         }
         ListTag wallsTag = tag.getList("walls", Tag.TAG_COMPOUND);
         for (int i = 0; i < wallsTag.size(); i++) {
            PlannedWall w = PlannedWall.load(wallsTag.getCompound(i));
            if (w != null) {
               plan.walls.add(w);
            }
         }
         ListTag hamletsTag = tag.getList("hamlets", Tag.TAG_COMPOUND);
         for (int i = 0; i < hamletsTag.size(); i++) {
            PlannedHamlet h = PlannedHamlet.load(hamletsTag.getCompound(i));
            if (h != null) {
               plan.hamlets.add(h);
            }
         }
         ListTag reserved = tag.getList("reservedChunks", Tag.TAG_LONG);
         for (int i = 0; i < reserved.size(); i++) {
            plan.reservedChunks.add(((LongTag)reserved.get(i)).getAsLong());
         }
         ListTag mats = tag.getList("materializedChunks", Tag.TAG_LONG);
         for (int i = 0; i < mats.size(); i++) {
            plan.materializedChunks.add(((LongTag)mats.get(i)).getAsLong());
         }
         return plan;
      } catch (Exception e) {
         return null;
      }
   }

   /** Thin holder so VillageId package need not be imported into every plan consumer. */
   private record VillageIdHolder(UUID uuid) {
   }
}
