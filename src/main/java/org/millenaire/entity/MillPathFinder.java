package org.millenaire.entity;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.Target;
import org.millenaire.diagnostics.NavigationCounters;

public class MillPathFinder extends PathFinder {
   private static final float FUDGING = 1.5F;
   private final Node[] neighbors = new Node[32];
   private final int maxVisitedNodes;
   private final NodeEvaluator nodeEvaluator;
   private static final boolean DEBUG = false;
   private final BinaryHeap openSet = new BinaryHeap();
   private static final long DEFAULT_SEARCH_TIME_BUDGET_NANOS = 2_000_000L;
   private static final int TIME_CHECK_INTERVAL = 64;

   private static long searchTimeBudgetNanos() {
      try {
         int ms = org.millenaire.config.MillenaireServerConfig.SERVER.pathMaxSearchMs.getAsInt();
         return Math.max(500_000L, ms * 1_000_000L);
      } catch (IllegalStateException e) {
         return DEFAULT_SEARCH_TIME_BUDGET_NANOS;
      }
   }

   public MillPathFinder(NodeEvaluator nodeEvaluator, int maxVisitedNodes) {
      super(nodeEvaluator, maxVisitedNodes);
      this.nodeEvaluator = nodeEvaluator;
      this.maxVisitedNodes = maxVisitedNodes;
   }

   @Nullable
   public Path findPath(PathNavigationRegion region, Mob mob, Set<BlockPos> targetPositions, float maxRange, int accuracy, float searchDepthMultiplier) {
      this.openSet.clear();
      this.nodeEvaluator.prepare(region, mob);
      Node node = this.nodeEvaluator.getStart();
      if (node == null) {
         return null;
      }

      Map<Target, BlockPos> map = targetPositions.stream()
         .collect(Collectors.toMap(p_326774_ -> this.nodeEvaluator.getTarget(p_326774_.getX(), p_326774_.getY(), p_326774_.getZ()), Function.identity()));
      long searchStart = System.nanoTime();
      Path path = this.findPath(region.getProfiler(), node, map, maxRange, accuracy, searchDepthMultiplier);
      NavigationCounters.recordAstarSearch(System.nanoTime() - searchStart);
      this.nodeEvaluator.done();
      return path;
   }

   @Nullable
   Path findPath(ProfilerFiller profiler, Node p_node, Map<Target, BlockPos> targetPos, float maxRange, int accuracy, float searchDepthMultiplier) {
      profiler.push("find_path");
      profiler.markForCharting(MetricCategory.PATH_FINDING);
      Set<Target> set = targetPos.keySet();
      p_node.g = 0.0F;
      p_node.h = this.getBestH(p_node, set);
      p_node.f = p_node.h;
      this.openSet.clear();
      this.openSet.insert(p_node);
      Set<Node> set1 = ImmutableSet.of();
      int i = 0;
      Set<Target> set2 = Sets.newHashSetWithExpectedSize(set.size());
      int j = (int)(this.maxVisitedNodes * searchDepthMultiplier);
      long startNanos = System.nanoTime();
      long budgetNanos = searchTimeBudgetNanos();

      while (!this.openSet.isEmpty()) {
         i++;
         if (i >= j || i % TIME_CHECK_INTERVAL == 0 && System.nanoTime() - startNanos > budgetNanos) {
            break;
         }

         Node node = this.openSet.pop();
         node.closed = true;

         for (Target target : set) {
            if (node.distanceManhattan(target) <= accuracy) {
               target.setReached();
               set2.add(target);
            }
         }

         if (!set2.isEmpty()) {
            break;
         }

         if (!(node.distanceTo(p_node) >= maxRange)) {
            int k = this.nodeEvaluator.getNeighbors(this.neighbors, node);

            for (int l = 0; l < k; l++) {
               Node node1 = this.neighbors[l];
               float f = this.distance(node, node1);
               node1.walkedDistance = node.walkedDistance + f;
               float f1 = node.g + f + node1.costMalus;
               if (node1.walkedDistance < maxRange && (!node1.inOpenSet() || f1 < node1.g)) {
                  node1.cameFrom = node;
                  node1.g = f1;
                  node1.h = this.getBestH(node1, set) * 1.5F;
                  if (node1.inOpenSet()) {
                     this.openSet.changeCost(node1, node1.g + node1.h);
                  } else {
                     node1.f = node1.g + node1.h;
                     this.openSet.insert(node1);
                  }
               }
            }
         }
      }

      Optional<Path> optional = !set2.isEmpty()
         ? set2.stream()
            .map(p_77454_ -> this.reconstructPath(p_77454_.getBestNode(), targetPos.get(p_77454_), true))
            .min(Comparator.comparingInt(Path::getNodeCount))
         : set.stream()
            .map(p_77451_ -> this.reconstructPath(p_77451_.getBestNode(), targetPos.get(p_77451_), false))
            .min(Comparator.comparingDouble(Path::getDistToTarget).thenComparingInt(Path::getNodeCount));
      profiler.pop();
      return optional.isEmpty() ? null : optional.get();
   }

   protected float distance(Node first, Node second) {
      return first.distanceTo(second);
   }

   private float getBestH(Node node, Set<Target> targets) {
      float f = Float.MAX_VALUE;

      for (Target target : targets) {
         float f1 = node.distanceTo(target);
         target.updateBest(f1, node);
         f = Math.min(f1, f);
      }

      return f;
   }

   private Path reconstructPath(Node point, BlockPos targetPos, boolean reachesTarget) {
      List<Node> list = Lists.newArrayList();
      Node node = point;
      list.add(0, point);

      while (node.cameFrom != null) {
         node = node.cameFrom;
         list.add(0, node);
      }

      return new Path(list, targetPos, reachesTarget);
   }
}
