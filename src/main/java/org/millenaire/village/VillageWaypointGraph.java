package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.culture.ModCultures;
import org.millenaire.perf.compute.ComputeOffload;
import org.millenaire.perf.compute.MillenaireComputePool;
import org.millenaire.village.path.PathRoute;
import org.millenaire.village.path.PathTerrainSnapshot;
import org.slf4j.Logger;

public class VillageWaypointGraph {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final double MAX_EDGE_DISTANCE = 64.0;
   public static final double SHORT_EDGE_THRESHOLD = 30.0;
   public static final double MACRO_THRESHOLD = 48.0;
   private static final long WARN_REBUILD_MS = 200L;
   private volatile VillageWaypointGraph.GraphSnapshot snapshot = VillageWaypointGraph.GraphSnapshot.EMPTY;
   private long asyncRebuildGeneration = 0L;
   private boolean asyncRebuildInFlight = false;

   public void rebuild(List<BuildingInstance> buildings, BlockPos villageCenter, ServerLevel level, Village village) {
      Objects.requireNonNull(level, "ServerLevel is required for pathfind validation");
      Objects.requireNonNull(village, "Village is required so the tester binds villageId");
      if (ComputeOffload.waypointGraph() && this.trySubmitAsyncRebuild(buildings, villageCenter, level, village)) {
         return;
      }
      this.rebuildInternal(buildings, villageCenter, level, village);
   }

   private boolean trySubmitAsyncRebuild(
      List<BuildingInstance> buildings, BlockPos villageCenter, ServerLevel level, Village village
   ) {
      if (this.asyncRebuildInFlight) {
         return true;
      }
      List<Waypoint> waypoints = collectWaypoints(buildings, villageCenter);
      if (waypoints.size() < 2) {
         this.snapshot = new VillageWaypointGraph.GraphSnapshot(List.copyOf(waypoints), Map.of());
         return true;
      }
      List<BlockPos> anchors = waypoints.stream().map(Waypoint::pos).toList();
      PathTerrainSnapshot terrain = PathTerrainSnapshot.captureAroundAnchors(
         level, village, anchors, Map.of(), Set.of(), Set.of()
      );
      List<Waypoint> waypointCapture = List.copyOf(waypoints);
      long generation = ++this.asyncRebuildGeneration;
      this.asyncRebuildInFlight = true;
      boolean submitted = MillenaireComputePool.submit(() -> {
         long start = System.nanoTime();
         GraphBuildResult built = buildGraph(waypointCapture, (from, to) -> pathNodesOnSnapshot(terrain, from, to));
         long ms = (System.nanoTime() - start) / 1_000_000L;
         return new WaypointGraphComputeResult(
            village.getId(), generation, built.snapshot(), ms, waypointCapture.size(), built.edgeCount()
         );
      });
      if (!submitted) {
         this.asyncRebuildInFlight = false;
         return false;
      }
      return true;
   }

   public boolean publishAsyncRebuild(
      long generation,
      GraphSnapshot graph,
      long rebuildMillis,
      int nodeCount,
      int edgeCount,
      long gameTime,
      Village village
   ) {
      if (generation != this.asyncRebuildGeneration) {
         return false;
      }
      this.asyncRebuildInFlight = false;
      this.snapshot = graph;
      LOGGER.info(
         "[Millenaire] Waypoint graph rebuilt (async): {} nodes, {} edges in {} ms",
         nodeCount,
         edgeCount,
         rebuildMillis
      );
      if (rebuildMillis >= WARN_REBUILD_MS) {
         LOGGER.warn(
            "[Millenaire] Waypoint graph rebuild SLOW (async): {} ms (>= {} ms) for {} nodes",
            rebuildMillis,
            WARN_REBUILD_MS,
            nodeCount
         );
      }
      village.onWaypointGraphAsyncPublished(gameTime);
      return true;
   }

   @Nullable
   private static List<BlockPos> pathNodesOnSnapshot(PathTerrainSnapshot terrain, BlockPos from, BlockPos to) {
      PathRoute route = new PathRoute(from, to, "dirt", 1, 0, null, null, false);
      var tr = terrain.searchTrace(route);
      return tr.trace();
   }

   void rebuild(List<BuildingInstance> buildings, BlockPos villageCenter) {
      this.rebuildInternal(buildings, villageCenter, null, null);
   }

   public void rebuildForTesting(List<BuildingInstance> buildings, BlockPos villageCenter, ServerLevel level) {
      Objects.requireNonNull(level, "ServerLevel is required for pathfind validation");
      this.rebuildInternal(buildings, villageCenter, level, null);
   }

   public boolean isAsyncRebuildInFlight() {
      return this.asyncRebuildInFlight;
   }

