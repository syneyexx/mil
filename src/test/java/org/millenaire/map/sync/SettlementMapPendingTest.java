package org.millenaire.map.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementMapPendingTest {
   @Test
   void dirtyAndRemovedTrackedSeparately() {
      SettlementMapPending p = new SettlementMapPending();
      UUID a = UUID.randomUUID();
      UUID b = UUID.randomUUID();
      p.markDirty(a);
      p.markRemoved(b);
      assertEquals(List.of(a), p.dirtyIds());
      assertEquals(List.of(b), p.removedIds());
      assertFalse(p.isFull());
      assertFalse(p.isEmpty());
   }

   @Test
   void copyIsIndependentSoClearAfterSendDoesNotLoseRetry() {
      SettlementMapPending p = new SettlementMapPending();
      UUID a = UUID.randomUUID();
      p.markDirty(a);
      p.markFull();
      SettlementMapPending retry = p.copy();
      p.clear();
      assertTrue(p.isEmpty());
      assertEquals(List.of(a), retry.dirtyIds());
      assertTrue(retry.isFull());
   }

   @Test
   void mergeCombinesPendingForFailedPlayers() {
      UUID a = UUID.randomUUID();
      UUID b = UUID.randomUUID();
      UUID gone = UUID.randomUUID();
      SettlementMapPending next = new SettlementMapPending();
      next.markDirty(b);
      SettlementMapPending failed = new SettlementMapPending();
      failed.markDirty(a);
      failed.markRemoved(gone);
      next.merge(failed);
      assertEquals(2, next.dirtyIds().size());
      assertEquals(List.of(gone), next.removedIds());
   }

   @Test
   void chunkSplitsAtMaxPerPacket() {
      List<Integer> items = new ArrayList<>();
      for (int i = 0; i < 450; i++) {
         items.add(i);
      }
      List<List<Integer>> chunks = SettlementMapPending.chunk(items, 200);
      assertEquals(3, chunks.size());
      assertEquals(200, chunks.get(0).size());
      assertEquals(50, chunks.get(2).size());
      assertTrue(SettlementMapPending.chunk(List.<Integer>of(), 200).isEmpty());
   }
}
