package org.millenaire.village;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.millenaire.perf.tick.ServerTickGovernor;

class VillageWorkSliceTest {
   @AfterEach
   void tearDown() {
      ServerTickGovernor.resetForTest();
   }

   @Test
   void whenHasTimeAndHealthyAllSlicesAllowed() {
      ServerTickGovernor.resetForTest();
      for (VillageWorkSlice slice : VillageWorkSlice.values()) {
         assertTrue(VillageWorkSlice.allow(slice, VillageWorkSlice.PATHS, true));
      }
   }

   @Test
   void whenOverBudgetOnlyBookkeepingAndFocus() {
      assertTrue(VillageWorkSlice.allow(VillageWorkSlice.BOOKKEEPING, VillageWorkSlice.GROWTH, false));
      assertTrue(VillageWorkSlice.allow(VillageWorkSlice.GROWTH, VillageWorkSlice.GROWTH, false));
      assertFalse(VillageWorkSlice.allow(VillageWorkSlice.PATHS, VillageWorkSlice.GROWTH, false));
   }
}