   private void rebuildInternal(List<BuildingInstance> buildings, BlockPos villageCenter, @Nullable ServerLevel level, @Nullable Village village) {
      long rebuildStartNanos = System.nanoTime();
      List<Waypoint> newWaypoints = collectWaypoints(buildings, villageCenter);
      int n = newWaypoints.size();
      if (n < 2) {
         this.snapshot = new VillageWaypointGraph.GraphSnapshot(List.copyOf(newWaypoints), Map.of());
         long elapsedMs = (System.nanoTime() - rebuildStartNanos) / 1000000L;
         LOGGER.info("[Millenaire] Waypoint graph rebuilt: {} nodes, 0 edges (too few) in {} ms", n, elapsedMs);
         return;
      }

      try (WaypointTraversalTester tester = level != null ? new WaypointTraversalTester(level, village) : null) {
         EdgeValidator validator = tester == null
            ? (from, to) -> List.of()
            : (from, to) -> {
               WaypointTraversalTester.Result r = tester.findPath(from, to);
               return r.reachable() ? r.nodes() : null;
            };
         GraphBuildResult built = buildGraph(newWaypoints, validator);
         this.snapshot = built.snapshot();
         long elapsedMs = (System.nanoTime() - rebuildStartNanos) / 1000000L;
         LOGGER.info(
            "[Millenaire] Waypoint graph rebuilt: {} nodes, {} edges (short: {} valid / {} rejected, long: {} valid / {} rejected / {} skipped-transitive) in {} ms",
            n,
            built.edgeCount(),
            built.shortValid(),
            built.shortRejected(),
            built.longValidated(),
            built.longRejected(),
            built.longSkippedTransitive(),
            elapsedMs
         );
         if (level != null && built.shortValid() + built.shortRejected() >= 4
            && built.shortRejected() * 5 >= (built.shortValid() + built.shortRejected()) * 4) {
            LOGGER.warn(
               "[Millenaire] Validating tester rejected {}/{} short edges (>= 80%) — falling back to euclidean validation for this rebuild",
               built.shortRejected(),
               built.shortValid() + built.shortRejected()
            );
            GraphBuildResult permissive = buildGraph(newWaypoints, (from, to) -> List.of());
            this.snapshot = permissive.snapshot();
            LOGGER.warn(
               "[Millenaire] Permissive fallback added edges (graph now {} edges total)",
               permissive.edgeCount()
            );
         }
         if (elapsedMs >= WARN_REBUILD_MS) {
            LOGGER.warn(
               "[Millenaire] Waypoint graph rebuild SLOW: {} ms (>= {} ms threshold) for {} nodes — see PERF roadmap",
               elapsedMs,
               WARN_REBUILD_MS,
               n
            );
         }
      }
   }

   private static List<Waypoint> collectWaypoints(List<BuildingInstance> buildings, BlockPos villageCenter) {
      List<Waypoint> newWaypoints = new ArrayList<>();
      newWaypoints.add(new Waypoint(villageCenter, null));
      Set<BlockPos> seen = new HashSet<>();
      seen.add(villageCenter);
      List<BlockPos> solidAnchors = new ArrayList<>();
      solidAnchors.add(villageCenter);

      for (BuildingInstance building : buildings) {
         if (!building.isSubBuilding() && (building.isWallSegment() ? hasPathNodeTag(building) : building.hasExplicitPathStartPos())) {
            BlockPos anchor = building.resolvePathAnchor();
            if (anchor != null && seen.add(anchor)) {
               newWaypoints.add(new Waypoint(anchor, building.getId()));
               solidAnchors.add(anchor);
            }
         }
      }

      double maxEdgeSq = 4096.0;
      for (BuildingInstance building : buildings) {
         if (!building.isSubBuilding() && !building.isWallSegment() && !building.hasExplicitPathStartPos()) {
            BlockPos anchor = building.resolvePathAnchor();
            if (anchor != null && !seen.contains(anchor)) {
               boolean solidWithinReach = false;
               for (BlockPos solid : solidAnchors) {
                  if (solid.distSqr(anchor) <= maxEdgeSq) {
                     solidWithinReach = true;
                     break;
                  }
               }
               if (!solidWithinReach && seen.add(anchor)) {
                  newWaypoints.add(new Waypoint(anchor, building.getId()));
               }
            }
         }
      }
      return newWaypoints;
   }

   @FunctionalInterface
   private interface EdgeValidator {
      /** null = unreachable; empty or nodes = accept */
      @Nullable
      List<BlockPos> validate(BlockPos from, BlockPos to);
   }

