package org.millenaire.world.road;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;

/**
 * Main-thread budgeted road builder. Only writes within the target chunk (no cross-chunk writes).
 */
public final class RoadBuilder {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

   private final Deque<BuildJob> queue = new ArrayDeque<>();
   private final LongList queuedChunks = new LongArrayList();
   private ProtectedAreaProvider protection = ProtectedAreaProvider.spawnProtection();
   private boolean paused;
   private double lastTickMillis;
   private int builtColumnsThisTick;

   public void setProtection(ProtectedAreaProvider protection) {
      this.protection = protection;
   }

   public void setPaused(boolean paused) {
      this.paused = paused;
   }

   public boolean isPaused() {
      return this.paused;
   }

   public int queueSize() {
      return this.queue.size();
   }

   public double lastTickMillis() {
      return this.lastTickMillis;
   }

   public void enqueue(ServerLevel level, long chunkKey, long edgeId) {
      if (this.queuedChunks.contains(chunkKey)) {
         return;
      }
      if (this.queue.size() >= RoadNetworkConfig.maxQueueSize()) {
         // Drop oldest far from players
         BuildJob dropped = this.queue.pollFirst();
         if (dropped != null) {
            this.queuedChunks.rem(dropped.chunkKey);
         }
      }
      this.queue.addLast(new BuildJob(chunkKey, edgeId, 0));
      this.queuedChunks.add(chunkKey);
   }

   public void tick(ServerLevel level, RoadNetworkSavedData data) {
      long start = System.nanoTime();
      this.builtColumnsThisTick = 0;
      if (this.paused) {
         this.lastTickMillis = 0;
         return;
      }
      double mspt = level.getServer().getCurrentSmoothedTickTime();
      if (mspt > RoadNetworkConfig.msptPauseThreshold()) {
         this.lastTickMillis = 0;
         return;
      }

      long budgetNanos = (long)(RoadNetworkConfig.maxMillisPerTick() * 1_000_000L);
      int maxChunks = RoadNetworkConfig.maxChunksPerTick();
      int chunksDone = 0;

      while (!this.queue.isEmpty() && chunksDone < maxChunks) {
         if (System.nanoTime() - start > budgetNanos) {
            break;
         }
         BuildJob job = this.queue.pollFirst();
         if (job == null) {
            break;
         }
         this.queuedChunks.rem(job.chunkKey);

         ChunkPos cp = new ChunkPos(job.chunkKey);
         if (!level.hasChunk(cp.x, cz(job.chunkKey))) {
            continue;
         }
         LevelChunk chunk = level.getChunk(cp.x, cp.z);
         RoadEdge edge = data.getGraph().getEdge(job.edgeId);
         if (edge == null || edge.polyline() == null) {
            continue;
         }
         if (edge.isChunkBuilt(job.chunkKey)) {
            continue;
         }

         int cursor = buildChunk(level, chunk, edge, job.cursor, start, budgetNanos);
         if (cursor < 0) {
            edge.markChunkBuilt(job.chunkKey);
            maybeMarkEdgeDone(data, edge);
            data.setDirty();
            chunksDone++;
         } else {
            // Resume later
            job.cursor = cursor;
            this.queue.addFirst(job);
            this.queuedChunks.add(job.chunkKey);
            break;
         }
      }

      this.lastTickMillis = (System.nanoTime() - start) / 1_000_000.0;
      if (this.lastTickMillis > 5.0) {
         LOGGER.warn("[Roads] RoadBuilder tick took {} ms (budget {} ms)", String.format("%.2f", this.lastTickMillis), RoadNetworkConfig.maxMillisPerTick());
      }
   }

   private static int cz(long chunkKey) {
      return ChunkPos.getZ(chunkKey);
   }

