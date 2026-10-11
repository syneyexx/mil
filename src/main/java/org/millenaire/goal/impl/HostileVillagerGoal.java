package org.millenaire.goal.impl;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.millenaire.combat.CombatHelper;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;

public class HostileVillagerGoal implements VillagerGoal {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "hostile_villager");
   static final int PRIORITY = 5000;

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
      VillagerType type = ModCultures.getVillagerType(v.getVillagerTypeId());
      return type != null && type.isHostile() ? findTarget(v) != null : false;
   }

   @Override
   public VillagerTask start(GoalContext context) {
      return new HostileVillagerGoal.HostileVillagerTask();
   }

   @Nullable
   static LivingEntity findTarget(MillVillager v) {
      VillagerType type = ModCultures.getVillagerType(v.getVillagerTypeId());
      double range = type != null && type.isDefensive() ? 20.0 : 80.0;
      return org.millenaire.perf.sense.CombatSenseCache.findClosestHostile(v, range);
   }

   private static boolean isValidOpponent(LivingEntity e) {
      if (!e.isAlive()) {
         return false;
      } else if (e instanceof Player p) {
         return p instanceof ServerPlayer && CombatHelper.isPlayerTargetable(p);
      } else if (!(e instanceof MillVillager mv)) {
         return false;
      } else {
         VillagerType t = ModCultures.getVillagerType(mv.getVillagerTypeId());
         return t != null && !t.isHostile();
      }
   }

   static class HostileVillagerTask extends AbstractCombatTask {
      private static final double WALK_SPEED = 0.7;
      @Nullable
      private LivingEntity target;
      private boolean done;

      @Override
      public ResourceLocation goalId() {
         return HostileVillagerGoal.ID;
      }

      @Override
      public void tick(GoalContext ctx) {
         MillVillager v = ctx.villager();
         this.ctxVillager = v;
         if (this.target == null || !HostileVillagerGoal.isValidOpponent(this.target)) {
            this.target = HostileVillagerGoal.findTarget(v);
            if (this.target == null) {
               this.done = true;
               return;
            }
         }

         v.setAttackTarget(this.target);
         if (v.distanceToSqr(this.target) > 6400.0) {
            this.done = true;
         } else {
            if (CombatGoalSupport.pursueAndAttack(v, this.target, 0.7, 4.0)) {
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
