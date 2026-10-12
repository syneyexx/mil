package org.millenaire.perf.compute;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.perf.tick.ServerTickGovernor;
import org.slf4j.Logger;

/**
 * Thread-safe bounded completed-result queue. Drain/apply only from the server thread.
 * When full, the oldest result is dropped (newer results supersede older path/plan generations).
 */
public final class ComputeMailbox {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final ComputeMailbox INSTANCE = new ComputeMailbox();

   private final ConcurrentLinkedQueue<ComputeResult> completed = new ConcurrentLinkedQueue<>();
   private final AtomicInteger pendingCount = new AtomicInteger();

   private ComputeMailbox() {
   }

   public static ComputeMailbox get() {
      return INSTANCE;
   }

   /** @return true if queued; false if {@code result} was null */
   public boolean offer(ComputeResult result) {
      if (result == null) {
         return false;
      }
      this.pendingCount.incrementAndGet();
      this.completed.offer(result);
      while (this.pendingCount.get() > ComputeOffload.MAILBOX_CAPACITY) {
         if (this.completed.poll() == null) {
            break;
         }
         this.pendingCount.decrementAndGet();
         ComputeMetrics.RESULTS_DROPPED_FULL.incrementAndGet();
      }
      return true;
   }

   public int pending() {
      return Math.max(0, this.pendingCount.get());
   }

   /**
    * Poll up to {@code budget} results (every polled attempt counts, including failures) and apply them
    * on the server thread. Stops early when the tick governor deadline is spent, but always attempts
    * at least one result so the queue cannot starve.
    *
    * @return results that were applied or ignored as stale (failed applies are not counted)
    */
   public int drainAndApply(ServerLevel level, int budget) {
      if (level == null || budget <= 0) {
         return 0;
      }
      int applied = 0;
      int attempts = 0;
      long start = System.nanoTime();
      ComputeResult next;
      while (attempts < budget && (attempts == 0 || ServerTickGovernor.hasTime()) && (next = this.completed.poll()) != null) {
         this.pendingCount.decrementAndGet();
         attempts++;
         try {
            if (next.apply(level)) {
               ComputeMetrics.RESULTS_APPLIED.incrementAndGet();
            } else {
               ComputeMetrics.RESULTS_DROPPED_STALE.incrementAndGet();
            }
            applied++;
         } catch (Exception e) {
            ComputeMetrics.JOBS_FAILED.incrementAndGet();
            LOGGER.error("[Millenaire] Compute result apply failed: {}", e.toString(), e);
         }
      }
      ComputeMetrics.APPLY_NANOS.addAndGet(Math.max(0L, System.nanoTime() - start));
      return applied;
   }

   public void clear() {
      List<ComputeResult> dropped = new ArrayList<>();
      ComputeResult next;
      while ((next = this.completed.poll()) != null) {
         dropped.add(next);
      }
      this.pendingCount.set(0);
      MillenaireComputePool.advanceEpoch();
      if (!dropped.isEmpty()) {
         ComputeMetrics.RESULTS_DROPPED_STALE.addAndGet(dropped.size());
      }
   }
}
