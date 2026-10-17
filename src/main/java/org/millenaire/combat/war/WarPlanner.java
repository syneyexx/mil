package org.millenaire.combat.war;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.millenaire.perf.TickPerfCounters;
import org.millenaire.perf.plan.BackgroundWorkGate;
import org.millenaire.perf.plan.DirtyFairQueue;
import org.millenaire.perf.plan.PlanDomain;
import org.millenaire.perf.plan.PlanPriority;
import org.slf4j.Logger;

/**
 * Background war planner. Workers receive only immutable snapshots — never live world objects.
 * Rejection never runs on the caller thread; in-flight and gate slots are always cleared.
 */
public final class WarPlanner {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final WarPlanner INSTANCE = new WarPlanner();

   private final AtomicInteger inFlight = new AtomicInteger();
   private final Set<UUID> inflightCampaigns = ConcurrentHashMap.newKeySet();
   private final DirtyFairQueue<UUID> dirtyCampaigns = new DirtyFairQueue<>();
   private final ArrayBlockingQueue<WarPlanResult> results = new ArrayBlockingQueue<>(64);
   private final ArrayBlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(64);
   private ExecutorService executor;
   private volatile boolean shutdown;

   private WarPlanner() {
   }

   public static WarPlanner get() {
      return INSTANCE;
   }

   public void onServerStarted(int threads) {
      shutdown();
      this.shutdown = false;
      int n = Math.max(1, Math.min(2, threads));
      ThreadFactory factory = r -> {
         Thread t = new Thread(r, "millenaire-war-planner");
         t.setDaemon(true);
         t.setPriority(Thread.MIN_PRIORITY + 1);
         return t;
      };
      this.executor = new ThreadPoolExecutor(
         n,
         n,
         60L,
         TimeUnit.SECONDS,
         this.workQueue,
         factory,
         (runnable, pool) -> {
            TickPerfCounters.WAR_QUEUE_REJECTS.incrementAndGet();
            throw new RejectedExecutionException("war planner queue full");
         }
      );
   }

   public void shutdown() {
      this.shutdown = true;
      this.inflightCampaigns.clear();
      this.dirtyCampaigns.clearAll();
      this.results.clear();
      this.workQueue.clear();
      if (this.executor != null) {
         this.executor.shutdownNow();
         this.executor = null;
      }
      this.inFlight.set(0);
   }

   public void markDirty(UUID campaignId) {
      markDirty(campaignId, 0L);
   }

   public void markDirty(UUID campaignId, long gameTime) {
      if (campaignId != null) {
         this.dirtyCampaigns.mark(campaignId, gameTime);
         BackgroundWorkGate.get().noteQueued(PlanDomain.WAR, gameTime);
      }
   }

   public boolean canSubmit(UUID campaignId) {
      return !this.shutdown
         && this.executor != null
         && campaignId != null
         && !this.inflightCampaigns.contains(campaignId)
         && this.inFlight.get() < 4
         && !BackgroundWorkGate.get().isShutdown();
   }

   public void submit(WarSnapshot snapshot) {
      if (snapshot == null || !canSubmit(snapshot.campaignId())) {
         if (snapshot != null) {
            this.dirtyCampaigns.mark(snapshot.campaignId(), snapshot.gameTime());
         }
         return;
      }
      if (!BackgroundWorkGate.get().tryAcquire(PlanDomain.WAR, PlanPriority.SIM, snapshot.gameTime())) {
         this.dirtyCampaigns.mark(snapshot.campaignId(), snapshot.gameTime());
         return;
      }

      this.dirtyCampaigns.clear(snapshot.campaignId());
      this.inflightCampaigns.add(snapshot.campaignId());
      this.inFlight.incrementAndGet();
      try {
         this.executor.execute(() -> {
            try {
               WarPlanResult result = plan(snapshot);
               if (!this.results.offer(result)) {
                  this.dirtyCampaigns.mark(snapshot.campaignId(), snapshot.gameTime());
               }
            } catch (Throwable t) {
               LOGGER.warn("[War] Plan failed for {}: {}", snapshot.campaignId(), t.toString());
               this.results.offer(WarPlanResult.failed(snapshot.campaignId(), snapshot.worldSessionId(), "exception"));
            } finally {
               this.inflightCampaigns.remove(snapshot.campaignId());
               this.inFlight.decrementAndGet();
               BackgroundWorkGate.get().release(PlanDomain.WAR);
            }
         });
      } catch (RuntimeException e) {
         this.inflightCampaigns.remove(snapshot.campaignId());
         this.inFlight.decrementAndGet();
         BackgroundWorkGate.get().release(PlanDomain.WAR);
         this.dirtyCampaigns.mark(snapshot.campaignId(), snapshot.gameTime());
         TickPerfCounters.WAR_QUEUE_REJECTS.incrementAndGet();
      }
   }

