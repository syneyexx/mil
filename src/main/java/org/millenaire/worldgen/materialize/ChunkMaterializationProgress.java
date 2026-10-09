package org.millenaire.worldgen.materialize;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Per-plan, per-chunk placement progress. Every concrete work unit (building, wall, road)
 * keeps independent cursors so budget exhaustion never restarts another unit from scratch
 * and never reuses a shared cursor across different placement lists.
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
   /** @deprecated schema v2 shared cursor — migrated into per-building cursors on load */
   private int legacySharedCursor;
   private final Map<UUID, Integer> buildingPlacementCursors = new HashMap<>();
   private final Map<UUID, Integer> wallPlacementCursors = new HashMap<>();
   private final Map<UUID, Integer> roadWaypointCursors = new HashMap<>();
   private final Map<UUID, Integer> terrainBuildingCursors = new HashMap<>();
   private final Map<UUID, Integer> terrainWallCursors = new HashMap<>();
   private final Map<UUID, Integer> bridgeSupportCursors = new HashMap<>();
   private final Map<UUID, Integer> bridgeDeckHeights = new HashMap<>();
   private UUID activeBuildingId;
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

   /** Prefer {@link #buildingPlacementCursor(UUID)}. Kept for callers still using the shared API. */
   public int placementCursor() {
      if (this.activeBuildingId != null) {
         return buildingPlacementCursor(this.activeBuildingId);
      }
      return this.legacySharedCursor;
   }

   public void setPlacementCursor(int cursor) {
      if (this.activeBuildingId != null) {
         setBuildingPlacementCursor(this.activeBuildingId, cursor);
         return;
      }
      this.legacySharedCursor = Math.max(0, cursor);
   }

   public void setActiveBuilding(@Nullable UUID buildingId) {
      this.activeBuildingId = buildingId;
   }

   public int buildingPlacementCursor(UUID id) {
      return id == null ? 0 : this.buildingPlacementCursors.getOrDefault(id, 0);
   }

   public void setBuildingPlacementCursor(UUID id, int cursor) {
      if (id == null) {
         return;
      }
      if (cursor <= 0) {
         this.buildingPlacementCursors.remove(id);
      } else {
         this.buildingPlacementCursors.put(id, cursor);
      }
   }

   public void clearBuildingPlacementCursor(UUID id) {
      if (id != null) {
         this.buildingPlacementCursors.remove(id);
      }
   }

   public int wallPlacementCursor(UUID id) {
      return id == null ? 0 : this.wallPlacementCursors.getOrDefault(id, 0);
   }

   public void setWallPlacementCursor(UUID id, int cursor) {
      if (id == null) {
         return;
      }
      if (cursor <= 0) {
         this.wallPlacementCursors.remove(id);
      } else {
         this.wallPlacementCursors.put(id, cursor);
      }
   }

   public void clearWallPlacementCursor(UUID id) {
      if (id != null) {
         this.wallPlacementCursors.remove(id);
      }
   }

   public int roadWaypointCursor(UUID id) {
      return id == null ? 0 : this.roadWaypointCursors.getOrDefault(id, 0);
   }

   public void setRoadWaypointCursor(UUID id, int cursor) {
      if (id == null) {
         return;
      }
      if (cursor <= 0) {
         this.roadWaypointCursors.remove(id);
      } else {
         this.roadWaypointCursors.put(id, cursor);
      }
   }

   public void clearRoadWaypointCursor(UUID id) {
      if (id != null) {
         this.roadWaypointCursors.remove(id);
      }
   }

   public int terrainBuildingCursor(UUID id) {
      return id == null ? 0 : this.terrainBuildingCursors.getOrDefault(id, 0);
   }

   public void setTerrainBuildingCursor(UUID id, int cursor) {
      if (id == null) {
         return;
      }
      if (cursor <= 0) {
         this.terrainBuildingCursors.remove(id);
      } else {
         this.terrainBuildingCursors.put(id, cursor);
      }
   }

   public void clearTerrainBuildingCursor(UUID id) {
      if (id != null) {
         this.terrainBuildingCursors.remove(id);
      }
   }

   public int terrainWallCursor(UUID id) {
      return id == null ? 0 : this.terrainWallCursors.getOrDefault(id, 0);
   }

   public void setTerrainWallCursor(UUID id, int cursor) {
      if (id == null) {
         return;
      }
      if (cursor <= 0) {
         this.terrainWallCursors.remove(id);
      } else {
         this.terrainWallCursors.put(id, cursor);
      }
   }

   public void clearTerrainWallCursor(UUID id) {
      if (id != null) {
         this.terrainWallCursors.remove(id);
      }
   }

   public int bridgeSupportCursor(UUID key) {
      return key == null ? 0 : this.bridgeSupportCursors.getOrDefault(key, 0);
   }

   public void setBridgeSupportCursor(UUID key, int cursor) {
      if (key == null) {
         return;
      }
      if (cursor <= 0) {
         this.bridgeSupportCursors.remove(key);
      } else {
         this.bridgeSupportCursors.put(key, cursor);
      }
   }

   public void clearBridgeSupportCursor(UUID key) {
      if (key != null) {
         this.bridgeSupportCursors.remove(key);
         this.bridgeDeckHeights.remove(key);
      }
   }

   public int bridgeDeckHeight(UUID key, int fallback) {
      return key == null ? fallback : this.bridgeDeckHeights.getOrDefault(key, fallback);
   }

   public void setBridgeDeckHeight(UUID key, int height) {
      if (key != null) {
         this.bridgeDeckHeights.put(key, height);
      }
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
      tag.putInt("cursor", this.legacySharedCursor);
      tag.putBoolean("legacyAmbiguous", this.legacyAmbiguous);
      tag.put("terrainBuildings", uuidList(this.terrainPreparedBuildings));
      tag.put("placeBuildings", uuidList(this.placementCompleteBuildings));
      tag.put("terrainWalls", uuidList(this.terrainPreparedWalls));
      tag.put("placeWalls", uuidList(this.placementCompleteWalls));
      tag.put("roads", uuidList(this.roadsCompleteInChunk));
      tag.put("buildingCursors", intMap(this.buildingPlacementCursors));
      tag.put("wallCursors", intMap(this.wallPlacementCursors));
      tag.put("roadCursors", intMap(this.roadWaypointCursors));
      tag.put("terrainBuildingCursors", intMap(this.terrainBuildingCursors));
      tag.put("terrainWallCursors", intMap(this.terrainWallCursors));
      tag.put("bridgeSupportCursors", intMap(this.bridgeSupportCursors));
      tag.put("bridgeDeckHeights", intMap(this.bridgeDeckHeights));
      if (this.activeBuildingId != null) {
         tag.putUUID("activeBuilding", this.activeBuildingId);
      }
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
      progress.legacySharedCursor = tag.getInt("cursor");
      progress.legacyAmbiguous = tag.getBoolean("legacyAmbiguous");
      readUuids(tag.getList("terrainBuildings", Tag.TAG_STRING), progress.terrainPreparedBuildings);
      readUuids(tag.getList("placeBuildings", Tag.TAG_STRING), progress.placementCompleteBuildings);
      readUuids(tag.getList("terrainWalls", Tag.TAG_STRING), progress.terrainPreparedWalls);
      readUuids(tag.getList("placeWalls", Tag.TAG_STRING), progress.placementCompleteWalls);
      readUuids(tag.getList("roads", Tag.TAG_STRING), progress.roadsCompleteInChunk);
      readIntMap(tag.getCompound("buildingCursors"), progress.buildingPlacementCursors);
      readIntMap(tag.getCompound("wallCursors"), progress.wallPlacementCursors);
      readIntMap(tag.getCompound("roadCursors"), progress.roadWaypointCursors);
      readIntMap(tag.getCompound("terrainBuildingCursors"), progress.terrainBuildingCursors);
      readIntMap(tag.getCompound("terrainWallCursors"), progress.terrainWallCursors);
      readIntMap(tag.getCompound("bridgeSupportCursors"), progress.bridgeSupportCursors);
      readIntMap(tag.getCompound("bridgeDeckHeights"), progress.bridgeDeckHeights);
      if (tag.hasUUID("activeBuilding")) {
         progress.activeBuildingId = tag.getUUID("activeBuilding");
      }
      // Migrate schema-v2 shared cursor into the active / sole incomplete building if known.
      if (progress.buildingPlacementCursors.isEmpty() && progress.legacySharedCursor > 0 && progress.activeBuildingId != null) {
         progress.buildingPlacementCursors.put(progress.activeBuildingId, progress.legacySharedCursor);
      }
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

   private static CompoundTag intMap(Map<UUID, Integer> map) {
      CompoundTag tag = new CompoundTag();
      for (Map.Entry<UUID, Integer> e : map.entrySet()) {
         tag.putInt(e.getKey().toString(), e.getValue());
      }
      return tag;
   }

   private static void readIntMap(CompoundTag tag, Map<UUID, Integer> dest) {
      if (tag == null) {
         return;
      }
      for (String key : tag.getAllKeys()) {
         try {
            dest.put(UUID.fromString(key), tag.getInt(key));
         } catch (Exception ignored) {
         }
      }
   }
}
