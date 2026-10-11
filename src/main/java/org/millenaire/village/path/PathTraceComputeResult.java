package org.millenaire.village.path;

import net.minecraft.server.level.ServerLevel;
import org.millenaire.perf.compute.ComputeResult;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;

/**
 * Server-thread apply of one offloaded path A* search.
 */
public record PathTraceComputeResult(
   VillageId villageId,
   long generation,
   PathRoute route,
   VillagePathManager.TraceResult traceResult
) implements ComputeResult {
   @Override
   public boolean apply(ServerLevel level) {
      Village village = VillageSavedData.get(level).getVillageManager().getVillage(this.villageId);
      if (village == null) {
         return false;
      }
      return village.getPathManager().acceptAsyncTrace(level, village, this.generation, this.route, this.traceResult);
   }
}
