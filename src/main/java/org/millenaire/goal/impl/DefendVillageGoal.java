package org.millenaire.goal.impl;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.combat.raid.RaidManager;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;

public class DefendVillageGoal implements VillagerGoal {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "defend_village");
   static final int PRIORITY = 9999;

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
      return type != null && type.isHelpInAttacks() ? context.village() != null && context.village().isUnderAttack() : false;
   }

   @Override
   public VillagerTask start(GoalContext context) {
      return new DefendVillageGoal.DefendVillageTask();
   }

   static class DefendVillageTask extends AbstractCombatTask {
      private static final double WALK_SPEED = 0.7;
      @Nullable
      private BlockPos defendingPos;
      private boolean finished;
      private static final double ACQUIRE_RADIUS = 5.0;
      private static final double FALLBACK_ARRIVE_SQ = 9.0;

      @Override
      public ResourceLocation goalId() {
         return DefendVillageGoal.ID;
      }

      @Override
      public void tick(GoalContext ctx) {
         MillVillager v = ctx.villager();
         this.ctxVillager = v;
         if (!this.raidStillValid(ctx)) {
            this.finished = true;
            v.getNavManager().stop(v);
         } else {
            if (this.defendingPos == null) {
               this.defendingPos = RaidManager.resolveDefendingPos(ctx.village());
            }

            if (CombatGoalSupport.tickCombat(v, this.defendingPos, 5.0, 0.7, 9.0, MillVillager::isRaiderEntity)) {
               this.reportProgress();
            }
         }
      }

      @Override
      public boolean isFinished() {
         return this.finished;
      }
   }
}
