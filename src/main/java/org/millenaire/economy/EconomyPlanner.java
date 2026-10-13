package org.millenaire.economy;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.millenaire.perf.TickPerfCounters;
import org.millenaire.perf.plan.BackgroundWorkGate;
import org.millenaire.perf.plan.DirtyFairQueue;
import org.millenaire.perf.plan.PlanDomain;
import org.millenaire.perf.plan.PlanPriority;
import org.slf4j.Logger;

/**
 * Off-tick economy planner. Workers see only {@link EconomySnapshot}.
 */
public final class EconomyPlanner {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final EconomyPlanner INSTANCE = new EconomyPlanner();

   private final AtomicInteger inFlight = new AtomicInteger();
   private final java.util.Set<UUID> inflightVillages = java.util.concurrent.ConcurrentHashMap.newKeySet();
   private final DirtyFairQueue<UUID> dirtyVillages = new DirtyFairQueue<>();
   private final ArrayBlockingQueue<EconomyPlanResult> results = new ArrayBlockingQueue<>(64);
   private final ArrayBlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(32);
   private ExecutorService executor;
   private volatile boolean shutdown = true;

   private EconomyPlanner() {
   }

   public static EconomyPlanner get() {
      return INSTANCE;
   }

   public void onServerStarted(int threads) {
      shutdown();
      this.shutdown = false;
      int n = Math.max(1, Math.min(2, threads));
      ThreadFactory factory = r -> {
         Thread t = new Thread(r, "millenaire-economy-planner");
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
            throw new RejectedExecutionException("economy planner queue full");
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
   }

   public void markDirty(UUID villageId, long gameTime) {
      if (villageId != null) {
         this.dirtyVillages.mark(villageId, gameTime);
         BackgroundWorkGate.get().noteQueued(PlanDomain.ECONOMY, gameTime);
      }
   }

   public boolean canSubmit(UUID villageId) {
      return !this.shutdown
         && this.executor != null
         && villageId != null
         && !this.inflightVillages.contains(villageId)
         && this.inFlight.get() < 4
         && !BackgroundWorkGate.get().isShutdown();
   }

   public void submit(EconomySnapshot snapshot) {
      if (snapshot == null || !canSubmit(snapshot.villageId())) {
         if (snapshot != null) {
            this.dirtyVillages.mark(snapshot.villageId(), snapshot.gameTime());
         }
         return;
      }
      if (!BackgroundWorkGate.get().tryAcquire(PlanDomain.ECONOMY, PlanPriority.SIM, snapshot.gameTime())) {
         this.dirtyVillages.mark(snapshot.villageId(), snapshot.gameTime());
         return;
      }
      this.dirtyVillages.clear(snapshot.villageId());
      this.inflightVillages.add(snapshot.villageId());
      this.inFlight.incrementAndGet();
      TickPerfCounters.ECONOMY_SNAPSHOTS.incrementAndGet();
      try {
         this.executor.execute(() -> {
            try {
               EconomyPlanResult result = plan(snapshot);
               if (!this.results.offer(result)) {
                  this.dirtyVillages.mark(snapshot.villageId(), snapshot.gameTime());
               }
            } catch (Throwable t) {
               LOGGER.warn("[Economy] Plan failed for {}: {}", snapshot.villageId(), t.toString());
               this.results.offer(EconomyPlanResult.failed(snapshot.villageId(), snapshot.worldSessionId(), "exception"));
            } finally {
               this.inflightVillages.remove(snapshot.villageId());
               this.inFlight.decrementAndGet();
               BackgroundWorkGate.get().release(PlanDomain.ECONOMY);
            }
         });
      } catch (RejectedExecutionException | RuntimeException e) {
         this.inflightVillages.remove(snapshot.villageId());
         this.inFlight.decrementAndGet();
         BackgroundWorkGate.get().release(PlanDomain.ECONOMY);
         this.dirtyVillages.mark(snapshot.villageId(), snapshot.gameTime());
      }
   }

   public List<EconomyPlanResult> drainResults(int max) {
      List<EconomyPlanResult> out = new ArrayList<>(Math.min(max, 4));
      EconomyPlanResult r;
      while (out.size() < max && (r = this.results.poll()) != null) {
         out.add(r);
      }
      return out;
   }

   public List<UUID> pollDirtyVillages(int max) {
      return this.dirtyVillages.pollLimited(max);
   }

   public void clearDirty(UUID villageId) {
      this.dirtyVillages.clear(villageId);
   }

   static EconomyPlanResult plan(EconomySnapshot snap) {
      List<EconomyPlanResult.NeedMutation> mutations = new ArrayList<>();

      // Derive construction / subsistence / production needs from stock lines (no world access).
      for (EconomySnapshot.StockLine stock : snap.stocks()) {
         int deficit = stock.target() + stock.constructionReserved() - stock.count();
         if (deficit <= 0) {
            continue;
         }
         NeedCategory category;
         int priority;
         String source;
         if (stock.constructionReserved() > 0 && stock.count() < stock.constructionReserved()) {
            category = NeedCategory.CONSTRUCTION;
            priority = 80;
            source = "construction";
         } else if (isFood(stock.item())) {
            category = NeedCategory.SUBSISTENCE;
            priority = 90;
            source = "subsistence";
         } else if (isToolOrMetal(stock.item())) {
            category = NeedCategory.PRODUCTION;
            priority = 70;
            source = "production_tools";
         } else {
            category = NeedCategory.PRODUCTION;
            priority = 50;
            source = "production_stock";
         }
         mutations.add(
            new EconomyPlanResult.NeedMutation(
               category,
               stock.item(),
               deficit,
               priority,
               snap.gameTime() + 24000L,
               source,
               null
            )
         );
         if (mutations.size() >= 8) {
            break;
         }
      }

      EconomySnapshot.NeedLine focus = null;
      for (EconomySnapshot.NeedLine need : snap.needs()) {
         if (focus == null || need.priority() > focus.priority()) {
            focus = need;
         }
      }
      // Prefer newly derived high-priority mutation if needs empty
      EconomyPlanResult.NeedMutation focusMutation = mutations.isEmpty() ? null : mutations.getFirst();
      for (EconomyPlanResult.NeedMutation m : mutations) {
         if (focusMutation == null || m.priority() > focusMutation.priority()) {
            focusMutation = m;
         }
      }

      ProductionChoice choice = ProductionChoice.WAIT;
      UUID partner = null;
      Item focusItem = focus != null ? focus.item() : focusMutation != null ? focusMutation.item() : null;
      UUID focusNeedId = focus != null ? focus.needId() : null;
      int amount = focus != null ? focus.remaining() : focusMutation != null ? focusMutation.amount() : 0;
      String summary = "stable";

      if (focusItem != null && amount > 0) {
         boolean canLocal = snap.freeLaborEstimate() > 0;
         EconomySnapshot.PartnerLine bestPartner = null;
         for (EconomySnapshot.PartnerLine p : snap.tradePartners()) {
            if (p.wantItem() == focusItem && p.surplusAmount() > 0 && p.relation() >= 0) {
               if (bestPartner == null || p.surplusAmount() > bestPartner.surplusAmount()) {
                  bestPartner = p;
               }
            }
         }
         if (bestPartner != null && (!canLocal || amount >= 8)) {
            choice = ProductionChoice.REQUEST_TRADE;
            partner = bestPartner.villageId();
            summary = "trade_for_" + itemKey(focusItem) + "_from_partner";
            mutations.add(
               new EconomyPlanResult.NeedMutation(
                  NeedCategory.PRODUCTION,
                  focusItem,
                  amount,
                  75,
                  snap.gameTime() + 24000L,
                  "trade_request",
                  "awaiting_delivery"
               )
            );
         } else if (canLocal) {
            choice = ProductionChoice.PRODUCE_LOCAL;
            summary = "produce_local_" + itemKey(focusItem);
            if (isToolOrMetal(focusItem) && countStock(snap, Items.IRON_INGOT) < 4) {
               summary = "blocked_smith_needs_iron";
               mutations.add(
                  new EconomyPlanResult.NeedMutation(
                     NeedCategory.PRODUCTION,
                     Items.IRON_INGOT,
                     8,
                     85,
                     snap.gameTime() + 24000L,
                     "smith_input",
                     "missing_iron_for_tools"
                  )
               );
            }
         } else if (snap.playerControlled()) {
            choice = ProductionChoice.ASK_PLAYER;
            summary = "ask_player_" + itemKey(focusItem);
         } else {
            choice = ProductionChoice.WAIT;
            summary = "wait_labor_" + itemKey(focusItem);
         }
      }

      if (snap.hasPendingConstruction() && choice == ProductionChoice.WAIT) {
         summary = "construction_waiting_resources";
      }

      return new EconomyPlanResult(
         snap.villageId(),
         snap.worldSessionId(),
         EconomyPlanResult.Kind.UPDATE_PLAN,
         choice,
         focusNeedId,
         focusItem,
         partner,
         amount,
         summary,
         List.copyOf(mutations)
      );
   }

   private static int countStock(EconomySnapshot snap, Item item) {
      for (EconomySnapshot.StockLine s : snap.stocks()) {
         if (s.item() == item) {
            return s.count();
         }
      }
      return 0;
   }

   private static boolean isFood(Item item) {
      return item == Items.BREAD
         || item == Items.WHEAT
         || item == Items.APPLE
         || item == Items.CARROT
         || item == Items.POTATO
         || item == Items.COOKED_BEEF
         || item == Items.COOKED_PORKCHOP
         || item == Items.COOKED_CHICKEN;
   }

   private static boolean isToolOrMetal(Item item) {
      return item == Items.IRON_INGOT
         || item == Items.IRON_PICKAXE
         || item == Items.IRON_AXE
         || item == Items.IRON_HOE
         || item == Items.IRON_SHOVEL
         || item == Items.COAL
         || item == Items.COBBLESTONE;
   }

   private static String itemKey(Item item) {
      return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
   }
}
