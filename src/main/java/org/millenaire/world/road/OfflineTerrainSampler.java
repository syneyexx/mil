package org.millenaire.world.road;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Immutable-input terrain sampler for route workers: generator estimates only.
 * Captured on the server thread; workers never touch {@link ServerLevel} or loaded chunks.
 */
public final class OfflineTerrainSampler implements TerrainView {
   private final ChunkGenerator generator;
   private final BiomeSource biomeSource;
   private final RandomState randomState;
   private final LevelHeightAccessor heightAccessor;
   private final Long2IntOpenHashMap heightCache = new Long2IntOpenHashMap(4096);
   private final Long2IntOpenHashMap flagsCache = new Long2IntOpenHashMap(4096);

   public static final int FLAG_WATER = TerrainSampler.FLAG_WATER;
   public static final int FLAG_DEEP_OCEAN = TerrainSampler.FLAG_DEEP_OCEAN;
   public static final int FLAG_LAVA = TerrainSampler.FLAG_LAVA;
   public static final int FLAG_FOREST = TerrainSampler.FLAG_FOREST;
   public static final int FLAG_MOUNTAIN = TerrainSampler.FLAG_MOUNTAIN;
   public static final int FLAG_STRUCTURE = TerrainSampler.FLAG_STRUCTURE;

   private OfflineTerrainSampler(
      ChunkGenerator generator,
      BiomeSource biomeSource,
      RandomState randomState,
      LevelHeightAccessor heightAccessor
   ) {
      this.generator = generator;
      this.biomeSource = biomeSource;
      this.randomState = randomState;
      this.heightAccessor = heightAccessor;
      this.heightCache.defaultReturnValue(Integer.MIN_VALUE);
      this.flagsCache.defaultReturnValue(0);
   }

   /** Capture generator references on the server thread before submitting a plan job. */
   public static OfflineTerrainSampler capture(ServerLevel level) {
      ChunkGenerator generator = level.getChunkSource().getGenerator();
      return new OfflineTerrainSampler(
         generator,
         generator.getBiomeSource(),
         level.getChunkSource().randomState(),
         level
      );
   }

   public int height(int x, int z) {
      long key = ChunkPos.asLong(x, z);
      int cached = this.heightCache.get(key);
      if (cached != Integer.MIN_VALUE) {
         return cached;
      }
      int h = this.generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, this.heightAccessor, this.randomState);
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
      Holder<Biome> biome = this.biomeSource.getNoiseBiome(x >> 2, y >> 2, z >> 2, this.randomState.sampler());
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

   public void clear() {
      this.heightCache.clear();
      this.flagsCache.clear();
   }
}
