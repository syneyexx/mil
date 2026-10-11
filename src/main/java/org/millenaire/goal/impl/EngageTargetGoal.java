package org.millenaire.goal.impl;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import org.millenaire.building.BuildingInstance;
import org.millenaire.combat.CombatHelper;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.village.Village;

public class EngageTargetGoal implements VillagerGoal {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "engage_target");
   static final int PRIORITY = 5000;
   static final int HUNT_PURSUIT_RADIUS = 50;

   @Override
   public ResourceLocation id() {
      return ID;
   }

   @Override
   public int computePriority(GoalContext context) {
      return 5000;
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

      LivingEntity t = resolveTarget(v);
      return t == null ? false : !isMonsterOutsideHuntZone(context, t);
   }

   static boolean isMonsterOutsideHuntZone(GoalContext context, LivingEntity target) {
      if (!(target instanceof Monster)) {
         return false;
      }

      Village village = context.village();
      if (village == null) {
         return false;
      }

      BuildingInstance townhall = village.getTownhall();
      if (townhall == null) {
         return false;
      }

      BlockPos c = townhall.getOrigin();
      return outOfHuntZone(target.getBlockX() - c.getX(), target.getBlockZ() - c.getZ());
   }

   static boolean outOfHuntZone(int dx, int dz) {
      return Math.abs(dx) > 50 || Math.abs(dz) > 50;
   }

   @Override
   public VillagerTask start(GoalContext context) {
      return new EngageTargetGoal.EngageTargetTask();
   }

   @Nullable
   static LivingEntity resolveTarget(MillVillager v) {
      LivingEntity t = v.getAttackTarget();
      if (t == null || !t.isAlive()) {
         return null;
      } else if (t instanceof ServerPlayer sp) {
         return CombatHelper.isPlayerTargetable(sp) ? sp : null;
      } else {
         return !(t instanceof Monster) && !(t instanceof MillVillager) ? null : t;
      }
   }

   static class EngageTargetTask extends AbstractCombatTask {
      private static final double WALK_SPEED = 0.7;
      private boolean done;

      @Override
      public ResourceLocation goalId() {
         return EngageTargetGoal.ID;
      }

      @Override
      public void tick(GoalContext ctx) {
         MillVillager v = ctx.villager();
         this.ctxVillager = v;
         LivingEntity target = EngageTargetGoal.resolveTarget(v);
         if (target == null) {
            this.done = true;
         } else if (EngageTargetGoal.isMonsterOutsideHuntZone(ctx, target)) {
            v.setAttackTarget(null);
            this.done = true;
         } else if (v.distanceToSqr(target) > 6400.0) {
            this.done = true;
         } else {
            if (CombatGoalSupport.pursueAndAttack(v, target, 0.7, 4.0)) {
               this.reportProgress();
            }
         }
      }

      @Override
      public boolean isFinished() {
         return this.done;
      }
   }
}
