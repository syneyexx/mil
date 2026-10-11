package org.millenaire.entity;

import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.faction.crime.hunter.BountyEligibilityService;

/**
 * Nonlethal capture bola — slows only the assigned bounty target.
 */
public class BountyHunterBolaEntity extends ThrowableItemProjectile {
   private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(BountyHunterBolaEntity.class, EntityDataSerializers.INT);
   private UUID intendedTargetId;
   private int lifeTicks;

   public BountyHunterBolaEntity(EntityType<? extends BountyHunterBolaEntity> type, Level level) {
      super(type, level);
   }

   public BountyHunterBolaEntity(Level level, LivingEntity owner) {
      super(ModEntities.BOUNTY_HUNTER_BOLA.get(), owner, level);
   }

   public void setIntendedTarget(UUID targetId) {
      this.intendedTargetId = targetId;
   }

   @Override
   protected Item getDefaultItem() {
      return Items.LEAD;
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(DATA_LIFE, 0);
   }

   @Override
   public void tick() {
      super.tick();
      this.lifeTicks++;
      if (!this.level().isClientSide && this.lifeTicks > 80) {
         this.discard();
      }
      if (this.level().isClientSide && this.random.nextBoolean()) {
         this.level().addParticle(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
      }
   }

   @Override
   protected void onHitEntity(EntityHitResult result) {
      super.onHitEntity(result);
      if (this.level().isClientSide) {
         return;
      }
      Entity hit = result.getEntity();
      Entity owner = this.getOwner();
      if (!(owner instanceof BountyHunterEntity hunter)) {
         this.discard();
         return;
      }
      if (!(hit instanceof ServerPlayer player)) {
         this.discard();
         return;
      }
      if (this.intendedTargetId == null || !this.intendedTargetId.equals(player.getUUID())) {
         this.discard();
         return;
      }
      if (!BountyEligibilityService.isEligibleForArrest(hunter.level() instanceof net.minecraft.server.level.ServerLevel sl ? sl : null, player)) {
         this.discard();
         return;
      }
      int slowTicks = slownessTicks();
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowTicks, 1, false, true, true));
      this.playSound(SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
      this.discard();
   }

   @Override
   protected void onHit(HitResult result) {
      super.onHit(result);
      if (!this.level().isClientSide && result.getType() != HitResult.Type.ENTITY) {
         this.playSound(SoundEvents.ARMOR_EQUIP_CHAIN.value(), 0.6F, 1.1F);
         this.discard();
      }
   }

   private static int slownessTicks() {
      try {
         return Math.max(20, MillenaireServerConfig.SERVER.bountyHunterBolaSlownessSeconds.getAsInt() * 20);
      } catch (IllegalStateException e) {
         return 60;
      }
   }
}
