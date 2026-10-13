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
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class ScharlakenKoninginEntity extends NativeMonster {
   private static final ResourceLocation EXTRA_ARMOR_ID = ResourceLocation.fromNamespaceAndPath("millenaire", "scharlaken_extra_armor");

   private int stingCooldown;
   private int mistCooldown;
   private int pincerTicks;
   private LivingEntity pincerTarget;
   private boolean spidersSpawned;
   private boolean extraArmorApplied;

   public ScharlakenKoninginEntity(EntityType<? extends ScharlakenKoninginEntity> type, Level level) {
      super(type, level);
   }

   @Override
   public String assetId() {
      return "scharlaken_koningin";
   }

   public static AttributeSupplier.Builder createAttributes() {
      return baseAttributes(
         MonsterConstants.ScharlakenKoningin.HEALTH,
         MonsterConstants.ScharlakenKoningin.DAMAGE,
         MonsterConstants.ScharlakenKoningin.ARMOR,
         MonsterConstants.ScharlakenKoningin.SPEED
      );
   }

   @Override
   protected int getBaseExperienceReward() {
      return MonsterConstants.ScharlakenKoningin.XP;
   }

   @Override
   protected void customServerAiStep() {
      super.customServerAiStep();
      if (!(level() instanceof ServerLevel server)) {
         return;
      }
      LivingEntity target = getTarget();
      float healthFrac = getHealth() / getMaxHealth();

      if (stingCooldown > 0) {
         stingCooldown--;
      }
      if (mistCooldown > 0) {
         mistCooldown--;
      }

      if (pincerTicks > 0 && pincerTarget != null && pincerTarget.isAlive()) {
         pincerTicks--;
         if (tickCount % 20 == 0) {
            pincerTarget.hurt(damageSources().mobAttack(this), MonsterConstants.ScharlakenKoningin.PINCER_DPS);
         }
         pincerTarget.setDeltaMovement(position().subtract(pincerTarget.position()).normalize().scale(0.15));
         if (pincerTicks <= 0) {
            pincerTarget = null;
         }
      }

      if (!extraArmorApplied && healthFrac <= MonsterConstants.ScharlakenKoningin.EXTRA_ARMOR_HEALTH_FRACTION) {
         extraArmorApplied = true;
         AttributeInstance armor = getAttribute(Attributes.ARMOR);
         if (armor != null) {
            armor.addTransientModifier(
               new AttributeModifier(EXTRA_ARMOR_ID, MonsterConstants.ScharlakenKoningin.EXTRA_ARMOR, AttributeModifier.Operation.ADD_VALUE)
            );
         }
      }

      if (!spidersSpawned && healthFrac <= MonsterConstants.ScharlakenKoningin.SPIDER_HEALTH_FRACTION) {
         spidersSpawned = true;
         for (int i = 0; i < 3; i++) {
            CaveSpider spider = EntityType.CAVE_SPIDER.create(server);
            if (spider != null) {
               spider.moveTo(getX() + (i - 1), getY(), getZ() + (i % 2), getYRot(), 0);
               server.addFreshEntity(spider);
            }
         }
      }

      if (target == null || !target.isAlive()) {
         return;
      }

      if (stingCooldown <= 0 && distanceTo(target) < 8) {
         target.hurt(damageSources().mobAttack(this), MonsterConstants.ScharlakenKoningin.STING_DAMAGE);
         target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
         stingCooldown = 40;
         if (pincerTicks <= 0 && random.nextBoolean()) {
            pincerTarget = target;
            pincerTicks = MonsterConstants.ScharlakenKoningin.PINCER_HOLD_TICKS;
         }
         triggerAttackAnimation();
      }

      if (mistCooldown <= 0) {
         AABB box = getBoundingBox().inflate(10);
         for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive())) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
            e.addEffect(new MobEffectInstance(MobEffects.HUNGER, 120, 1));
         }
         mistCooldown = MonsterConstants.ScharlakenKoningin.MIST_COOLDOWN;
         triggerAttackAnimation();
      }
   }

   @Override
   public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
      boolean hit = super.hurt(source, amount);
      if (hit && pincerTicks > 0 && amount >= 8) {
         pincerTicks = 0;
         pincerTarget = null;
      }
      return hit;
   }
}
