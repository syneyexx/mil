package org.millenaire.perf.compute;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

/**
 * Thread-safe completed-result queue. Drain/apply only from the server thread.
 */
public final class ComputeMailbox {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final ComputeMailbox INSTANCE = new ComputeMailbox();

   private final ConcurrentLinkedQueue<ComputeResult> completed = new ConcurrentLinkedQueue<>();

   private ComputeMailbox() {
   }

   public static ComputeMailbox get() {
      return INSTANCE;
   }

   public void offer(ComputeResult result) {
      if (result != null) {
         this.completed.offer(result);
      }
   }

   public int pending() {
      return this.completed.size();
   }

   /**
    * Drain up to {@code budget} results and apply them on the server thread.
    */
   public int drainAndApply(ServerLevel level, int budget) {
      if (level == null || budget <= 0) {
         return 0;
      }
      int applied = 0;
      long start = System.nanoTime();
      ComputeResult next;
      while (applied < budget && (next = this.completed.poll()) != null) {
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
      if (!dropped.isEmpty()) {
         ComputeMetrics.RESULTS_DROPPED_STALE.addAndGet(dropped.size());
      }
   }
}
