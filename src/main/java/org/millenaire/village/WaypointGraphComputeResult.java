package org.millenaire.village;

import net.minecraft.server.level.ServerLevel;
import org.millenaire.perf.compute.ComputeResult;

/**
 * Atomically publishes an offloaded waypoint graph rebuild.
 */
public record WaypointGraphComputeResult(
   VillageId villageId,
   long generation,
   VillageWaypointGraph.GraphSnapshot graph,
   long rebuildMillis,
   int nodeCount,
   int edgeCount
) implements ComputeResult {
   @Override
   public boolean apply(ServerLevel level) {
      Village village = VillageSavedData.get(level).getVillageManager().getVillage(this.villageId);
      if (village == null) {
         return false;
      }
      return village.getWaypointGraph().publishAsyncRebuild(
         this.generation, this.graph, this.rebuildMillis, this.nodeCount, this.edgeCount, level.getGameTime(), village
      );
   }
}
