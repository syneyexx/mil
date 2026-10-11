package org.millenaire.goal.impl;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerNavDriver;

final class CombatGoalSupport {
   private CombatGoalSupport() {
   }

   static boolean shouldHoldAndFire(MillVillager v, double distSq) {
      VillagerType vt = ModCultures.getVillagerType(v.getVillagerTypeId());
      if (vt == null || !vt.isArcher()) {
         return false;
      }

      boolean hasBow = v.getMainHandItem().getItem() instanceof BowItem;
      boolean hasGun = org.millenaire.compat.tacz.TaczWeaponCompat.isGun(v.getMainHandItem());
      if (!hasBow && !hasGun) {
         return false;
      }

      double d = Math.sqrt(distSq);
      double max = hasGun ? 32.0 : 20.0;
      return d > 5.0 && d < max;
   }

   static boolean tickCombat(
      MillVillager v, BlockPos defendingPos, double acquireRadius, double walkSpeed, double fallbackArriveSq, Predicate<MillVillager> isOpponent
   ) {
      MillVillager target = validateOrAcquireTarget(v, defendingPos, acquireRadius, isOpponent);
      if (target != null) {
         v.setAttackTarget(target);
         return pursueAndAttack(v, target, walkSpeed, 4.0);
      }

      if (v.blockPosition().distSqr(defendingPos) > fallbackArriveSq) {
         v.getNavManager().navigateTo(v, defendingPos, walkSpeed);
      } else {
         v.getNavManager().stop(v);
      }

      return false;
   }

   static boolean pursueAndAttack(MillVillager v, LivingEntity target, double walkSpeed, double attackRangeSq) {
      v.getLookControl().setLookAt(target, 30.0F, 30.0F);
      double distSq = v.distanceToSqr(target);
      v.ensureCombatWeaponEquipped();
      if (shouldHoldAndFire(v, distSq)) {
         v.getNavManager().stop(v);
         return v.performAttack(target);
      }

      if (!(distSq > attackRangeSq)) {
         v.getNavManager().stop(v);
         return v.performAttack(target);
      }

      VillagerNavDriver nav = v.getNavManager();
      BlockPos to = target.blockPosition();
      if (nav.getDestination() == null || nav.getDestination().distSqr(to) > 4.0 || !nav.isDestinationCombatTarget()) {
         nav.navigateToCombatTarget(v, to, walkSpeed);
      }

      return false;
   }

   @Nullable
   private static MillVillager validateOrAcquireTarget(MillVillager v, BlockPos defendingPos, double acquireRadius, Predicate<MillVillager> isOpponent) {
      LivingEntity current = v.getAttackTarget();
      if (current instanceof MillVillager mv && mv.isAlive() && isOpponent.test(mv)) {
         VillagerType vt = ModCultures.getVillagerType(v.getVillagerTypeId());
         boolean defensiveLeashBroken = vt != null && vt.isDefensive() && Math.sqrt(v.blockPosition().distSqr(defendingPos)) > 20.0;
         boolean tooFar = Math.sqrt(v.distanceToSqr(mv)) > 80.0;
         if (!defensiveLeashBroken && !tooFar) {
            return mv;
         }
      }

      if (current != null) {
         v.setAttackTarget(null);
      }

      return findClosestOpponent(v, acquireRadius, isOpponent);
   }

   @Nullable
   private static MillVillager findClosestOpponent(MillVillager v, double radius, Predicate<MillVillager> isOpponent) {
      return org.millenaire.perf.sense.CombatSenseCache.findClosestVillager(v, radius, isOpponent);
   }
}
