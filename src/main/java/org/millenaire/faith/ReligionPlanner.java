package org.millenaire.faith;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.perf.TickPerfCounters;
import org.millenaire.perf.plan.BackgroundWorkGate;
import org.millenaire.perf.plan.DirtyFairQueue;
import org.millenaire.perf.plan.PlanDomain;
import org.millenaire.perf.plan.PlanPriority;
import org.slf4j.Logger;

/**
 * Background religion planner. Workers receive only immutable snapshots — never live world objects.
 * Rejection never runs work on the caller (server) thread; in-flight and gate slots are always cleared.
 */
public final class ReligionPlanner {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final ReligionPlanner INSTANCE = new ReligionPlanner();

   private final AtomicInteger inFlight = new AtomicInteger();
   private final Set<UUID> inflightVillages = ConcurrentHashMap.newKeySet();
   private final DirtyFairQueue<UUID> dirtyVillages = new DirtyFairQueue<>();
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
            // Do NOT run on caller. Throw so submit() can clear in-flight + gate + re-dirty.
            TickPerfCounters.RELIGION_QUEUE_REJECTS.incrementAndGet();
            throw new RejectedExecutionException("religion planner queue full");
         }
      );
   }

   public void shutdown() {
      this.shutdown = true;
      this.inflightVillages.clear();
      this.dirtyVillages.clearAll();
      this.results.clear();
      this.workQueue.clear();
      if (this.executor != null) {
         this.executor.shutdownNow();
         this.executor = null;
      }
      this.inFlight.set(0);
      this.oldestQueuedGameTime = -1L;
   }

   public void markDirty(UUID villageId) {
      markDirty(villageId, 0L);
   }

   public void markDirty(UUID villageId, long gameTime) {
      if (villageId != null) {
         this.dirtyVillages.mark(villageId, gameTime);
         BackgroundWorkGate.get().noteQueued(PlanDomain.RELIGION, gameTime);
         if (this.oldestQueuedGameTime < 0L || (gameTime > 0L && gameTime < this.oldestQueuedGameTime)) {
            this.oldestQueuedGameTime = gameTime;
         }
      }
   }

   /** Local readiness only — does not acquire the shared gate. */
   public boolean canSubmit(UUID villageId) {
      return !this.shutdown
         && this.executor != null
         && villageId != null
         && !this.inflightVillages.contains(villageId)
         && this.inFlight.get() < 8
         && !BackgroundWorkGate.get().isShutdown();
   }

   public void submit(ReligionSnapshot snapshot) {
      if (snapshot == null || !canSubmit(snapshot.villageId())) {
         if (snapshot != null) {
            this.dirtyVillages.mark(snapshot.villageId(), snapshot.gameTime());
         }
         return;
      }
      if (!BackgroundWorkGate.get().tryAcquire(PlanDomain.RELIGION, PlanPriority.SIM, snapshot.gameTime())) {
         this.dirtyVillages.mark(snapshot.villageId(), snapshot.gameTime());
         return;
      }

      this.dirtyVillages.clear(snapshot.villageId());
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
                  this.dirtyVillages.mark(snapshot.villageId(), snapshot.gameTime());
               }
            } catch (Throwable t) {
               LOGGER.warn("[Religion] Plan failed for {}: {}", snapshot.villageId(), t.toString());
               this.results.offer(
                  ReligionPlanResult.failed(
                     snapshot.villageId(), snapshot.worldSessionId(), snapshot.contentGeneration(), "exception"
                  )
               );
            } finally {
               this.inflightVillages.remove(snapshot.villageId());
               this.inFlight.decrementAndGet();
               BackgroundWorkGate.get().release(PlanDomain.RELIGION);
            }
         });
      } catch (RuntimeException e) {
         this.inflightVillages.remove(snapshot.villageId());
         this.inFlight.decrementAndGet();
         BackgroundWorkGate.get().release(PlanDomain.RELIGION);
         this.dirtyVillages.mark(snapshot.villageId(), snapshot.gameTime());
         TickPerfCounters.RELIGION_QUEUE_REJECTS.incrementAndGet();
      }
   }

   public List<ReligionPlanResult> drainResults(int max) {
      List<ReligionPlanResult> out = new ArrayList<>(Math.min(max, 4));
      ReligionPlanResult r;
      while (out.size() < max && (r = this.results.poll()) != null) {
         out.add(r);
      }
      if (this.results.isEmpty() && this.dirtyVillages.isEmpty()) {
         this.oldestQueuedGameTime = -1L;
         BackgroundWorkGate.get().clearQueuedMarker(PlanDomain.RELIGION);
      }
      return out;
   }

   /** Budgeted dirty poll — does not copy the entire dirty set. */
   public List<UUID> pollDirtyVillages(int max) {
      return this.dirtyVillages.pollLimited(max);
   }

   /** @deprecated use {@link #pollDirtyVillages(int)} */
   @Deprecated
   public Set<UUID> pollDirtyVillages() {
      return Set.copyOf(this.dirtyVillages.pollLimited(64));
   }

   public void clearDirty(UUID villageId) {
      this.dirtyVillages.clear(villageId);
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
