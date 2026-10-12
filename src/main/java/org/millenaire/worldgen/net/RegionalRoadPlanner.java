package org.millenaire.worldgen.net;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.worldgen.plan.PlannedRoad;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.WorldgenSeeds;

/**
 * Plans bounded inter-settlement connectors with stable route identity.
 * Coarse waypoints guide the corridor; {@link #expandContinuous} produces adjacent walkable cells.
 * Physical placement is handled by {@link RegionalRoadMaterializer}.
 */
public final class RegionalRoadPlanner {
   private static final int MAX_NEIGHBORS = 2;
   private static final int MAX_LINK_DISTANCE = 1200;
   private static final int COARSE_SPACING = 32;
   private static final int MAX_STEP_RISE = 2;

   private RegionalRoadPlanner() {
   }

   public static List<PlannedRoad> planLinks(SettlementPlan from, List<SettlementPlan> nearby) {
      if (!Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenGenerateRegionalRoads.get())) {
         return List.of();
      }
      List<SettlementPlan> candidates = new ArrayList<>();
      for (SettlementPlan other : nearby) {
         if (other.planId().equals(from.planId()) || other.status().isTerminalFailure()) {
            continue;
         }
         long dx = (long)other.center().getX() - from.center().getX();
         long dz = (long)other.center().getZ() - from.center().getZ();
         long distSq = dx * dx + dz * dz;
         if (distSq > 0 && distSq <= (long)MAX_LINK_DISTANCE * MAX_LINK_DISTANCE) {
            candidates.add(other);
         }
      }
      candidates.sort(Comparator
         .comparingLong((SettlementPlan o) -> {
            long dx = (long)o.center().getX() - from.center().getX();
            long dz = (long)o.center().getZ() - from.center().getZ();
            return dx * dx + dz * dz;
         })
         .thenComparing(SettlementPlan::planId));

