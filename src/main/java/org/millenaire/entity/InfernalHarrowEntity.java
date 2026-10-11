package org.millenaire.entity;

import java.util.EnumSet;
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
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Flying Infernal Harrow. Extends {@link Monster} so vanilla targeting goals work,
 * while using flying navigation / move control for aerial combat.
 */
public class InfernalHarrowEntity extends Monster {
   private static final int MELEE_ANIM_TICKS = 14;
   private static final int RANGED_ANIM_TICKS = 18;
   private static final int RANGED_COOLDOWN_MIN = 50;
   private static final int RANGED_COOLDOWN_MAX = 80;

   private int clientMeleeTicks;
   private int clientMeleeType;
   private int clientRangedTicks;
   private int rangedCooldown;

   public InfernalHarrowEntity(EntityType<? extends InfernalHarrowEntity> type, Level level) {
      super(type, level);
      this.moveControl = new FlyingMoveControl(this, 18, true);
      this.setNoGravity(true);
      this.xpReward = 10;
   }

   @Override
   protected PathNavigation createNavigation(Level level) {
      FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
      navigation.setCanOpenDoors(false);
      navigation.setCanFloat(true);
      navigation.setCanPassDoors(true);
      return navigation;
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(1, new HarrowMeleeGoal(this));
      this.goalSelector.addGoal(2, new HarrowFireSpitGoal(this));
      this.goalSelector.addGoal(3, new HarrowCircleGoal(this));
      this.goalSelector.addGoal(7, new HarrowWanderGoal(this));
      this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0F));
      this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 35.0)
         .add(Attributes.FOLLOW_RANGE, 36.0)
         .add(Attributes.ATTACK_DAMAGE, 15.0)
         .add(Attributes.MOVEMENT_SPEED, 0.32)
         .add(Attributes.FLYING_SPEED, 0.32)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.28)
         .add(Attributes.ARMOR, 2.0);
   }

   public static boolean checkInfernalHarrowSpawnRules(
      EntityType<InfernalHarrowEntity> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random
   ) {
      if (level.getDifficulty() == Difficulty.PEACEFUL) {
         return false;
      }
      if (spawnType == MobSpawnType.SPAWNER || spawnType == MobSpawnType.SPAWN_EGG || spawnType == MobSpawnType.COMMAND) {
         return Monster.isDarkEnoughToSpawn(level, pos, random);
      }
      if (!level.canSeeSky(pos) && Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random)) {
         return random.nextFloat() < 0.35F;
      }
      return level.canSeeSky(pos)
         && Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random)
         && level.getLevel().isNight();
   }

   @Override
   public void travel(Vec3 travelVector) {
      if (this.isControlledByLocalInstance()) {
         if (this.isInWater()) {
            this.moveRelative(0.02F, travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
         } else if (this.isInLava()) {
            this.moveRelative(0.02F, travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
         } else {
            float speed = this.onGround() ? this.getSpeed() * 0.1F : this.getSpeed();
            this.moveRelative(speed, travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.91));
         }
      }
      this.calculateEntityAnimation(false);
   }

   @Override
   public void aiStep() {
      super.aiStep();
      if (this.rangedCooldown > 0) {
         this.rangedCooldown--;
      }
      if (this.level().isClientSide) {
         if (this.clientMeleeTicks > 0) {
            this.clientMeleeTicks--;
         }
         if (this.clientRangedTicks > 0) {
            this.clientRangedTicks--;
         }
         if (this.random.nextInt(10) == 0) {
            this.level()
               .addParticle(
                  ParticleTypes.FLAME,
                  this.getX() + (this.random.nextDouble() - 0.5) * 0.6,
                  this.getY() + 1.2 + this.random.nextDouble() * 0.4,
                  this.getZ() + (this.random.nextDouble() - 0.5) * 0.6,
                  0.0,
                  0.01,
                  0.0
               );
         }
      }
   }

   @Override
   public boolean doHurtTarget(Entity target) {
      this.level().broadcastEntityEvent(this, (byte)4);
      boolean hurt = super.doHurtTarget(target);
      if (hurt && target instanceof LivingEntity living) {
         living.igniteForSeconds(2);
      }
      return hurt;
   }

   public void beginRangedAttackAnim() {
      this.level().broadcastEntityEvent(this, (byte)5);
   }

   public void fireProjectileAt(LivingEntity target) {
      if (this.level().isClientSide || target == null) {
         return;
      }
      Vec3 eye = this.getEyePosition();
      Vec3 aim = target.getEyePosition().subtract(eye);
      double dist = aim.length();
      if (dist < 0.001) {
         return;
      }
      Vec3 lead = target.getDeltaMovement().scale(dist * 0.15);
      Vec3 dir = aim.add(lead).normalize();
      InfernalFireballEntity fireball = new InfernalFireballEntity(this.level(), this, dir.scale(0.95));
      fireball.setPos(eye.x, eye.y, eye.z);
      this.level().addFreshEntity(fireball);
      this.playSound(SoundEvents.BLAZE_SHOOT, 1.0F, 0.85F + this.random.nextFloat() * 0.2F);
      this.rangedCooldown = RANGED_COOLDOWN_MIN + this.random.nextInt(RANGED_COOLDOWN_MAX - RANGED_COOLDOWN_MIN + 1);
   }

   public boolean canFireRanged() {
      return this.rangedCooldown <= 0;
   }

   @Override
   public void handleEntityEvent(byte id) {
      if (id == 4) {
         this.clientMeleeTicks = MELEE_ANIM_TICKS;
         this.clientMeleeType = 0;
      } else if (id == 5) {
         this.clientRangedTicks = RANGED_ANIM_TICKS;
      } else {
         super.handleEntityEvent(id);
      }
   }

   public float getMeleeAttackProgress(float partialTick) {
      if (this.clientMeleeTicks <= 0) {
         return 0.0F;
      }
      return 1.0F - Mth.clamp((this.clientMeleeTicks - partialTick) / MELEE_ANIM_TICKS, 0.0F, 1.0F);
   }

   public int getMeleeAttackType() {
      return this.clientMeleeType;
   }

   public float getRangedAttackProgress(float partialTick) {
      if (this.clientRangedTicks <= 0) {
         return 0.0F;
      }
      return 1.0F - Mth.clamp((this.clientRangedTicks - partialTick) / RANGED_ANIM_TICKS, 0.0F, 1.0F);
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

   public boolean isFlyingHard() {
      return this.getDeltaMovement().horizontalDistanceSqr() > 0.012
         || this.getTarget() != null && this.distanceToSqr(this.getTarget()) > 16.0;
   }

   @Override
   protected SoundEvent getAmbientSound() {
      return SoundEvents.PHANTOM_AMBIENT;
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return SoundEvents.PHANTOM_HURT;
   }

   @Override
   protected SoundEvent getDeathSound() {
      return SoundEvents.PHANTOM_DEATH;
   }

   @Override
   public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
      return false;
   }

   @Override
   protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
   }

   @Override
   public boolean onClimbable() {
      return false;
   }

   private static void flyToward(InfernalHarrowEntity harrow, Vec3 wanted, double speed) {
      harrow.getMoveControl().setWantedPosition(wanted.x, wanted.y, wanted.z, speed);
   }

   private static double combatAltitude(InfernalHarrowEntity harrow, LivingEntity target) {
      BlockPos below = BlockPos.containing(target.getX(), target.getY(), target.getZ());
      int groundY = harrow.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, below.getX(), below.getZ());
      double base = Math.max(target.getY() + 2.5, groundY + 3.0);
      return Mth.clamp(base, groundY + 2.0, groundY + 7.0);
   }

   private static class HarrowCircleGoal extends Goal {
      private final InfernalHarrowEntity harrow;
      private int repathTicks;
      private float angle;

      HarrowCircleGoal(InfernalHarrowEntity harrow) {
         this.harrow = harrow;
         this.setFlags(EnumSet.of(Flag.MOVE));
      }

      @Override
      public boolean canUse() {
         return this.harrow.getTarget() != null && this.harrow.getTarget().isAlive();
      }

      @Override
      public void start() {
         this.angle = this.harrow.getRandom().nextFloat() * ((float)Math.PI * 2.0F);
         this.repathTicks = 0;
      }

      @Override
      public void tick() {
         LivingEntity target = this.harrow.getTarget();
         if (target == null) {
            return;
         }
         double dist = this.harrow.distanceToSqr(target);
         if (dist < 12.0 || dist > 420.0) {
            return;
         }
         if (--this.repathTicks <= 0) {
            this.repathTicks = 8 + this.harrow.getRandom().nextInt(8);
            this.angle += 0.55F + this.harrow.getRandom().nextFloat() * 0.35F;
            double radius = 7.0 + this.harrow.getRandom().nextDouble() * 5.0;
            double x = target.getX() + Math.cos(this.angle) * radius;
            double z = target.getZ() + Math.sin(this.angle) * radius;
            double y = combatAltitude(this.harrow, target) + this.harrow.getRandom().nextDouble() * 1.5;
            flyToward(this.harrow, new Vec3(x, y, z), 1.05);
         }
         this.harrow.getLookControl().setLookAt(target, 40.0F, 40.0F);
      }
   }

   private static class HarrowFireSpitGoal extends Goal {
      private final InfernalHarrowEntity harrow;
      private int windup;

      HarrowFireSpitGoal(InfernalHarrowEntity harrow) {
         this.harrow = harrow;
         this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
      }

      @Override
      public boolean canUse() {
         LivingEntity target = this.harrow.getTarget();
         if (target == null || !target.isAlive() || !this.harrow.canFireRanged()) {
            return false;
         }
         double dist = this.harrow.distanceToSqr(target);
         return dist > 36.0 && dist < 400.0 && this.harrow.getRandom().nextInt(8) == 0;
      }

      @Override
      public boolean canContinueToUse() {
         return this.windup > 0 && this.harrow.getTarget() != null && this.harrow.getTarget().isAlive();
      }

      @Override
      public void start() {
         this.windup = 16;
         this.harrow.beginRangedAttackAnim();
         this.harrow.getNavigation().stop();
      }

      @Override
      public void tick() {
         LivingEntity target = this.harrow.getTarget();
         if (target == null) {
            return;
         }
         this.harrow.getLookControl().setLookAt(target, 60.0F, 60.0F);
         this.windup--;
         if (this.windup == 6) {
            this.harrow.fireProjectileAt(target);
         }
      }
   }

   private static class HarrowMeleeGoal extends Goal {
      private final InfernalHarrowEntity harrow;
      private int strikeCooldown;

      HarrowMeleeGoal(InfernalHarrowEntity harrow) {
         this.harrow = harrow;
         this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
      }

      @Override
      public boolean canUse() {
         LivingEntity target = this.harrow.getTarget();
         return target != null && target.isAlive() && this.harrow.distanceToSqr(target) < 20.0;
      }

      @Override
      public void tick() {
         LivingEntity target = this.harrow.getTarget();
         if (target == null) {
            return;
         }
         this.harrow.getLookControl().setLookAt(target, 50.0F, 50.0F);
         double dist = this.harrow.distanceToSqr(target);
         if (dist > 6.0) {
            double y = Math.min(target.getY() + 1.2, combatAltitude(this.harrow, target));
            flyToward(this.harrow, new Vec3(target.getX(), y, target.getZ()), 1.25);
         } else if (--this.strikeCooldown <= 0) {
            this.strikeCooldown = 18;
            this.harrow.doHurtTarget(target);
            double angle = this.harrow.getRandom().nextDouble() * Math.PI * 2.0;
            flyToward(
               this.harrow,
               new Vec3(target.getX() + Math.cos(angle) * 5.5, combatAltitude(this.harrow, target), target.getZ() + Math.sin(angle) * 5.5),
               1.15
            );
         }
      }
   }

   private static class HarrowWanderGoal extends Goal {
      private final InfernalHarrowEntity harrow;
      private int cooldown;

      HarrowWanderGoal(InfernalHarrowEntity harrow) {
         this.harrow = harrow;
         this.setFlags(EnumSet.of(Flag.MOVE));
      }

      @Override
      public boolean canUse() {
         return this.harrow.getTarget() == null && --this.cooldown <= 0;
      }

      @Override
      public void start() {
         this.cooldown = 40 + this.harrow.getRandom().nextInt(40);
         BlockPos pos = this.harrow.blockPosition();
         int ground = this.harrow.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
         double x = pos.getX() + (this.harrow.getRandom().nextDouble() - 0.5) * 14.0;
         double z = pos.getZ() + (this.harrow.getRandom().nextDouble() - 0.5) * 14.0;
         double y = ground + 3.0 + this.harrow.getRandom().nextDouble() * 3.0;
         flyToward(this.harrow, new Vec3(x, y, z), 0.85);
      }
   }
}
