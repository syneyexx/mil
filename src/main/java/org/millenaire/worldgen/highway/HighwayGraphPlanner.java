package org.millenaire.worldgen.highway;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.worldgen.highway.HighwayPlanSnapshot.NodeView;
import org.millenaire.worldgen.highway.HighwayPlanSnapshot.RouteView;
import org.millenaire.worldgen.net.RegionalRoadPlanner;
import org.millenaire.worldgen.plan.WorldgenSeeds;

/**
 * Sparse growing graph: k-nearest / component stitching among discovered highway nodes.
 * Pure planning accepts {@link HighwayPlanSnapshot} so it can run on worker threads.
 */
public final class HighwayGraphPlanner {
   private static final int COARSE_SPACING = 32;
   private static final long SALT_HIGHWAY = 0x4D494C4C48575931L; // MILLHWY1

   private HighwayGraphPlanner() {
   }

   public static List<HighwayRoute> planNewLinks(ServerLevel level, HighwayNodeSavedData nodes, HighwayRouteSavedData routes) {
      if (level == null || nodes == null || routes == null) {
         return List.of();
      }
      List<HighwayRoute> created = planFromSnapshot(HighwayPlanSnapshot.capture(level, nodes, routes));
      for (HighwayRoute route : created) {
         HighwayNode from = nodes.get(route.fromNodeKey());
         HighwayNode to = nodes.get(route.toNodeKey());
         if (from != null) {
            from.incrementLinkDegree();
         }
         if (to != null) {
            to.incrementLinkDegree();
         }
      }
      if (!created.isEmpty()) {
         nodes.setDirty();
      }
      return created;
   }

   /**
    * Pure link planning — no SavedData or ServerLevel mutation.
    */
   public static List<HighwayRoute> planFromSnapshot(HighwayPlanSnapshot snap) {
      if (snap == null || snap.nodes().isEmpty()) {
         return List.of();
      }
      long maxDistSq = (long)snap.maxDist() * snap.maxDist();
      Map<String, Integer> degree = new HashMap<>();
      Set<String> existingLinks = new HashSet<>();
      for (RouteView route : snap.routes()) {
         if (route.status() == HighwayRoute.Status.FAILED) {
            continue;
         }
         degree.merge(route.fromKey(), 1, Integer::sum);
         degree.merge(route.toKey(), 1, Integer::sum);
         existingLinks.add(HighwayPlanSnapshot.linkKey(route.fromKey(), route.toKey()));
      }

      UnionFind components = new UnionFind();
      for (NodeView n : snap.nodes()) {
         components.add(n.key());
      }
      for (RouteView route : snap.routes()) {
         if (route.status() != HighwayRoute.Status.FAILED) {
            components.union(route.fromKey(), route.toKey());
         }
      }

      List<NodeView> ordered = new ArrayList<>(snap.nodes());
      ordered.sort(Comparator.comparing(NodeView::key));
      List<HighwayRoute> created = new ArrayList<>();
      long seed = snap.worldSeed();

      for (NodeView from : ordered) {
         int fromDeg = degree.getOrDefault(from.key(), 0);
         int fromMax = from.maxNeighbors();
         if (fromDeg >= fromMax) {
            continue;
         }
         List<NodeView> candidates = new ArrayList<>();
         for (NodeView to : ordered) {
            if (to.key().equals(from.key())) {
               continue;
            }
            if (existingLinks.contains(HighwayPlanSnapshot.linkKey(from.key(), to.key()))) {
               continue;
            }
            int toDeg = degree.getOrDefault(to.key(), 0);
            if (toDeg >= to.maxNeighbors()) {
               continue;
            }
            long dx = (long)to.center().getX() - from.center().getX();
            long dz = (long)to.center().getZ() - from.center().getZ();
            long distSq = dx * dx + dz * dz;
            if (distSq <= 0 || distSq > maxDistSq) {
               continue;
            }
            candidates.add(to);
         }
         candidates.sort(Comparator
            .comparingInt((NodeView to) -> components.find(from.key()).equals(components.find(to.key())) ? 1 : 0)
            .thenComparingInt((NodeView to) -> to.factionHub() ? 0 : 1)
            .thenComparingLong(to -> {
               long dx = (long)to.center().getX() - from.center().getX();
               long dz = (long)to.center().getZ() - from.center().getZ();
               return dx * dx + dz * dz;
            })
            .thenComparing(NodeView::key));

         int need = Math.min(2, fromMax - fromDeg);
         for (int i = 0; i < candidates.size() && need > 0; i++) {
            NodeView to = candidates.get(i);
            if (from.key().compareTo(to.key()) > 0) {
               continue;
            }
            HighwayRoute route = buildRoute(seed, from, to, snap);
            if (route == null) {
               continue;
            }
            created.add(route);
            existingLinks.add(HighwayPlanSnapshot.linkKey(from.key(), to.key()));
            degree.merge(from.key(), 1, Integer::sum);
            degree.merge(to.key(), 1, Integer::sum);
            components.union(from.key(), to.key());
            need--;
         }
      }
      return created;
   }

