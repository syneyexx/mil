package org.millenaire.perf.plan;

import com.mojang.logging.LogUtils;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.millenaire.perf.TickPerfCounters;
import org.slf4j.Logger;

/**
 * Shared limit on background planning across religion, war, roads, and new simulations.
 * Keeps domain planners separate but prevents unbounded concurrent CPU work.
 *
 * <p>Server thread must never block on workers. Acquire/release are lock-free atomics.
 */
public final class BackgroundWorkGate {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final BackgroundWorkGate INSTANCE = new BackgroundWorkGate();

   /** Default joint in-flight across all domains. */
   public static final int DEFAULT_MAX_IN_FLIGHT_TOTAL = 4;
   /** Soft per-domain cap so one domain cannot monopolize the gate. */
   public static final int DEFAULT_MAX_PER_DOMAIN = 2;
   /** Game ticks before a waiting domain gets starvation boost. */
   public static final long DEFAULT_MAX_WAIT_GAME_TICKS = 200L;

   private final AtomicInteger totalInFlight = new AtomicInteger();
   private final Map<PlanDomain, AtomicInteger> domainInFlight = new EnumMap<>(PlanDomain.class);
   private final Map<PlanDomain, AtomicLong> oldestQueuedGameTime = new EnumMap<>(PlanDomain.class);
   private final Map<PlanDomain, AtomicLong> rejects = new EnumMap<>(PlanDomain.class);
   private final Map<PlanDomain, AtomicLong> acquires = new EnumMap<>(PlanDomain.class);

   private volatile int maxInFlightTotal = DEFAULT_MAX_IN_FLIGHT_TOTAL;
   private volatile int maxPerDomain = DEFAULT_MAX_PER_DOMAIN;
   private volatile long maxWaitGameTicks = DEFAULT_MAX_WAIT_GAME_TICKS;
   private volatile long worldSessionId;
   private volatile boolean shutdown = true;

   private BackgroundWorkGate() {
      for (PlanDomain d : PlanDomain.values()) {
         this.domainInFlight.put(d, new AtomicInteger());
         this.oldestQueuedGameTime.put(d, new AtomicLong(-1L));
         this.rejects.put(d, new AtomicLong());
         this.acquires.put(d, new AtomicLong());
      }
   }

   public static BackgroundWorkGate get() {
      return INSTANCE;
   }

   public void onServerStarted(long worldSessionId, int maxInFlightTotal, int maxPerDomain) {
      shutdown();
      this.shutdown = false;
      this.worldSessionId = worldSessionId;
      this.maxInFlightTotal = Math.max(1, Math.min(8, maxInFlightTotal));
      this.maxPerDomain = Math.max(1, Math.min(4, maxPerDomain));
      LOGGER.debug(
         "[PlanGate] started session={} maxTotal={} maxPerDomain={}",
         this.worldSessionId,
         this.maxInFlightTotal,
         this.maxPerDomain
      );
   }

   public void shutdown() {
      this.shutdown = true;
      this.totalInFlight.set(0);
      for (PlanDomain d : PlanDomain.values()) {
         this.domainInFlight.get(d).set(0);
         this.oldestQueuedGameTime.get(d).set(-1L);
      }
   }

   public long worldSessionId() {
      return this.worldSessionId;
   }

   public boolean isShutdown() {
      return this.shutdown;
   }

   public void noteQueued(PlanDomain domain, long gameTime) {
      AtomicLong oldest = this.oldestQueuedGameTime.get(domain);
      long prev = oldest.get();
      if (prev < 0L || gameTime < prev) {
         oldest.compareAndSet(prev, gameTime);
      }
   }

   public void clearQueuedMarker(PlanDomain domain) {
      this.oldestQueuedGameTime.get(domain).set(-1L);
   }

   /**
    * Try to acquire a background slot. Never blocks.
    * Starvation: if this domain has waited longer than {@link #maxWaitGameTicks},
    * soft per-domain cap is relaxed while total cap still applies.
    */
   public boolean tryAcquire(PlanDomain domain, PlanPriority priority, long gameTime) {
      if (this.shutdown) {
         return false;
      }
      AtomicInteger domainCount = this.domainInFlight.get(domain);
      int domainNow = domainCount.get();
      boolean starving = isStarving(domain, gameTime);
      int domainCap = starving || priority == PlanPriority.PLAYER || priority == PlanPriority.MUST_FINISH
         ? this.maxInFlightTotal
         : this.maxPerDomain;
      if (domainNow >= domainCap) {
         this.rejects.get(domain).incrementAndGet();
         TickPerfCounters.PLAN_GATE_REJECTS.incrementAndGet();
         return false;
      }
      int totalNow = this.totalInFlight.get();
      if (totalNow >= this.maxInFlightTotal && priority == PlanPriority.SIM && !starving) {
         this.rejects.get(domain).incrementAndGet();
         TickPerfCounters.PLAN_GATE_REJECTS.incrementAndGet();
         return false;
      }
      if (totalNow >= this.maxInFlightTotal + 1) {
         this.rejects.get(domain).incrementAndGet();
         TickPerfCounters.PLAN_GATE_REJECTS.incrementAndGet();
         return false;
      }
      if (!domainCount.compareAndSet(domainNow, domainNow + 1)) {
         return false;
      }
      this.totalInFlight.incrementAndGet();
      this.acquires.get(domain).incrementAndGet();
      TickPerfCounters.PLAN_GATE_ACQUIRES.incrementAndGet();
      TickPerfCounters.PLAN_GATE_IN_FLIGHT.set(this.totalInFlight.get());
      return true;
   }

   public void release(PlanDomain domain) {
      this.domainInFlight.get(domain).updateAndGet(v -> Math.max(0, v - 1));
      this.totalInFlight.updateAndGet(v -> Math.max(0, v - 1));
      TickPerfCounters.PLAN_GATE_IN_FLIGHT.set(this.totalInFlight.get());
   }

   private boolean isStarving(PlanDomain domain, long gameTime) {
      long oldest = this.oldestQueuedGameTime.get(domain).get();
      return oldest >= 0L && gameTime - oldest >= this.maxWaitGameTicks;
   }

   public int totalInFlight() {
      return this.totalInFlight.get();
   }

   public int domainInFlight(PlanDomain domain) {
      return this.domainInFlight.get(domain).get();
   }

   public String statusLine() {
      StringBuilder sb = new StringBuilder("planGate session=")
         .append(this.worldSessionId)
         .append(" inFlight=")
         .append(this.totalInFlight.get())
         .append('/')
         .append(this.maxInFlightTotal);
      for (PlanDomain d : PlanDomain.values()) {
         int inf = this.domainInFlight.get(d).get();
         if (inf > 0 || this.acquires.get(d).get() > 0L) {
            sb.append(' ')
               .append(d.name())
               .append('=')
               .append(inf)
               .append("/a")
               .append(this.acquires.get(d).get())
               .append("/r")
               .append(this.rejects.get(d).get());
         }
      }
      return sb.toString();
   }
}
