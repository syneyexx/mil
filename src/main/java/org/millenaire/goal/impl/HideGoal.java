package org.millenaire.goal.impl;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.building.BuildingInstance;
import org.millenaire.combat.raid.SpecialPointFallback;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerNavDriver;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;

public class HideGoal implements VillagerGoal {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "hide");
   static final int PRIORITY = 9999;

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
      return 9999;
   }

   @Override
   public boolean canBeDoneAtNight() {
      return true;
   }

   @Override
   public boolean isCombatUrgent() {
      return true;
   }

   @Override
   public boolean canStart(GoalContext context) {
      MillVillager v = context.villager();
      if (v.isRaiderEntity()) {
         return false;
      }

      VillagerType type = ModCultures.getVillagerType(v.getVillagerTypeId());
      return type != null && !type.isHelpInAttacks() && !type.isRaider() ? context.village() != null && context.village().isUnderAttack() : false;
   }

   @Override
   public VillagerTask start(GoalContext context) {
      return new HideGoal.HideTask();
   }

   static class HideTask implements VillagerTask {
      private static final double WALK_SPEED = 0.65;
      @Nullable
      private BlockPos shelterPos;
      private boolean finished;

      @Override
      public ResourceLocation goalId() {
         return HideGoal.ID;
      }

      @Override
      public void tick(GoalContext ctx) {
         MillVillager v = ctx.villager();
         if (ctx.village() != null && ctx.village().isUnderAttack()) {
            if (this.shelterPos == null) {
               BuildingInstance th = ctx.village().getTownhall();
               if (th != null) {
                  this.shelterPos = SpecialPointFallback.resolveOrFallback(th.getFirstPointPos("shelterPos"), th.getOrigin());
               }

               if (this.shelterPos == null) {
                  this.shelterPos = ctx.village().getCenter();
               }
            }

            double distSq = v.blockPosition().distSqr(this.shelterPos);
            if (distSq > 9.0) {
               VillagerNavDriver nav = v.getNavManager();
               if (nav.getDestination() == null) {
                  nav.navigateTo(v, this.shelterPos, 0.65);
               }
            } else {
               v.getNavManager().stop(v);
            }
         } else {
            this.finished = true;
            v.getNavManager().stop(v);
         }
      }

      @Override
      public boolean isFinished() {
         return this.finished;
      }

      @Override
      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }
      }
   }
}
