package org.millenaire.world.road;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.ChunkPos;

/**
 * Simplifies and labels planned polylines, then builds a per-chunk index.
 */
public final class RoutePostProcessor {
   private RoutePostProcessor() {
   }

   public static final class PlannedRoute {
      public final int[] polyline;
      public final byte[] segmentKinds;
      public final Long2ObjectOpenHashMap<int[]> chunkSegments;

      public PlannedRoute(int[] polyline, byte[] segmentKinds, Long2ObjectOpenHashMap<int[]> chunkSegments) {
         this.polyline = polyline;
         this.segmentKinds = segmentKinds;
         this.chunkSegments = chunkSegments;
      }
   }

   public static PlannedRoute process(int[] rawPath, TerrainView sampler, long edgeId, long worldSeed, int maxBridgeLen, int tunnelThreshold) {
      int[] simplified = douglasPeucker(rawPath, 2.5);
      int[] smooth = chaikin(simplified, 1, edgeId ^ worldSeed);
      byte[] kinds = labelSegments(smooth, sampler, maxBridgeLen, tunnelThreshold);
      Long2ObjectOpenHashMap<int[]> index = buildChunkIndex(smooth);
      return new PlannedRoute(smooth, kinds, index);
   }

   static byte[] labelSegments(int[] path, TerrainView sampler, int maxBridgeLen, int tunnelThreshold) {
      int points = path.length / 3;
      byte[] kinds = new byte[Math.max(0, points - 1)];
      int bridgeRun = 0;
      for (int i = 0; i < points - 1; i++) {
         int x = path[i * 3];
         int y = path[i * 3 + 1];
         int z = path[i * 3 + 2];
         int nx = path[(i + 1) * 3];
         int ny = path[(i + 1) * 3 + 1];
         int nz = path[(i + 1) * 3 + 2];
         int flags = sampler.flags(x, z) | sampler.flags(nx, nz);
         int surface = sampler.height(x, z);
         int dy = Math.abs(ny - y);
         if ((flags & TerrainSampler.FLAG_LAVA) != 0) {
            kinds[i] = RouteSegment.GROUND;
         } else if ((flags & TerrainSampler.FLAG_WATER) != 0 || (flags & TerrainSampler.FLAG_DEEP_OCEAN) != 0) {
            kinds[i] = RouteSegment.BRIDGE;
            bridgeRun++;
            if (bridgeRun > maxBridgeLen) {
               kinds[i] = RouteSegment.GROUND;
            }
         } else if (surface - y >= tunnelThreshold || ((flags & TerrainSampler.FLAG_MOUNTAIN) != 0 && surface - Math.min(y, ny) > tunnelThreshold / 2)) {
            kinds[i] = RouteSegment.TUNNEL;
            bridgeRun = 0;
         } else if (dy > 1) {
            kinds[i] = RouteSegment.STAIRS;
            bridgeRun = 0;
         } else {
            kinds[i] = RouteSegment.GROUND;
            bridgeRun = 0;
         }
      }
      return kinds;
   }

