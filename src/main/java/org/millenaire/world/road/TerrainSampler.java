package org.millenaire.world.road;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Samples height/biome without forcing chunk loads. Prefer real chunk data when present;
 * otherwise use ChunkGenerator estimates with a per-job RandomState snapshot from the level.
 */
public final class TerrainSampler {
   private final ServerLevel level;
   private final RandomState randomState;
   private final Long2IntOpenHashMap heightCache = new Long2IntOpenHashMap(4096);
   private final Long2IntOpenHashMap flagsCache = new Long2IntOpenHashMap(4096);

   public static final int FLAG_WATER = 1;
   public static final int FLAG_DEEP_OCEAN = 2;
   public static final int FLAG_LAVA = 4;
   public static final int FLAG_FOREST = 8;
   public static final int FLAG_MOUNTAIN = 16;
   public static final int FLAG_STRUCTURE = 32;

   public TerrainSampler(ServerLevel level) {
      this.level = level;
      this.randomState = level.getChunkSource().randomState();
      this.heightCache.defaultReturnValue(Integer.MIN_VALUE);
      this.flagsCache.defaultReturnValue(0);
   }

   public int height(int x, int z) {
      long key = ChunkPos.asLong(x, z);
      int cached = this.heightCache.get(key);
      if (cached != Integer.MIN_VALUE) {
         return cached;
      }
      int h;
      ChunkAccess chunk = getLoadedChunk(x, z);
      if (chunk != null) {
         h = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x & 15, z & 15);
      } else {
         h = this.level
            .getChunkSource()
            .getGenerator()
            .getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, this.level, this.randomState);
      }
      this.heightCache.put(key, h);
      return h;
   }

   public int flags(int x, int z) {
      long key = ChunkPos.asLong(x, z);
      if (this.flagsCache.containsKey(key)) {
         return this.flagsCache.get(key);
      }
      int flags = 0;
      int y = height(x, z);
      Holder<Biome> biome = sampleBiome(x, y, z);
      if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)) {
         flags |= FLAG_DEEP_OCEAN;
      }
      if (biome.is(BiomeTags.IS_RIVER)) {
         flags |= FLAG_WATER;
      }
      if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_JUNGLE)) {
         flags |= FLAG_FOREST;
      }
      if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) {
         flags |= FLAG_MOUNTAIN;
      }
      ChunkAccess chunk = getLoadedChunk(x, z);
      if (chunk instanceof LevelChunk levelChunk) {
         BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
         var state = levelChunk.getBlockState(pos);
         if (!state.getFluidState().isEmpty()) {
            flags |= FLAG_WATER;
            if (state.getFluidState().getType().toString().contains("lava")) {
               flags |= FLAG_LAVA;
            }
         }
      }
      this.flagsCache.put(key, flags);
      return flags;
   }

   public void markStructureBox(BoundingBox box, int buffer) {
      int minX = box.minX() - buffer;
      int maxX = box.maxX() + buffer;
      int minZ = box.minZ() - buffer;
      int maxZ = box.maxZ() + buffer;
      for (int x = minX; x <= maxX; x += 4) {
         for (int z = minZ; z <= maxZ; z += 4) {
            long key = ChunkPos.asLong(x, z);
            int f = this.flagsCache.get(key);
            this.flagsCache.put(key, f | FLAG_STRUCTURE);
         }
      }
   }

   private Holder<Biome> sampleBiome(int x, int y, int z) {
      return this.level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(x >> 2, y >> 2, z >> 2, this.randomState.sampler());
   }

   @Nullable
   private ChunkAccess getLoadedChunk(int x, int z) {
      ChunkPos pos = new ChunkPos(x >> 4, z >> 4);
      if (!this.level.hasChunk(pos.x, pos.z)) {
         return null;
      }
      return this.level.getChunk(pos.x, pos.z);
   }

   public void clear() {
      this.heightCache.clear();
      this.flagsCache.clear();
   }
}
