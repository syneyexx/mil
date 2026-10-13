package org.millenaire.monster;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ZesarmigeRazernijEntity extends NativeMonster {
   private static final ResourceLocation RAGE_SPEED_ID = ResourceLocation.fromNamespaceAndPath("millenaire", "razernij_rage_speed");
   private static final ResourceLocation RAGE_DAMAGE_ID = ResourceLocation.fromNamespaceAndPath("millenaire", "razernij_rage_damage");

   private int sixStrikeCooldown;
   private int roarCooldown;
   private int bounceCooldown;
   private int rageStacks;
   private float lastHealthFrac = 1.0F;
   private int sixStrikeHitsLeft;

   public ZesarmigeRazernijEntity(EntityType<? extends ZesarmigeRazernijEntity> type, Level level) {
      super(type, level);
   }

   @Override
   public String assetId() {
      return "zesarmige_razernij";
   }

   public static AttributeSupplier.Builder createAttributes() {
      return baseAttributes(
         MonsterConstants.ZesarmigeRazernij.HEALTH,
         MonsterConstants.ZesarmigeRazernij.DAMAGE,
         MonsterConstants.ZesarmigeRazernij.ARMOR,
         MonsterConstants.ZesarmigeRazernij.SPEED
      );
   }

   @Override
   protected int getBaseExperienceReward() {
      return MonsterConstants.ZesarmigeRazernij.XP;
   }

   @Override
   protected void customServerAiStep() {
      super.customServerAiStep();
      if (!(level() instanceof ServerLevel server)) {
         return;
      }

      float healthFrac = getHealth() / getMaxHealth();
      updateRage(healthFrac);

      if (sixStrikeCooldown > 0) {
         sixStrikeCooldown--;
      }
      if (roarCooldown > 0) {
         roarCooldown--;
      }
      if (bounceCooldown > 0) {
         bounceCooldown--;
      }

      if (sixStrikeHitsLeft > 0 && tickCount % 4 == 0) {
         LivingEntity target = getTarget();
         if (target != null && target.isAlive() && distanceTo(target) < 5) {
            target.hurt(damageSources().mobAttack(this), MonsterConstants.ZesarmigeRazernij.SIX_STRIKE_DAMAGE);
         }
         sixStrikeHitsLeft--;
      }

      LivingEntity target = getTarget();
      if (target == null || !target.isAlive()) {
         return;
      }

      if (sixStrikeCooldown <= 0 && distanceTo(target) < 5) {
         sixStrikeHitsLeft = MonsterConstants.ZesarmigeRazernij.SIX_STRIKE_HITS;
         sixStrikeCooldown = MonsterConstants.ZesarmigeRazernij.SIX_STRIKE_COOLDOWN;
         triggerAttackAnimation();
      }

      if (bounceCooldown <= 0 && distanceTo(target) < 12) {
         Vec3 dir = target.position().subtract(position()).normalize().scale(1.4).add(0, 0.7, 0);
         setDeltaMovement(dir);
         hurtMarked = true;
         AABB box = getBoundingBox().inflate(3);
         for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive())) {
            e.hurt(damageSources().mobAttack(this), MonsterConstants.ZesarmigeRazernij.BOUNCE_DAMAGE);
         }
         bounceCooldown = 140;
         triggerAttackAnimation();
      }

      if (roarCooldown <= 0) {
         AABB box = getBoundingBox().inflate(8);
         for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive())) {
            Vec3 push = e.position().subtract(position()).normalize().scale(1.5).add(0, 0.35, 0);
            e.push(push.x, push.y, push.z);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
         }
         roarCooldown = MonsterConstants.ZesarmigeRazernij.ROAR_COOLDOWN;
         triggerAttackAnimation();
      }
   }

   private void updateRage(float healthFrac) {
      int wanted = Math.min(
         MonsterConstants.ZesarmigeRazernij.RAGE_MAX_STACKS,
         (int) ((1.0F - healthFrac) / MonsterConstants.ZesarmigeRazernij.RAGE_STEP)
      );
      if (wanted == rageStacks) {
         lastHealthFrac = healthFrac;
         return;
      }
      rageStacks = wanted;
      lastHealthFrac = healthFrac;
      AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
      AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
      if (speed != null) {
         speed.removeModifier(RAGE_SPEED_ID);
         if (rageStacks > 0) {
            speed.addTransientModifier(
               new AttributeModifier(
                  RAGE_SPEED_ID,
                  MonsterConstants.ZesarmigeRazernij.RAGE_BONUS * rageStacks,
                  AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
               )
            );
         }
      }
      if (damage != null) {
         damage.removeModifier(RAGE_DAMAGE_ID);
         if (rageStacks > 0) {
            damage.addTransientModifier(
               new AttributeModifier(
                  RAGE_DAMAGE_ID,
                  MonsterConstants.ZesarmigeRazernij.RAGE_BONUS * rageStacks,
                  AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
               )
            );
         }
      }
   }
}
