package org.millenaire.perf.tick;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VillageSimulationLodTest {
   @Test
   void nearAlwaysRunsFullTick() {
      assertTrue(VillageSimulationLod.NEAR.shouldRunFullTick(0L, 0));
      assertTrue(VillageSimulationLod.NEAR.shouldRunFullTick(1L, 99));
      assertTrue(VillageSimulationLod.NEAR.shouldRunFullTick(17L, -3));
   }

   @Test
   void farNeverRunsFullTick() {
      assertFalse(VillageSimulationLod.FAR.shouldRunFullTick(0L, 0));
      assertFalse(VillageSimulationLod.FAR.shouldRunFullTick(100L, 7));
   }

   @Test
   void midStaggersAcrossInterval() {
      // midInterval defaults to 2 when config is unavailable in unit tests.
      int hits = 0;
      for (long t = 0; t < 20; t++) {
         if (VillageSimulationLod.MID.shouldRunFullTick(t, 0)) {
            hits++;
         }
      }
      assertTrue(hits >= 8 && hits <= 12, "expected ~half of ticks for hash 0, got " + hits);

      int hitsOther = 0;
      for (long t = 0; t < 20; t++) {
         if (VillageSimulationLod.MID.shouldRunFullTick(t, 1)) {
            hitsOther++;
         }
      }
      assertTrue(hitsOther >= 8 && hitsOther <= 12, "expected ~half of ticks for hash 1, got " + hitsOther);
   }
}
