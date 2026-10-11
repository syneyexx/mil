package org.millenaire.entity;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
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
import org.jetbrains.annotations.Nullable;

/**
 * Infernal Cultist — aggressive melee follower of the Antichrist Priest.
 * Naturally spawned cultists are independent; priest-summoned cultists track ownership and lifetime.
 */
public class InfernalCultistEntity extends Monster {
   public static final byte VARIANT_RITUALIST = 0;
   public static final byte VARIANT_ZEALOT = 1;

   private static final EntityDataAccessor<Byte> DATA_VARIANT = SynchedEntityData.defineId(
      InfernalCultistEntity.class, EntityDataSerializers.BYTE
   );
   private static final EntityDataAccessor<Boolean> DATA_SUMMONED = SynchedEntityData.defineId(
      InfernalCultistEntity.class, EntityDataSerializers.BOOLEAN
   );

   private static final int ATTACK_ANIM_TICKS = 12;
   private static final int APPEAR_ANIM_TICKS = 20;
   public static final int SUMMONED_LIFESPAN_TICKS = 2400;
   public static final int OWNER_MISSING_GRACE_TICKS = 200;
   public static final int OWNER_DEATH_DESPAWN_TICKS = 200;

   private int clientAttackTicks;
   private int clientAppearTicks;
   @Nullable
   private UUID summonerUuid;
   private int summonedAgeTicks;
   private int ownerUnresolvedTicks;
   private int ownerDeathGraceTicks = -1;

