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
   private static final int NODE_BUDGET_MULTIPLIER = 8;

   public MillPathNavigation(Mob mob, Level level) {
      super(mob, level);
      this.setCanOpenDoors(true);
      this.setCanWalkOverFences(false);
      this.setCanFloat(true);
   }

   protected PathFinder createPathFinder(int maxVisitedNodes) {
      this.nodeEvaluator = new MillWalkNodeEvaluator(this.mob);
      this.nodeEvaluator.setCanPassDoors(true);
      this.nodeEvaluator.setCanOpenDoors(true);
      return new MillPathFinder(this.nodeEvaluator, maxVisitedNodes * 8);
   }

   @Nullable
   protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange) {
      NavigationCounters.incAstarCreatePath();
      return super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange);
   }
}
