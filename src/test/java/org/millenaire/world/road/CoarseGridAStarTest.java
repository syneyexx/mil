package org.millenaire.world.road;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class CoarseGridAStarTest {
   @Test
   void findsPathAcrossFlatTerrain() {
      CoarseGridAStar.HeightProvider heights = (x, z) -> 64;
      CoarseGridAStar.CostProvider costs = (fx, fz, tx, tz, fy, ty) -> 1f;
      CoarseGridAStar.Result result = CoarseGridAStar.search(0, 0, 80, 0, 8, heights, costs, 1f, 50_000, 5_000);
      assertTrue(result.found(), result.reason());
      assertNotNull(result.pathXzY());
      assertEquals(0, result.pathXzY()[0]);
      assertEquals(0, result.pathXzY()[2]);
      assertEquals(80, result.pathXzY()[result.pathXzY().length - 3]);
   }

   @Test
   void avoidsBlockedDeepOceanBand() {
      CoarseGridAStar.HeightProvider heights = (x, z) -> 64;
      CoarseGridAStar.CostProvider costs = (fx, fz, tx, tz, fy, ty) -> {
         // Block a vertical band that forces a detour
         if (tx >= 30 && tx <= 40 && tz >= -20 && tz <= 20) {
            return CoarseGridAStar.COST_BLOCKED;
         }
         return 1f;
      };
      CoarseGridAStar.Result result = CoarseGridAStar.search(0, 0, 80, 0, 8, heights, costs, 1f, 100_000, 10_000);
      assertTrue(result.found(), result.reason());
      int[] path = result.pathXzY();
      for (int i = 0; i < path.length; i += 3) {
         int x = path[i];
         int z = path[i + 2];
         assertTrue(!(x >= 30 && x <= 40 && z >= -20 && z <= 20), "path entered blocked band at " + x + "," + z);
      }
   }

   @Test
   void prefersBridgeOverLongDetourOnRiver() {
      CoarseGridAStar.HeightProvider heights = (x, z) -> 64;
      CoarseGridAStar.CostProvider costs = (fx, fz, tx, tz, fy, ty) -> {
         if (tx >= 36 && tx <= 44) {
            return 6f; // bridge cost
         }
         return 1f;
      };
      CoarseGridAStar.Result result = CoarseGridAStar.search(0, 0, 80, 0, 8, heights, costs, 1f, 50_000, 5_000);
      assertTrue(result.found(), result.reason());
   }

   @Test
   void deterministicSameSeedSamePath() {
      CoarseGridAStar.HeightProvider heights = (x, z) -> 64 + (Math.abs(x) % 3);
      CoarseGridAStar.CostProvider costs = (fx, fz, tx, tz, fy, ty) -> 1f + Math.abs(ty - fy) * 0.35f;
      CoarseGridAStar.Result a = CoarseGridAStar.search(0, 0, 64, 64, 8, heights, costs, 1f, 50_000, 5_000);
      CoarseGridAStar.Result b = CoarseGridAStar.search(0, 0, 64, 64, 8, heights, costs, 1f, 50_000, 5_000);
      assertTrue(a.found() && b.found());
      assertEquals(a.pathXzY().length, b.pathXzY().length);
      for (int i = 0; i < a.pathXzY().length; i++) {
         assertEquals(a.pathXzY()[i], b.pathXzY()[i]);
      }
   }
}
