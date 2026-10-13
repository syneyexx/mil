package org.millenaire.goal.impl;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.crime.CrimeService;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;

public class InvestigateChestGoal implements VillagerGoal {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "investigate_chest");

   @Override
   public ResourceLocation id() {
      return ID;
   }

   @Override
   public int computePriority(GoalContext context) {
      return 0;
   }

   @Override
   public boolean canStart(GoalContext context) {
      return false;
   }

   @Override
   public VillagerTask start(GoalContext context) {
      return new InvestigateChestGoal.InvestigateChestTask(BlockPos.ZERO, null);
   }

   @Override
   public boolean showInTravelBook() {
      return false;
   }

   @Override
   public boolean canBeDoneAtNight() {
      return true;
   }

   public static class InvestigateChestTask extends WalkThenActTask {
      private final BlockPos chestPos;
      @Nullable
      private final UUID thiefId;
      private boolean caught;

      public InvestigateChestTask(BlockPos chestPos, @Nullable UUID thiefId) {
         this.chestPos = chestPos;
         this.thiefId = thiefId;
      }

      @Override
      public ResourceLocation goalId() {
         return InvestigateChestGoal.ID;
      }

      @Override
      protected BlockPos resolveTarget(GoalContext ctx) {
         return this.chestPos;
      }

      @Override
      protected int actionDuration() {
         return 60;
      }

      @Override
      protected void performAction(GoalContext ctx) {
         if (!this.caught && this.thiefId != null) {
            this.caught = true;
            CrimeService.onInspectorArrived(ctx.villager(), this.chestPos, this.thiefId);
         }
      }
   }
}
