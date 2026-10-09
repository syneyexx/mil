package org.millenaire.worldgen.materialize;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Per-plan, per-chunk placement progress. A chunk is only fully complete when every
 * intersecting building/wall/road unit for that chunk has finished all phases.
 * Terrain prep is tracked separately so resumes never re-clear already prepared footprints.
 */
public final class ChunkMaterializationProgress {
   public enum Status {
      PENDING,
      IN_PROGRESS,
      COMPLETE,
      BLOCKED,
      FAILED
   }

   private final long chunkPacked;
   private final Set<UUID> terrainPreparedBuildings = new HashSet<>();
   private final Set<UUID> placementCompleteBuildings = new HashSet<>();
   private final Set<UUID> terrainPreparedWalls = new HashSet<>();
   private final Set<UUID> placementCompleteWalls = new HashSet<>();
   private final Set<UUID> roadsCompleteInChunk = new HashSet<>();
   private int placementCursor;
   private Status status = Status.PENDING;
   private String blockReason = "";
   private boolean legacyAmbiguous;

   public ChunkMaterializationProgress(long chunkPacked) {
      this.chunkPacked = chunkPacked;
   }

   public long chunkPacked() {
      return this.chunkPacked;
   }

   public Status status() {
      return this.status;
   }

   public void setStatus(Status status) {
      this.status = status == null ? Status.PENDING : status;
   }

   public String blockReason() {
      return this.blockReason;
   }

   public void setBlockReason(String reason) {
      this.blockReason = reason == null ? "" : reason;
   }

   public boolean legacyAmbiguous() {
      return this.legacyAmbiguous;
   }

   public void setLegacyAmbiguous(boolean v) {
      this.legacyAmbiguous = v;
   }

   public int placementCursor() {
      return this.placementCursor;
   }

   public void setPlacementCursor(int cursor) {
      this.placementCursor = Math.max(0, cursor);
   }

   public Set<UUID> terrainPreparedBuildings() {
      return this.terrainPreparedBuildings;
   }

   public Set<UUID> placementCompleteBuildings() {
      return this.placementCompleteBuildings;
   }

   public Set<UUID> terrainPreparedWalls() {
      return this.terrainPreparedWalls;
   }

   public Set<UUID> placementCompleteWalls() {
      return this.placementCompleteWalls;
   }

   public Set<UUID> roadsCompleteInChunk() {
      return this.roadsCompleteInChunk;
   }

   public boolean isTerrainPreparedBuilding(UUID id) {
      return id != null && this.terrainPreparedBuildings.contains(id);
   }

   public boolean isPlacementCompleteBuilding(UUID id) {
      return id != null && this.placementCompleteBuildings.contains(id);
   }

   public boolean isTerrainPreparedWall(UUID id) {
      return id != null && this.terrainPreparedWalls.contains(id);
   }

   public boolean isPlacementCompleteWall(UUID id) {
      return id != null && this.placementCompleteWalls.contains(id);
   }

   public boolean isRoadComplete(UUID id) {
      return id != null && this.roadsCompleteInChunk.contains(id);
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putLong("chunk", this.chunkPacked);
      tag.putString("status", this.status.name());
      tag.putString("blockReason", this.blockReason);
      tag.putInt("cursor", this.placementCursor);
      tag.putBoolean("legacyAmbiguous", this.legacyAmbiguous);
      tag.put("terrainBuildings", uuidList(this.terrainPreparedBuildings));
      tag.put("placeBuildings", uuidList(this.placementCompleteBuildings));
      tag.put("terrainWalls", uuidList(this.terrainPreparedWalls));
      tag.put("placeWalls", uuidList(this.placementCompleteWalls));
      tag.put("roads", uuidList(this.roadsCompleteInChunk));
      return tag;
   }

   @Nullable
   public static ChunkMaterializationProgress load(CompoundTag tag) {
      if (tag == null || !tag.contains("chunk")) {
         return null;
      }
      ChunkMaterializationProgress progress = new ChunkMaterializationProgress(tag.getLong("chunk"));
      try {
         progress.status = Status.valueOf(tag.getString("status"));
      } catch (Exception ignored) {
         progress.status = Status.PENDING;
      }
      progress.blockReason = tag.getString("blockReason");
      progress.placementCursor = tag.getInt("cursor");
      progress.legacyAmbiguous = tag.getBoolean("legacyAmbiguous");
      readUuids(tag.getList("terrainBuildings", Tag.TAG_STRING), progress.terrainPreparedBuildings);
      readUuids(tag.getList("placeBuildings", Tag.TAG_STRING), progress.placementCompleteBuildings);
      readUuids(tag.getList("terrainWalls", Tag.TAG_STRING), progress.terrainPreparedWalls);
      readUuids(tag.getList("placeWalls", Tag.TAG_STRING), progress.placementCompleteWalls);
      readUuids(tag.getList("roads", Tag.TAG_STRING), progress.roadsCompleteInChunk);
      return progress;
   }

   private static ListTag uuidList(Set<UUID> ids) {
      ListTag list = new ListTag();
      for (UUID id : ids) {
         list.add(StringTag.valueOf(id.toString()));
      }
      return list;
   }

   private static void readUuids(ListTag list, Set<UUID> dest) {
      for (int i = 0; i < list.size(); i++) {
         try {
            dest.add(UUID.fromString(list.getString(i)));
         } catch (Exception ignored) {
         }
      }
   }
}
