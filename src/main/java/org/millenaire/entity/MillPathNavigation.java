package org.millenaire.entity;

import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.millenaire.diagnostics.NavigationCounters;

public class MillPathNavigation extends GroundPathNavigation {
   public MillPathNavigation(Mob mob, Level level) {
      super(mob, level);
      this.setCanOpenDoors(true);
      this.setCanWalkOverFences(false);
      this.setCanFloat(true);
   }

   private static int nodeMultiplier() {
      try {
         return Math.max(1, org.millenaire.config.MillenaireServerConfig.SERVER.pathNodeMultiplier.getAsInt());
      } catch (IllegalStateException e) {
         return 2;
      }
   }

   protected PathFinder createPathFinder(int maxVisitedNodes) {
      this.nodeEvaluator = new MillWalkNodeEvaluator(this.mob);
      this.nodeEvaluator.setCanPassDoors(true);
      this.nodeEvaluator.setCanOpenDoors(true);
      return new MillPathFinder(this.nodeEvaluator, maxVisitedNodes * nodeMultiplier());
   }

   @Nullable
   protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange) {
      // Mid LOD: prefer async snapshot A* (does not burn sync PathRequestQueue slots).
      if (this.mob instanceof MillVillager villager
         && villager.getNavManager() instanceof VillagerNavigationManager nav
         && !targets.isEmpty()) {
         BlockPos dest = targets.iterator().next();
         if (nav.trySubmitAsyncPath(villager, dest, 0.5)) {
            return null;
         }
      }
      int entityId = this.mob.getId();
      long gameTime = this.level.getGameTime();
      if (!org.millenaire.perf.path.PathRequestQueue.tryBeginSearch(entityId, gameTime)) {
         return null;
      }
      try {
         NavigationCounters.incAstarCreatePath();
         return super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange);
      } finally {
         org.millenaire.perf.path.PathRequestQueue.completeSearch(entityId);
      }
   }
}
