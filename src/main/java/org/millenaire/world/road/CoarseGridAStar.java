package org.millenaire.world.road;

import java.util.Arrays;
import javax.annotation.Nullable;

/**
 * Primitive A* on a coarse grid. Nodes encoded as packed longs. No object allocations in the hot loop
 * beyond the reusable open-set arrays.
 */
public final class CoarseGridAStar {
   public static final int COST_BLOCKED = Integer.MAX_VALUE / 4;

   @FunctionalInterface
   public interface CostProvider {
      /** Return step cost from (fx,fz) to (tx,tz), or >= COST_BLOCKED to forbid. */
      float cost(int fromX, int fromZ, int toX, int toZ, int fromY, int toY);
   }

   @FunctionalInterface
   public interface HeightProvider {
      int height(int x, int z);
   }

   public record Result(@Nullable int[] pathXzY, int expansions, String reason) {
      public boolean found() {
         return this.pathXzY != null && this.pathXzY.length >= 3;
      }
   }

   private CoarseGridAStar() {
   }

   public static Result search(
      int startX,
      int startZ,
      int goalX,
      int goalZ,
      int cellSize,
      HeightProvider heights,
      CostProvider costs,
      float minCostPerBlock,
      int maxExpansions,
      long maxMillis
   ) {
      int sx = floorDiv(startX, cellSize);
      int sz = floorDiv(startZ, cellSize);
      int gx = floorDiv(goalX, cellSize);
      int gz = floorDiv(goalZ, cellSize);

      LongFloatHeap open = new LongFloatHeap(1024);
      // bestG: sparse via open-addressing long->float map
      LongFloatMap bestG = new LongFloatMap(4096);
      LongLongMap cameFrom = new LongLongMap(4096);
      LongIntMap cameY = new LongIntMap(4096);

      long startKey = pack(sx, sz);
      int startY = heights.height(sx * cellSize + cellSize / 2, sz * cellSize + cellSize / 2);
      bestG.put(startKey, 0f);
      cameY.put(startKey, startY);
      float h0 = heuristic(sx, sz, gx, gz, cellSize, minCostPerBlock);
      open.push(startKey, h0);

      int expansions = 0;
      long deadline = System.nanoTime() + maxMillis * 1_000_000L;
      final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
      final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};

