package org.millenaire.monster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DrievoudigeVorstEntity extends NativeMonster {
   private int abilityCooldown;
   private int nextHead;

   public DrievoudigeVorstEntity(EntityType<? extends DrievoudigeVorstEntity> type, Level level) {
      super(type, level);
   }

   @Override
   public String assetId() {
      return "drievoudige_vorst";
   }

   public static AttributeSupplier.Builder createAttributes() {
      return baseAttributes(
         MonsterConstants.DrievoudigeVorst.HEALTH,
         MonsterConstants.DrievoudigeVorst.DAMAGE,
         MonsterConstants.DrievoudigeVorst.ARMOR,
         MonsterConstants.DrievoudigeVorst.SPEED
      );
   }

   @Override
   protected int getBaseExperienceReward() {
      return MonsterConstants.DrievoudigeVorst.XP;
   }

   @Override
   protected void customServerAiStep() {
      super.customServerAiStep();
      if (!(level() instanceof ServerLevel server) || getTarget() == null || !getTarget().isAlive()) {
         return;
      }
      if (abilityCooldown > 0) {
         abilityCooldown--;
         return;
      }
      LivingEntity target = getTarget();
      float healthFrac = getHealth() / getMaxHealth();
      boolean dual = healthFrac <= MonsterConstants.DrievoudigeVorst.DUAL_HEAD_HEALTH_FRACTION;
      useHead(server, target, nextHead % 3);
      if (dual) {
         useHead(server, target, (nextHead + 1) % 3);
      }
      nextHead++;
      abilityCooldown = MonsterConstants.DrievoudigeVorst.HEAD_ABILITY_COOLDOWN;
      triggerAttackAnimation();
   }

   private void useHead(ServerLevel server, LivingEntity target, int head) {
      switch (head) {
         case 0 -> royalCommand(server);
         case 1 -> catLeap(target);
         default -> tongueWhip(target);
      }
   }

   private void royalCommand(ServerLevel server) {
      AABB box = getBoundingBox().inflate(MonsterConstants.DrievoudigeVorst.ROYAL_COMMAND_RADIUS);
      for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive())) {
         e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
         e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
      }
   }

   private void catLeap(LivingEntity target) {
      Vec3 dir = target.position().subtract(position()).normalize().scale(1.6).add(0, 0.55, 0);
      setDeltaMovement(dir);
      hurtMarked = true;
      if (distanceTo(target) < 6) {
         target.hurt(damageSources().mobAttack(this), MonsterConstants.DrievoudigeVorst.CAT_LEAP_DAMAGE);
      }
   }

   private void tongueWhip(LivingEntity target) {
      Vec3 pull = position().subtract(target.position()).normalize().scale(1.2);
      target.push(pull.x, 0.2, pull.z);
      target.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.6F);
      target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
   }
}
