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

/**
 * Infernal Harrow ranged spit — visible fire projectile with modest damage and short ignition.
 */
public class InfernalFireballEntity extends AbstractHurtingProjectile {
   private static final float DIRECT_DAMAGE = 7.0F;
   private static final int FIRE_SECONDS = 3;
   private static final int MAX_LIFE_TICKS = 80;

   private int lifeTicks;

   public InfernalFireballEntity(EntityType<? extends InfernalFireballEntity> type, Level level) {
      super(type, level);
   }

   public InfernalFireballEntity(Level level, LivingEntity owner, Vec3 movement) {
      super(ModEntities.INFERNAL_FIREBALL.get(), owner, movement, level);
   }

   @Override
   public void tick() {
      super.tick();
      this.lifeTicks++;
      if (!this.level().isClientSide && this.lifeTicks > MAX_LIFE_TICKS) {
         this.discard();
      }
      if (this.level().isClientSide && CombatFxBudget.allowTrailParticle(this)) {
         this.level()
            .addParticle(
               ParticleTypes.FLAME,
               this.getX() + (this.random.nextDouble() - 0.5) * 0.2,
               this.getY(),
               this.getZ() + (this.random.nextDouble() - 0.5) * 0.2,
               0.0,
               0.0,
               0.0
            );
         if (this.random.nextInt(3) == 0) {
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
         }
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
      boolean hurt = target.hurt(this.damageSources().mobProjectile(this, owner instanceof LivingEntity living ? living : null), DIRECT_DAMAGE);
      if (hurt) {
         target.igniteForSeconds(FIRE_SECONDS);
      }
   }

   @Override
   protected void onHit(HitResult result) {
      super.onHit(result);
      if (!this.level().isClientSide) {
         this.playSound(SoundEvents.BLAZE_SHOOT, 0.6F, 1.15F + this.random.nextFloat() * 0.2F);
         this.discard();
      }
   }

   @Override
   protected ParticleOptions getTrailParticle() {
      return ParticleTypes.FLAME;
   }

   @Override
   protected boolean shouldBurn() {
      return false;
   }

   @Override
   public boolean isOnFire() {
      return true;
   }
}
