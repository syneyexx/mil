package org.millenaire.worldgen.prefill;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

/**
 * Persists spawn-ring prefill progress so restarts can resume CHUNKING/DRAINING.
 */
public final class SpawnRingPrefillSavedData extends SavedData {
   private static final String DATA_NAME = "millenaire_spawn_ring_prefill";

   public enum Phase {
      IDLE,
      CHUNKING,
      DRAINING,
      DONE,
      GAVE_UP
   }

   public static final int MAX_REGION_CELL_OFFSET = 8;

   private Phase phase = Phase.IDLE;
   private int radiusBlocks;
   private int regionIndex;
   /** Cursor 0..8 into the current region's 3x3 centre-chunk cells; absent in older saves (defaults to 0). */
   private int regionCellOffset;
   private int drainTicks;
   @Nullable
   private BlockPos spawnAnchor;
   private final List<Long> regionPacked = new ArrayList<>();

   SpawnRingPrefillSavedData() {
   }

   public static Factory<SpawnRingPrefillSavedData> factory() {
      return new Factory<>(SpawnRingPrefillSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static SpawnRingPrefillSavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      ServerLevel storage = overworld != null ? overworld : level;
      return storage.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public Phase phase() {
      return this.phase;
   }

   public void setPhase(Phase phase) {
      this.phase = phase != null ? phase : Phase.IDLE;
      this.setDirty();
   }

   public int radiusBlocks() {
      return this.radiusBlocks;
   }

   public void setRadiusBlocks(int radiusBlocks) {
      this.radiusBlocks = Math.max(0, radiusBlocks);
      this.setDirty();
   }

   public int regionIndex() {
      return this.regionIndex;
   }

   public void setRegionIndex(int regionIndex) {
      this.regionIndex = Math.max(0, regionIndex);
      this.setDirty();
   }

   public int regionCellOffset() {
      return this.regionCellOffset;
   }

   public void setRegionCellOffset(int regionCellOffset) {
      int clamped = Math.max(0, Math.min(MAX_REGION_CELL_OFFSET, regionCellOffset));
      if (clamped != this.regionCellOffset) {
         this.regionCellOffset = clamped;
         this.setDirty();
      }
   }

   public void incrementRegionIndex() {
      this.regionIndex++;
      this.regionCellOffset = 0;
      this.setDirty();
   }

   public int drainTicks() {
      return this.drainTicks;
   }

   public void incrementDrainTicks() {
      this.drainTicks++;
      this.setDirty();
   }

   @Nullable
   public BlockPos spawnAnchor() {
      return this.spawnAnchor;
   }

   public void setSpawnAnchor(BlockPos spawnAnchor) {
      this.spawnAnchor = spawnAnchor != null ? spawnAnchor.immutable() : null;
      this.setDirty();
   }

   public List<Long> regionPacked() {
      return this.regionPacked;
   }

   public void setRegions(List<Long> packed) {
      this.regionPacked.clear();
      if (packed != null) {
         this.regionPacked.addAll(packed);
      }
      this.regionIndex = 0;
      this.regionCellOffset = 0;
      this.drainTicks = 0;
      this.setDirty();
   }

   public int regionCount() {
      return this.regionPacked.size();
   }

   public boolean isFinished() {
      return this.phase == Phase.DONE || this.phase == Phase.GAVE_UP;
   }

   /** Clears progress so prefill can run again (does not remove existing villages/plans). */
   public void prepareRetry() {
      this.phase = Phase.IDLE;
      this.radiusBlocks = 0;
      this.regionIndex = 0;
      this.regionCellOffset = 0;
      this.drainTicks = 0;
      this.spawnAnchor = null;
      this.regionPacked.clear();
      this.setDirty();
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      tag.putString("phase", this.phase.name());
      tag.putInt("radius", this.radiusBlocks);
      tag.putInt("regionIndex", this.regionIndex);
      tag.putInt("regionCellOffset", this.regionCellOffset);
      tag.putInt("drainTicks", this.drainTicks);
      if (this.spawnAnchor != null) {
         tag.putLong("spawnAnchor", this.spawnAnchor.asLong());
      }
      ListTag regions = new ListTag();
      for (Long packed : this.regionPacked) {
         if (packed != null) {
            regions.add(LongTag.valueOf(packed));
         }
      }
      tag.put("regions", regions);
      return tag;
   }

   static SpawnRingPrefillSavedData load(CompoundTag tag) {
      SpawnRingPrefillSavedData data = new SpawnRingPrefillSavedData();
      try {
         data.phase = Phase.valueOf(tag.getString("phase"));
      } catch (IllegalArgumentException e) {
         data.phase = Phase.IDLE;
      }
      data.radiusBlocks = tag.getInt("radius");
      data.regionIndex = tag.getInt("regionIndex");
      data.regionCellOffset = Math.max(0, Math.min(MAX_REGION_CELL_OFFSET, tag.getInt("regionCellOffset")));
      data.drainTicks = tag.getInt("drainTicks");
      if (tag.contains("spawnAnchor")) {
         data.spawnAnchor = BlockPos.of(tag.getLong("spawnAnchor"));
      }
      if (tag.contains("regions", Tag.TAG_LIST)) {
         ListTag regions = tag.getList("regions", Tag.TAG_LONG);
         for (int i = 0; i < regions.size(); i++) {
            data.regionPacked.add(((LongTag)regions.get(i)).getAsLong());
         }
      }
      return data;
   }
}
