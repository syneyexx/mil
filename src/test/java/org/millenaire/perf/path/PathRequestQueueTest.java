package org.millenaire.perf.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PathRequestQueueTest {
   @AfterEach
   void tearDown() {
      PathRequestQueue.resetForTest();
   }

   @Test
   void capsStartsPerTick() {
      PathRequestQueue.ensureTick(10L);
      assertTrue(PathRequestQueue.tryBeginSearch(1, 10L));
      PathRequestQueue.completeSearch(1);
      assertTrue(PathRequestQueue.tryBeginSearch(2, 10L));
      PathRequestQueue.completeSearch(2);
      // Default max is 2
      assertFalse(PathRequestQueue.tryBeginSearch(3, 10L));
      assertEquals(2, PathRequestQueue.startsThisTick());
      assertEquals(1, PathRequestQueue.rejectsThisTick());
   }

   @Test
   void coalescesInFlightSameEntity() {
      PathRequestQueue.ensureTick(11L);
      assertTrue(PathRequestQueue.tryBeginSearch(5, 11L));
      assertFalse(PathRequestQueue.tryBeginSearch(5, 11L));
      assertEquals(1, PathRequestQueue.coalescedThisTick());
      PathRequestQueue.completeSearch(5);
   }

   @Test
   void resetsOnNewGameTime() {
      PathRequestQueue.ensureTick(1L);
      assertTrue(PathRequestQueue.tryBeginSearch(1, 1L));
      PathRequestQueue.completeSearch(1);
      assertTrue(PathRequestQueue.tryBeginSearch(2, 1L));
      PathRequestQueue.completeSearch(2);
      assertFalse(PathRequestQueue.tryBeginSearch(3, 1L));

      PathRequestQueue.ensureTick(2L);
      assertTrue(PathRequestQueue.tryBeginSearch(3, 2L));
      assertEquals(0, PathRequestQueue.rejectsThisTick());
   }

   @Test
   void diagnosticContainsCounters() {
      PathRequestQueue.ensureTick(3L);
      PathRequestQueue.tryBeginSearch(1, 3L);
      String d = PathRequestQueue.diagnostic();
      assertTrue(d.contains("pathQ={"), d);
      assertTrue(d.contains("starts="), d);
   }

   @Test
   void rejectedStartsAreNotRetainedButCountedCumulatively() {
      PathRequestQueue.ensureTick(20L);
      assertTrue(PathRequestQueue.tryBeginSearch(1, 20L));
      assertTrue(PathRequestQueue.tryBeginSearch(2, 20L));
      assertFalse(PathRequestQueue.tryBeginSearch(3, 20L));
      PathRequestQueue.ensureTick(21L);
      assertEquals(0, PathRequestQueue.inFlightCount());
      assertEquals(1L, PathRequestQueue.totalRejects());
      assertEquals(2L, PathRequestQueue.totalStarts());
   }
}
