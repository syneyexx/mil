package org.millenaire.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VillageTickSlotsTest {
   @Test
   void everyItemDueExactlyOncePerInterval() {
      List<Integer> items = new ArrayList<>();
      for (int i = -50; i < 150; i++) {
         items.add(i);
      }
      VillageTickSlots<Integer> slots = new VillageTickSlots<>();
      slots.rebuild(items, Integer::intValue, 20);
      Set<Integer> seen = new HashSet<>();
      int total = 0;
      for (long t = 1000; t < 1020; t++) {
         for (Integer item : slots.due(t)) {
            assertTrue(seen.add(item), "duplicate " + item);
            total++;
         }
      }
      assertEquals(items.size(), total);
   }

   @Test
   void dueMatchesStaggerFormula() {
      VillageTickSlots<Integer> slots = new VillageTickSlots<>();
      slots.rebuild(List.of(3, 23, 7), Integer::intValue, 20);
      assertTrue(slots.due(3L).contains(3));
      assertTrue(slots.due(23L).contains(23));
      assertTrue(slots.due(43L).contains(3));
      assertTrue(slots.due(7L).contains(7));
   }

   @Test
   void emptyBeforeRebuild() {
      assertTrue(new VillageTickSlots<Integer>().due(5L).isEmpty());
   }
}
