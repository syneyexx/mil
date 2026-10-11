package org.millenaire.perf.path;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Immutable walkability capture for async entity A* (no ServerLevel on workers).
 */
public final class NavRegionSnapshot {
   private static final int MAX_SPAN = 96;
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
      for (int z = 0; z < sizeZ; z++) {
         for (int x = 0; x < sizeX; x++) {
            int wx = minX + x;
            int wz = minZ + z;
            ground[z * sizeX + x] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
         }
      }
      return new NavRegionSnapshot(minX, minZ, sizeX, sizeZ, ground);
   }

   public Path findPath(BlockPos from, BlockPos to) {
      int sx = from.getX() - this.minX;
      int sz = from.getZ() - this.minZ;
      int tx = to.getX() - this.minX;
      int tz = to.getZ() - this.minZ;
      if (!inBounds(sx, sz) || !inBounds(tx, tz)) {
         return null;
      }
      record Key(int x, int z) {
      }
      PriorityQueue<long[]> open = new PriorityQueue<>(Comparator.comparingLong(a -> a[0]));
      Map<Long, Long> came = new HashMap<>();
      Map<Long, Integer> gScore = new HashMap<>();
      long start = pack(sx, sz);
      long goal = pack(tx, tz);
      gScore.put(start, 0);
      open.add(new long[]{heuristic(sx, sz, tx, tz), start});
      Set<Long> closed = new HashSet<>();
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
         int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
         for (int[] d : dirs) {
            int nx = cx + d[0];
            int nz = cz + d[1];
            if (!inBounds(nx, nz)) {
               continue;
            }
            int cy = yAt(cx, cz);
            int ny = yAt(nx, nz);
            if (Math.abs(ny - cy) > 1) {
               continue;
            }
            long nk = pack(nx, nz);
            int step = d[0] != 0 && d[1] != 0 ? 14 : 10;
            int tentative = gScore.getOrDefault(key, Integer.MAX_VALUE) + step;
            if (tentative < gScore.getOrDefault(nk, Integer.MAX_VALUE)) {
               came.put(nk, key);
               gScore.put(nk, tentative);
               open.add(new long[]{tentative + heuristic(nx, nz, tx, tz), nk});
            }
         }
      }
      return null;
   }

   private Path reconstruct(Map<Long, Long> came, long key, BlockPos target) {
      List<Node> nodes = new ArrayList<>();
      long cur = key;
      while (true) {
         int x = (int)(cur >> 32);
         int z = (int)cur;
         int y = yAt(x, z);
         nodes.add(new Node(this.minX + x, y, this.minZ + z));
         Long prev = came.get(cur);
         if (prev == null) {
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
