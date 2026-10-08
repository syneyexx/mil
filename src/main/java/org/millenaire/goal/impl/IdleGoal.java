package org.millenaire.goal.impl;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.TaskLabels;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;

public class IdleGoal implements VillagerGoal {
   private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "idle");
   private static final double HOME_DISTANCE_THRESHOLD_SQ = 25.0;

   @Override
   public boolean showInTravelBook() {
      return false;
   }

   @Override
   public ResourceLocation id() {
      return ID;
   }

   @Override
   public int computePriority(GoalContext context) {
      return 0;
   }

   @Override
   public boolean isLeisure() {
      return true;
   }

   @Override
   public boolean canStart(GoalContext context) {
      return true;
   }

   @Override
   public VillagerTask start(GoalContext context) {
      BlockPos home = resolveHomePos(context);
      if (home != null && context.villager().blockPosition().distSqr(home) <= 25.0) {
         home = null;
      }

      return new IdleGoal.IdleTask(home);
   }

   @Nullable
   private static BlockPos resolveHomePos(GoalContext ctx) {
      BuildingId homeId = ctx.villager().getHomeBuilding();
      if (homeId != null) {
         BuildingInstance home = ctx.village().getBuilding(homeId);
         if (home != null) {
            return home.getSleepingPos();
         }
      }

      BuildingInstance th = ctx.village().getTownhall();
      return th != null ? th.getSleepingPos() : null;
   }

   static class IdleTask extends WalkAndWaitTask {
      private static final int MAX_TICKS = 200;

      IdleTask(@Nullable BlockPos home) {
         super(home, 200);
      }

      @Override
      public ResourceLocation goalId() {
         return IdleGoal.ID;
      }

      @Override
      protected boolean allowRandomMoves() {
         return true;
      }

      @Override
      public Component getGoalLabel() {
         return TaskLabels.labelForPhase(this.arrived, "rest");
      }
   }
}
