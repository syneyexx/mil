package org.millenaire.worldgen.highway;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.millenaire.worldgen.plan.PlannedRoad;
import org.millenaire.worldgen.plan.SettlementPlan;

/**
 * Immutable settlement attachment inputs captured on the server thread for off-thread highway planning.
 */
public record HighwayAttachmentData(UUID planId, BlockPos center, int radius, List<BlockPos> attachmentPoints) {
   public HighwayAttachmentData {
      attachmentPoints = List.copyOf(attachmentPoints);
   }

   public static HighwayAttachmentData capture(SettlementPlan plan) {
      List<BlockPos> points = new ArrayList<>();
      for (PlannedRoad road : plan.roads()) {
         points.addAll(road.waypoints());
      }
      return new HighwayAttachmentData(plan.planId(), plan.center(), plan.radius(), points);
   }
}
