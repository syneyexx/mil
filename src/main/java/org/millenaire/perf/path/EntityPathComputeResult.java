package org.millenaire.perf.path;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Path;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerNavigationManager;
import org.millenaire.perf.compute.ComputeResult;

/**
 * Applies an async entity path on the server thread.
 */
public record EntityPathComputeResult(UUID entityUuid, long generation, BlockPos destination, double speed, @Nullable Path path)
   implements ComputeResult {
   @Override
   public boolean apply(ServerLevel level) {
      Entity entity = level.getEntity(this.entityUuid);
      if (!(entity instanceof MillVillager villager) || !villager.isAlive()) {
         return false;
      }
      if (!(villager.getNavManager() instanceof VillagerNavigationManager nav)) {
         return false;
      }
      return nav.acceptAsyncPath(villager, this.generation, this.destination, this.speed, this.path);
   }
}
