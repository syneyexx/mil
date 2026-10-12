package org.millenaire.worldgen.highway;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.perf.compute.ComputeResult;
import org.slf4j.Logger;

/**
 * Applies offloaded highway link plans on the server thread.
 */
public record HighwayPlanComputeResult(List<HighwayRoute> planned, long generation, long revision, long configKey) implements ComputeResult {
   private static final Logger LOGGER = LogUtils.getLogger();

   @Override
   public boolean apply(ServerLevel level) {
      if (!HighwayMaterializer.completeAsyncPlan(this.generation, this.revision, this.configKey)) {
         return false;
      }
      if (this.planned == null || this.planned.isEmpty()) {
         return true;
      }
      HighwayNodeSavedData nodes = HighwayNodeSavedData.get(level);
      HighwayRouteSavedData routes = HighwayRouteSavedData.get(level);
      for (HighwayRoute route : this.planned) {
         if (routes.hasLink(route.fromNodeKey(), route.toNodeKey())) {
            continue;
         }
         routes.put(route);
         HighwayNode from = nodes.get(route.fromNodeKey());
         HighwayNode to = nodes.get(route.toNodeKey());
         if (from != null) {
            from.incrementLinkDegree();
         }
         if (to != null) {
            to.incrementLinkDegree();
         }
         LOGGER.debug(
            "[Millenaire] Highway planned (async) {} -> {} ({} cells)",
            route.fromNodeKey(),
            route.toNodeKey(),
            route.waypoints().size()
         );
      }
      nodes.setDirty();
      routes.setDirty();
      return true;
   }
}
