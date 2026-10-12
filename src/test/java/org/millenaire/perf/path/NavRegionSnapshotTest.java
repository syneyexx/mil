package org.millenaire.perf.path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class NavRegionSnapshotTest {
   private static int[] flat(int sizeX, int sizeZ, int y) {
      int[] ground = new int[sizeX * sizeZ];
      Arrays.fill(ground, y);
      return ground;
   }

   @Test
   void findsPathOnLoadedFlatGround() {
      NavRegionSnapshot snap = NavRegionSnapshot.ofGrid(0, 0, 8, 8, flat(8, 8, 64));
      assertNotNull(snap.findPath(new BlockPos(0, 64, 0), new BlockPos(7, 64, 7)));
   }

   @Test
   void unloadedColumnsAreImpassable() {
      int[] ground = flat(8, 8, 64);
      for (int z = 0; z < 8; z++) {
         ground[z * 8 + 4] = NavRegionSnapshot.UNLOADED;
      }
      NavRegionSnapshot snap = NavRegionSnapshot.ofGrid(0, 0, 8, 8, ground);
      assertNull(snap.findPath(new BlockPos(0, 64, 0), new BlockPos(7, 64, 0)));
   }

   @Test
   void unloadedGoalReturnsNull() {
      int[] ground = flat(4, 4, 64);
      ground[3 * 4 + 3] = NavRegionSnapshot.UNLOADED;
      NavRegionSnapshot snap = NavRegionSnapshot.ofGrid(0, 0, 4, 4, ground);
      assertNull(snap.findPath(new BlockPos(0, 64, 0), new BlockPos(3, 64, 3)));
   }
}