   public InfernalCultistEntity(EntityType<? extends InfernalCultistEntity> type, Level level) {
      super(type, level);
      this.xpReward = 5;
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(DATA_VARIANT, VARIANT_ZEALOT);
      builder.define(DATA_SUMMONED, false);
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(2, new CultistMeleeGoal(this, 1.25, true));
      this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.85));
      this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
      this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(InfernalCultistEntity.class));
      this.targetSelector.addGoal(
         2,
         new NearestAttackableTargetGoal<>(this, Player.class, true, this::isValidPlayerTarget)
      );
   }

   private boolean isValidPlayerTarget(LivingEntity candidate) {
      return candidate instanceof Player && !InfernalFaction.shouldSkipHostileTarget(this, candidate);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 26.0)
         .add(Attributes.MOVEMENT_SPEED, 0.29)
         .add(Attributes.FOLLOW_RANGE, 30.0)
         .add(Attributes.ATTACK_DAMAGE, 7.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.10)
         .add(Attributes.STEP_HEIGHT, 1.0);
   }

   public static boolean checkInfernalCultistSpawnRules(
      EntityType<InfernalCultistEntity> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random
   ) {
      return InfernalNightSpawnRules.canNaturalSpawnAtNight(type, level, spawnType, pos, random);
   }

   @Nullable
   @Override
   public SpawnGroupData finalizeSpawn(
      ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData groupData
   ) {
      SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
      if (!this.isSummoned()) {
         // Ritualist 40% / Zealot 60%
         this.setVariant(this.random.nextFloat() < 0.4F ? VARIANT_RITUALIST : VARIANT_ZEALOT);
      }
      return data;
   }

   public byte getVariant() {
      return this.entityData.get(DATA_VARIANT);
   }

   public void setVariant(byte variant) {
      this.entityData.set(DATA_VARIANT, variant == VARIANT_RITUALIST ? VARIANT_RITUALIST : VARIANT_ZEALOT);
   }

   public boolean isRitualist() {
      return this.getVariant() == VARIANT_RITUALIST;
   }

   public boolean isZealot() {
      return this.getVariant() == VARIANT_ZEALOT;
   }

   public boolean isSummoned() {
      return this.entityData.get(DATA_SUMMONED);
   }

   public void markSummoned(@Nullable UUID summoner) {
      this.entityData.set(DATA_SUMMONED, true);
      this.summonerUuid = summoner;
      this.summonedAgeTicks = 0;
      this.ownerUnresolvedTicks = 0;
      this.ownerDeathGraceTicks = -1;
      this.xpReward = 0;
      this.setPersistenceRequired();
      if (this.level().isClientSide) {
         this.clientAppearTicks = APPEAR_ANIM_TICKS;
      } else {
         this.level().broadcastEntityEvent(this, (byte)8);
      }
   }

   @Nullable
   public UUID getSummonerUuid() {
      return this.summonerUuid;
   }

   public void adoptPriestTarget(@Nullable LivingEntity target) {
      if (target != null && target.isAlive() && !InfernalFaction.areAllies(this, target)) {
         this.setTarget(target);
      }
   }

   @Override
   public void aiStep() {
      super.aiStep();
      if (!this.level().isClientSide && this.isSummoned()) {
         this.tickSummonedLifecycle();
      }
      if (this.level().isClientSide) {
         if (this.clientAttackTicks > 0) {
            this.clientAttackTicks--;
         }
         if (this.clientAppearTicks > 0) {
            this.clientAppearTicks--;
         }
         if (this.random.nextInt(16) == 0) {
            this.level()
               .addParticle(
                  ParticleTypes.SMOKE,
                  this.getX() + (this.random.nextDouble() - 0.5) * 0.4,
                  this.getY() + 1.2 + this.random.nextDouble() * 0.3,
                  this.getZ() + (this.random.nextDouble() - 0.5) * 0.4,
                  0.0,
                  0.01,
                  0.0
               );
         }
      }
   }

   private void tickSummonedLifecycle() {
      this.summonedAgeTicks++;
      if (this.summonedAgeTicks >= SUMMONED_LIFESPAN_TICKS) {
         this.fadeOutSummoned();
         return;
      }
      if (!(this.level() instanceof ServerLevel server)) {
         return;
      }
      Optional<Entity> ownerOpt = this.summonerUuid == null ? Optional.empty() : Optional.ofNullable(server.getEntity(this.summonerUuid));
      if (ownerOpt.isPresent()) {
         Entity owner = ownerOpt.get();
         this.ownerUnresolvedTicks = 0;
         if (owner instanceof AntichristPriestEntity priest) {
            if (!priest.isAlive()) {
               if (this.ownerDeathGraceTicks < 0) {
                  this.ownerDeathGraceTicks = OWNER_DEATH_DESPAWN_TICKS;
               }
            } else {
               this.ownerDeathGraceTicks = -1;
               LivingEntity priestTarget = priest.getTarget();
               if (priestTarget != null && (this.getTarget() == null || !this.getTarget().isAlive())) {
                  this.adoptPriestTarget(priestTarget);
               }
            }
         } else if (!(owner instanceof LivingEntity living) || !living.isAlive()) {
            if (this.ownerDeathGraceTicks < 0) {
               this.ownerDeathGraceTicks = OWNER_DEATH_DESPAWN_TICKS;
            }
         }
      } else {
         this.ownerUnresolvedTicks++;
         if (this.ownerUnresolvedTicks >= OWNER_MISSING_GRACE_TICKS) {
            this.fadeOutSummoned();
            return;
         }
      }
      if (this.ownerDeathGraceTicks >= 0) {
         this.ownerDeathGraceTicks--;
         if (this.ownerDeathGraceTicks <= 0) {
            this.fadeOutSummoned();
         }
      }
   }

   private void fadeOutSummoned() {
      if (this.level() instanceof ServerLevel server) {
         server.sendParticles(
            ParticleTypes.SMOKE, this.getX(), this.getY() + 1.0, this.getZ(), 12, 0.25, 0.5, 0.25, 0.02
         );
         server.sendParticles(
            ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 1.0, this.getZ(), 6, 0.2, 0.4, 0.2, 0.01
         );
      }
      this.discard();
   }

   @Override
   public boolean isAlliedTo(Entity other) {
      return InfernalFaction.areAllies(this, other) || super.isAlliedTo(other);
   }

   @Override
   public boolean doHurtTarget(Entity target) {
      if (InfernalFaction.areAllies(this, target)) {
         return false;
      }
      this.level().broadcastEntityEvent(this, (byte)4);
      return super.doHurtTarget(target);
   }

   @Override
   protected void dropFromLootTable(DamageSource damageSource, boolean hitByPlayer) {
      if (this.isSummoned()) {
         return;
      }
      super.dropFromLootTable(damageSource, hitByPlayer);
   }

   @Override
   public boolean shouldDropExperience() {
      return !this.isSummoned() && super.shouldDropExperience();
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putByte("Variant", this.getVariant());
      tag.putBoolean("Summoned", this.isSummoned());
      if (this.summonerUuid != null) {
         tag.putUUID("Summoner", this.summonerUuid);
      }
      tag.putInt("SummonedAge", this.summonedAgeTicks);
      tag.putInt("OwnerUnresolved", this.ownerUnresolvedTicks);
      tag.putInt("OwnerDeathGrace", this.ownerDeathGraceTicks);
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      if (tag.contains("Variant")) {
         this.setVariant(tag.getByte("Variant"));
      }
      boolean summoned = tag.getBoolean("Summoned");
      this.entityData.set(DATA_SUMMONED, summoned);
      this.summonerUuid = tag.hasUUID("Summoner") ? tag.getUUID("Summoner") : null;
      this.summonedAgeTicks = tag.getInt("SummonedAge");
      this.ownerUnresolvedTicks = tag.getInt("OwnerUnresolved");
      this.ownerDeathGraceTicks = tag.contains("OwnerDeathGrace") ? tag.getInt("OwnerDeathGrace") : -1;
      if (summoned) {
         this.xpReward = 0;
         this.setPersistenceRequired();
      }
   }

   @Override
   public void handleEntityEvent(byte id) {
      if (id == 4) {
         this.clientAttackTicks = ATTACK_ANIM_TICKS;
      } else if (id == 8) {
         this.clientAppearTicks = APPEAR_ANIM_TICKS;
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

   public float getAppearProgress(float partialTick) {
      if (this.clientAppearTicks <= 0) {
         return 1.0F;
      }
      return 1.0F - Mth.clamp((this.clientAppearTicks - partialTick) / APPEAR_ANIM_TICKS, 0.0F, 1.0F);
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

   public boolean isRunning() {
      return this.getTarget() != null
         && this.getTarget().isAlive()
         && this.distanceToSqr(this.getTarget()) < 256.0
         && this.walkAnimation.speed() > 0.35F;
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
      this.playSound(SoundEvents.WITHER_SKELETON_STEP, 0.18F, 1.1F);
   }

   private static class CultistMeleeGoal extends MeleeAttackGoal {
      private final InfernalCultistEntity cultist;

      CultistMeleeGoal(InfernalCultistEntity cultist, double speed, boolean followEvenIfNotSeen) {
         super(cultist, speed, followEvenIfNotSeen);
         this.cultist = cultist;
      }

      @Override
      protected void checkAndPerformAttack(LivingEntity target) {
         if (InfernalFaction.areAllies(this.cultist, target)) {
            return;
         }
         super.checkAndPerformAttack(target);
      }
   }
}
