package org.millenaire.compat;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;

/**
 * Lightweight ranged combat for vanilla villagers holding a TacZ gun.
 * Only registered when TacZ is present — keeps Millénaire tick cost near zero otherwise.
 */
public final class VanillaTacZGunGoal extends Goal {
   private final AbstractVillager villager;
   private int cooldown;

   public VanillaTacZGunGoal(AbstractVillager villager) {
      this.villager = villager;
      this.setFlags(EnumSet.of(Flag.LOOK, Flag.MOVE));
   }

   public static void tryAttach(AbstractVillager villager) {
      if (!TacZCompat.isUsable() || villager.level().isClientSide() || !(villager instanceof PathfinderMob mob)) {
         return;
      }
      mob.goalSelector.addGoal(2, new VanillaTacZGunGoal(villager));
      mob.targetSelector.addGoal(1, new HurtByTargetGoal(mob));
      mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, Monster.class, true));
   }

   @Override
   public boolean canUse() {
      if (!TacZCompat.holdingGun(this.villager) || !(this.villager instanceof PathfinderMob mob)) {
         return false;
      }
      LivingEntity target = mob.getTarget();
      return target != null && target.isAlive();
   }

   @Override
   public boolean canContinueToUse() {
      return this.canUse();
   }

   @Override
   public void stop() {
      TacZCompat.stopAiming(this.villager);
   }

   @Override
   public void tick() {
      if (!(this.villager instanceof PathfinderMob mob)) {
         return;
      }
      LivingEntity target = mob.getTarget();
      if (target == null) {
         return;
      }
      this.villager.getLookControl().setLookAt(target, 30.0F, 30.0F);
      double dist = this.villager.distanceToSqr(target);
      if (dist > 4.0 && dist < 2304.0) {
         mob.getNavigation().stop();
         if (this.cooldown > 0) {
            this.cooldown--;
            return;
         }
         if (TacZCompat.tryShootAt(this.villager, target)) {
            this.cooldown = 8;
         } else {
            this.cooldown = 5;
         }
      } else if (dist >= 2304.0) {
         mob.getNavigation().moveTo(target, 0.55);
      }
   }
}
