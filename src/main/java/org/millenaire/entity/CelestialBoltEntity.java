package org.millenaire.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.millenaire.config.MillenaireServerConfig;

/**
 * Visible celestial projectile — limited tracking, no terrain destruction.
 */
public class CelestialBoltEntity extends AbstractHurtingProjectile {
   private static final int MAX_LIFE_TICKS = 90;
   private int lifeTicks;
   private int trackTicks;

   public CelestialBoltEntity(EntityType<? extends CelestialBoltEntity> type, Level level) {
      super(type, level);
   }

   public CelestialBoltEntity(Level level, LivingEntity owner, Vec3 movement) {
      super(ModEntities.CELESTIAL_BOLT.get(), owner, movement, level);
   }

   @Override
   public void tick() {
      super.tick();
      this.lifeTicks++;
      if (!this.level().isClientSide && this.lifeTicks > MAX_LIFE_TICKS) {
         this.discard();
         return;
      }
      if (!this.level().isClientSide && this.trackTicks < 25) {
         this.trackTicks++;
         Entity owner = this.getOwner();
         if (owner instanceof HemelwachterEntity hemel) {
            LivingEntity target = hemel.getTarget();
            if (target != null && target.isAlive()) {
               Vec3 desire = target.getEyePosition().subtract(this.position()).normalize().scale(0.08);
               this.setDeltaMovement(this.getDeltaMovement().add(desire).normalize().scale(0.55));
            }
         }
      }
      if (this.level().isClientSide && this.random.nextInt(2) == 0 && CombatFxBudget.allowTrailParticle(this)) {
         this.level()
            .addParticle(
               ParticleTypes.END_ROD,
               this.getX(),
               this.getY(),
               this.getZ(),
               0.0,
               0.0,
               0.0
            );
      }
   }

   @Override
   protected void onHitEntity(EntityHitResult result) {
      super.onHitEntity(result);
      if (this.level().isClientSide) {
         return;
      }
      Entity target = result.getEntity();
      Entity owner = this.getOwner();
      if (owner != null && (target == owner || target.isAlliedTo(owner))) {
         return;
      }
      float damage = 8.0F;
      try {
         damage = MillenaireServerConfig.SERVER.worldBossProjectileDamage.get().floatValue();
      } catch (IllegalStateException ignored) {
      }
      target.hurt(this.damageSources().mobProjectile(this, owner instanceof LivingEntity living ? living : null), damage);
   }

   @Override
   protected void onHit(HitResult result) {
      super.onHit(result);
      if (!this.level().isClientSide) {
         this.playSound(SoundEvents.AMETHYST_BLOCK_HIT, 0.7F, 1.4F);
         this.discard();
      }
   }

   @Override
   protected ParticleOptions getTrailParticle() {
      return ParticleTypes.END_ROD;
   }

   @Override
   protected boolean shouldBurn() {
      return false;
   }

   @Override
   public boolean isOnFire() {
      return false;
   }
}
