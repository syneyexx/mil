package org.millenaire.goal.impl;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.activity.ActivityOperation;
import org.millenaire.activity.ActivitySavedData;
import org.millenaire.activity.ActivityState;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.village.VillagerRecord;

/**
 * High-priority mission travel/execute goal for NPCs reserved on a regional activity.
 * Does not override active combat targets every tick.
 */
public class ActivityMissionGoal implements VillagerGoal {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "activity_mission");
   static final int PRIORITY = 8000;

   @Override
   public ResourceLocation id() {
      return ID;
   }

   @Override
   public int computePriority(GoalContext context) {
      return PRIORITY;
   }

   @Override
   public boolean canBeDoneAtNight() {
      return true;
   }

   @Override
   public boolean isCombatUrgent() {
      return false;
   }

   @Override
   public boolean canStart(GoalContext context) {
      MillVillager v = context.villager();
      if (!(v.level() instanceof ServerLevel level)) {
         return false;
      }
      ActivityOperation op = findOperation(level, v);
      return op != null && op.state().isActivePhysical();
   }

   @Override
   public VillagerTask start(GoalContext context) {
      return new ActivityMissionTask();
   }

   @Nullable
   static ActivityOperation findOperation(ServerLevel level, MillVillager v) {
      VillagerRecord rec = contextVillageRecord(level, v);
      if (rec == null || rec.getActivityOperationId() == null) {
         return null;
      }
      return ActivitySavedData.get(level).getOperation(rec.getActivityOperationId());
   }

   @Nullable
   private static VillagerRecord contextVillageRecord(ServerLevel level, MillVillager v) {
      if (v.getVillageId() == null) return null;
      var village = org.millenaire.village.Village.resolve(level, v.getVillageId());
      if (village == null) return null;
      return village.getVillagerRecords().get(v.getUUID());
   }

   static class ActivityMissionTask implements VillagerTask {
      private boolean finished;

      @Override
      public ResourceLocation goalId() {
         return ActivityMissionGoal.ID;
      }

      @Override
      public void tick(GoalContext ctx) {
         MillVillager v = ctx.villager();
         if (!(v.level() instanceof ServerLevel level)) {
            this.finished = true;
            return;
         }
         // Do not stomp combat
         if (v.getTarget() != null && v.getTarget().isAlive()) {
            return;
         }
         ActivityOperation op = ActivityMissionGoal.findOperation(level, v);
         if (op == null || op.state().isTerminal() || op.state() == ActivityState.SETTLING) {
            this.finished = true;
            v.getNavManager().stop(v);
            return;
         }
         var wp = op.currentWaypoint();
         if (wp != null && v.blockPosition().distSqr(wp) > 16.0) {
            // Soft follow — director owns nav budget; goal only nudges if idle
            if (v.getNavigation().isDone()) {
               v.getNavManager().navigateTo(v, wp, 0.55);
            }
         }
      }

      @Override
      public boolean isFinished() {
         return this.finished;
      }

      @Override
      public void stop(GoalContext ctx, StopReason reason) {
         ctx.villager().getNavManager().stop(ctx.villager());
      }
   }
}