      while (!open.isEmpty() && expansions < maxExpansions) {
         if ((expansions & 1023) == 0 && System.nanoTime() > deadline) {
            return new Result(null, expansions, "timeout");
         }
         long cur = open.pop();
         expansions++;
         int cx = unpackX(cur);
         int cz = unpackZ(cur);
         if (cx == gx && cz == gz) {
            return new Result(reconstruct(cameFrom, cameY, cur, cellSize, startX, startZ, goalX, goalZ), expansions, "found");
         }

         float gCur = bestG.getOrDefault(cur, Float.POSITIVE_INFINITY);
         int cy = cameY.getOrDefault(cur, startY);

         for (int di = 0; di < 8; di++) {
            int nx = cx + DX[di];
            int nz = cz + DZ[di];
            long nKey = pack(nx, nz);
            int wx = nx * cellSize + cellSize / 2;
            int wz = nz * cellSize + cellSize / 2;
            int ny = heights.height(wx, wz);
            float step = costs.cost(cx * cellSize + cellSize / 2, cz * cellSize + cellSize / 2, wx, wz, cy, ny);
            if (step >= COST_BLOCKED) {
               continue;
            }
            // Diagonal scale
            if (DX[di] != 0 && DZ[di] != 0) {
               step *= 1.4142135f;
            }
            float gNew = gCur + step;
            float gOld = bestG.getOrDefault(nKey, Float.POSITIVE_INFINITY);
            if (gNew + 1e-4f < gOld) {
               bestG.put(nKey, gNew);
               cameFrom.put(nKey, cur);
               cameY.put(nKey, ny);
               float f = gNew + heuristic(nx, nz, gx, gz, cellSize, minCostPerBlock);
               open.push(nKey, f);
            }
         }
      }
      return new Result(null, expansions, expansions >= maxExpansions ? "max_expansions" : "exhausted");
   }

   private static float heuristic(int x, int z, int gx, int gz, int cellSize, float minCost) {
      float dx = (x - gx) * (float)cellSize;
      float dz = (z - gz) * (float)cellSize;
      return minCost * (float)Math.sqrt(dx * dx + dz * dz);
   }

   private static int[] reconstruct(LongLongMap cameFrom, LongIntMap cameY, long goal, int cellSize, int startX, int startZ, int goalX, int goalZ) {
      // Count
      int len = 1;
      long cur = goal;
      while (cameFrom.contains(cur)) {
         cur = cameFrom.get(cur);
         len++;
         if (len > 1_000_000) {
            break;
         }
      }
      int[] path = new int[len * 3];
      cur = goal;
      for (int i = len - 1; i >= 0; i--) {
         int cx = unpackX(cur);
         int cz = unpackZ(cur);
         int y = cameY.getOrDefault(cur, 64);
         int wx = i == 0 ? startX : (i == len - 1 ? goalX : cx * cellSize + cellSize / 2);
         int wz = i == 0 ? startZ : (i == len - 1 ? goalZ : cz * cellSize + cellSize / 2);
         if (i == len - 1) {
            wx = goalX;
            wz = goalZ;
         }
         if (i == 0) {
            wx = startX;
            wz = startZ;
         }
         path[i * 3] = wx;
         path[i * 3 + 1] = y;
         path[i * 3 + 2] = wz;
         if (!cameFrom.contains(cur)) {
            break;
         }
         cur = cameFrom.get(cur);
      }
      // Fix first point
      path[0] = startX;
      path[2] = startZ;
      path[path.length - 3] = goalX;
      path[path.length - 1] = goalZ;
      return path;
   }

   private static int floorDiv(int a, int b) {
      int r = a / b;
      if ((a ^ b) < 0 && r * b != a) {
         r--;
      }
      return r;
   }

   static long pack(int x, int z) {
      return ((long)x & 0xFFFFFFFFL) << 32 | (long)z & 0xFFFFFFFFL;
   }

   static int unpackX(long key) {
      return (int)(key >> 32);
   }

   static int unpackZ(long key) {
      return (int)key;
   }

   /** Binary min-heap of (key,f). */
   static final class LongFloatHeap {
      private long[] keys;
      private float[] vals;
      private int size;

      LongFloatHeap(int cap) {
         this.keys = new long[cap];
         this.vals = new float[cap];
      }

      boolean isEmpty() {
         return this.size == 0;
      }

      void push(long key, float val) {
         if (this.size >= this.keys.length) {
            this.keys = Arrays.copyOf(this.keys, this.keys.length * 2);
            this.vals = Arrays.copyOf(this.vals, this.vals.length * 2);
         }
         int i = this.size++;
         this.keys[i] = key;
         this.vals[i] = val;
         siftUp(i);
      }

      long pop() {
         long result = this.keys[0];
         this.size--;
         if (this.size > 0) {
            this.keys[0] = this.keys[this.size];
            this.vals[0] = this.vals[this.size];
            siftDown(0);
         }
         return result;
      }

      private void siftUp(int i) {
         while (i > 0) {
            int p = (i - 1) >> 1;
            if (this.vals[i] >= this.vals[p]) {
               break;
            }
            swap(i, p);
            i = p;
         }
      }

      private void siftDown(int i) {
         while (true) {
            int l = i * 2 + 1;
            int r = l + 1;
            int smallest = i;
            if (l < this.size && this.vals[l] < this.vals[smallest]) {
               smallest = l;
            }
            if (r < this.size && this.vals[r] < this.vals[smallest]) {
               smallest = r;
            }
            if (smallest == i) {
               break;
            }
            swap(i, smallest);
            i = smallest;
         }
      }

      private void swap(int a, int b) {
         long tk = this.keys[a];
         this.keys[a] = this.keys[b];
         this.keys[b] = tk;
         float tv = this.vals[a];
         this.vals[a] = this.vals[b];
         this.vals[b] = tv;
      }
   }

   static final class LongFloatMap {
      private long[] keys;
      private float[] vals;
      private byte[] used; // 0 empty, 1 occupied
      private int size;
      private int mask;

      LongFloatMap(int cap) {
         int n = Integer.highestOneBit(Math.max(16, cap - 1)) << 1;
         this.keys = new long[n];
         this.vals = new float[n];
         this.used = new byte[n];
         this.mask = n - 1;
      }

      void put(long key, float val) {
         if (this.size * 2 >= this.keys.length) {
            resize();
         }
         int i = mix(key) & this.mask;
         while (this.used[i] != 0) {
            if (this.keys[i] == key) {
               this.vals[i] = val;
               return;
            }
            i = (i + 1) & this.mask;
         }
         this.used[i] = 1;
         this.keys[i] = key;
         this.vals[i] = val;
         this.size++;
      }

      float getOrDefault(long key, float def) {
         int i = mix(key) & this.mask;
         while (this.used[i] != 0) {
            if (this.keys[i] == key) {
               return this.vals[i];
            }
            i = (i + 1) & this.mask;
         }
         return def;
      }

      private void resize() {
         long[] ok = this.keys;
         float[] ov = this.vals;
         byte[] ou = this.used;
         int n = ok.length * 2;
         this.keys = new long[n];
         this.vals = new float[n];
         this.used = new byte[n];
         this.mask = n - 1;
         this.size = 0;
         for (int i = 0; i < ok.length; i++) {
            if (ou[i] != 0) {
               put(ok[i], ov[i]);
            }
         }
      }

      private static int mix(long key) {
         key ^= key >>> 33;
         key *= 0xff51afd7ed558ccdL;
         key ^= key >>> 33;
         return (int)key;
      }
   }

   static final class LongLongMap {
      private long[] keys;
      private long[] vals;
      private byte[] used;
      private int size;
      private int mask;

      LongLongMap(int cap) {
         int n = Integer.highestOneBit(Math.max(16, cap - 1)) << 1;
         this.keys = new long[n];
         this.vals = new long[n];
         this.used = new byte[n];
         this.mask = n - 1;
      }

      void put(long key, long val) {
         if (this.size * 2 >= this.keys.length) {
            resize();
         }
         int i = LongFloatMap.mix(key) & this.mask;
         while (this.used[i] != 0) {
            if (this.keys[i] == key) {
               this.vals[i] = val;
               return;
            }
            i = (i + 1) & this.mask;
         }
         this.used[i] = 1;
         this.keys[i] = key;
         this.vals[i] = val;
         this.size++;
      }

      boolean contains(long key) {
         int i = LongFloatMap.mix(key) & this.mask;
         while (this.used[i] != 0) {
            if (this.keys[i] == key) {
               return true;
            }
            i = (i + 1) & this.mask;
         }
         return false;
      }

      long get(long key) {
         int i = LongFloatMap.mix(key) & this.mask;
         while (this.used[i] != 0) {
            if (this.keys[i] == key) {
               return this.vals[i];
            }
            i = (i + 1) & this.mask;
         }
         return 0L;
      }

      private void resize() {
         long[] ok = this.keys;
         long[] ov = this.vals;
         byte[] ou = this.used;
         int n = ok.length * 2;
         this.keys = new long[n];
         this.vals = new long[n];
         this.used = new byte[n];
         this.mask = n - 1;
         this.size = 0;
         for (int i = 0; i < ok.length; i++) {
            if (ou[i] != 0) {
               put(ok[i], ov[i]);
            }
         }
      }
   }

   static final class LongIntMap {
      private long[] keys;
      private int[] vals;
      private byte[] used;
      private int size;
      private int mask;

      LongIntMap(int cap) {
         int n = Integer.highestOneBit(Math.max(16, cap - 1)) << 1;
         this.keys = new long[n];
         this.vals = new int[n];
         this.used = new byte[n];
         this.mask = n - 1;
      }

      void put(long key, int val) {
         if (this.size * 2 >= this.keys.length) {
            resize();
         }
         int i = LongFloatMap.mix(key) & this.mask;
         while (this.used[i] != 0) {
            if (this.keys[i] == key) {
               this.vals[i] = val;
               return;
            }
            i = (i + 1) & this.mask;
         }
         this.used[i] = 1;
         this.keys[i] = key;
         this.vals[i] = val;
         this.size++;
      }

      int getOrDefault(long key, int def) {
         int i = LongFloatMap.mix(key) & this.mask;
         while (this.used[i] != 0) {
            if (this.keys[i] == key) {
               return this.vals[i];
            }
            i = (i + 1) & this.mask;
         }
         return def;
      }

      private void resize() {
         long[] ok = this.keys;
         int[] ov = this.vals;
         byte[] ou = this.used;
         int n = ok.length * 2;
         this.keys = new long[n];
         this.vals = new int[n];
         this.used = new byte[n];
         this.mask = n - 1;
         this.size = 0;
         for (int i = 0; i < ok.length; i++) {
            if (ou[i] != 0) {
               put(ok[i], ov[i]);
            }
         }
      }
   }
}
