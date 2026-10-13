package org.millenaire.world.road;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Builds a realistic road network: k-nearest candidates → Kruskal MST → optional loops.
 * Preserves DONE edges across rebuilds when both endpoints still exist.
 */
public final class RoadGraph {
   private final Map<Long, RoadEdge> edges = new HashMap<>();

   public Collection<RoadEdge> getEdges() {
      return this.edges.values();
   }

   @Nullable
   public RoadEdge getEdge(long id) {
      return this.edges.get(id);
   }

   public int size() {
      return this.edges.size();
   }

   public int countByState(EdgeState state) {
      int n = 0;
      for (RoadEdge e : this.edges.values()) {
         if (e.state() == state) {
            n++;
         }
      }
      return n;
   }

   /**
    * Rebuild graph from current nodes. Keeps DONE/PLANNED/BUILDING edges whose endpoints still exist.
    */
   public boolean rebuild(Collection<RoadNode> nodes, int kNearest, double maxEdgeLength, double hardMaxEdgeLength, double loopRatio) {
      List<RoadNode> list = new ArrayList<>(nodes);
      if (list.size() < 2) {
         this.edges.keySet().removeIf(id -> {
            RoadEdge e = this.edges.get(id);
            return e == null || !nodeExists(list, e.nodeA()) || !nodeExists(list, e.nodeB());
         });
         return false;
      }

      Map<Long, RoadEdge> previous = new HashMap<>(this.edges);
      this.edges.clear();

      List<Candidate> candidates = buildCandidates(list, kNearest, maxEdgeLength);
      List<Candidate> mst = kruskal(list, candidates);

      Set<Long> kept = new HashSet<>();
      for (Candidate c : mst) {
         keepOrCreate(previous, c, kept);
      }

      // Extra loop edges where MST detour is expensive
      List<Candidate> extras = new ArrayList<>();
      for (Candidate c : candidates) {
         if (kept.contains(c.id)) {
            continue;
         }
         double mstDist = estimatePathDistance(list, mst, c.a, c.b);
         if (mstDist > 2.2 * c.dist) {
            extras.add(c);
         }
      }
      extras.sort(Comparator.comparingDouble(c -> c.dist));
      int extraBudget = Math.max(0, (int)Math.round(mst.size() * loopRatio));
      for (int i = 0; i < extras.size() && i < extraBudget; i++) {
         keepOrCreate(previous, extras.get(i), kept);
      }

      // Connect disconnected components with longer edges if needed
      connectComponents(list, previous, kept, hardMaxEdgeLength);

      // Drop obsolete previous edges not retained
      return true;
   }

   private void keepOrCreate(Map<Long, RoadEdge> previous, Candidate c, Set<Long> kept) {
      RoadEdge existing = previous.get(c.id);
      if (existing != null) {
         this.edges.put(c.id, existing);
      } else {
         this.edges.put(c.id, new RoadEdge(c.id, c.a, c.b));
      }
      kept.add(c.id);
   }

