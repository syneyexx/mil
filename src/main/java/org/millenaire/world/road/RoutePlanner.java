package org.millenaire.world.road;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.perf.plan.BackgroundWorkGate;
import org.millenaire.perf.plan.PlanDomain;
import org.millenaire.perf.plan.PlanPriority;
import org.slf4j.Logger;

/**
 * Async route planner. Workers use {@link OfflineTerrainSampler} only — never live world objects.
 * Results are queued for the main thread.
 */
public final class RoutePlanner {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final ExecutorService executor;
   private final AtomicInteger inFlight = new AtomicInteger();
   private final ConcurrentLinkedQueue<PlanResult> results = new ConcurrentLinkedQueue<>();
   private final int maxConcurrent;
   private volatile boolean shutdown;

   public RoutePlanner(int threads, int maxConcurrent) {
      this.maxConcurrent = Math.max(1, maxConcurrent);
      ThreadFactory factory = r -> {
         Thread t = new Thread(r, "millenaire-road-planner");
         t.setDaemon(true);
         t.setPriority(Thread.MIN_PRIORITY + 1);
         return t;
      };
      this.executor = Executors.newFixedThreadPool(Math.max(1, threads), factory);
   }

   public void shutdown() {
      this.shutdown = true;
      this.executor.shutdownNow();
   }

   public int inFlight() {
      return this.inFlight.get();
   }

   public boolean canSubmit() {
      return !this.shutdown && this.inFlight.get() < this.maxConcurrent && !BackgroundWorkGate.get().isShutdown();
   }

   public void submit(ServerLevel level, RoadEdge edge, RoadNode a, RoadNode b, Collection<RoadNode> allNodes, RoadPlanConfig config) {
      if (!canSubmit()) {
         return;
      }
      long gameTime = level.getGameTime();
      if (!BackgroundWorkGate.get().tryAcquire(PlanDomain.ROADS, PlanPriority.SIM, gameTime)) {
         return;
      }

      edge.setState(EdgeState.PLANNING);
      this.inFlight.incrementAndGet();
      long edgeId = edge.id();
      String idA = a.id();
      String idB = b.id();
      BlockPos start = RoadNode.pickEntranceToward(a, b);
      BlockPos goal = RoadNode.pickEntranceToward(b, a);
      List<RoadNode> nodesSnapshot = new ArrayList<>(allNodes);
      long seed = level.getSeed();
      // Capture generator refs on server thread; worker never sees ServerLevel.
      OfflineTerrainSampler terrain = OfflineTerrainSampler.capture(level);
      this.executor.execute(() -> {
         try {
            PlanResult result = plan(terrain, edgeId, idA, idB, start, goal, nodesSnapshot, seed, config);
            this.results.offer(result);
         } catch (Throwable t) {
            LOGGER.warn("[Roads] Planning failed for edge {}: {}", edgeId, t.toString());
            this.results.offer(PlanResult.failed(edgeId, "exception"));
         } finally {
            this.inFlight.decrementAndGet();
            BackgroundWorkGate.get().release(PlanDomain.ROADS);
         }
      });
   }

   public List<PlanResult> drainResults() {
      List<PlanResult> out = new ArrayList<>();
      PlanResult r;
      while ((r = this.results.poll()) != null) {
         out.add(r);
      }
      return out;
   }

