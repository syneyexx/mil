package org.millenaire.civilization.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TradeCorridorServiceTest {
   @Test
   void recognizesAtThreshold() {
      TradeCorridorRecord current = null;
      for (int i = 1; i < InteractionConfig.CORRIDOR_RECOGNITION_THRESHOLD; i++) {
         current = TradeCorridorService.incrementPure(current, "a", "b", "v1", "v2");
         assertFalse(current.recognized(), "should not recognize at " + i);
      }
      current = TradeCorridorService.incrementPure(current, "a", "b", "v1", "v2");
      assertTrue(current.recognized());
      assertEquals(InteractionConfig.CORRIDOR_RECOGNITION_THRESHOLD, current.successfulDeliveries());
   }

   @Test
   void keepsDisruptedFlagAcrossIncrements() {
      TradeCorridorRecord base = new TradeCorridorRecord("a", "b", "v1", "v2", 3, false, true, null);
      TradeCorridorRecord next = TradeCorridorService.incrementPure(base, "a", "b", "v1", "v2");
      assertTrue(next.disrupted());
      assertEquals(4, next.successfulDeliveries());
   }
}