   private static GraphBuildResult buildGraph(List<Waypoint> newWaypoints, EdgeValidator validator) {
      Map<Waypoint, List<WaypointEdge>> newAdjacency = new HashMap<>();
      for (Waypoint wp : newWaypoints) {
         newAdjacency.put(wp, new ArrayList<>());
      }
      int n = newWaypoints.size();
      int[] parent = new int[n];
      for (int i = 0; i < n; i++) {
         parent[i] = i;
      }
      int shortValid = 0;
      int shortRejected = 0;
      int longValidated = 0;
      int longRejected = 0;
      int longSkippedTransitive = 0;
      double shortSq = 900.0;
      double maxSq = 4096.0;

      for (int ix = 0; ix < n; ix++) {
         Waypoint a = newWaypoints.get(ix);
         for (int j = ix + 1; j < n; j++) {
            Waypoint b = newWaypoints.get(j);
            double distSq = a.pos().distSqr(b.pos());
            if (distSq > shortSq) {
               continue;
            }
            List<BlockPos> nodes = validator.validate(a.pos(), b.pos());
            if (nodes == null) {
               shortRejected++;
               continue;
            }
            addEdge(newAdjacency, a, b, Math.sqrt(distSq), nodes);
            union(parent, ix, j);
            shortValid++;
         }
      }

      for (int ix = 0; ix < n; ix++) {
         Waypoint a = newWaypoints.get(ix);
         for (int j = ix + 1; j < n; j++) {
            Waypoint b = newWaypoints.get(j);
            double distSq = a.pos().distSqr(b.pos());
            if (distSq <= shortSq || distSq > maxSq) {
               continue;
            }
            if (find(parent, ix) == find(parent, j)) {
               longSkippedTransitive++;
               continue;
            }
            List<BlockPos> nodes = validator.validate(a.pos(), b.pos());
            if (nodes == null) {
               longRejected++;
               continue;
            }
            addEdge(newAdjacency, a, b, Math.sqrt(distSq), nodes);
            longValidated++;
         }
      }

      Map<Waypoint, List<WaypointEdge>> publishAdj = new HashMap<>(newAdjacency.size() * 2);
      for (Entry<Waypoint, List<WaypointEdge>> e : newAdjacency.entrySet()) {
         publishAdj.put(e.getKey(), List.copyOf(e.getValue()));
      }
      GraphSnapshot snap = new GraphSnapshot(List.copyOf(newWaypoints), Map.copyOf(publishAdj));
      return new GraphBuildResult(snap, shortValid + longValidated, shortValid, shortRejected, longValidated, longRejected, longSkippedTransitive);
   }

   private record GraphBuildResult(
      GraphSnapshot snapshot,
      int edgeCount,
      int shortValid,
      int shortRejected,
      int longValidated,
      int longRejected,
      int longSkippedTransitive
   ) {
   }

   private static void addEdge(Map<Waypoint, List<WaypointEdge>> adj, Waypoint a, Waypoint b, double cost, List<BlockPos> nodes) {
      List<BlockPos> reversed;
      if (nodes.isEmpty()) {
         reversed = List.of();
      } else {
         ArrayList<BlockPos> rev = new ArrayList<>(nodes);
         Collections.reverse(rev);
         reversed = Collections.unmodifiableList(rev);
      }

      adj.get(a).add(new WaypointEdge(b, cost, nodes));
      adj.get(b).add(new WaypointEdge(a, cost, reversed));
   }

   private static int find(int[] parent, int x) {
      while (parent[x] != x) {
         parent[x] = parent[parent[x]];
         x = parent[x];
      }

      return x;
   }

   private static void union(int[] parent, int x, int y) {
      int rx = find(parent, x);
      int ry = find(parent, y);
      if (rx != ry) {
         parent[rx] = ry;
      }
   }

   @Nullable
   public Waypoint findNearestWaypoint(BlockPos pos) {
      return findNearestWaypoint(this.snapshot.waypoints(), pos);
   }

   @Nullable
   private static Waypoint findNearestWaypoint(List<Waypoint> wps, BlockPos pos) {
      Waypoint nearest = null;
      double nearestDistSq = Double.MAX_VALUE;

      for (Waypoint wp : wps) {
         double distSq = wp.pos().distSqr(pos);
         if (distSq < nearestDistSq) {
            nearestDistSq = distSq;
            nearest = wp;
         }
      }

      return nearest;
   }

