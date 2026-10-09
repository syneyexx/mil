package org.millenaire.worldgen.plan;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;

public final class PlannedBuilding {
   private final UUID buildingId;
   private final ResourceLocation planSetId;
   private final ResourceLocation planId;
   private final String variant;
   private final int level;
   private final String role;
   private final BlockPos origin;
   private final Rotation rotation;
   private final int width;
   private final int depth;
   private final int groundLevel;
   private boolean materialized;
   private boolean chunksComplete;

   public PlannedBuilding(
      UUID buildingId,
      ResourceLocation planSetId,
      ResourceLocation planId,
      String variant,
      int level,
      String role,
      BlockPos origin,
      Rotation rotation,
      int width,
      int depth,
      int groundLevel
   ) {
      this.buildingId = buildingId;
      this.planSetId = planSetId;
      this.planId = planId;
      this.variant = variant == null ? "default" : variant;
      this.level = level;
      this.role = role == null ? "" : role;
      this.origin = origin;
      this.rotation = rotation == null ? Rotation.NONE : rotation;
      this.width = width;
      this.depth = depth;
      this.groundLevel = groundLevel;
   }

   public UUID buildingId() {
      return this.buildingId;
   }

   public ResourceLocation planSetId() {
      return this.planSetId;
   }

   public ResourceLocation planId() {
      return this.planId;
   }

   public String variant() {
      return this.variant;
   }

   public int level() {
      return this.level;
   }

   public String role() {
      return this.role;
   }

   public BlockPos origin() {
      return this.origin;
   }

   public Rotation rotation() {
      return this.rotation;
   }

   public int width() {
      return this.width;
   }

   public int depth() {
      return this.depth;
   }

   public int groundLevel() {
      return this.groundLevel;
   }

   public boolean materialized() {
      return this.materialized;
   }

   public void setMaterialized(boolean v) {
      this.materialized = v;
   }

   public boolean chunksComplete() {
      return this.chunksComplete;
   }

   public void setChunksComplete(boolean v) {
      this.chunksComplete = v;
   }

   public boolean intersectsChunk(ChunkPos chunk) {
      int minX = this.origin.getX();
      int minZ = this.origin.getZ();
      int effW = effectiveWidth();
      int effD = effectiveDepth();
      int maxX = minX + Math.max(effW, 1) - 1;
      int maxZ = minZ + Math.max(effD, 1) - 1;
      int cMinX = chunk.getMinBlockX();
      int cMinZ = chunk.getMinBlockZ();
      int cMaxX = chunk.getMaxBlockX();
      int cMaxZ = chunk.getMaxBlockZ();
      return minX <= cMaxX && maxX >= cMinX && minZ <= cMaxZ && maxZ >= cMinZ;
   }

   /** Footprint width after rotation (blocks). Matches TerrainPreparer. */
   public int effectiveWidth() {
      return this.rotation != Rotation.CLOCKWISE_90 && this.rotation != Rotation.COUNTERCLOCKWISE_90
         ? this.width
         : this.depth;
   }

   /** Footprint depth after rotation (blocks). Matches TerrainPreparer. */
   public int effectiveDepth() {
      return this.rotation != Rotation.CLOCKWISE_90 && this.rotation != Rotation.COUNTERCLOCKWISE_90
         ? this.depth
         : this.width;
   }

   public int minChunkX() {
      return this.origin.getX() >> 4;
   }

   public int maxChunkX() {
      return (this.origin.getX() + Math.max(effectiveWidth(), 1) - 1) >> 4;
   }

   public int minChunkZ() {
      return this.origin.getZ() >> 4;
   }

   public int maxChunkZ() {
      return (this.origin.getZ() + Math.max(effectiveDepth(), 1) - 1) >> 4;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.buildingId);
      if (this.planSetId != null) {
         tag.putString("planSet", this.planSetId.toString());
      }
      if (this.planId != null) {
         tag.putString("plan", this.planId.toString());
      }
      tag.putString("variant", this.variant);
      tag.putInt("level", this.level);
      tag.putString("role", this.role);
      tag.putInt("x", this.origin.getX());
      tag.putInt("y", this.origin.getY());
      tag.putInt("z", this.origin.getZ());
      tag.putString("rot", this.rotation.name());
      tag.putInt("w", this.width);
      tag.putInt("d", this.depth);
      tag.putInt("ground", this.groundLevel);
      tag.putBoolean("materialized", this.materialized);
      tag.putBoolean("chunksComplete", this.chunksComplete);
      return tag;
   }

   @Nullable
   public static PlannedBuilding load(CompoundTag tag) {
      try {
         UUID id = tag.getUUID("id");
         ResourceLocation planSet = tag.contains("planSet") ? ResourceLocation.tryParse(tag.getString("planSet")) : null;
         ResourceLocation plan = tag.contains("plan") ? ResourceLocation.tryParse(tag.getString("plan")) : null;
         Rotation rot = Rotation.NONE;
         try {
            rot = Rotation.valueOf(tag.getString("rot"));
         } catch (Exception ignored) {
         }
         PlannedBuilding b = new PlannedBuilding(
            id,
            planSet,
            plan,
            tag.getString("variant"),
            tag.getInt("level"),
            tag.getString("role"),
            new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
            rot,
            tag.getInt("w"),
            tag.getInt("d"),
            tag.getInt("ground")
         );
         b.materialized = tag.getBoolean("materialized");
         b.chunksComplete = tag.getBoolean("chunksComplete");
         return b;
      } catch (Exception e) {
         return null;
      }
   }
}