      RandomSource rng = WorldgenSeeds.stream(from.worldSeed(), WorldgenSeeds.SALT_ROAD, from.region());
      List<PlannedRoad> links = new ArrayList<>();
      int limit = Math.min(MAX_NEIGHBORS, candidates.size());
      for (int i = 0; i < limit; i++) {
         SettlementPlan to = candidates.get(i);
         // Deterministic arbitration on full UUID (MSB then LSB).
         int cmp = Long.compareUnsigned(from.planId().getMostSignificantBits(), to.planId().getMostSignificantBits());
         if (cmp > 0) {
            continue;
         }
         if (cmp == 0 && Long.compareUnsigned(from.planId().getLeastSignificantBits(), to.planId().getLeastSignificantBits()) > 0) {
            continue;
         }
         BlockPos fromAttach = attachmentPoint(from, to.center());
         BlockPos toAttach = attachmentPoint(to, from.center());
         List<BlockPos> coarse = coarseWaypoints(fromAttach, toAttach, rng);
         List<BlockPos> continuous = expandContinuous(coarse);
         UUID roadId = canonicalRouteId(from.worldSeed(), from.planId(), to.planId(), from.region().packed(), to.region().packed());
         links.add(new PlannedRoad(roadId, from.planId(), to.planId(), continuous));
      }
      return links;
   }

   public static UUID canonicalRouteId(long worldSeed, UUID fromPlan, UUID toPlan, long fromRegion, long toRegion) {
      UUID a = fromPlan;
      UUID b = toPlan;
      if (Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits()) > 0
         || (a.getMostSignificantBits() == b.getMostSignificantBits()
            && Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits()) > 0)) {
         a = toPlan;
         b = fromPlan;
         long tmp = fromRegion;
         fromRegion = toRegion;
         toRegion = tmp;
      }
      return new UUID(
         WorldgenSeeds.mix(worldSeed, WorldgenSeeds.SALT_ROAD, a.getMostSignificantBits(), b.getMostSignificantBits()),
         WorldgenSeeds.mix(worldSeed, WorldgenSeeds.SALT_ROAD, fromRegion, toRegion)
      );
   }

   /**
    * Prefer an existing local-road edge toward the neighbour; otherwise a radius rim point —
    * never the townhall centre when buildings occupy it.
    */
   public static BlockPos attachmentPoint(SettlementPlan plan, BlockPos toward) {
      List<BlockPos> roadPoints = new ArrayList<>();
      for (var road : plan.roads()) {
         roadPoints.addAll(road.waypoints());
      }
      return attachmentPoint(plan.center(), plan.radius(), roadPoints, toward);
   }

   /** Immutable-input variant for off-thread planning. */
   public static BlockPos attachmentPoint(BlockPos center, int planRadius, List<BlockPos> roadPoints, BlockPos toward) {
      BlockPos best = null;
      double bestScore = Double.MAX_VALUE;
      for (BlockPos p : roadPoints) {
         double towardDist = p.distSqr(toward);
         double centerDist = p.distSqr(center);
         // Prefer points near the rim facing the neighbour.
         double score = towardDist - centerDist * 0.25;
         if (score < bestScore) {
            bestScore = score;
            best = p;
         }
      }
      if (best != null) {
         return best;
      }
      int radius = Math.max(8, planRadius - 4);
      double dx = toward.getX() - center.getX();
      double dz = toward.getZ() - center.getZ();
      double len = Math.sqrt(dx * dx + dz * dz);
      if (len < 1.0) {
         return center.offset(radius, 0, 0);
      }
      int x = center.getX() + (int)Math.round(dx / len * radius);
      int z = center.getZ() + (int)Math.round(dz / len * radius);
      return new BlockPos(x, center.getY(), z);
   }

   private static List<BlockPos> coarseWaypoints(BlockPos from, BlockPos to, RandomSource rng) {
      List<BlockPos> pts = new ArrayList<>();
      int steps = Math.max(2, (int)Math.ceil(Math.sqrt(from.distSqr(to)) / (double)COARSE_SPACING));
      for (int i = 0; i <= steps; i++) {
         double t = i / (double)steps;
         int x = (int)Math.round(from.getX() + (to.getX() - from.getX()) * t);
         int z = (int)Math.round(from.getZ() + (to.getZ() - from.getZ()) * t);
         rng.nextInt(); // salt separation
         pts.add(new BlockPos(x, from.getY(), z));
      }
      return pts;
   }

   /**
    * Expand coarse corridor points into an adjacent, walkable cell sequence (Bresenham + diagonal fill).
    */
   public static List<BlockPos> expandContinuous(List<BlockPos> coarse) {
      if (coarse == null || coarse.isEmpty()) {
         return List.of();
      }
      List<BlockPos> out = new ArrayList<>();
      Set<Long> seen = new HashSet<>();
      BlockPos prev = null;
      for (BlockPos next : coarse) {
         if (prev == null) {
            addUnique(out, seen, next);
            prev = next;
            continue;
         }
         for (BlockPos step : bresenham(prev, next)) {
            addUnique(out, seen, step);
         }
         prev = next;
      }
      return out;
   }

   private static void addUnique(List<BlockPos> out, Set<Long> seen, BlockPos p) {
      long key = (((long)p.getX()) << 32) ^ (p.getZ() & 0xffffffffL);
      if (seen.add(key)) {
         out.add(p);
      }
   }

   private static List<BlockPos> bresenham(BlockPos from, BlockPos to) {
      List<BlockPos> pts = new ArrayList<>();
      int x0 = from.getX();
      int z0 = from.getZ();
      int x1 = to.getX();
      int z1 = to.getZ();
      int dx = Math.abs(x1 - x0);
      int dz = Math.abs(z1 - z0);
      int sx = x0 < x1 ? 1 : -1;
      int sz = z0 < z1 ? 1 : -1;
      int err = dx - dz;
      int x = x0;
      int z = z0;
      int prevX = x;
      int prevZ = z;
      while (true) {
         // Fill diagonal corners so the route stays orthogonally walkable.
         if (x != prevX && z != prevZ) {
            pts.add(new BlockPos(prevX, from.getY(), z));
         }
         pts.add(new BlockPos(x, from.getY(), z));
         if (x == x1 && z == z1) {
            break;
         }
         prevX = x;
         prevZ = z;
         int e2 = 2 * err;
         if (e2 > -dz) {
            err -= dz;
            x += sx;
         }
         if (e2 < dx) {
            err += dx;
            z += sz;
         }
      }
      return pts;
   }

   /** Exposed for diagnostics / steepness checks during materialization. */
   public static int maxStepRise() {
      return MAX_STEP_RISE;
   }
}
