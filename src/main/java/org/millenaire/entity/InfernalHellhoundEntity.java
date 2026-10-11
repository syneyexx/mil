package org.millenaire.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
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

public class InfernalHellhoundEntity extends Monster {
   private static final int ATTACK_ANIM_TICKS = 14;

   private int clientAttackTicks;
   private int clientAttackType;

   public InfernalHellhoundEntity(EntityType<? extends InfernalHellhoundEntity> type, Level level) {
      super(type, level);
      this.xpReward = 12;
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.28F));
      this.goalSelector.addGoal(3, new HellhoundMeleeGoal(this, 1.35, true));
      this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.9));
      this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
      this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 40.0)
         .add(Attributes.MOVEMENT_SPEED, 0.33)
         .add(Attributes.FOLLOW_RANGE, 36.0)
         .add(Attributes.ATTACK_DAMAGE, 17.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.32)
         .add(Attributes.ARMOR, 3.0)
         .add(Attributes.STEP_HEIGHT, 1.0);
   }

   public static boolean checkInfernalHellhoundSpawnRules(
      EntityType<InfernalHellhoundEntity> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random
   ) {
      if (level.getDifficulty() == Difficulty.PEACEFUL) {
         return false;
      }
      if (spawnType == MobSpawnType.SPAWNER || spawnType == MobSpawnType.SPAWN_EGG || spawnType == MobSpawnType.COMMAND) {
         return Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random);
      }
      if (!level.canSeeSky(pos) && Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random)) {
         return random.nextFloat() < 0.45F;
      }
      return level.canSeeSky(pos)
         && level.getLevel().isNight()
         && Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random);
   }

   @Override
   public void aiStep() {
      super.aiStep();
      if (this.level().isClientSide) {
         if (this.clientAttackTicks > 0) {
            this.clientAttackTicks--;
         }
         if (this.random.nextInt(12) == 0) {
            this.level()
               .addParticle(
                  ParticleTypes.FLAME,
                  this.getX() + (this.random.nextDouble() - 0.5) * 0.7,
                  this.getY() + 0.7 + this.random.nextDouble() * 0.4,
                  this.getZ() + (this.random.nextDouble() - 0.5) * 0.7,
                  0.0,
                  0.015,
                  0.0
               );
         }
      }
   }

   @Override
   public boolean doHurtTarget(Entity target) {
      boolean hurt = super.doHurtTarget(target);
      if (hurt && target instanceof LivingEntity living) {
         living.igniteForSeconds(2);
      }
      return hurt;
   }

   @Override
   public void handleEntityEvent(byte id) {
      if (id == 4) {
         this.clientAttackTicks = ATTACK_ANIM_TICKS;
         this.clientAttackType = 0;
      } else {
         super.handleEntityEvent(id);
      }
   }

   public float getMeleeAttackProgress(float partialTick) {
      if (this.clientAttackTicks <= 0) {
         return 0.0F;
      }
      return 1.0F - Mth.clamp((this.clientAttackTicks - partialTick) / ATTACK_ANIM_TICKS, 0.0F, 1.0F);
   }

   public int getMeleeAttackType() {
      return this.clientAttackType;
   }

   public float getHurtReaction(float partialTick) {
      if (this.hurtTime <= 0) {
         return 0.0F;
      }
      return Mth.clamp((this.hurtTime - partialTick) / Math.max(1, this.hurtDuration), 0.0F, 1.0F);
   }

   public float getDeathReaction(float partialTick) {
      if (!this.isDeadOrDying()) {
         return 0.0F;
      }
      return Mth.clamp((this.deathTime + partialTick) / 20.0F, 0.0F, 1.0F);
   }

   /** Distinct run gait while aggressively chasing a target. */
   public boolean isRunning() {
      return this.getTarget() != null && this.getTarget().isAlive() && this.distanceToSqr(this.getTarget()) < 256.0 && this.walkAnimation.speed() > 0.35F;
   }

   @Override
   protected SoundEvent getAmbientSound() {
      return SoundEvents.WOLF_GROWL;
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return SoundEvents.WOLF_HURT;
   }

   @Override
   protected SoundEvent getDeathSound() {
      return SoundEvents.WOLF_DEATH;
   }

   @Override
   protected void playStepSound(BlockPos pos, BlockState state) {
      this.playSound(SoundEvents.WOLF_STEP, 0.25F, 0.75F);
   }

   private static class HellhoundMeleeGoal extends MeleeAttackGoal {
      private final InfernalHellhoundEntity hound;

      HellhoundMeleeGoal(InfernalHellhoundEntity hound, double speed, boolean followIfNotSeen) {
         super(hound, speed, followIfNotSeen);
         this.hound = hound;
      }

      @Override
      protected void checkAndPerformAttack(LivingEntity target) {
         if (this.canPerformAttack(target)) {
            // Wind-up event before damage so the bite pose is visible.
            this.hound.level().broadcastEntityEvent(this.hound, (byte)4);
            this.hound.doHurtTarget(target);
            this.resetAttackCooldown();
         }
      }
   }
}
