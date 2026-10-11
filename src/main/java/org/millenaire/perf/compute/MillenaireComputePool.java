package org.millenaire.perf.compute;

import com.mojang.logging.LogUtils;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import org.slf4j.Logger;

/**
 * Bounded daemon worker pool for pure Millenaire compute. Never mutates the world.
 */
public final class MillenaireComputePool {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final AtomicInteger THREAD_SEQ = new AtomicInteger();
   private static final int QUEUE_CAPACITY = 256;

   @Nullable
   private static volatile ThreadPoolExecutor executor;
   private static volatile int configuredThreads;

   private MillenaireComputePool() {
   }

   public static synchronized void start(int configuredWorkerThreads) {
      shutdown();
      int threads = resolveThreadCount(configuredWorkerThreads);
      configuredThreads = threads;
      ThreadFactory factory = r -> {
         Thread t = new Thread(r, "millenaire-compute-" + THREAD_SEQ.incrementAndGet());
         t.setDaemon(true);
         t.setPriority(Thread.NORM_PRIORITY - 1);
         return t;
      };
      ThreadPoolExecutor pool = new ThreadPoolExecutor(
         threads,
         threads,
         60L,
         TimeUnit.SECONDS,
         new LinkedBlockingQueue<>(QUEUE_CAPACITY),
         factory,
         new ThreadPoolExecutor.AbortPolicy()
      );
      pool.allowCoreThreadTimeOut(true);
      executor = pool;
      ComputeMetrics.reset();
      LOGGER.info("[Millenaire] Compute pool started ({} workers, queue={})", threads, QUEUE_CAPACITY);
   }

   public static synchronized void shutdown() {
      ExecutorService pool = executor;
      executor = null;
      ComputeMailbox.get().clear();
      if (pool == null) {
         return;
      }
      pool.shutdownNow();
      try {
         if (!pool.awaitTermination(2L, TimeUnit.SECONDS)) {
            LOGGER.warn("[Millenaire] Compute pool did not terminate cleanly");
         }
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
      LOGGER.info("[Millenaire] Compute pool stopped");
   }

   public static boolean isRunning() {
      ThreadPoolExecutor pool = executor;
      return pool != null && !pool.isShutdown();
   }

   public static String describe() {
      ThreadPoolExecutor pool = executor;
      if (pool == null) {
         return "stopped";
      }
      return "threads="
         + configuredThreads
         + " active="
         + pool.getActiveCount()
         + " queued="
         + pool.getQueue().size()
         + " mailbox="
         + ComputeMailbox.get().pending();
   }

   public static int resolveThreadCount(int configured) {
      if (configured > 0) {
         return Math.min(32, Math.max(1, configured));
      }
      int procs = Runtime.getRuntime().availableProcessors();
      return Math.min(8, Math.max(2, procs / 4));
   }

   /**
    * Submit pure compute. Completed results are offered to {@link ComputeMailbox}.
    *
    * @return true if accepted
    */
   public static boolean submit(ComputeJob job) {
      ThreadPoolExecutor pool = executor;
      if (pool == null || job == null) {
         ComputeMetrics.JOBS_REJECTED.incrementAndGet();
         return false;
      }
      try {
         pool.execute(() -> runJob(job));
         ComputeMetrics.JOBS_SUBMITTED.incrementAndGet();
         return true;
      } catch (RejectedExecutionException e) {
         ComputeMetrics.JOBS_REJECTED.incrementAndGet();
         return false;
      }
   }

   private static void runJob(ComputeJob job) {
      long start = System.nanoTime();
      try {
         ComputeResult result = job.call();
         ComputeMetrics.JOBS_COMPLETED.incrementAndGet();
         if (result != null) {
            ComputeMailbox.get().offer(result);
         }
      } catch (Throwable t) {
         ComputeMetrics.JOBS_FAILED.incrementAndGet();
         LOGGER.error("[Millenaire] Compute job failed: {}", t.toString(), t);
      } finally {
         ComputeMetrics.WORKER_NANOS.addAndGet(Math.max(0L, System.nanoTime() - start));
      }
   }
}