   public List<WarPlanResult> drainResults(int max) {
      List<WarPlanResult> out = new ArrayList<>(Math.min(max, 4));
      WarPlanResult r;
      while (out.size() < max && (r = this.results.poll()) != null) {
         out.add(r);
      }
      if (this.results.isEmpty() && this.dirtyCampaigns.isEmpty()) {
         BackgroundWorkGate.get().clearQueuedMarker(PlanDomain.WAR);
      }
      return out;
   }

   public List<UUID> pollDirtyCampaigns(int max) {
      return this.dirtyCampaigns.pollLimited(max);
   }

   /** @deprecated use {@link #pollDirtyCampaigns(int)} */
   @Deprecated
   public Set<UUID> pollDirtyCampaigns() {
      return Set.copyOf(this.dirtyCampaigns.pollLimited(64));
   }

   public void clearDirty(UUID campaignId) {
      this.dirtyCampaigns.clear(campaignId);
   }

   public int resultQueueDepth() {
      return this.results.size();
   }

   static WarPlanResult plan(WarSnapshot snap) {
      if (!snap.warEnabled()) {
         return WarPlanResult.none(snap.campaignId(), snap.worldSessionId());
      }
      if (snap.phase() == WarCampaign.Phase.ENDED) {
         return WarPlanResult.none(snap.campaignId(), snap.worldSessionId());
      }

      if (snap.phase() == WarCampaign.Phase.WARNING || snap.frontVillageId() == null) {
         UUID front = snap.frontVillageId();
         UUID attacker = snap.attackerVillageId();
         UUID defender = snap.defenderVillageId();
         if (front == null && !snap.candidateFrontVillages().isEmpty()) {
            front = snap.candidateFrontVillages().getFirst();
            defender = front;
         }
         if (attacker == null && snap.candidateFrontVillages().size() > 1) {
            attacker = snap.candidateFrontVillages().get(1);
         } else if (attacker == null && !snap.candidateFrontVillages().isEmpty()) {
            attacker = snap.candidateFrontVillages().getFirst();
         }
         long nextWave = snap.gameTime() + Math.max(200L, snap.waveIntervalTicks() / 2L);
         return new WarPlanResult(
            snap.campaignId(),
            snap.worldSessionId(),
            WarPlanResult.Kind.PICK_FRONT,
            front,
            attacker,
            defender,
            nextWave,
            List.of(),
            "pick_front"
         );
      }

      if (snap.phase() == WarCampaign.Phase.ACTIVE || snap.phase() == WarCampaign.Phase.ARMISTICE) {
         if (snap.waveIndex() >= snap.wavesTotal()) {
            return new WarPlanResult(
               snap.campaignId(),
               snap.worldSessionId(),
               WarPlanResult.Kind.END_CAMPAIGN,
               snap.frontVillageId(),
               snap.attackerVillageId(),
               snap.defenderVillageId(),
               -1L,
               List.of(),
               "waves_complete"
            );
         }
         if (snap.gameTime() >= snap.nextWaveGameTime()) {
            List<UUID> sabotage = new ArrayList<>();
            if (snap.buildingDamageEnabled()) {
               int max = Math.min(4, snap.candidateSabotageBuildings().size());
               for (int i = 0; i < max; i++) {
                  sabotage.add(snap.candidateSabotageBuildings().get(i));
               }
            }
            return new WarPlanResult(
               snap.campaignId(),
               snap.worldSessionId(),
               WarPlanResult.Kind.SCHEDULE_WAVE,
               snap.frontVillageId(),
               snap.attackerVillageId(),
               snap.defenderVillageId(),
               snap.gameTime(),
               sabotage,
               "schedule_wave"
            );
         }
      }

      if (snap.phase() == WarCampaign.Phase.WAVE_FIGHT
         && snap.buildingDamageEnabled()
         && !snap.candidateSabotageBuildings().isEmpty()) {
         List<UUID> sabotage = snap.candidateSabotageBuildings().stream().limit(4).toList();
         return new WarPlanResult(
            snap.campaignId(),
            snap.worldSessionId(),
            WarPlanResult.Kind.SET_SABOTAGE,
            snap.frontVillageId(),
            snap.attackerVillageId(),
            snap.defenderVillageId(),
            -1L,
            sabotage,
            "refresh_sabotage"
         );
      }

      return WarPlanResult.none(snap.campaignId(), snap.worldSessionId());
   }
}
