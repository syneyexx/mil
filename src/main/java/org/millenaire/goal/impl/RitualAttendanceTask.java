package org.millenaire.goal.impl;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.entity.VillagerNavDriver;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TaskLabels;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerTask;
import org.millenaire.perf.TickPerfCounters;

/**
 * Walk to a ritual site and wait. Abandoned navigation is failure — not arrival
 * (unlike {@link WalkAndWaitTask}).
 */
public class RitualAttendanceTask implements VillagerTask {
   private static final ResourceLocation GOAL_ID = ResourceLocation.fromNamespaceAndPath("millenaire", "ritual_attendance");
   private static final double ARRIVE_DISTANCE = 3.0;
   private final BlockPos target;
   private final int maxWaitTicks;
   private boolean arrived;
   private boolean failed;
   private int waitTicks;

   public RitualAttendanceTask(BlockPos target, int maxWaitTicks) {
      this.target = target;
      this.maxWaitTicks = Math.max(40, maxWaitTicks);
   }

   @Override
   public ResourceLocation goalId() {
      return GOAL_ID;
   }

   public boolean arrived() {
      return this.arrived;
   }

   public boolean failed() {
      return this.failed;
   }

   @Override
   public void tick(GoalContext ctx) {
      if (this.failed || this.target == null) {
         this.failed = true;
         return;
      }
      if (!this.arrived) {
         VillagerNavDriver nav = ctx.villager().getNavManager();
         if (nav.getDestination() == null) {
            nav.navigateTo(ctx.villager(), this.target, 0.5);
            TickPerfCounters.RELIGION_NAV_STARTS.incrementAndGet();
         }
         if (nav.isArrived(ctx.villager(), ARRIVE_DISTANCE)) {
            this.arrived = true;
            nav.stop(ctx.villager());
         } else if (nav.isAbandoned()) {
            nav.stop(ctx.villager());
            this.failed = true;
         }
         return;
      }
      this.waitTicks++;
   }

   @Override
   public boolean isFinished() {
      return this.failed || (this.arrived && this.waitTicks >= this.maxWaitTicks);
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