   public List<BlockPos> findPath(BlockPos from, BlockPos to) {
      VillageWaypointGraph.GraphSnapshot s = this.snapshot;
      List<Waypoint> wps = s.waypoints();
      Map<Waypoint, List<WaypointEdge>> adj = s.adjacency();
      if (wps.size() < 2) {
         return Collections.emptyList();
      }

      Waypoint startWp = findNearestWaypoint(wps, from);
      Waypoint endWp = findNearestWaypoint(wps, to);
      if (startWp != null && endWp != null) {
         if (startWp.equals(endWp)) {
            return Collections.emptyList();
         }

         Map<Waypoint, Double> gScore = new HashMap<>();
         Map<Waypoint, Waypoint> cameFrom = new HashMap<>();
         Set<Waypoint> closed = new HashSet<>();
         gScore.put(startWp, 0.0);
         PriorityQueue<Waypoint> open = new PriorityQueue<>(
            Comparator.comparingDouble(wp -> gScore.getOrDefault(wp, Double.MAX_VALUE) + this.heuristic(wp, endWp))
         );
         open.add(startWp);

         while (!open.isEmpty()) {
            Waypoint current = open.poll();
            if (current.equals(endWp)) {
               return this.reconstructPath(cameFrom, current);
            }

            if (!closed.contains(current)) {
               closed.add(current);

               for (WaypointEdge edge : adj.getOrDefault(current, Collections.emptyList())) {
                  Waypoint neighbor = edge.target();
                  if (!closed.contains(neighbor)) {
                     double tentativeG = gScore.getOrDefault(current, Double.MAX_VALUE) + edge.cost();
                     if (tentativeG < gScore.getOrDefault(neighbor, Double.MAX_VALUE)) {
                        gScore.put(neighbor, tentativeG);
                        cameFrom.put(neighbor, current);
                        open.add(neighbor);
                     }
                  }
               }
            }
         }

         return Collections.emptyList();
      } else {
         return Collections.emptyList();
      }
   }

   public boolean isAvailable() {
      return this.snapshot.waypoints().size() >= 2;
   }

   public List<Waypoint> getWaypoints() {
      return Collections.unmodifiableList(this.snapshot.waypoints());
   }

   public List<VillageWaypointGraph.DirectedEdge> getEdges() {
      VillageWaypointGraph.GraphSnapshot s = this.snapshot;
      List<Waypoint> wps = s.waypoints();
      Map<Waypoint, List<WaypointEdge>> adj = s.adjacency();
      List<VillageWaypointGraph.DirectedEdge> out = new ArrayList<>();
      Set<Waypoint> seen = new HashSet<>();

      for (Waypoint a : wps) {
         seen.add(a);

         for (WaypointEdge edge : adj.getOrDefault(a, Collections.emptyList())) {
            if (!seen.contains(edge.target())) {
               out.add(new VillageWaypointGraph.DirectedEdge(a, edge.target(), edge.cost(), edge.pathNodes()));
            }
         }
      }

      return out;
   }

   public int waypointCount() {
      return this.snapshot.waypoints().size();
   }

   public long structuralSignature() {
      VillageWaypointGraph.GraphSnapshot s = this.snapshot;
      List<Waypoint> wps = s.waypoints();
      long[] posKeys = new long[wps.size()];

      for (int i = 0; i < wps.size(); i++) {
         posKeys[i] = wps.get(i).pos().asLong();
      }

      Arrays.sort(posKeys);
      long hash = 1125899906842597L;

      for (long key : posKeys) {
         hash = hash * 31L + key;
      }

      int edgeCount = 0;

      for (List<WaypointEdge> edges : s.adjacency().values()) {
         edgeCount += edges.size();
      }

      return hash * 31L + edgeCount;
   }

   private static boolean hasPathNodeTag(BuildingInstance building) {
      if (!building.getRuntimeTags().isEmpty()) {
         return building.hasRuntimeTag("pathnode");
      }

      BuildingPlan plan = building.getPlanId() != null ? ModCultures.getBuildingPlan(building.getPlanId()) : null;
      return plan != null && plan.tags().contains("pathnode");
   }

   private double heuristic(Waypoint a, Waypoint b) {
      return Math.sqrt(a.pos().distSqr(b.pos()));
   }

   private List<BlockPos> reconstructPath(Map<Waypoint, Waypoint> cameFrom, Waypoint current) {
      List<BlockPos> path = new ArrayList<>();

      while (cameFrom.containsKey(current)) {
         path.add(current.pos());
         current = cameFrom.get(current);
      }

      Collections.reverse(path);
      return path;
   }

   public record DirectedEdge(Waypoint from, Waypoint to, double cost, List<BlockPos> pathNodes) {
   }

   public record GraphSnapshot(List<Waypoint> waypoints, Map<Waypoint, List<WaypointEdge>> adjacency) {
      static final VillageWaypointGraph.GraphSnapshot EMPTY = new VillageWaypointGraph.GraphSnapshot(List.of(), Map.of());
   }
}
