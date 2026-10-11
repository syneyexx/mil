package org.millenaire.village;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VillageWorkSliceTest {
   @Test
   void whenHasTimeAllSlicesAllowed() {
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
