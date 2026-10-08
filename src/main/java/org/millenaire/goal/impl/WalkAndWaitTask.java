package org.millenaire.goal.impl;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerNavDriver;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TaskLabels;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerTask;

public abstract class WalkAndWaitTask implements VillagerTask {
   protected static final double ARRIVE_DISTANCE = 3.0;
   protected static final double WALK_SPEED = 0.5;
   private static final double WANDER_RADIUS_SQ = 100.0;
   @Nullable
   protected final BlockPos target;
   protected final int maxWaitTicks;
   protected boolean arrived;
   protected int waitTicks;
   @Nullable
   private BlockPos wanderAnchor;

   protected WalkAndWaitTask(@Nullable BlockPos target, int maxWaitTicks) {
      this.target = target;
      this.maxWaitTicks = maxWaitTicks;
      this.wanderAnchor = target;
      if (target == null) {
         this.arrived = true;
      }
   }

   protected boolean allowRandomMoves() {
      return false;
   }

   protected void tickAtDestination(GoalContext ctx) {
   }

   @Override
   public final void tick(GoalContext ctx) {
      if (!this.arrived) {
         VillagerNavDriver nav = ctx.villager().getNavManager();
         if (nav.getDestination() == null) {
            nav.navigateTo(ctx.villager(), this.target, 0.5);
         }

         if (nav.isArrived(ctx.villager(), 3.0) || nav.isAbandoned()) {
            this.arrived = true;
            nav.stop(ctx.villager());
         }
      }

      if (this.arrived) {
         if (this.wanderAnchor == null) {
            this.wanderAnchor = ctx.villager().blockPosition();
         }

         this.waitTicks++;
         this.tickAtDestination(ctx);
         if (this.allowRandomMoves()) {
            this.maybeWander(ctx);
         }
      }
   }

   private void maybeWander(GoalContext ctx) {
      MillVillager villager = ctx.villager();
      if (villager.getNavigation().isDone()) {
         if (villager.getRandom().nextInt(120) == 0) {
            Vec3 pos = LandRandomPos.getPos(villager, 10, 7);
            if (pos != null) {
               if (this.wanderAnchor == null
                  || !(pos.distanceToSqr(this.wanderAnchor.getX() + 0.5, this.wanderAnchor.getY(), this.wanderAnchor.getZ() + 0.5) > 100.0)) {
                  BlockPos below = BlockPos.containing(pos.x, pos.y, pos.z).below();
                  if (!villager.level().getBlockState(below).is(Blocks.FARMLAND)) {
                     villager.getNavigation().moveTo(pos.x, pos.y, pos.z, 0.5);
                  }
               }
            }
         }
      }
   }

   @Override
   public boolean isFinished() {
      return this.arrived && this.waitTicks >= this.maxWaitTicks;
   }

   @Override
   public void stop(GoalContext ctx, StopReason reason) {
      if (ctx != null) {
         ctx.villager().getNavManager().stop(ctx.villager());
      }
   }

   @Override
   public TravelPhase getTravelPhase() {
      return TaskLabels.phaseFor(this.arrived);
   }
}
