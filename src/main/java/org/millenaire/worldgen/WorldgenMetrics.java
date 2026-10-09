package org.millenaire.worldgen;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight counters for worldgen cost. Opt-in logging via worldgenDetailedDiagnostics.
 * No unbounded retention.
 */
public final class WorldgenMetrics {
   public static final AtomicLong CANDIDATES_EVALUATED = new AtomicLong();
   public static final AtomicLong PLANS_CREATED = new AtomicLong();
   public static final AtomicLong PLANS_REJECTED = new AtomicLong();
   public static final AtomicLong CHUNKS_MATERIALIZED = new AtomicLong();
   public static final AtomicLong ACTIVATIONS = new AtomicLong();
   public static final AtomicLong ACTIVATION_FAILURES = new AtomicLong();

   private WorldgenMetrics() {
   }

   public static String snapshot() {
      return "candidates="
         + CANDIDATES_EVALUATED.get()
         + " plans="
         + PLANS_CREATED.get()
         + " rejected="
         + PLANS_REJECTED.get()
         + " chunks="
         + CHUNKS_MATERIALIZED.get()
         + " activations="
         + ACTIVATIONS.get()
         + " actFail="
         + ACTIVATION_FAILURES.get();
   }
}