   private static PlanResult plan(
      OfflineTerrainSampler sampler,
      long edgeId,
      String idA,
      String idB,
      BlockPos start,
      BlockPos goal,
      List<RoadNode> nodes,
      long seed,
      RoadPlanConfig config
   ) {
      for (RoadNode n : nodes) {
         sampler.markStructureBox(n.boundingBox(), 6);
      }

      CoarseGridAStar.CostProvider costs = (fx, fz, tx, tz, fy, ty) -> {
         int flags = sampler.flags(tx, tz);
         if ((flags & OfflineTerrainSampler.FLAG_DEEP_OCEAN) != 0) {
            return CoarseGridAStar.COST_BLOCKED;
         }
         if ((flags & OfflineTerrainSampler.FLAG_LAVA) != 0) {
            return CoarseGridAStar.COST_BLOCKED;
         }
         if ((flags & OfflineTerrainSampler.FLAG_STRUCTURE) != 0) {
            return CoarseGridAStar.COST_BLOCKED;
         }
         float cost = 1.0f;
         int dy = Math.abs(ty - fy);
         cost += dy * dy * config.slopeWeight;
         if (dy > config.maxSlope) {
            cost += 8f;
         }
         if ((flags & OfflineTerrainSampler.FLAG_WATER) != 0) {
            cost = config.bridgeCostPerBlock;
         }
         if ((flags & OfflineTerrainSampler.FLAG_FOREST) != 0) {
            cost += 0.35f;
         }
         int surface = sampler.height(tx, tz);
         if (surface - ty >= config.tunnelThreshold) {
            cost = Math.min(cost, config.tunnelCostPerBlock);
         }
         return cost;
      };

      CoarseGridAStar.HeightProvider heights = sampler::height;

      CoarseGridAStar.Result result = CoarseGridAStar.search(
         start.getX(),
         start.getZ(),
         goal.getX(),
         goal.getZ(),
         8,
         heights,
         costs,
         1.0f,
         config.maxExpansions,
         config.maxPlanningMillis
      );

      if (!result.found()) {
         result = CoarseGridAStar.search(
            start.getX(),
            start.getZ(),
            goal.getX(),
            goal.getZ(),
            16,
            heights,
            costs,
            1.0f,
            config.maxExpansions / 2,
            config.maxPlanningMillis / 2
         );
      }

      int[] path;
      if (result.found()) {
         path = result.pathXzY();
      } else {
         path = straightLine(start, goal, sampler);
         LOGGER.debug("[Roads] Edge {} fallback straight line after {}", edgeId, result.reason());
      }

      RoutePostProcessor.PlannedRoute planned = RoutePostProcessor.process(
         path, sampler, edgeId, seed, config.maxBridgeLength, config.tunnelThreshold
      );
      sampler.clear();
      return PlanResult.success(edgeId, planned.polyline, planned.segmentKinds, planned.chunkSegments);
   }

   private static int[] straightLine(BlockPos start, BlockPos goal, OfflineTerrainSampler sampler) {
      int dx = goal.getX() - start.getX();
      int dz = goal.getZ() - start.getZ();
      int steps = Math.max(1, (int)Math.ceil(Math.sqrt(dx * dx + dz * dz) / 8.0));
      int[] path = new int[(steps + 1) * 3];
      for (int i = 0; i <= steps; i++) {
         double t = i / (double)steps;
         int x = start.getX() + (int)Math.round(dx * t);
         int z = start.getZ() + (int)Math.round(dz * t);
         int y = sampler.height(x, z);
         path[i * 3] = x;
         path[i * 3 + 1] = y;
         path[i * 3 + 2] = z;
      }
      return path;
   }

   public record PlanResult(
      long edgeId,
      boolean success,
      @Nullable int[] polyline,
      @Nullable byte[] segmentKinds,
      @Nullable it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<int[]> chunkIndex,
      String reason
   ) {
      static PlanResult success(long id, int[] poly, byte[] kinds, it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<int[]> index) {
         return new PlanResult(id, true, poly, kinds, index, "ok");
      }

      static PlanResult failed(long id, String reason) {
         return new PlanResult(id, false, null, null, null, reason);
      }
   }

   public static final class RoadPlanConfig {
      public float slopeWeight = 0.35f;
      public int maxSlope = 2;
      public float bridgeCostPerBlock = 6f;
      public float tunnelCostPerBlock = 4f;
      public int tunnelThreshold = 12;
      public int maxBridgeLength = 48;
      public int maxExpansions = 250_000;
      public long maxPlanningMillis = 20_000L;
   }
}
