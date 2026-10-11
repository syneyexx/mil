package org.millenaire.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Antichrist Priest ranged hex bolt — crimson magical projectile, no terrain damage.
 */
public class InfernalHexBoltEntity extends AbstractHurtingProjectile {
   public static final float DIRECT_DAMAGE = 8.0F;
   private static final int WEAKNESS_TICKS = 40;
   private static final int MAX_LIFE_TICKS = 70;

   private int lifeTicks;

   public InfernalHexBoltEntity(EntityType<? extends InfernalHexBoltEntity> type, Level level) {
      super(type, level);
   }

   public InfernalHexBoltEntity(Level level, LivingEntity owner, Vec3 movement) {
      super(ModEntities.INFERNAL_HEX_BOLT.get(), owner, movement, level);
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
               ParticleTypes.SOUL_FIRE_FLAME,
               this.getX() + (this.random.nextDouble() - 0.5) * 0.15,
               this.getY(),
               this.getZ() + (this.random.nextDouble() - 0.5) * 0.15,
               0.0,
               0.0,
               0.0
            );
         if (this.random.nextInt(2) == 0) {
            this.level().addParticle(ParticleTypes.CRIMSON_SPORE, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
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
      if (owner != null && (target == owner || InfernalFaction.areAllies(owner, target))) {
         return;
      }
      LivingEntity livingOwner = owner instanceof LivingEntity living ? living : null;
      boolean hurt = target.hurt(this.damageSources().mobProjectile(this, livingOwner), DIRECT_DAMAGE);
      if (hurt && target instanceof Player player) {
         player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS, 0, false, true, true));
      }
   }

   @Override
   protected void onHit(HitResult result) {
      super.onHit(result);
      if (!this.level().isClientSide) {
         this.playSound(SoundEvents.BLAZE_SHOOT, 0.55F, 0.55F + this.random.nextFloat() * 0.2F);
         this.discard();
      }
   }

   @Override
   protected ParticleOptions getTrailParticle() {
      return ParticleTypes.SOUL_FIRE_FLAME;
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
