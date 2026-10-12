package org.millenaire.worldgen.prefill;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.millenaire.worldgen.plan.SettlementRegionKey;

class SpawnRingPrefillEnumerateTest {
   @Test
   void enumeratesSpawnRegionAndNeighborsWithinRadius() {
      BlockPos spawn = new BlockPos(0, 64, 0);
      int regionSize = 24;
      List<Long> regions = SpawnRingPrefillService.enumerateRegions(spawn, 1024, regionSize);
      assertFalse(regions.isEmpty(), "prefill ring should contain regions");
      SettlementRegionKey spawnRegion = SettlementRegionKey.fromBlock(0, 0, regionSize);
      assertTrue(regions.contains(spawnRegion.packed()), "spawn region must be included");
      // At 1024 blocks / ~384 block regions, expect multiple cells.
      assertTrue(regions.size() >= 5, "expected several regions, got " + regions.size());
   }

   @Test
   void smallerRadiusYieldsFewerRegions() {
      BlockPos spawn = new BlockPos(0, 64, 0);
      List<Long> small = SpawnRingPrefillService.enumerateRegions(spawn, 256, 24);
      List<Long> large = SpawnRingPrefillService.enumerateRegions(spawn, 1024, 24);
      assertTrue(large.size() >= small.size());
   }
}
