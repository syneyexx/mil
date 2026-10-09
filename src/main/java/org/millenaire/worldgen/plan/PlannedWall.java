package org.millenaire.worldgen.plan;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

public final class PlannedWall {
   private final UUID segmentId;
   private final ResourceLocation wallTypeId;
   private final ResourceLocation planSetId;
   private final ResourceLocation planId;
   private final BlockPos origin;
   private final Rotation rotation;
   private final boolean gate;
   private final int width;
   private final int depth;
   private boolean materialized;

   public PlannedWall(
      UUID segmentId,
      ResourceLocation wallTypeId,
      ResourceLocation planSetId,
      ResourceLocation planId,
      BlockPos origin,
      Rotation rotation,
      boolean gate,
      int width,
      int depth
   ) {
      this.segmentId = segmentId;
      this.wallTypeId = wallTypeId;
      this.planSetId = planSetId;
      this.planId = planId;
      this.origin = origin;
      this.rotation = rotation == null ? Rotation.NONE : rotation;
      this.gate = gate;
      this.width = width;
      this.depth = depth;
   }

   public UUID segmentId() {
      return this.segmentId;
   }

   public ResourceLocation wallTypeId() {
      return this.wallTypeId;
   }

   public ResourceLocation planSetId() {
      return this.planSetId;
   }

   public ResourceLocation planId() {
      return this.planId;
   }

   public BlockPos origin() {
      return this.origin;
   }

   public Rotation rotation() {
      return this.rotation;
   }

   public boolean gate() {
      return this.gate;
   }

   public int width() {
      return this.width;
   }

   public int depth() {
      return this.depth;
   }

   public boolean materialized() {
      return this.materialized;
   }

   public void setMaterialized(boolean v) {
      this.materialized = v;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.segmentId);
      if (this.wallTypeId != null) {
         tag.putString("wallType", this.wallTypeId.toString());
      }
      if (this.planSetId != null) {
         tag.putString("planSet", this.planSetId.toString());
      }
      if (this.planId != null) {
         tag.putString("plan", this.planId.toString());
      }
      tag.putInt("x", this.origin.getX());
      tag.putInt("y", this.origin.getY());
      tag.putInt("z", this.origin.getZ());
      tag.putString("rot", this.rotation.name());
      tag.putBoolean("gate", this.gate);
      tag.putInt("w", this.width);
      tag.putInt("d", this.depth);
      tag.putBoolean("materialized", this.materialized);
      return tag;
   }

   @Nullable
   public static PlannedWall load(CompoundTag tag) {
      try {
         Rotation rot = Rotation.NONE;
         try {
            rot = Rotation.valueOf(tag.getString("rot"));
         } catch (Exception ignored) {
         }
         PlannedWall wall = new PlannedWall(
            tag.getUUID("id"),
            tag.contains("wallType") ? ResourceLocation.tryParse(tag.getString("wallType")) : null,
            tag.contains("planSet") ? ResourceLocation.tryParse(tag.getString("planSet")) : null,
            tag.contains("plan") ? ResourceLocation.tryParse(tag.getString("plan")) : null,
            new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
            rot,
            tag.getBoolean("gate"),
            tag.getInt("w"),
            tag.getInt("d")
         );
         wall.materialized = tag.getBoolean("materialized");
         return wall;
      } catch (Exception e) {
         return null;
      }
   }
}
