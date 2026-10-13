package org.millenaire.monster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;

public class DrakenkoningEntity extends NativeMonster {
   private static final ResourceLocation ENRAGE_SPEED_ID = ResourceLocation.fromNamespaceAndPath("millenaire", "drakenkoning_enrage");

   private int fireBreathCooldown;
   private int wingSlamCooldown;
   private int salvoCooldown;
   private boolean enraged;

   public DrakenkoningEntity(EntityType<? extends DrakenkoningEntity> type, Level level) {
      super(type, level);
   }

   @Override
   public String assetId() {
      return "drakenkoning";
   }

   public static AttributeSupplier.Builder createAttributes() {
      return baseAttributes(
         MonsterConstants.Drakenkoning.HEALTH,
         MonsterConstants.Drakenkoning.DAMAGE,
         MonsterConstants.Drakenkoning.ARMOR,
         MonsterConstants.Drakenkoning.SPEED
      );
   }

   @Override
   protected int getBaseExperienceReward() {
      return MonsterConstants.Drakenkoning.XP;
   }

   @Override
   protected void customServerAiStep() {
      super.customServerAiStep();
      if (!(level() instanceof ServerLevel server)) {
         return;
      }
      LivingEntity target = getTarget();
      if (target == null || !target.isAlive()) {
         return;
      }

      float healthFrac = getHealth() / getMaxHealth();
      updateEnrage(healthFrac);

      double cooldownFactor = enraged ? MonsterConstants.Drakenkoning.ENRAGE_COOLDOWN_FACTOR : 1.0;
      if (fireBreathCooldown > 0) {
         fireBreathCooldown--;
      }
      if (wingSlamCooldown > 0) {
         wingSlamCooldown--;
      }
      if (salvoCooldown > 0) {
         salvoCooldown--;
      }

      if (fireBreathCooldown <= 0 && distanceTo(target) <= MonsterConstants.Drakenkoning.FIRE_BREATH_RANGE + 2) {
         breathFire(target);
         fireBreathCooldown = (int) (MonsterConstants.Drakenkoning.FIRE_BREATH_COOLDOWN * cooldownFactor);
         triggerAttackAnimation();
      }
      if (wingSlamCooldown <= 0 && distanceTo(target) <= MonsterConstants.Drakenkoning.WING_SLAM_RADIUS) {
         wingSlam(server);
         wingSlamCooldown = (int) (MonsterConstants.Drakenkoning.WING_SLAM_COOLDOWN * cooldownFactor);
         triggerAttackAnimation();
      }
      if (healthFrac <= MonsterConstants.Drakenkoning.SALVO_HEALTH_FRACTION && salvoCooldown <= 0) {
         fireSalvo(server, target);
         salvoCooldown = (int) (160 * cooldownFactor);
         triggerAttackAnimation();
      }
   }

   private void updateEnrage(float healthFrac) {
      boolean should = healthFrac <= MonsterConstants.Drakenkoning.ENRAGE_HEALTH_FRACTION;
      if (should == enraged) {
         return;
      }
      enraged = should;
      AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed == null) {
         return;
      }
      speed.removeModifier(ENRAGE_SPEED_ID);
      if (enraged) {
         speed.addTransientModifier(
            new AttributeModifier(ENRAGE_SPEED_ID, MonsterConstants.Drakenkoning.ENRAGE_SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
         );
      }
   }

   private void breathFire(LivingEntity target) {
      Vec3 look = getLookAngle();
      AABB cone = getBoundingBox().expandTowards(look.scale(MonsterConstants.Drakenkoning.FIRE_BREATH_RANGE)).inflate(1.5);
      for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, cone, e -> e != this && e.isAlive())) {
         Vec3 to = e.position().subtract(position()).normalize();
         if (look.dot(to) > 0.55) {
            e.igniteForSeconds(MonsterConstants.Drakenkoning.FIRE_BREATH_BURN_SECONDS);
            e.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.35F);
         }
      }
   }

   private void wingSlam(ServerLevel server) {
      AABB box = getBoundingBox().inflate(MonsterConstants.Drakenkoning.WING_SLAM_RADIUS);
      for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive())) {
         e.hurt(damageSources().mobAttack(this), MonsterConstants.Drakenkoning.WING_SLAM_DAMAGE);
         Vec3 push = e.position().subtract(position()).normalize().scale(1.8).add(0, 0.45, 0);
         e.push(push.x, push.y, push.z);
      }
   }

   private void fireSalvo(ServerLevel server, LivingEntity target) {
      Vec3 from = getEyePosition();
      for (int i = 0; i < 5; i++) {
         Vec3 to = target.getEyePosition().add((random.nextDouble() - 0.5) * 2.5, random.nextDouble(), (random.nextDouble() - 0.5) * 2.5);
         Vec3 dir = to.subtract(from).normalize();
         SmallFireball ball = new SmallFireball(server, this, dir.scale(0.15));
         ball.setPos(from.x, from.y, from.z);
         server.addFreshEntity(ball);
      }
   }
}
