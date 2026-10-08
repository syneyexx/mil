package org.millenaire.village;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

public final class HuntUnreachableMemory {
   static final int CELL = 8;
   static final long COOLDOWN = 2400L;
   private final Map<Long, Long> cells = new HashMap<>();

   public void mark(int x, int y, int z, long now) {
      this.prune(now);
      this.cells.put(cellKey(x, y, z), now + 2400L);
   }

   public boolean contains(int x, int y, int z, long now) {
      Long expiry = this.cells.get(cellKey(x, y, z));
      return expiry != null && expiry > now;
   }

   private void prune(long now) {
      this.cells.values().removeIf(expiry -> expiry <= now);
   }

   int size() {
      return this.cells.size();
   }

   static long cellKey(int x, int y, int z) {
      return BlockPos.asLong(Math.floorDiv(x, 8), Math.floorDiv(y, 8), Math.floorDiv(z, 8));
   }
}
