package org.millenaire.worldgen.terrain;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Optional;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/**
 * World reads that never schedule generation of missing chunks.
 */
public final class WorldgenChunkAccess {
   private WorldgenChunkAccess() {
   }

   @Nullable
   public static LevelChunk chunkNow(ServerLevel level, int chunkX, int chunkZ) {
      LevelChunk now = level.getChunkSource().getChunkNow(chunkX, chunkZ);
      if (now != null) {
         return now;
      }
      ChunkAccess access = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
      return access instanceof LevelChunk levelChunk ? levelChunk : null;
   }

   /**
    * Per-call-site cache for hot sample loops (site validation / terrain sampling).
    * Does not schedule generation.
    */
   public static final class ChunkCache {
      private final Long2ObjectOpenHashMap<LevelChunk> map = new Long2ObjectOpenHashMap<>();
      private final it.unimi.dsi.fastutil.longs.LongOpenHashSet misses = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

      @Nullable
      public LevelChunk get(ServerLevel level, int chunkX, int chunkZ) {
         long key = ChunkPos.asLong(chunkX, chunkZ);
         if (this.misses.contains(key)) {
            return null;
         }
         LevelChunk cached = this.map.get(key);
         if (cached != null) {
            return cached;
         }
         LevelChunk chunk = chunkNow(level, chunkX, chunkZ);
         if (chunk == null) {
            this.misses.add(key);
         } else {
            this.map.put(key, chunk);
         }
         return chunk;
      }

      public OptionalInt motionBlockingY(ServerLevel level, int x, int z) {
         LevelChunk chunk = get(level, x >> 4, z >> 4);
         if (chunk != null) {
            return OptionalInt.of(chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15));
         }
         if (level.hasChunk(x >> 4, z >> 4)) {
            return OptionalInt.of(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
         }
         return OptionalInt.empty();
      }

      public Optional<BlockState> blockState(ServerLevel level, BlockPos pos) {
         LevelChunk chunk = get(level, pos.getX() >> 4, pos.getZ() >> 4);
         if (chunk != null) {
            return Optional.of(chunk.getBlockState(pos));
         }
         if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            return Optional.of(level.getBlockState(pos));
         }
         return Optional.empty();
      }

      public Optional<FluidState> fluidState(ServerLevel level, BlockPos pos) {
         LevelChunk chunk = get(level, pos.getX() >> 4, pos.getZ() >> 4);
         if (chunk != null) {
            return Optional.of(chunk.getFluidState(pos));
         }
         if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            return Optional.of(level.getFluidState(pos));
         }
         return Optional.empty();
      }
   }

   public static boolean isChunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
      // Prefer cheap hasChunk before getChunkNow / FULL lookup.
      return level.hasChunk(chunkX, chunkZ) || level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
   }

   public static boolean isChunkLoaded(ServerLevel level, ChunkPos chunk) {
      return isChunkLoaded(level, chunk.x, chunk.z);
   }

   public static boolean isColumnLoaded(ServerLevel level, int blockX, int blockZ) {
      return isChunkLoaded(level, blockX >> 4, blockZ >> 4);
   }

   public static boolean areColumnsLoaded(ServerLevel level, int minX, int minZ, int maxX, int maxZ) {
      int minCx = minX >> 4;
      int maxCx = maxX >> 4;
      int minCz = minZ >> 4;
      int maxCz = maxZ >> 4;
      for (int cx = minCx; cx <= maxCx; cx++) {
         for (int cz = minCz; cz <= maxCz; cz++) {
            if (!isChunkLoaded(level, cx, cz)) {
               return false;
            }
         }
      }
      return true;
   }

   public static OptionalInt surfaceY(ServerLevel level, int x, int z) {
      LevelChunk chunk = chunkNow(level, x >> 4, z >> 4);
      if (chunk != null) {
         int h = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x & 15, z & 15);
         if (h <= level.getMinBuildHeight()) {
            h = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
         }
         return OptionalInt.of(h);
      }
      if (level.hasChunk(x >> 4, z >> 4)) {
         // Chunk is loaded — heightmap read will not generate a new chunk.
         return OptionalInt.of(level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z));
      }
      return OptionalInt.empty();
   }

   public static OptionalInt motionBlockingY(ServerLevel level, int x, int z) {
      LevelChunk chunk = chunkNow(level, x >> 4, z >> 4);
      if (chunk != null) {
         return OptionalInt.of(chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15));
      }
      if (level.hasChunk(x >> 4, z >> 4)) {
         return OptionalInt.of(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
      }
      return OptionalInt.empty();
   }

   public static Optional<BlockState> blockState(ServerLevel level, BlockPos pos) {
      LevelChunk chunk = chunkNow(level, pos.getX() >> 4, pos.getZ() >> 4);
      if (chunk != null) {
         return Optional.of(chunk.getBlockState(pos));
      }
      if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
         return Optional.of(level.getBlockState(pos));
      }
      return Optional.empty();
   }

   public static Optional<FluidState> fluidState(ServerLevel level, BlockPos pos) {
      LevelChunk chunk = chunkNow(level, pos.getX() >> 4, pos.getZ() >> 4);
      if (chunk != null) {
         return Optional.of(chunk.getFluidState(pos));
      }
      if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
         return Optional.of(level.getFluidState(pos));
      }
      return Optional.empty();
   }

   public static Optional<Holder<Biome>> biomeAt(ServerLevel level, int x, int z) {
      if (!isColumnLoaded(level, x, z)) {
         return Optional.empty();
      }
      OptionalInt yOpt = surfaceY(level, x, z);
      int y = yOpt.orElse(level.getSeaLevel());
      y = Math.min(level.getMaxBuildHeight() - 1, Math.max(level.getMinBuildHeight(), y));
      return Optional.of(level.getBiome(new BlockPos(x, y, z)));
   }
}
