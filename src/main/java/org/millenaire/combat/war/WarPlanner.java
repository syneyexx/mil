package org.millenaire.combat.war;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;

/**
 * Background war planner. Workers receive only immutable snapshots — never live world objects.
 * Rejection does not run on the caller (server) thread. Coalesces one in-flight plan per campaign.
 */
public final class WarPlanner {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final WarPlanner INSTANCE = new WarPlanner();

   private final AtomicInteger inFlight = new AtomicInteger();
   private final Set<UUID> inflightCampaigns = ConcurrentHashMap.newKeySet();
   private final Set<UUID> dirtyCampaigns = ConcurrentHashMap.newKeySet();
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
         (runnable, pool) -> LOGGER.debug("[War] Planner queue full; dropping submit")
      );
   }

   public void shutdown() {
      this.shutdown = true;
      this.inflightCampaigns.clear();
      this.dirtyCampaigns.clear();
      this.results.clear();
      this.workQueue.clear();
      if (this.executor != null) {
         this.executor.shutdownNow();
         this.executor = null;
      }
      this.inFlight.set(0);
   }

   public void markDirty(UUID campaignId) {
      if (campaignId != null) {
         this.dirtyCampaigns.add(campaignId);
      }
   }

   public boolean canSubmit(UUID campaignId) {
      return !this.shutdown
         && this.executor != null
         && campaignId != null
         && !this.inflightCampaigns.contains(campaignId)
         && this.inFlight.get() < 4;
   }

   public void submit(WarSnapshot snapshot) {
      if (!canSubmit(snapshot.campaignId())) {
         this.dirtyCampaigns.add(snapshot.campaignId());
         return;
      }
      this.dirtyCampaigns.remove(snapshot.campaignId());
      this.inflightCampaigns.add(snapshot.campaignId());
      this.inFlight.incrementAndGet();
      try {
         this.executor.execute(() -> {
            try {
               WarPlanResult result = plan(snapshot);
               if (!this.results.offer(result)) {
                  this.dirtyCampaigns.add(snapshot.campaignId());
               }
            } catch (Throwable t) {
               LOGGER.warn("[War] Plan failed for {}: {}", snapshot.campaignId(), t.toString());
               this.results.offer(WarPlanResult.failed(snapshot.campaignId(), snapshot.worldSessionId(), "exception"));
            } finally {
               this.inflightCampaigns.remove(snapshot.campaignId());
               this.inFlight.decrementAndGet();
            }
         });
      } catch (RuntimeException e) {
         this.inflightCampaigns.remove(snapshot.campaignId());
         this.inFlight.decrementAndGet();
         this.dirtyCampaigns.add(snapshot.campaignId());
      }
   }

   public List<WarPlanResult> drainResults(int max) {
      List<WarPlanResult> out = new ArrayList<>(Math.min(max, 4));
      WarPlanResult r;
      while (out.size() < max && (r = this.results.poll()) != null) {
         out.add(r);
      }
      return out;
   }

   public Set<UUID> pollDirtyCampaigns() {
      return Set.copyOf(this.dirtyCampaigns);
   }

   public void clearDirty(UUID campaignId) {
      this.dirtyCampaigns.remove(campaignId);
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
         && !snap.candidateSabotageBuildings().isEmpty()
         && snap.candidateSabotageBuildings().size() > 0) {
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
