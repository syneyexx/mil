package org.millenaire.perf.compute;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counters for the Millenaire compute offload pool (server-thread apply + worker execute).
 */
public final class ComputeMetrics {
   public static final AtomicLong JOBS_SUBMITTED = new AtomicLong();
   public static final AtomicLong JOBS_COMPLETED = new AtomicLong();
   public static final AtomicLong JOBS_REJECTED = new AtomicLong();
   public static final AtomicLong JOBS_FAILED = new AtomicLong();
   public static final AtomicLong RESULTS_APPLIED = new AtomicLong();
   public static final AtomicLong RESULTS_DROPPED_STALE = new AtomicLong();
   public static final AtomicLong RESULTS_DROPPED_FULL = new AtomicLong();
   public static final AtomicLong RESULTS_DROPPED_EPOCH = new AtomicLong();
   public static final AtomicLong APPLY_NANOS = new AtomicLong();
   public static final AtomicLong WORKER_NANOS = new AtomicLong();

   private ComputeMetrics() {
   }

   public static String snapshot() {
      return "submitted="
         + JOBS_SUBMITTED.get()
         + " completed="
         + JOBS_COMPLETED.get()
         + " rejected="
         + JOBS_REJECTED.get()
         + " failed="
         + JOBS_FAILED.get()
         + " applied="
         + RESULTS_APPLIED.get()
         + " stale="
         + RESULTS_DROPPED_STALE.get()
         + " droppedFull="
         + RESULTS_DROPPED_FULL.get()
         + " droppedEpoch="
         + RESULTS_DROPPED_EPOCH.get()
         + " applyMs="
         + APPLY_NANOS.get() / 1_000_000L
         + " workerMs="
         + WORKER_NANOS.get() / 1_000_000L
         + " pool="
         + MillenaireComputePool.describe();
   }

   public static void reset() {
      JOBS_SUBMITTED.set(0L);
      JOBS_COMPLETED.set(0L);
      JOBS_REJECTED.set(0L);
      JOBS_FAILED.set(0L);
      RESULTS_APPLIED.set(0L);
      RESULTS_DROPPED_STALE.set(0L);
      RESULTS_DROPPED_FULL.set(0L);
      RESULTS_DROPPED_EPOCH.set(0L);
      APPLY_NANOS.set(0L);
      WORKER_NANOS.set(0L);
   }
}
