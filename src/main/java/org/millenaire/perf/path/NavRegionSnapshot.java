package org.millenaire.perf.path;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Immutable walkability capture for async entity A* (no ServerLevel on workers).
 */
public final class NavRegionSnapshot {
   private static final int MAX_SPAN = 64;
   /** Column whose chunk was not loaded at capture time; impassable (never force-load for async A*). */
   static final int UNLOADED = Integer.MIN_VALUE;
   private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
   private final int minX;
   private final int minZ;
   private final int sizeX;
   private final int sizeZ;
   /** Packed xz -> ground Y (MOTION_BLOCKING_NO_LEAVES). */
   private final int[] groundY;

   private NavRegionSnapshot(int minX, int minZ, int sizeX, int sizeZ, int[] groundY) {
      this.minX = minX;
      this.minZ = minZ;
      this.sizeX = sizeX;
      this.sizeZ = sizeZ;
      this.groundY = groundY;
   }

   public static NavRegionSnapshot capture(ServerLevel level, BlockPos from, BlockPos to, int pad) {
      int minX = Math.min(from.getX(), to.getX()) - pad;
      int maxX = Math.max(from.getX(), to.getX()) + pad;
      int minZ = Math.min(from.getZ(), to.getZ()) - pad;
      int maxZ = Math.max(from.getZ(), to.getZ()) + pad;
      if (maxX - minX > MAX_SPAN) {
         int mid = (minX + maxX) / 2;
         minX = mid - MAX_SPAN / 2;
         maxX = minX + MAX_SPAN;
      }
      if (maxZ - minZ > MAX_SPAN) {
         int mid = (minZ + maxZ) / 2;
         minZ = mid - MAX_SPAN / 2;
         maxZ = minZ + MAX_SPAN;
      }
      int sizeX = maxX - minX + 1;
      int sizeZ = maxZ - minZ + 1;
      int[] ground = new int[sizeX * sizeZ];
      int cachedChunkX = Integer.MIN_VALUE;
      int cachedChunkZ = Integer.MIN_VALUE;
      LevelChunk cachedChunk = null;
      for (int z = 0; z < sizeZ; z++) {
         for (int x = 0; x < sizeX; x++) {
            int wx = minX + x;
            int wz = minZ + z;
            int chunkX = wx >> 4;
            int chunkZ = wz >> 4;
            if (chunkX != cachedChunkX || chunkZ != cachedChunkZ) {
               cachedChunkX = chunkX;
               cachedChunkZ = chunkZ;
               cachedChunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            }
            ground[z * sizeX + x] = cachedChunk == null
               ? UNLOADED
               : cachedChunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx & 15, wz & 15);
         }
      }
      return new NavRegionSnapshot(minX, minZ, sizeX, sizeZ, ground);
   }

   static NavRegionSnapshot ofGrid(int minX, int minZ, int sizeX, int sizeZ, int[] groundY) {
      return new NavRegionSnapshot(minX, minZ, sizeX, sizeZ, groundY);
   }

   public Path findPath(BlockPos from, BlockPos to) {
      int sx = from.getX() - this.minX;
      int sz = from.getZ() - this.minZ;
      int tx = to.getX() - this.minX;
      int tz = to.getZ() - this.minZ;
      if (!inBounds(sx, sz) || !inBounds(tx, tz) || yAt(sx, sz) == UNLOADED || yAt(tx, tz) == UNLOADED) {
         return null;
      }
      PriorityQueue<long[]> open = new PriorityQueue<>(Comparator.comparingLong(a -> a[0]));
      Long2LongOpenHashMap came = new Long2LongOpenHashMap();
      came.defaultReturnValue(Long.MIN_VALUE);
      Long2IntOpenHashMap gScore = new Long2IntOpenHashMap();
      gScore.defaultReturnValue(Integer.MAX_VALUE);
      long start = pack(sx, sz);
      long goal = pack(tx, tz);
      gScore.put(start, 0);
      open.add(new long[]{heuristic(sx, sz, tx, tz), start});
      LongOpenHashSet closed = new LongOpenHashSet();
      int expands = 0;
      final int maxExpands = 4000;
      while (!open.isEmpty() && expands++ < maxExpands) {
         long[] cur = open.poll();
         long key = cur[1];
         if (key == goal) {
            return reconstruct(came, key, to);
         }
         if (!closed.add(key)) {
            continue;
         }
         int cx = (int)(key >> 32);
         int cz = (int)key;
         for (int[] d : DIRS) {
            int nx = cx + d[0];
            int nz = cz + d[1];
            if (!inBounds(nx, nz)) {
               continue;
            }
            int cy = yAt(cx, cz);
            int ny = yAt(nx, nz);
            if (ny == UNLOADED || Math.abs(ny - cy) > 1) {
               continue;
            }
            long nk = pack(nx, nz);
            int step = d[0] != 0 && d[1] != 0 ? 14 : 10;
            int tentative = gScore.get(key) + step;
            if (tentative < gScore.get(nk)) {
               came.put(nk, key);
               gScore.put(nk, tentative);
               open.add(new long[]{tentative + heuristic(nx, nz, tx, tz), nk});
            }
         }
      }
      return null;
   }

   /**
    * Cheap main-thread validation of an async path: sample every {@code stride} node for solid ground
    * and passable feet/head air. Returns false when the path should be discarded.
    */
   public static boolean validateWalkability(ServerLevel level, Path path, int stride) {
      if (level == null || path == null || path.getNodeCount() == 0) {
         return false;
      }
      int step = Math.max(1, stride);
      BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
      for (int i = 0; i < path.getNodeCount(); i += step) {
         Node node = path.getNode(i);
         if (node == null) {
            return false;
         }
         mutable.set(node.x, node.y - 1, node.z);
         BlockState below = level.getBlockState(mutable);
         if (below.isAir() || below.getCollisionShape(level, mutable).isEmpty()) {
            // Allow water/fluid surfaces loosely — only reject total voids.
            if (!below.getFluidState().isEmpty()) {
               continue;
            }
            return false;
         }
         mutable.set(node.x, node.y, node.z);
         if (level.getBlockState(mutable).isSuffocating(level, mutable)) {
            return false;
         }
      }
      return true;
   }

   private Path reconstruct(Long2LongOpenHashMap came, long key, BlockPos target) {
      List<Node> nodes = new ArrayList<>();
      long cur = key;
      while (true) {
         int x = (int)(cur >> 32);
         int z = (int)cur;
         int y = yAt(x, z);
         nodes.add(new Node(this.minX + x, y, this.minZ + z));
         long prev = came.get(cur);
         if (prev == Long.MIN_VALUE) {
            break;
         }
         cur = prev;
      }
      java.util.Collections.reverse(nodes);
      if (nodes.isEmpty()) {
         return null;
      }
      return new Path(nodes, target, true);
   }

   private boolean inBounds(int x, int z) {
      return x >= 0 && z >= 0 && x < this.sizeX && z < this.sizeZ;
   }

   private int yAt(int x, int z) {
      return this.groundY[z * this.sizeX + x];
   }

   private static long pack(int x, int z) {
      return ((long)x << 32) | (z & 0xFFFFFFFFL);
   }

   private static long heuristic(int x, int z, int tx, int tz) {
      int dx = Math.abs(x - tx);
      int dz = Math.abs(z - tz);
      return (Math.max(dx, dz) * 10L) + (Math.min(dx, dz) * 4L);
   }
}