   @Nullable
   private static HighwayRoute buildRoute(long worldSeed, NodeView from, NodeView to, HighwayPlanSnapshot snap) {
      BlockPos fromAttach = attachmentPoint(from, to.center(), snap);
      BlockPos toAttach = attachmentPoint(to, from.center(), snap);
      List<BlockPos> coarse = coarseWaypoints(fromAttach, toAttach);
      List<BlockPos> continuous = RegionalRoadPlanner.expandContinuous(coarse);
      if (continuous.size() < 2) {
         return null;
      }
      UUID routeId = canonicalRouteId(worldSeed, from.key(), to.key());
      return new HighwayRoute(routeId, from.key(), to.key(), continuous);
   }

   static BlockPos attachmentPoint(NodeView node, BlockPos toward, HighwayPlanSnapshot snap) {
      if (node.planId() != null) {
         HighwayAttachmentData attachment = snap.attachment(node.planId());
         if (attachment != null) {
            return RegionalRoadPlanner.attachmentPoint(
               attachment.center(), attachment.radius(), attachment.attachmentPoints(), toward
            );
         }
      }
      int radius = Math.max(8, node.radiusHint() - 4);
      double dx = toward.getX() - node.center().getX();
      double dz = toward.getZ() - node.center().getZ();
      double len = Math.sqrt(dx * dx + dz * dz);
      if (len < 1.0) {
         return node.center().offset(radius, 0, 0);
      }
      int x = node.center().getX() + (int)Math.round(dx / len * radius);
      int z = node.center().getZ() + (int)Math.round(dz / len * radius);
      return new BlockPos(x, node.center().getY(), z);
   }

   private static List<BlockPos> coarseWaypoints(BlockPos from, BlockPos to) {
      List<BlockPos> pts = new ArrayList<>();
      int steps = Math.max(2, (int)Math.ceil(Math.sqrt(from.distSqr(to)) / (double)COARSE_SPACING));
      for (int i = 0; i <= steps; i++) {
         double t = i / (double)steps;
         int x = (int)Math.round(from.getX() + (to.getX() - from.getX()) * t);
         int z = (int)Math.round(from.getZ() + (to.getZ() - from.getZ()) * t);
         pts.add(new BlockPos(x, from.getY(), z));
      }
      return pts;
   }

   public static UUID canonicalRouteId(long worldSeed, String fromKey, String toKey) {
      String a = fromKey;
      String b = toKey;
      if (a.compareTo(b) > 0) {
         a = toKey;
         b = fromKey;
      }
      return new UUID(
         WorldgenSeeds.mix(worldSeed, SALT_HIGHWAY, a.hashCode(), b.hashCode()),
         WorldgenSeeds.mix(worldSeed, WorldgenSeeds.SALT_ROAD, a.hashCode(), b.hashCode())
      );
   }

   private static final class UnionFind {
      private final Map<String, String> parent = new HashMap<>();

      void add(String key) {
         parent.putIfAbsent(key, key);
      }

      String find(String key) {
         String p = parent.getOrDefault(key, key);
         if (!p.equals(key)) {
            p = find(p);
            parent.put(key, p);
         }
         return p;
      }

      void union(String a, String b) {
         String ra = find(a);
         String rb = find(b);
         if (!ra.equals(rb)) {
            if (ra.compareTo(rb) < 0) {
               parent.put(rb, ra);
            } else {
               parent.put(ra, rb);
            }
         }
      }
   }
}
