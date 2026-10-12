package org.millenaire.goal.impl;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import org.millenaire.entity.VillagerNavDriver;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.ProgressAwareTask;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TravelPhase;

abstract class WalkThenActTask extends ProgressAwareTask {
   protected static final double ARRIVE_DISTANCE = 3.0;
   protected static final double WALK_SPEED = 0.5;
   protected static final int DEFAULT_ACTION_DURATION = 40;
   protected WalkThenActTask.Phase phase = WalkThenActTask.Phase.WALKING;
   protected int actionTicks;

   @Nullable
   protected abstract BlockPos resolveTarget(GoalContext var1);

   protected abstract void performAction(GoalContext var1);

   protected int actionDuration() {
      return 40;
   }

   @Override
   public void tick(GoalContext ctx) {
      switch (this.phase) {
         case WALKING:
            this.tickWalking(ctx);
            break;
         case ACTING:
            this.tickActing(ctx);
         case DONE:
      }
   }

   private void tickWalking(GoalContext ctx) {
      BlockPos target = this.resolveTarget(ctx);
      if (target == null) {
         this.phase = WalkThenActTask.Phase.DONE;
      } else {
         VillagerNavDriver nav = ctx.villager().getNavManager();
         if (nav.getDestination() == null) {
            nav.navigateTo(ctx.villager(), target, 0.5);
         }

         if (nav.isArrivedSameFloor(ctx.villager(), 3.0)) {
            nav.stop(ctx.villager());
            this.phase = WalkThenActTask.Phase.ACTING;
            this.actionTicks = 0;
            this.reportProgress();
         } else {
            if (nav.isAbandoned()) {
               nav.stop(ctx.villager());
               this.phase = WalkThenActTask.Phase.DONE;
            }
         }
      }
   }

   private void tickActing(GoalContext ctx) {
      this.actionTicks++;
      if (this.actionTicks >= this.actionDuration()) {
         this.performAction(ctx);
         this.phase = WalkThenActTask.Phase.DONE;
      }
   }

   @Override
   public boolean isFinished() {
      return this.phase == WalkThenActTask.Phase.DONE;
   }

   @Override
   public void stop(GoalContext ctx, StopReason reason) {
      if (ctx != null) {
         ctx.villager().getNavManager().stop(ctx.villager());
      }
   }

   @Override
   public boolean isFarSleepPausable() {
      return this.phase == WalkThenActTask.Phase.WALKING;
   }

   @Override
   public TravelPhase getTravelPhase() {
      return this.phase == WalkThenActTask.Phase.WALKING ? TravelPhase.TRAVELLING : TravelPhase.AT_DESTINATION;
   }

   protected enum Phase {
      WALKING,
      ACTING,
      DONE;
   }
}