   private int buildChunk(ServerLevel level, LevelChunk chunk, RoadEdge edge, int startIndex, long tickStart, long budgetNanos) {
      int[] poly = edge.polyline();
      byte[] kinds = edge.segmentKinds();
      int points = poly.length / 3;
      ChunkPos cp = chunk.getPos();
      int minX = cp.getMinBlockX();
      int maxX = cp.getMaxBlockX();
      int minZ = cp.getMinBlockZ();
      int maxZ = cp.getMaxBlockZ();
      int width = RoadNetworkConfig.roadWidth();
      BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();

      for (int i = Math.max(0, startIndex); i < points; i++) {
         if ((i & 7) == 0 && System.nanoTime() - tickStart > budgetNanos) {
            return i;
         }
         int x = poly[i * 3];
         int z = poly[i * 3 + 2];
         if (x < minX - width || x > maxX + width || z < minZ - width || z > maxZ + width) {
            continue;
         }
         byte kind = kinds != null && i < kinds.length ? kinds[i] : RouteSegment.GROUND;
         if (kind == RouteSegment.BRIDGE && !RoadNetworkConfig.bridgesEnabled()) {
            kind = RouteSegment.GROUND;
         }
         if (kind == RouteSegment.TUNNEL && !RoadNetworkConfig.tunnelsEnabled()) {
            kind = RouteSegment.GROUND;
         }
         // Direction for width offset
         int nx = i + 1 < points ? poly[(i + 1) * 3] : x;
         int nz = i + 1 < points ? poly[(i + 1) * 3 + 2] : z;
         int dx = Integer.signum(nx - x);
         int dz = Integer.signum(nz - z);
         int px = -dz;
         int pz = dx;
         if (px == 0 && pz == 0) {
            px = 1;
         }

         for (int w = -width / 2; w <= width / 2; w++) {
            int bx = x + px * w;
            int bz = z + pz * w;
            if (bx < minX || bx > maxX || bz < minZ || bz > maxZ) {
               continue; // hard rule: no cross-chunk writes
            }
            placeColumn(level, chunk, mut, bx, bz, kind, Math.abs(w) == width / 2, edge.id());
            this.builtColumnsThisTick++;
         }

         // Props every ~56 blocks on centerline
         if (kind == RouteSegment.GROUND && i % 7 == 0 && (edge.id() + i) % 8 == 0) {
            placeLamp(level, chunk, mut, x, z, minX, maxX, minZ, maxZ);
         }
      }
      return -1;
   }

