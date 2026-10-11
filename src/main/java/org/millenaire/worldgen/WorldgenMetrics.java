package org.millenaire.worldgen;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.millenaire.worldgen.plan.SettlementFailureReason;

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
   private static final Map<String, AtomicLong> REJECT_REASONS = new ConcurrentHashMap<>();

   private WorldgenMetrics() {
   }

   public static void recordReject(SettlementFailureReason reason, String detail) {
      String key = (reason == null ? "UNKNOWN" : reason.name())
         + (detail == null || detail.isBlank() ? "" : ":" + detail);
      REJECT_REASONS.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
   }

   public static String snapshot() {
      String reasons = REJECT_REASONS.isEmpty()
         ? ""
         : " rejectReasons={"
            + REJECT_REASONS.entrySet()
               .stream()
               .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
               .limit(8)
               .map(e -> e.getKey() + "=" + e.getValue().get())
               .collect(Collectors.joining(", "))
            + "}";
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
         + ACTIVATION_FAILURES.get()
         + reasons;
   }
}
