package org.millenaire.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public class NetherWraithEntity extends Monster {
   private static final int ATTACK_ANIM_TICKS = 12;

   private int clientAttackTicks;
   private int clientAttackType;

   public NetherWraithEntity(EntityType<? extends NetherWraithEntity> type, Level level) {
      super(type, level);
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
      this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.9));
      this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
      this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 50.0)
         .add(Attributes.MOVEMENT_SPEED, 0.28)
         .add(Attributes.FOLLOW_RANGE, 35.0)
         .add(Attributes.ATTACK_DAMAGE, 40.0)
         .add(Attributes.ARMOR, 4.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.35)
         .add(Attributes.STEP_HEIGHT, 1.0);
   }

   public static boolean checkNetherWraithSpawnRules(
      EntityType<NetherWraithEntity> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random
   ) {
      return Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random);
   }

   @Override
   public void aiStep() {
      super.aiStep();
      if (this.level().isClientSide) {
         if (this.clientAttackTicks > 0) {
            this.clientAttackTicks--;
         }
         if (this.random.nextInt(8) == 0) {
            this.level()
               .addParticle(
                  ParticleTypes.FLAME,
                  this.getX() + (this.random.nextDouble() - 0.5) * 0.5,
                  this.getY() + 1.8 + this.random.nextDouble() * 0.5,
                  this.getZ() + (this.random.nextDouble() - 0.5) * 0.5,
                  0.0,
                  0.02,
                  0.0
               );
         }
      }
   }

   @Override
   protected SoundEvent getAmbientSound() {
      return SoundEvents.WITHER_SKELETON_AMBIENT;
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return SoundEvents.WITHER_SKELETON_HURT;
   }

   @Override
   protected SoundEvent getDeathSound() {
      return SoundEvents.WITHER_SKELETON_DEATH;
   }

   @Override
   protected void playStepSound(BlockPos pos, BlockState state) {
      this.playSound(SoundEvents.WITHER_SKELETON_STEP, 0.2F, 0.8F);
   }

   @Override
   public boolean doHurtTarget(Entity target) {
      // Alternate claw swipe (4) and bite/lunge (5) for distinct attack presentation
      byte event = this.random.nextBoolean() ? (byte)4 : (byte)5;
      this.level().broadcastEntityEvent(this, event);
      return super.doHurtTarget(target);
   }

   @Override
   public void handleEntityEvent(byte id) {
      if (id == 4 || id == 5) {
         this.clientAttackTicks = ATTACK_ANIM_TICKS;
         this.clientAttackType = id == 5 ? 1 : 0;
      } else {
         super.handleEntityEvent(id);
      }
   }

   /** 0..1 progress through the current melee attack animation (client). */
   public float getMeleeAttackProgress(float partialTick) {
      if (this.clientAttackTicks <= 0) {
         return 0.0F;
      }
      float remaining = this.clientAttackTicks - partialTick;
      return 1.0F - Mth.clamp(remaining / ATTACK_ANIM_TICKS, 0.0F, 1.0F);
   }

   public int getMeleeAttackType() {
      return this.clientAttackType;
   }

   /** 0..1 hurt flash skeletal reaction. */
   public float getHurtReaction(float partialTick) {
      if (this.hurtTime <= 0) {
         return 0.0F;
      }
      return Mth.clamp((this.hurtTime - partialTick) / Math.max(1, this.hurtDuration), 0.0F, 1.0F);
   }

   /** 0..1 death collapse (capped so removal timing stays vanilla). */
   public float getDeathReaction(float partialTick) {
      if (!this.isDeadOrDying()) {
         return 0.0F;
      }
      return Mth.clamp((this.deathTime + partialTick) / 20.0F, 0.0F, 1.0F);
   }
}
