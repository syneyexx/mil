package org.millenaire.worldgen.plan;

import net.minecraft.world.level.ChunkPos;

/**
 * Region grid cell for deterministic settlement distribution.
 * Region size is independent from village radius.
 */
public record SettlementRegionKey(int regionX, int regionZ) {
   public static SettlementRegionKey fromChunk(ChunkPos chunk, int regionSizeChunks) {
      int size = Math.max(4, regionSizeChunks);
      int rx = Math.floorDiv(chunk.x, size);
      int rz = Math.floorDiv(chunk.z, size);
      return new SettlementRegionKey(rx, rz);
   }

   public static SettlementRegionKey fromBlock(int blockX, int blockZ, int regionSizeChunks) {
      return fromChunk(new ChunkPos(blockX >> 4, blockZ >> 4), regionSizeChunks);
   }

   public long packed() {
      return ((long)this.regionX << 32) ^ (this.regionZ & 0xffffffffL);
   }

   public static SettlementRegionKey fromPacked(long packed) {
      return new SettlementRegionKey((int)(packed >> 32), (int)packed);
   }

   public int centerChunkX(int regionSizeChunks) {
      int size = Math.max(4, regionSizeChunks);
      return this.regionX * size + size / 2;
   }

   public int centerChunkZ(int regionSizeChunks) {
      int size = Math.max(4, regionSizeChunks);
      return this.regionZ * size + size / 2;
   }
}
