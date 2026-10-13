package org.millenaire.faith;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.millenaire.perf.TickPerfCounters;
import org.slf4j.Logger;

/**
 * Background religion planner. Workers receive only immutable snapshots — never live world objects.
 * Rejection does not run on the caller (server) thread.
 */
public final class ReligionPlanner {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final ReligionPlanner INSTANCE = new ReligionPlanner();

   private final AtomicInteger inFlight = new AtomicInteger();
   private final Set<UUID> inflightVillages = ConcurrentHashMap.newKeySet();
   private final Set<UUID> dirtyVillages = ConcurrentHashMap.newKeySet();
   private final ArrayBlockingQueue<ReligionPlanResult> results = new ArrayBlockingQueue<>(64);
   private final ArrayBlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(64);
   private ExecutorService executor;
   private volatile boolean shutdown;
   private volatile long oldestQueuedGameTime = -1L;

   private ReligionPlanner() {
   }

   public static ReligionPlanner get() {
      return INSTANCE;
   }

   public void onServerStarted(int threads) {
      shutdown();
      this.shutdown = false;
      int n = Math.max(1, Math.min(4, threads));
      ThreadFactory factory = r -> {
         Thread t = new Thread(r, "millenaire-religion-planner");
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
            // Do NOT run on caller. Mark dirty for retry with jitter via deadline elsewhere.
            TickPerfCounters.RELIGION_QUEUE_REJECTS.incrementAndGet();
            LOGGER.debug("[Religion] Planner queue full; dropping submit");
         }
      );
   }

   public void shutdown() {
      this.shutdown = true;
      this.inflightVillages.clear();
      this.dirtyVillages.clear();
      this.results.clear();
      this.workQueue.clear();
      if (this.executor != null) {
         this.executor.shutdownNow();
         this.executor = null;
      }
      this.inFlight.set(0);
   }

   public void markDirty(UUID villageId) {
      if (villageId != null) {
         this.dirtyVillages.add(villageId);
      }
   }

   public boolean canSubmit(UUID villageId) {
      return !this.shutdown
         && this.executor != null
         && villageId != null
         && !this.inflightVillages.contains(villageId)
         && this.inFlight.get() < 8;
   }

   public void submit(ReligionSnapshot snapshot) {
      if (!canSubmit(snapshot.villageId())) {
         this.dirtyVillages.add(snapshot.villageId());
         return;
      }
      this.dirtyVillages.remove(snapshot.villageId());
      this.inflightVillages.add(snapshot.villageId());
      this.inFlight.incrementAndGet();
      TickPerfCounters.RELIGION_SNAPSHOTS.incrementAndGet();
      if (this.oldestQueuedGameTime < 0L) {
         this.oldestQueuedGameTime = snapshot.gameTime();
      }
      try {
         this.executor.execute(() -> {
            try {
               ReligionPlanResult result = plan(snapshot);
               if (!this.results.offer(result)) {
                  TickPerfCounters.RELIGION_QUEUE_REJECTS.incrementAndGet();
                  this.dirtyVillages.add(snapshot.villageId());
               }
            } catch (Throwable t) {
               LOGGER.warn("[Religion] Plan failed for {}: {}", snapshot.villageId(), t.toString());
               this.results.offer(ReligionPlanResult.failed(
                  snapshot.villageId(), snapshot.worldSessionId(), snapshot.contentGeneration(), "exception"
               ));
            } finally {
               this.inflightVillages.remove(snapshot.villageId());
               this.inFlight.decrementAndGet();
            }
         });
      } catch (RuntimeException e) {
         this.inflightVillages.remove(snapshot.villageId());
         this.inFlight.decrementAndGet();
         this.dirtyVillages.add(snapshot.villageId());
      }
   }

   public List<ReligionPlanResult> drainResults(int max) {
      List<ReligionPlanResult> out = new ArrayList<>(Math.min(max, 4));
      ReligionPlanResult r;
      while (out.size() < max && (r = this.results.poll()) != null) {
         out.add(r);
      }
      if (this.results.isEmpty()) {
         this.oldestQueuedGameTime = -1L;
      }
      return out;
   }

   public Set<UUID> pollDirtyVillages() {
      Set<UUID> copy = Set.copyOf(this.dirtyVillages);
      return copy;
   }

   public void clearDirty(UUID villageId) {
      this.dirtyVillages.remove(villageId);
   }

   public int inFlight() {
      return this.inFlight.get();
   }

   public int resultQueueDepth() {
      return this.results.size();
   }

   public int workQueueDepth() {
      return this.workQueue.size();
   }

   public long oldestQueuedGameTime() {
      return this.oldestQueuedGameTime;
   }

   static ReligionPlanResult plan(ReligionSnapshot snap) {
      if (!snap.religionEnabled()) {
         return ReligionPlanResult.none(snap.villageId(), snap.worldSessionId(), snap.contentGeneration());
      }
      if (!snap.ordersEnabled()) {
         return ReligionPlanResult.none(snap.villageId(), snap.worldSessionId(), snap.contentGeneration());
      }

      // Seed chapter if eligible and missing.
      if (snap.chapterId() == null
         && snap.population() >= snap.minPopForOrder()
         && snap.religiousBuildingCount() > 0
         && snap.orderSpawnChance() > 0.0) {
         long hash = snap.seed() ^ (snap.dayNumber() * 31L);
         double roll = ((hash >>> 1) & 0x1FFFFFFFFFFFFFL) / (double) 0x1FFFFFFFFFFFFFL;
         if (roll < snap.orderSpawnChance()) {
            ResourceLocation order = OrderRegistry.lastFlameId();
            if (OrderRegistry.get(order) != null) {
               return new ReligionPlanResult(
                  snap.villageId(),
                  snap.worldSessionId(),
                  snap.contentGeneration(),
                  ReligionPlanResult.Kind.SEED_CHAPTER,
                  order,
                  pickLeader(snap),
                  null,
                  -1L,
                  List.of(),
                  "seed_last_flame"
               );
            }
         }
         return ReligionPlanResult.none(snap.villageId(), snap.worldSessionId(), snap.contentGeneration());
      }

      if (snap.chapterId() == null || snap.hasActiveRitual()) {
         return ReligionPlanResult.none(snap.villageId(), snap.worldSessionId(), snap.contentGeneration());
      }

      if (snap.chapterStage() == Chapter.Stage.DISBANDED || snap.chapterStage() == Chapter.Stage.ORPHANED) {
         return ReligionPlanResult.none(snap.villageId(), snap.worldSessionId(), snap.contentGeneration());
      }

      // Suggest night wake when shrine ready / active and night window approaching.
      long dayTime = snap.dayTime() % 24000L;
      if ((snap.chapterStage() == Chapter.Stage.SHRINE_READY || snap.chapterStage() == Chapter.Stage.ACTIVE)
         && dayTime >= 12000L
         && dayTime < 13000L) {
         ResourceLocation ritual = ResourceLocation.fromNamespaceAndPath("millenaire", "night_wake");
         if (RitualRegistry.get(ritual) != null) {
            long startAt = snap.gameTime() + (13000L - dayTime);
            List<UUID> invites = new ArrayList<>();
            for (ReligionSnapshot.MemberSnap m : snap.members()) {
               if (m.role().isMember() || m.role() == OrderRole.INTERESTED) {
                  invites.add(m.recordUuid());
                  if (invites.size() >= 8) {
                     break;
                  }
               }
            }
            return new ReligionPlanResult(
               snap.villageId(),
               snap.worldSessionId(),
               snap.contentGeneration(),
               ReligionPlanResult.Kind.SCHEDULE_RITUAL,
               snap.orderTemplateId(),
               pickLeader(snap),
               ritual,
               startAt,
               invites,
               "night_wake"
            );
         }
      }

      if (pickLeader(snap) == null && !snap.members().isEmpty()) {
         return new ReligionPlanResult(
            snap.villageId(),
            snap.worldSessionId(),
            snap.contentGeneration(),
            ReligionPlanResult.Kind.PROMOTE_LEADER,
            snap.orderTemplateId(),
            snap.members().getFirst().recordUuid(),
            null,
            -1L,
            List.of(),
            "promote"
         );
      }

      return ReligionPlanResult.none(snap.villageId(), snap.worldSessionId(), snap.contentGeneration());
   }

   @Nullable
   private static UUID pickLeader(ReligionSnapshot snap) {
      UUID best = null;
      int bestScore = -1;
      for (ReligionSnapshot.MemberSnap m : snap.members()) {
         int score = m.devotion();
         if (m.role() == OrderRole.LEADER) {
            return m.recordUuid();
         }
         if (m.role() == OrderRole.KEEPER) {
            score += 50;
         }
         if (m.clergyPreferred()) {
            score += 20;
         }
         if (score > bestScore) {
            bestScore = score;
            best = m.recordUuid();
         }
      }
      return best;
   }
}