   private void connectComponents(List<RoadNode> nodes, Map<Long, RoadEdge> previous, Set<Long> kept, double hardMax) {
      Map<String, Integer> index = indexNodes(nodes);
      UnionFind uf = new UnionFind(nodes.size());
      for (long id : kept) {
         RoadEdge e = this.edges.get(id);
         if (e == null) {
            continue;
         }
         Integer ia = index.get(e.nodeA());
         Integer ib = index.get(e.nodeB());
         if (ia != null && ib != null) {
            uf.union(ia, ib);
         }
      }

      Set<Integer> roots = new HashSet<>();
      for (int i = 0; i < nodes.size(); i++) {
         roots.add(uf.find(i));
      }
      if (roots.size() <= 1) {
         return;
      }

      // Greedy: repeatedly link closest pair of different components
      boolean linked = true;
      while (roots.size() > 1 && linked) {
         linked = false;
         Candidate best = null;
         for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
               if (uf.find(i) == uf.find(j)) {
                  continue;
               }
               double dist = Math.sqrt(nodes.get(i).center().distSqr(nodes.get(j).center()));
               if (dist > hardMax) {
                  continue;
               }
               if (best == null || dist < best.dist) {
                  String a = nodes.get(i).id();
                  String b = nodes.get(j).id();
                  best = new Candidate(RoadEdge.stableId(a, b), a, b, dist);
               }
            }
         }
         if (best != null) {
            keepOrCreate(previous, best, kept);
            Integer ia = index.get(best.a);
            Integer ib = index.get(best.b);
            if (ia != null && ib != null) {
               uf.union(ia, ib);
            }
            roots.clear();
            for (int i = 0; i < nodes.size(); i++) {
               roots.add(uf.find(i));
            }
            linked = true;
         }
      }
   }

   private static boolean nodeExists(List<RoadNode> nodes, String id) {
      for (RoadNode n : nodes) {
         if (n.id().equals(id)) {
            return true;
         }
      }
      return false;
   }

   private static Map<String, Integer> indexNodes(List<RoadNode> nodes) {
      Map<String, Integer> map = new HashMap<>();
      for (int i = 0; i < nodes.size(); i++) {
         map.put(nodes.get(i).id(), i);
      }
      return map;
   }

   private static List<Candidate> buildCandidates(List<RoadNode> nodes, int k, double maxLen) {
      List<Candidate> out = new ArrayList<>();
      Set<Long> seen = new HashSet<>();
      for (int i = 0; i < nodes.size(); i++) {
         RoadNode a = nodes.get(i);
         Neighbor[] nearest = new Neighbor[k];
         int filled = 0;
         for (int j = 0; j < nodes.size(); j++) {
            if (i == j) {
               continue;
            }
            RoadNode b = nodes.get(j);
            double dist = Math.sqrt(a.center().distSqr(b.center()));
            if (dist > maxLen) {
               continue;
            }
            Neighbor n = new Neighbor(j, dist);
            if (filled < k) {
               nearest[filled++] = n;
               if (filled == k) {
                  Arrays.sort(nearest, 0, filled, Comparator.comparingDouble(x -> x.dist));
               }
            } else if (dist < nearest[k - 1].dist) {
               nearest[k - 1] = n;
               Arrays.sort(nearest, 0, k, Comparator.comparingDouble(x -> x.dist));
            }
         }
         for (int n = 0; n < filled; n++) {
            RoadNode b = nodes.get(nearest[n].index);
            long id = RoadEdge.stableId(a.id(), b.id());
            if (seen.add(id)) {
               out.add(new Candidate(id, a.id(), b.id(), nearest[n].dist));
            }
         }
      }
      out.sort(Comparator.comparingDouble(c -> c.dist));
      return out;
   }

   private static List<Candidate> kruskal(List<RoadNode> nodes, List<Candidate> candidates) {
      Map<String, Integer> index = indexNodes(nodes);
      UnionFind uf = new UnionFind(nodes.size());
      List<Candidate> mst = new ArrayList<>();
      for (Candidate c : candidates) {
         Integer ia = index.get(c.a);
         Integer ib = index.get(c.b);
         if (ia == null || ib == null) {
            continue;
         }
         if (uf.find(ia) != uf.find(ib)) {
            uf.union(ia, ib);
            mst.add(c);
         }
      }
      return mst;
   }

   /** Approximate MST-path distance between two nodes using only MST edges (BFS). */
   private static double estimatePathDistance(List<RoadNode> nodes, List<Candidate> mst, String from, String to) {
      Map<String, List<Candidate>> adj = new HashMap<>();
      for (Candidate c : mst) {
         adj.computeIfAbsent(c.a, k -> new ArrayList<>()).add(c);
         adj.computeIfAbsent(c.b, k -> new ArrayList<>()).add(c);
      }
      Map<String, Double> dist = new HashMap<>();
      ArrayList<String> queue = new ArrayList<>();
      dist.put(from, 0.0);
      queue.add(from);
      for (int qi = 0; qi < queue.size(); qi++) {
         String cur = queue.get(qi);
         if (cur.equals(to)) {
            return dist.get(cur);
         }
         double dCur = dist.get(cur);
         List<Candidate> nbrs = adj.get(cur);
         if (nbrs == null) {
            continue;
         }
         for (Candidate c : nbrs) {
            String next = c.a.equals(cur) ? c.b : c.a;
            double nd = dCur + c.dist;
            Double existing = dist.get(next);
            if (existing == null || nd < existing) {
               dist.put(next, nd);
               queue.add(next);
            }
         }
      }
      return Double.POSITIVE_INFINITY;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      ListTag list = new ListTag();
      for (RoadEdge e : this.edges.values()) {
         list.add(e.save());
      }
      tag.put("edges", list);
      return tag;
   }

   public void load(CompoundTag tag) {
      this.edges.clear();
      ListTag list = tag.getList("edges", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         RoadEdge e = RoadEdge.load(list.getCompound(i));
         this.edges.put(e.id(), e);
      }
   }

   private record Neighbor(int index, double dist) {
   }

   private record Candidate(long id, String a, String b, double dist) {
   }

   private static final class UnionFind {
      private final int[] parent;
      private final int[] rank;

      UnionFind(int n) {
         this.parent = new int[n];
         this.rank = new int[n];
         for (int i = 0; i < n; i++) {
            this.parent[i] = i;
         }
      }

      int find(int x) {
         while (this.parent[x] != x) {
            this.parent[x] = this.parent[this.parent[x]];
            x = this.parent[x];
         }
         return x;
      }

      void union(int a, int b) {
         int ra = find(a);
         int rb = find(b);
         if (ra == rb) {
            return;
         }
         if (this.rank[ra] < this.rank[rb]) {
            this.parent[ra] = rb;
         } else if (this.rank[ra] > this.rank[rb]) {
            this.parent[rb] = ra;
         } else {
            this.parent[rb] = ra;
            this.rank[ra]++;
         }
      }
   }
}
