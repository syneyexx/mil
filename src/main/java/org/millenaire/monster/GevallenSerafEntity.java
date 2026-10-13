package org.millenaire.monster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GevallenSerafEntity extends NativeMonster {
   private int diveCooldown;
   private int wingCutCooldown;
   private int fallenLightWarn;
   private boolean diving;
   private int diveTicks;

   public GevallenSerafEntity(EntityType<? extends GevallenSerafEntity> type, Level level) {
      super(type, level);
   }

   @Override
   public String assetId() {
      return "gevallen_seraf";
   }

   public static AttributeSupplier.Builder createAttributes() {
      return baseAttributes(
         MonsterConstants.GevallenSeraf.HEALTH,
         MonsterConstants.GevallenSeraf.DAMAGE,
         MonsterConstants.GevallenSeraf.ARMOR,
         MonsterConstants.GevallenSeraf.SPEED
      );
   }

   @Override
   protected int getBaseExperienceReward() {
      return MonsterConstants.GevallenSeraf.XP;
   }

   @Override
   protected void customServerAiStep() {
      super.customServerAiStep();
      if (!(level() instanceof ServerLevel server)) {
         return;
      }
      LivingEntity target = getTarget();
      float healthFrac = getHealth() / getMaxHealth();
      double cooldownFactor = healthFrac <= MonsterConstants.GevallenSeraf.PERMA_FLY_HEALTH_FRACTION ? 0.7 : 1.0;

      if (healthFrac <= MonsterConstants.GevallenSeraf.PERMA_FLY_HEALTH_FRACTION) {
         setNoGravity(true);
         if (target != null) {
            getMoveControl().setWantedPosition(target.getX(), target.getY() + 3, target.getZ(), 1.3);
         }
      } else {
         setNoGravity(false);
      }

      if (diveCooldown > 0) {
         diveCooldown--;
      }
      if (wingCutCooldown > 0) {
         wingCutCooldown--;
      }

      if (diving) {
         diveTicks++;
         if (diveTicks == 1) {
            setDeltaMovement(0, 0.9, 0);
         } else if (diveTicks == 12 && target != null) {
            Vec3 dir = target.position().subtract(position()).normalize().scale(1.8).add(0, -0.4, 0);
            setDeltaMovement(dir);
         } else if (diveTicks >= 28) {
            shockwave(server, MonsterConstants.GevallenSeraf.DIVE_DAMAGE);
            diving = false;
            diveTicks = 0;
            diveCooldown = (int) (MonsterConstants.GevallenSeraf.DIVE_COOLDOWN * cooldownFactor);
         }
         return;
      }

      if (target == null || !target.isAlive()) {
         fallenLightWarn = 0;
         return;
      }

      if (diveCooldown <= 0 && distanceTo(target) < 16) {
         diving = true;
         diveTicks = 0;
         triggerAttackAnimation();
         return;
      }

      if (wingCutCooldown <= 0) {
         wingCuts(server, target);
         wingCutCooldown = (int) (MonsterConstants.GevallenSeraf.WING_CUT_COOLDOWN * cooldownFactor);
         triggerAttackAnimation();
      }

      if (healthFrac <= MonsterConstants.GevallenSeraf.FALLEN_LIGHT_HEALTH_FRACTION) {
         if (fallenLightWarn == 0) {
            fallenLightWarn = MonsterConstants.GevallenSeraf.FALLEN_LIGHT_WARN_TICKS;
         } else {
            fallenLightWarn--;
            if (fallenLightWarn <= 0) {
               shockwave(server, MonsterConstants.GevallenSeraf.FALLEN_LIGHT_DAMAGE);
               fallenLightWarn = 80;
               triggerAttackAnimation();
            }
         }
      }
   }

   private void wingCuts(ServerLevel server, LivingEntity target) {
      Vec3 base = getLookAngle();
      for (int i = 0; i < MonsterConstants.GevallenSeraf.WING_CUT_WAVES; i++) {
         double angle = (i - 1.5) * 0.35;
         Vec3 dir = base.yRot((float) angle).normalize().scale(1.2);
         Snowball proj = new Snowball(server, this);
         proj.setPos(getX(), getEyeY(), getZ());
         proj.setDeltaMovement(dir);
         server.addFreshEntity(proj);
         if (distanceTo(target) < 10 && hasLineOfSight(target)) {
            target.hurt(damageSources().mobAttack(this), MonsterConstants.GevallenSeraf.WING_CUT_DAMAGE);
         }
      }
   }

   private void shockwave(ServerLevel server, float damage) {
      AABB box = getBoundingBox().inflate(5);
      for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive())) {
         e.hurt(damageSources().mobAttack(this), damage);
         Vec3 push = e.position().subtract(position()).normalize().scale(1.2).add(0, 0.35, 0);
         e.push(push.x, push.y, push.z);
      }
   }
}
