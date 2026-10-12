package org.millenaire.perf.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MillenaireComputePoolTest {
   @AfterEach
   void tearDown() {
      MillenaireComputePool.shutdown();
      ComputeMailbox.get().clear();
      ComputeMetrics.reset();
   }

   @Test
   void autoThreadCountClamps() {
      assertTrue(MillenaireComputePool.resolveThreadCount(0) >= 2);
      assertTrue(MillenaireComputePool.resolveThreadCount(0) <= 8);
      assertEquals(4, MillenaireComputePool.resolveThreadCount(4));
      assertEquals(32, MillenaireComputePool.resolveThreadCount(99));
   }

   @Test
   void submitCompletesIntoMailbox() throws Exception {
      MillenaireComputePool.start(2);
      CountDownLatch done = new CountDownLatch(1);
      assertTrue(MillenaireComputePool.submit(() -> {
         done.countDown();
         return level -> true;
      }));
      assertTrue(done.await(3, TimeUnit.SECONDS));
      for (int i = 0; i < 40 && ComputeMetrics.JOBS_COMPLETED.get() < 1; i++) {
         Thread.sleep(25);
      }
      assertTrue(ComputeMetrics.JOBS_COMPLETED.get() >= 1);
      assertTrue(ComputeMailbox.get().pending() >= 1);
   }

   @Test
   void shutdownRejectsNewWork() {
      MillenaireComputePool.start(1);
      MillenaireComputePool.shutdown();
      assertFalse(MillenaireComputePool.isRunning());
      assertFalse(MillenaireComputePool.submit(() -> level -> true));
   }

   @Test
   void mailboxCapsCompletedQueueAndDropsOldest() {
      ComputeMailbox mailbox = ComputeMailbox.get();
      mailbox.clear();
      int over = 10;
      for (int i = 0; i < ComputeOffload.MAILBOX_CAPACITY + over; i++) {
         assertTrue(mailbox.offer(level -> true));
      }
      assertEquals(ComputeOffload.MAILBOX_CAPACITY, mailbox.pending());
      assertEquals(over, ComputeMetrics.RESULTS_DROPPED_FULL.get());
      assertFalse(mailbox.offer(null));
      mailbox.clear();
      assertEquals(0, mailbox.pending());
   }

   @Test
   void shutdownAdvancesSessionEpoch() {
      int before = MillenaireComputePool.sessionEpoch();
      MillenaireComputePool.start(1);
      assertTrue(MillenaireComputePool.sessionEpoch() > before);
   }

   @Test
   void staleEpochResultIsNotOfferedToMailbox() throws Exception {
      MillenaireComputePool.start(1);
      ComputeMailbox.get().clear();
      CountDownLatch started = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      CountDownLatch finished = new CountDownLatch(1);
      assertTrue(MillenaireComputePool.submit(() -> {
         started.countDown();
         try {
            release.await(3, TimeUnit.SECONDS);
         } catch (InterruptedException ignored) {
            // shutdownNow interrupts the worker; the result must still be discarded.
         }
         finished.countDown();
         return level -> true;
      }));
      assertTrue(started.await(3, TimeUnit.SECONDS));
      MillenaireComputePool.shutdown();
      release.countDown();
      assertTrue(finished.await(3, TimeUnit.SECONDS));
      for (int i = 0; i < 40 && ComputeMetrics.RESULTS_DROPPED_EPOCH.get() < 1; i++) {
         Thread.sleep(25);
      }
      assertEquals(0, ComputeMailbox.get().pending());
      assertEquals(1, ComputeMetrics.RESULTS_DROPPED_EPOCH.get());
   }
}
