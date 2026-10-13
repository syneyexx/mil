package org.millenaire.monster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

public class DeAlziendeEntity extends NativeMonster {
   private int laserCooldown;
   private int laserCharge;
   private int featherCooldown;
   private boolean helpersSpawned;

   public DeAlziendeEntity(EntityType<? extends DeAlziendeEntity> type, Level level) {
      super(type, level);
      moveControl = new FlyingMoveControl(this, 20, true);
      setNoGravity(true);
   }

   @Override
   public String assetId() {
      return "de_alziende";
   }

   public static AttributeSupplier.Builder createAttributes() {
      return baseAttributes(
         MonsterConstants.DeAlziende.HEALTH,
         MonsterConstants.DeAlziende.DAMAGE,
         MonsterConstants.DeAlziende.ARMOR,
         MonsterConstants.DeAlziende.SPEED
      ).add(Attributes.FLYING_SPEED, 0.45);
   }

   @Override
   protected int getBaseExperienceReward() {
      return MonsterConstants.DeAlziende.XP;
   }

   @Override
   protected PathNavigation createNavigation(Level level) {
      FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
      nav.setCanOpenDoors(false);
      nav.setCanFloat(true);
      nav.setCanPassDoors(true);
      return nav;
   }

   @Override
   protected void registerGoals() {
      goalSelector.addGoal(1, new HoverAwayGoal(this));
      goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0F));
      goalSelector.addGoal(9, new RandomLookAroundGoal(this));
      targetSelector.addGoal(1, new HurtByTargetGoal(this));
      targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
   }

   @Override
   public void travel(Vec3 travelVector) {
      if (isControlledByLocalInstance()) {
         if (isInWater()) {
            moveRelative(0.02F, travelVector);
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.8));
         } else if (isInLava()) {
            moveRelative(0.02F, travelVector);
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.5));
         } else {
            moveRelative(getSpeed(), travelVector);
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.91));
         }
      }
      calculateEntityAnimation(false);
   }

   @Override
   protected void customServerAiStep() {
      super.customServerAiStep();
      if (!(level() instanceof ServerLevel server)) {
         return;
      }
      LivingEntity target = getTarget();
      if (laserCooldown > 0) {
         laserCooldown--;
      }
      if (featherCooldown > 0) {
         featherCooldown--;
      }

      for (Player player : server.players()) {
         if (distanceTo(player) < 32) {
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
         }
      }

      if (target == null || !target.isAlive()) {
         laserCharge = 0;
         return;
      }

      if (laserCooldown <= 0) {
         if (laserCharge == 0) {
            laserCharge = MonsterConstants.DeAlziende.LASER_CHARGE_TICKS;
            triggerAttackAnimation();
         }
         laserCharge--;
         if (laserCharge <= 0) {
            if (hasLineOfSight(target) && distanceTo(target) < 24) {
               target.hurt(damageSources().mobAttack(this), MonsterConstants.DeAlziende.LASER_DAMAGE_PER_TICK);
            }
            laserCooldown = MonsterConstants.DeAlziende.LASER_COOLDOWN;
         } else if (hasLineOfSight(target) && distanceTo(target) < 24 && tickCount % 5 == 0) {
            target.hurt(damageSources().mobAttack(this), MonsterConstants.DeAlziende.LASER_DAMAGE_PER_TICK * 0.25F);
         }
      }

      if (featherCooldown <= 0) {
         rainFeathers(server, target);
         featherCooldown = MonsterConstants.DeAlziende.FEATHER_RAIN_COOLDOWN;
         triggerAttackAnimation();
      }

      float healthFrac = getHealth() / getMaxHealth();
      if (!helpersSpawned && healthFrac <= MonsterConstants.DeAlziende.HELPER_HEALTH_FRACTION) {
         helpersSpawned = true;
         for (int i = 0; i < 2; i++) {
            Vex vex = EntityType.VEX.create(server);
            if (vex != null) {
               vex.moveTo(getX() + (i - 0.5) * 2, getY() + 1, getZ(), getYRot(), 0);
               server.addFreshEntity(vex);
            }
         }
      }
   }

   private void rainFeathers(ServerLevel server, LivingEntity target) {
      for (int i = 0; i < MonsterConstants.DeAlziende.FEATHER_COUNT; i++) {
         Arrow arrow = new Arrow(server, this, net.minecraft.world.item.ItemStack.EMPTY, null);
         arrow.setPos(getX() + (random.nextDouble() - 0.5) * 4, getY() + 2, getZ() + (random.nextDouble() - 0.5) * 4);
         Vec3 dir = target.position().add(0, 1, 0).subtract(arrow.position()).normalize().scale(1.4);
         arrow.setDeltaMovement(dir);
         arrow.setBaseDamage(MonsterConstants.DeAlziende.FEATHER_DAMAGE);
         server.addFreshEntity(arrow);
      }
   }

   private static class HoverAwayGoal extends Goal {
      private final DeAlziendeEntity mob;

      HoverAwayGoal(DeAlziendeEntity mob) {
         this.mob = mob;
         setFlags(EnumSet.of(Flag.MOVE));
      }

      @Override
      public boolean canUse() {
         return mob.getTarget() != null;
      }

      @Override
      public void tick() {
         LivingEntity target = mob.getTarget();
         if (target == null) {
            return;
         }
         double dist = mob.distanceTo(target);
         Vec3 away = mob.position().subtract(target.position()).normalize();
         double desired = (MonsterConstants.DeAlziende.HOVER_MIN + MonsterConstants.DeAlziende.HOVER_MAX) * 0.5;
         Vec3 dest;
         if (dist < MonsterConstants.DeAlziende.HOVER_MIN) {
            dest = mob.position().add(away.scale(2)).add(0, 0.5, 0);
         } else if (dist > MonsterConstants.DeAlziende.HOVER_MAX) {
            dest = target.position().add(0, 4, 0);
         } else {
            dest = mob.position().add(away.yRot((float) Math.toRadians(8)).scale(0.6)).add(0, Math.sin(mob.tickCount * 0.05) * 0.2, 0);
         }
         mob.getMoveControl().setWantedPosition(dest.x, dest.y, dest.z, 1.0);
         mob.getLookControl().setLookAt(target, 30, 30);
      }
   }
}
