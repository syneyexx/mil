package org.millenaire.worldgen.net;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.worldgen.plan.PlannedRoad;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.WorldgenSeeds;

/**
 * Plans bounded inter-settlement connectors with stable route identity.
 * Physical placement is handled by {@link RegionalRoadMaterializer} when chunks are available.
 * {@code worldgenGenerateRegionalRoads} gates planning; {@code worldgenGenerateBridges} gates water crossings.
 */
public final class RegionalRoadPlanner {
   private static final int MAX_NEIGHBORS = 2;
   private static final int MAX_LINK_DISTANCE = 1200;

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
      candidates.sort(Comparator.comparingLong(o -> {
         long dx = (long)o.center().getX() - from.center().getX();
         long dz = (long)o.center().getZ() - from.center().getZ();
         return dx * dx + dz * dz;
      }));

      RandomSource rng = WorldgenSeeds.stream(from.worldSeed(), WorldgenSeeds.SALT_ROAD, from.region());
      List<PlannedRoad> links = new ArrayList<>();
      int limit = Math.min(MAX_NEIGHBORS, candidates.size());
      for (int i = 0; i < limit; i++) {
         SettlementPlan to = candidates.get(i);
         // Deterministic arbitration: only the lower unsigned plan id creates the link
         if (Long.compareUnsigned(from.planId().getMostSignificantBits(), to.planId().getMostSignificantBits()) > 0) {
            continue;
         }
         List<BlockPos> waypoints = coarseWaypoints(from.center(), to.center(), rng);
         UUID roadId = new UUID(
            WorldgenSeeds.mix(from.worldSeed(), WorldgenSeeds.SALT_ROAD, from.planId().getMostSignificantBits(), to.planId().getMostSignificantBits()),
            WorldgenSeeds.mix(from.worldSeed(), WorldgenSeeds.SALT_ROAD, from.region().packed(), to.region().packed())
         );
         links.add(new PlannedRoad(roadId, from.planId(), to.planId(), waypoints));
      }
      return links;
   }

   private static List<BlockPos> coarseWaypoints(BlockPos from, BlockPos to, RandomSource rng) {
      List<BlockPos> pts = new ArrayList<>();
      int steps = Math.max(2, (int)Math.ceil(Math.sqrt(from.distSqr(to)) / 32.0));
      for (int i = 0; i <= steps; i++) {
         double t = i / (double)steps;
         int x = (int)Math.round(from.getX() + (to.getX() - from.getX()) * t);
         int z = (int)Math.round(from.getZ() + (to.getZ() - from.getZ()) * t);
         rng.nextInt(); // salt separation
         pts.add(new BlockPos(x, from.getY(), z));
      }
      return pts;
   }
}
