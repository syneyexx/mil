package org.millenaire.world.road;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Public read-only API for NPCs, merchants, and caravans.
 */
public final class RoadNetwork {
   private RoadNetwork() {
   }

   @Nullable
   public static BlockPos getNearestRoad(Level level, BlockPos pos) {
      if (!(level instanceof ServerLevel serverLevel)) {
         return null;
      }
      RoadNetworkSavedData data = RoadNetworkSavedData.get(serverLevel);
      BlockPos best = null;
      double bestDist = Double.MAX_VALUE;
      for (RoadEdge edge : data.getGraph().getEdges()) {
         int[] poly = edge.polyline();
         if (poly == null) {
            continue;
         }
         for (int i = 0; i < poly.length; i += 3) {
            BlockPos p = new BlockPos(poly[i], poly[i + 1], poly[i + 2]);
            double d = p.distSqr(pos);
            if (d < bestDist) {
               bestDist = d;
               best = p;
            }
         }
      }
      return best;
   }

   public static boolean isOnRoad(Level level, BlockPos pos) {
      if (!(level instanceof ServerLevel serverLevel)) {
         return false;
      }
      RoadNetworkSavedData data = RoadNetworkSavedData.get(serverLevel);
      int width = RoadNetworkConfig.roadWidth();
      for (RoadEdge edge : data.getGraph().getEdges()) {
         int[] poly = edge.polyline();
         if (poly == null) {
            continue;
         }
         for (int i = 0; i < poly.length; i += 3) {
            int dx = Math.abs(poly[i] - pos.getX());
            int dz = Math.abs(poly[i + 2] - pos.getZ());
            if (dx <= width && dz <= width && Math.abs(poly[i + 1] - pos.getY()) <= 3) {
               return true;
            }
         }
      }
      return false;
   }

   @Nullable
   public static RoadEdge getRoute(Level level, String villageA, String villageB) {
      if (!(level instanceof ServerLevel serverLevel)) {
         return null;
      }
      long id = RoadEdge.stableId(villageA, villageB);
      return RoadNetworkSavedData.get(serverLevel).getGraph().getEdge(id);
   }

   @Nullable
   public static List<Vec3> getRouteAsPolyline(Level level, long edgeId) {
      if (!(level instanceof ServerLevel serverLevel)) {
         return null;
      }
      RoadEdge edge = RoadNetworkSavedData.get(serverLevel).getGraph().getEdge(edgeId);
      if (edge == null || edge.polyline() == null) {
         return null;
      }
      int[] poly = edge.polyline();
      List<Vec3> out = new ArrayList<>(poly.length / 3);
      for (int i = 0; i < poly.length; i += 3) {
         out.add(new Vec3(poly[i] + 0.5, poly[i + 1], poly[i + 2] + 0.5));
      }
      return out;
   }
}