   private void placeColumn(
      ServerLevel level, LevelChunk chunk, BlockPos.MutableBlockPos mut, int x, int z, byte kind, boolean edgeColumn, long edgeId
   ) {
      int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
      if (surface < level.getMinBuildHeight() + 1) {
         return;
      }
      Holder<Biome> biome = level.getBiome(mut.set(x, surface, z));
      int noise = (int)((edgeId * 31 + x * 734287 + z * 912371) & 0x7fffffff);

      if (kind == RouteSegment.BRIDGE) {
         placeBridgeColumn(level, chunk, mut, x, surface, z, biome, edgeColumn);
         return;
      }
      if (kind == RouteSegment.TUNNEL) {
         placeTunnelColumn(level, chunk, mut, x, surface, z, biome, edgeColumn, noise);
         return;
      }

      // Soft edge: 50% chance to skip outer column
      if (edgeColumn && (noise & 1) == 0) {
         return;
      }

      mut.set(x, surface, z);
      if (this.protection.isProtected(level, mut)) {
         return;
      }
      BlockState existing = chunk.getBlockState(mut);
      if (!PathPalette.isReplaceableNatural(existing) && !existing.is(BlockTags.DIRT) && !existing.is(BlockTags.BASE_STONE_OVERWORLD)
         && !existing.is(Blocks.GRASS_BLOCK) && !existing.is(Blocks.SNOW) && !existing.is(BlockTags.LEAVES) && !existing.is(BlockTags.LOGS)
         && !existing.is(BlockTags.REPLACEABLE)) {
         return;
      }

      // Clear above up to 4
      for (int dy = 1; dy <= 4; dy++) {
         mut.set(x, surface + dy, z);
         BlockState above = chunk.getBlockState(mut);
         if (above.is(BlockTags.LEAVES) || above.is(BlockTags.REPLACEABLE) || above.is(Blocks.SNOW) || above.is(BlockTags.LOGS)
            || above.is(BlockTags.FLOWERS) || above.isAir()) {
            if (!above.isAir() && PathPalette.isReplaceableNatural(above)) {
               level.setBlock(mut, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
            } else if (above.is(BlockTags.LEAVES) || above.is(Blocks.SNOW) || above.is(BlockTags.REPLACEABLE)) {
               level.setBlock(mut, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
            }
         } else {
            break;
         }
      }

      // Fill small cavities under path
      for (int dy = 1; dy <= 3; dy++) {
         mut.set(x, surface - dy, z);
         BlockState below = chunk.getBlockState(mut);
         if (below.isAir() || below.canBeReplaced()) {
            level.setBlock(mut, PathPalette.fillBlock(biome), UPDATE_FLAGS);
         } else {
            break;
         }
      }

      mut.set(x, surface, z);
      BlockState path = PathPalette.pathBlock(biome, noise);
      level.setBlock(mut, path, UPDATE_FLAGS);

      // Remove snow on top
      mut.set(x, surface + 1, z);
      if (chunk.getBlockState(mut).is(Blocks.SNOW)) {
         level.setBlock(mut, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
      }
   }

   private void placeBridgeColumn(
      ServerLevel level, LevelChunk chunk, BlockPos.MutableBlockPos mut, int x, int surface, int z, Holder<Biome> biome, boolean edgeColumn
   ) {
      // Find water surface / gap
      int y = surface;
      mut.set(x, y, z);
      if (this.protection.isProtected(level, mut)) {
         return;
      }
      // Deck at surface height
      level.setBlock(mut, PathPalette.bridgeDeck(biome), UPDATE_FLAGS);
      if (edgeColumn) {
         mut.set(x, y + 1, z);
         level.setBlock(mut, PathPalette.bridgeRail(biome), UPDATE_FLAGS);
      }
      // Pillars every so often down to solid
      if (!edgeColumn && ((x + z) & 7) == 0) {
         for (int py = y - 1; py >= level.getMinBuildHeight() + 1; py--) {
            mut.set(x, py, z);
            BlockState s = chunk.getBlockState(mut);
            if (s.isSolidRender(chunk, mut) && !s.getFluidState().isSource()) {
               break;
            }
            level.setBlock(mut, Blocks.OAK_LOG.defaultBlockState(), UPDATE_FLAGS);
         }
      }
   }

   private void placeTunnelColumn(
      ServerLevel level,
      LevelChunk chunk,
      BlockPos.MutableBlockPos mut,
      int x,
      int surface,
      int z,
      Holder<Biome> biome,
      boolean edgeColumn,
      int noise
   ) {
      int floorY = Math.max(level.getMinBuildHeight() + 8, surface - RoadNetworkConfig.tunnelThreshold());
      // Floor
      mut.set(x, floorY, z);
      if (this.protection.isProtected(level, mut)) {
         return;
      }
      level.setBlock(mut, PathPalette.pathBlock(biome, noise), UPDATE_FLAGS);
      // Carve 4 high
      for (int dy = 1; dy <= 4; dy++) {
         mut.set(x, floorY + dy, z);
         BlockState s = chunk.getBlockState(mut);
         if (s.is(Blocks.BEDROCK) || s.hasBlockEntity()) {
            continue;
         }
         if (s.getFluidState().isEmpty()) {
            level.setBlock(mut, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
         } else {
            // Seal fluids
            level.setBlock(mut, PathPalette.tunnelWall(biome), UPDATE_FLAGS);
         }
      }
      // Walls on edges
      if (edgeColumn) {
         for (int dy = 0; dy <= 4; dy++) {
            mut.set(x, floorY + dy, z);
            level.setBlock(mut, PathPalette.tunnelWall(biome), UPDATE_FLAGS);
         }
      }
      // Lights every 8
      if (!edgeColumn && (x & 7) == 0) {
         mut.set(x, floorY + 3, z);
         level.setBlock(mut, Blocks.TORCH.defaultBlockState(), UPDATE_FLAGS);
      }
      // Support beams
      if (!edgeColumn && ((x + z) & 7) == 0) {
         mut.set(x, floorY + 4, z);
         level.setBlock(mut, Blocks.OAK_LOG.defaultBlockState(), UPDATE_FLAGS);
      }
   }

   private void placeLamp(ServerLevel level, LevelChunk chunk, BlockPos.MutableBlockPos mut, int x, int z, int minX, int maxX, int minZ, int maxZ) {
      if (x < minX || x > maxX || z < minZ || z > maxZ) {
         return;
      }
      int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
      mut.set(x, surface + 1, z);
      if (this.protection.isProtected(level, mut)) {
         return;
      }
      level.setBlock(mut, Blocks.OAK_FENCE.defaultBlockState(), UPDATE_FLAGS);
      mut.set(x, surface + 2, z);
      level.setBlock(mut, Blocks.OAK_FENCE.defaultBlockState(), UPDATE_FLAGS);
      mut.set(x, surface + 3, z);
      level.setBlock(mut, Blocks.TORCH.defaultBlockState(), UPDATE_FLAGS);
   }

   private static void maybeMarkEdgeDone(RoadNetworkSavedData data, RoadEdge edge) {
      // Heuristic: if many chunks built relative to polyline span, mark DONE
      int[] poly = edge.polyline();
      if (poly == null) {
         return;
      }
      int points = poly.length / 3;
      int approxChunks = Math.max(1, points / 4);
      if (edge.builtChunkCount() >= approxChunks) {
         edge.setState(EdgeState.DONE);
      } else {
         edge.setState(EdgeState.BUILDING);
      }
   }

   private static final class BuildJob {
      final long chunkKey;
      final long edgeId;
      int cursor;

      BuildJob(long chunkKey, long edgeId, int cursor) {
         this.chunkKey = chunkKey;
         this.edgeId = edgeId;
         this.cursor = cursor;
      }
   }
}
