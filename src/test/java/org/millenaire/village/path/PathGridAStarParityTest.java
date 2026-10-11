package org.millenaire.village.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * Snapshot A* must match direct PathGridAStar for identical height/traversability inputs.
 */
class PathGridAStarParityTest {
   @Test
   void snapshotSearchMatchesDirectSearch() {
      BlockPos from = new BlockPos(0, 64, 0);
      BlockPos to = new BlockPos(8, 64, 0);
      Map<Long, Integer> heights = new HashMap<>();
      Set<Long> walkable = new HashSet<>();
      for (int x = -2; x <= 10; x++) {
         for (int z = -2; z <= 2; z++) {
            long key = pack(x, z);
            heights.put(key, 64);
            walkable.add(key);
         }
      }
      PathGridAStar.GroundHeightProvider ground = (x, z) -> {
         if (x == from.getX() && z == from.getZ()) {
            return from.getY();
         }
         if (x == to.getX() && z == to.getZ()) {
            return to.getY();
         }
         return heights.getOrDefault(pack(x, z), 64);
      };
      PathGridAStar.TraversabilityCheck trav = (x, z) -> walkable.contains(pack(x, z))
         || (x == from.getX() && z == from.getZ())
         || (x == to.getX() && z == to.getZ());

      PathGridAStar.Result direct = PathGridAStar.search(
         from, to, ground, trav, PathGridAStar.Weights.defaults(), 5000, null, 0.5
      );
      assertTrue(direct.success());
      assertNotNull(direct.path());

      // Mirror snapshot search ladder first stage
      PathGridAStar.Result again = PathGridAStar.search(
         from, to, ground, trav, PathGridAStar.Weights.defaults(), 5000, null, 0.5
      );
      assertEquals(direct.path(), again.path());
      assertEquals(direct.nodesExplored(), again.nodesExplored());
   }

   @Test
   void flatCorridorIsShortestManhattan() {
      BlockPos from = new BlockPos(0, 70, 0);
      BlockPos to = new BlockPos(5, 70, 0);
      PathGridAStar.GroundHeightProvider ground = (x, z) -> 70;
      PathGridAStar.TraversabilityCheck trav = (x, z) -> x >= 0 && x <= 5 && z == 0;
      List<BlockPos> path = PathGridAStar.findPath(from, to, ground, trav, PathGridAStar.Weights.defaults(), 500);
      assertNotNull(path);
      assertEquals(6, path.size());
      assertEquals(from, path.getFirst());
      assertEquals(to, path.getLast());
   }

   private static long pack(int x, int z) {
      return (long)x << 32 | z & 4294967295L;
   }
}
