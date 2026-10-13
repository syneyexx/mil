package org.millenaire.perf.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DirtyFairQueueTest {
   @Test
   void pollLimitedDoesNotClearAndRespectsBudget() {
      DirtyFairQueue<UUID> queue = new DirtyFairQueue<>();
      UUID a = UUID.randomUUID();
      UUID b = UUID.randomUUID();
      UUID c = UUID.randomUUID();
      queue.mark(a, 10L);
      queue.mark(b, 20L);
      queue.mark(c, 30L);

      List<UUID> first = queue.pollLimited(2);
      assertEquals(2, first.size());
      assertEquals(3, queue.size());

      queue.clear(first.get(0));
      assertEquals(2, queue.size());
      assertFalse(queue.contains(first.get(0)));
      assertTrue(queue.oldestGameTime() >= 0L || queue.isEmpty());
   }

   @Test
   void clearAllResetsOldest() {
      DirtyFairQueue<UUID> queue = new DirtyFairQueue<>();
      queue.mark(UUID.randomUUID(), 5L);
      queue.clearAll();
      assertTrue(queue.isEmpty());
      assertEquals(-1L, queue.oldestGameTime());
   }
}
