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
}