   static Long2ObjectOpenHashMap<int[]> buildChunkIndex(int[] path) {
      Long2ObjectOpenHashMap<List<Integer>> tmp = new Long2ObjectOpenHashMap<>();
      int points = path.length / 3;
      for (int i = 0; i < points; i++) {
         int x = path[i * 3];
         int z = path[i * 3 + 2];
         long key = ChunkPos.asLong(x >> 4, z >> 4);
         tmp.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
      }
      // Also mark segments that cross chunk boundaries
      for (int i = 0; i < points - 1; i++) {
         int x0 = path[i * 3];
         int z0 = path[i * 3 + 2];
         int x1 = path[(i + 1) * 3];
         int z1 = path[(i + 1) * 3 + 2];
         int minCx = Math.min(x0 >> 4, x1 >> 4);
         int maxCx = Math.max(x0 >> 4, x1 >> 4);
         int minCz = Math.min(z0 >> 4, z1 >> 4);
         int maxCz = Math.max(z0 >> 4, z1 >> 4);
         for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
               long key = ChunkPos.asLong(cx, cz);
               List<Integer> list = tmp.computeIfAbsent(key, k -> new ArrayList<>());
               if (!list.contains(i)) {
                  list.add(i);
               }
            }
         }
      }
      Long2ObjectOpenHashMap<int[]> out = new Long2ObjectOpenHashMap<>();
      tmp.forEach((key, list) -> {
         int[] arr = new int[list.size()];
         for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
         }
         out.put(key.longValue(), arr);
      });
      return out;
   }

   static int[] douglasPeucker(int[] path, double epsilon) {
      int n = path.length / 3;
      if (n <= 2) {
         return path.clone();
      }
      boolean[] keep = new boolean[n];
      keep[0] = true;
      keep[n - 1] = true;
      dpRecurse(path, 0, n - 1, epsilon, keep);
      int count = 0;
      for (boolean b : keep) {
         if (b) {
            count++;
         }
      }
      int[] out = new int[count * 3];
      int o = 0;
      for (int i = 0; i < n; i++) {
         if (keep[i]) {
            out[o++] = path[i * 3];
            out[o++] = path[i * 3 + 1];
            out[o++] = path[i * 3 + 2];
         }
      }
      return out;
   }

   private static void dpRecurse(int[] path, int first, int last, double epsilon, boolean[] keep) {
      if (last <= first + 1) {
         return;
      }
      double maxDist = 0;
      int index = first;
      for (int i = first + 1; i < last; i++) {
         double d = perpDist(path, first, last, i);
         if (d > maxDist) {
            maxDist = d;
            index = i;
         }
      }
      if (maxDist > epsilon) {
         keep[index] = true;
         dpRecurse(path, first, index, epsilon, keep);
         dpRecurse(path, index, last, epsilon, keep);
      }
   }

   private static double perpDist(int[] path, int a, int b, int i) {
      double ax = path[a * 3];
      double az = path[a * 3 + 2];
      double bx = path[b * 3];
      double bz = path[b * 3 + 2];
      double ix = path[i * 3];
      double iz = path[i * 3 + 2];
      double dx = bx - ax;
      double dz = bz - az;
      double len2 = dx * dx + dz * dz;
      if (len2 < 1e-6) {
         double ex = ix - ax;
         double ez = iz - az;
         return Math.sqrt(ex * ex + ez * ez);
      }
      double t = ((ix - ax) * dx + (iz - az) * dz) / len2;
      double px = ax + t * dx;
      double pz = az + t * dz;
      double ex = ix - px;
      double ez = iz - pz;
      return Math.sqrt(ex * ex + ez * ez);
   }

   static int[] chaikin(int[] path, int iterations, long seed) {
      int[] cur = path;
      for (int it = 0; it < iterations; it++) {
         int n = cur.length / 3;
         if (n < 2) {
            break;
         }
         int[] next = new int[(n * 2 - 2) * 3];
         int o = 0;
         for (int i = 0; i < n - 1; i++) {
            int x0 = cur[i * 3];
            int y0 = cur[i * 3 + 1];
            int z0 = cur[i * 3 + 2];
            int x1 = cur[(i + 1) * 3];
            int y1 = cur[(i + 1) * 3 + 1];
            int z1 = cur[(i + 1) * 3 + 2];
            // Q = 0.75 A + 0.25 B, R = 0.25 A + 0.75 B with tiny deterministic wobble
            int wobble = (int)((seed >> (i % 16)) & 1);
            next[o++] = (3 * x0 + x1) / 4 + (wobble == 0 ? 0 : 0);
            next[o++] = (3 * y0 + y1) / 4;
            next[o++] = (3 * z0 + z1) / 4;
            next[o++] = (x0 + 3 * x1) / 4;
            next[o++] = (y0 + 3 * y1) / 4;
            next[o++] = (z0 + 3 * z1) / 4;
         }
         // Keep endpoints exact
         next[0] = path[0];
         next[1] = path[1];
         next[2] = path[2];
         next[next.length - 3] = path[path.length - 3];
         next[next.length - 2] = path[path.length - 2];
         next[next.length - 1] = path[path.length - 1];
         cur = next;
      }
      return cur;
   }
}
