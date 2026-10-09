package org.millenaire.worldgen.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

public final class PlannedRoad {
   private final UUID roadId;
   private final UUID fromBuildingId;
   private final UUID toBuildingId;
   private final List<BlockPos> waypoints;
   private boolean materialized;

   public PlannedRoad(UUID roadId, UUID fromBuildingId, UUID toBuildingId, List<BlockPos> waypoints) {
      this.roadId = roadId;
      this.fromBuildingId = fromBuildingId;
      this.toBuildingId = toBuildingId;
      this.waypoints = new ArrayList<>(waypoints == null ? List.of() : waypoints);
   }

   public UUID roadId() {
      return this.roadId;
   }

   public UUID fromBuildingId() {
      return this.fromBuildingId;
   }

   public UUID toBuildingId() {
      return this.toBuildingId;
   }

   public List<BlockPos> waypoints() {
      return List.copyOf(this.waypoints);
   }

   public boolean materialized() {
      return this.materialized;
   }

   public void setMaterialized(boolean v) {
      this.materialized = v;
   }

   public boolean intersectsChunk(ChunkPos chunk) {
      for (BlockPos p : this.waypoints) {
         if (p.getX() >= chunk.getMinBlockX()
            && p.getX() <= chunk.getMaxBlockX()
            && p.getZ() >= chunk.getMinBlockZ()
            && p.getZ() <= chunk.getMaxBlockZ()) {
            return true;
         }
      }
      return false;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.roadId);
      tag.putUUID("from", this.fromBuildingId);
      tag.putUUID("to", this.toBuildingId);
      ListTag pts = new ListTag();
      for (BlockPos p : this.waypoints) {
         CompoundTag pt = new CompoundTag();
         pt.putInt("x", p.getX());
         pt.putInt("y", p.getY());
         pt.putInt("z", p.getZ());
         pts.add(pt);
      }
      tag.put("waypoints", pts);
      tag.putBoolean("materialized", this.materialized);
      return tag;
   }

   @Nullable
   public static PlannedRoad load(CompoundTag tag) {
      try {
         List<BlockPos> pts = new ArrayList<>();
         ListTag list = tag.getList("waypoints", Tag.TAG_COMPOUND);
         for (int i = 0; i < list.size(); i++) {
            CompoundTag pt = list.getCompound(i);
            pts.add(new BlockPos(pt.getInt("x"), pt.getInt("y"), pt.getInt("z")));
         }
         PlannedRoad road = new PlannedRoad(tag.getUUID("id"), tag.getUUID("from"), tag.getUUID("to"), pts);
         road.materialized = tag.getBoolean("materialized");
         return road;
      } catch (Exception e) {
         return null;
      }
   }
}
