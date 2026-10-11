package org.millenaire.entity;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Antichrist Priest — hostile summoner, cultist healer, and infernal hex caster.
 */
public class AntichristPriestEntity extends Monster {
   public static final int MAX_OWNED_SUMMONS = 4;
   public static final int MAX_LOCAL_SUMMONS = 8;
   public static final double LOCAL_SUMMON_RADIUS = 24.0;
   public static final double SUMMON_TARGET_RANGE = 28.0;
   public static final int SUMMON_COOLDOWN_TICKS = 600;
   public static final int SUMMON_WINDUP_TICKS = 30;
   public static final float HEAL_AMOUNT = 5.0F;
   public static final double HEAL_RADIUS = 10.0;
   public static final int HEAL_MAX_TARGETS = 2;
   public static final int HEAL_COOLDOWN_TICKS = 200;
   public static final int HEAL_WINDUP_TICKS = 24;
   public static final int PROJECTILE_COOLDOWN_MIN = 60;
   public static final int PROJECTILE_COOLDOWN_MAX = 80;
   public static final int PROJECTILE_WINDUP_TICKS = 16;

   private static final int MELEE_ANIM_TICKS = 14;
   private static final int CAST_ANIM_TICKS = 18;
   private static final int SUMMON_ANIM_TICKS = 34;
   private static final int HEAL_ANIM_TICKS = 28;

   private int summonCooldown;
   private int healCooldown;
   private int projectileCooldown;
   private int clientMeleeTicks;
   private int clientCastTicks;
   private int clientSummonTicks;
   private int clientHealTicks;

   public AntichristPriestEntity(EntityType<? extends AntichristPriestEntity> type, Level level) {
      super(type, level);
      this.xpReward = 15;
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(1, new PriestHealGoal(this));
      this.goalSelector.addGoal(2, new PriestSummonGoal(this));
      this.goalSelector.addGoal(3, new PriestHexBoltGoal(this));
      this.goalSelector.addGoal(4, new PriestMeleeGoal(this, 1.0, false));
      this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.75));
      this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0F));
      this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
      this.targetSelector.addGoal(
         2,
         new NearestAttackableTargetGoal<>(this, Player.class, true, living -> !InfernalFaction.areAllies(this, living))
      );
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 50.0)
         .add(Attributes.MOVEMENT_SPEED, 0.23)
         .add(Attributes.FOLLOW_RANGE, 36.0)
         .add(Attributes.ATTACK_DAMAGE, 6.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.30)
         .add(Attributes.STEP_HEIGHT, 1.0);
   }

   public static boolean checkAntichristPriestSpawnRules(
      EntityType<AntichristPriestEntity> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random
   ) {
      return InfernalNightSpawnRules.canNaturalSpawnAtNight(type, level, spawnType, pos, random);
   }

   @Override
   public void aiStep() {
      super.aiStep();
      if (this.summonCooldown > 0) {
         this.summonCooldown--;
      }
      if (this.healCooldown > 0) {
         this.healCooldown--;
      }
      if (this.projectileCooldown > 0) {
         this.projectileCooldown--;
      }
      if (this.level().isClientSide) {
         if (this.clientMeleeTicks > 0) {
            this.clientMeleeTicks--;
         }
         if (this.clientCastTicks > 0) {
            this.clientCastTicks--;
         }
         if (this.clientSummonTicks > 0) {
            this.clientSummonTicks--;
         }
         if (this.clientHealTicks > 0) {
            this.clientHealTicks--;
         }
         if (this.random.nextInt(10) == 0) {
            this.level()
               .addParticle(
                  ParticleTypes.SOUL_FIRE_FLAME,
                  this.getX() + (this.random.nextDouble() - 0.5) * 0.35,
                  this.getY() + 1.55 + this.random.nextDouble() * 0.25,
                  this.getZ() + (this.random.nextDouble() - 0.5) * 0.35,
                  0.0,
                  0.01,
                  0.0
               );
         }
      }
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

   public boolean canSummon() {
      return this.summonCooldown <= 0;
   }

   public boolean canHeal() {
      return this.healCooldown <= 0;
   }

   public boolean canFireHex() {
      return this.projectileCooldown <= 0;
   }

   public void beginSummonAnim() {
      this.level().broadcastEntityEvent(this, (byte)6);
   }

   public void beginHealAnim() {
      this.level().broadcastEntityEvent(this, (byte)7);
   }

   public void beginCastAnim() {
      this.level().broadcastEntityEvent(this, (byte)5);
   }

   public int countOwnedSummonsNearby() {
      return (int)this.level()
         .getEntitiesOfClass(
            InfernalCultistEntity.class,
            this.getBoundingBox().inflate(LOCAL_SUMMON_RADIUS),
            cultist -> cultist.isAlive() && cultist.isSummoned() && this.getUUID().equals(cultist.getSummonerUuid())
         )
         .size();
   }

   public int countLocalSummonedCultists() {
      return (int)this.level()
         .getEntitiesOfClass(
            InfernalCultistEntity.class,
            this.getBoundingBox().inflate(LOCAL_SUMMON_RADIUS),
            cultist -> cultist.isAlive() && cultist.isSummoned()
         )
         .size();
   }

   public boolean trySummonCultists() {
      if (this.level().isClientSide || !(this.level() instanceof ServerLevel server)) {
         return false;
      }
      if (this.level().getDifficulty() == Difficulty.PEACEFUL) {
         return false;
      }
      if (!this.level().getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
         return false;
      }
      LivingEntity target = this.getTarget();
      if (target == null || !target.isAlive() || this.distanceToSqr(target) > SUMMON_TARGET_RANGE * SUMMON_TARGET_RANGE) {
         return false;
      }
      int owned = this.countOwnedSummonsNearby();
      int local = this.countLocalSummonedCultists();
      if (owned >= MAX_OWNED_SUMMONS || local >= MAX_LOCAL_SUMMONS) {
         this.summonCooldown = SUMMON_COOLDOWN_TICKS;
         return false;
      }
      int desired = 1 + this.random.nextInt(2);
      int roomOwned = MAX_OWNED_SUMMONS - owned;
      int roomLocal = MAX_LOCAL_SUMMONS - local;
      int toSpawn = Math.min(desired, Math.min(roomOwned, roomLocal));
      int spawned = 0;
      for (int i = 0; i < toSpawn; i++) {
         BlockPos spawnPos = this.findSafeSummonPos(server, 2 + i);
         if (spawnPos == null) {
            continue;
         }
         InfernalCultistEntity cultist = ModEntities.INFERNAL_CULTIST.get().create(server);
         if (cultist == null) {
            continue;
         }
         cultist.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, this.getYRot(), 0.0F);
         if (!server.noCollision(cultist) || !server.isUnobstructed(cultist)) {
            cultist.discard();
            continue;
         }
         cultist.setVariant(this.random.nextFloat() < 0.4F ? InfernalCultistEntity.VARIANT_RITUALIST : InfernalCultistEntity.VARIANT_ZEALOT);
         cultist.finalizeSpawn(server, server.getCurrentDifficultyAt(spawnPos), MobSpawnType.MOB_SUMMONED, null);
         cultist.markSummoned(this.getUUID());
         cultist.adoptPriestTarget(target);
         server.addFreshEntity(cultist);
         spawned++;
         server.sendParticles(
            ParticleTypes.SOUL_FIRE_FLAME,
            cultist.getX(),
            cultist.getY() + 0.2,
            cultist.getZ(),
            18,
            0.35,
            0.15,
            0.35,
            0.02
         );
         server.sendParticles(ParticleTypes.SMOKE, cultist.getX(), cultist.getY() + 0.1, cultist.getZ(), 10, 0.3, 0.1, 0.3, 0.01);
      }
      this.summonCooldown = SUMMON_COOLDOWN_TICKS;
      if (spawned > 0) {
         this.playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.0F, 0.75F + this.random.nextFloat() * 0.15F);
         return true;
      }
      return false;
   }

   private BlockPos findSafeSummonPos(ServerLevel server, int attemptOffset) {
      BlockPos origin = this.blockPosition();
      for (int attempt = 0; attempt < 12; attempt++) {
         double angle = this.random.nextDouble() * Math.PI * 2.0 + attemptOffset;
         double dist = 1.8 + this.random.nextDouble() * 2.4;
         int x = origin.getX() + Mth.floor(Math.cos(angle) * dist);
         int z = origin.getZ() + Mth.floor(Math.sin(angle) * dist);
         int y = origin.getY();
         BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, y, z);
         // Prefer ground near the priest elevation
         for (int dy = 2; dy >= -3; dy--) {
            cursor.set(x, y + dy, z);
            BlockPos below = cursor.below();
            BlockState floor = server.getBlockState(below);
            if (!floor.isSolidRender(server, below) && !floor.isCollisionShapeFullBlock(server, below)) {
               continue;
            }
            if (!server.getBlockState(cursor).getCollisionShape(server, cursor).isEmpty()) {
               continue;
            }
            if (!server.getBlockState(cursor.above()).getCollisionShape(server, cursor.above()).isEmpty()) {
               continue;
            }
            AABB probe = ModEntities.INFERNAL_CULTIST.get()
               .getDimensions()
               .makeBoundingBox(cursor.getX() + 0.5, cursor.getY(), cursor.getZ() + 0.5);
            if (!server.noCollision(probe)) {
               continue;
            }
            return cursor.immutable();
         }
      }
      return null;
   }

   public boolean tryHealCultists() {
      if (this.level().isClientSide || !(this.level() instanceof ServerLevel server)) {
         return false;
      }
      List<InfernalCultistEntity> injured = server.getEntitiesOfClass(
         InfernalCultistEntity.class,
         this.getBoundingBox().inflate(HEAL_RADIUS),
         cultist -> cultist.isAlive() && cultist.getHealth() < cultist.getMaxHealth()
      );
      if (injured.isEmpty()) {
         return false;
      }
      injured.sort(Comparator.comparingDouble(c -> c.getHealth() / c.getMaxHealth()));
      int healed = 0;
      for (InfernalCultistEntity cultist : injured) {
         if (healed >= HEAL_MAX_TARGETS) {
            break;
         }
         float before = cultist.getHealth();
         cultist.heal(HEAL_AMOUNT);
         if (cultist.getHealth() > before) {
            healed++;
            this.spawnHealBeam(server, cultist);
            server.sendParticles(
               ParticleTypes.HEART, cultist.getX(), cultist.getY() + 1.4, cultist.getZ(), 3, 0.2, 0.25, 0.2, 0.0
            );
            server.sendParticles(
               ParticleTypes.SOUL_FIRE_FLAME, cultist.getX(), cultist.getY() + 1.1, cultist.getZ(), 8, 0.2, 0.3, 0.2, 0.01
            );
         }
      }
      this.healCooldown = HEAL_COOLDOWN_TICKS;
      if (healed > 0) {
         this.playSound(SoundEvents.EVOKER_CAST_SPELL, 0.9F, 1.2F + this.random.nextFloat() * 0.15F);
         return true;
      }
      return false;
   }

   private void spawnHealBeam(ServerLevel server, InfernalCultistEntity cultist) {
      Vec3 from = this.position().add(0.0, 1.8, 0.0);
      Vec3 to = cultist.position().add(0.0, 1.1, 0.0);
      Vec3 delta = to.subtract(from);
      int steps = 8;
      for (int i = 0; i <= steps; i++) {
         double t = i / (double)steps;
         Vec3 p = from.add(delta.scale(t));
         server.sendParticles(ParticleTypes.CRIMSON_SPORE, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
         server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0.01, 0.01, 0.01, 0.0);
      }
   }

   public void fireHexBoltAt(LivingEntity target) {
      if (this.level().isClientSide || target == null) {
         return;
      }
      Vec3 eye = this.getEyePosition();
      Vec3 aim = target.getEyePosition().subtract(eye);
      double dist = aim.length();
      if (dist < 0.001) {
         return;
      }
      Vec3 dir = aim.add(target.getDeltaMovement().scale(dist * 0.12)).normalize();
      InfernalHexBoltEntity bolt = new InfernalHexBoltEntity(this.level(), this, dir.scale(0.9));
      bolt.setPos(eye.x, eye.y, eye.z);
      this.level().addFreshEntity(bolt);
      this.playSound(SoundEvents.BLAZE_SHOOT, 0.85F, 0.55F + this.random.nextFloat() * 0.2F);
      this.projectileCooldown = PROJECTILE_COOLDOWN_MIN + this.random.nextInt(PROJECTILE_COOLDOWN_MAX - PROJECTILE_COOLDOWN_MIN + 1);
   }

   public void spawnSummonCircleParticles() {
      if (!(this.level() instanceof ServerLevel server)) {
         return;
      }
      for (int i = 0; i < 18; i++) {
         double angle = (Math.PI * 2.0 * i) / 18.0;
         double x = this.getX() + Math.cos(angle) * 1.6;
         double z = this.getZ() + Math.sin(angle) * 1.6;
         server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, this.getY() + 0.05, z, 1, 0.0, 0.02, 0.0, 0.0);
         server.sendParticles(ParticleTypes.CRIMSON_SPORE, x, this.getY() + 0.08, z, 1, 0.0, 0.01, 0.0, 0.0);
      }
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putInt("SummonCooldown", this.summonCooldown);
      tag.putInt("HealCooldown", this.healCooldown);
      tag.putInt("ProjectileCooldown", this.projectileCooldown);
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      this.summonCooldown = tag.getInt("SummonCooldown");
      this.healCooldown = tag.getInt("HealCooldown");
      this.projectileCooldown = tag.getInt("ProjectileCooldown");
   }

   @Override
   public void handleEntityEvent(byte id) {
      switch (id) {
         case 4 -> this.clientMeleeTicks = MELEE_ANIM_TICKS;
         case 5 -> this.clientCastTicks = CAST_ANIM_TICKS;
         case 6 -> this.clientSummonTicks = SUMMON_ANIM_TICKS;
         case 7 -> this.clientHealTicks = HEAL_ANIM_TICKS;
         default -> super.handleEntityEvent(id);
      }
   }

   public float getMeleeAttackProgress(float partialTick) {
      if (this.clientMeleeTicks <= 0) {
         return 0.0F;
      }
      return 1.0F - Mth.clamp((this.clientMeleeTicks - partialTick) / MELEE_ANIM_TICKS, 0.0F, 1.0F);
   }

   public float getCastProgress(float partialTick) {
      if (this.clientCastTicks <= 0) {
         return 0.0F;
      }
      return 1.0F - Mth.clamp((this.clientCastTicks - partialTick) / CAST_ANIM_TICKS, 0.0F, 1.0F);
   }

   public float getSummonProgress(float partialTick) {
      if (this.clientSummonTicks <= 0) {
         return 0.0F;
      }
      return 1.0F - Mth.clamp((this.clientSummonTicks - partialTick) / SUMMON_ANIM_TICKS, 0.0F, 1.0F);
   }

   public float getHealProgress(float partialTick) {
      if (this.clientHealTicks <= 0) {
         return 0.0F;
      }
      return 1.0F - Mth.clamp((this.clientHealTicks - partialTick) / HEAL_ANIM_TICKS, 0.0F, 1.0F);
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

   @Override
   protected SoundEvent getAmbientSound() {
      return SoundEvents.EVOKER_AMBIENT;
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return SoundEvents.EVOKER_HURT;
   }

   @Override
   protected SoundEvent getDeathSound() {
      return SoundEvents.EVOKER_DEATH;
   }

   @Override
   protected void playStepSound(BlockPos pos, BlockState state) {
      this.playSound(SoundEvents.WITHER_SKELETON_STEP, 0.15F, 0.7F);
   }

   private static class PriestMeleeGoal extends MeleeAttackGoal {
      private final AntichristPriestEntity priest;

      PriestMeleeGoal(AntichristPriestEntity priest, double speed, boolean followEvenIfNotSeen) {
         super(priest, speed, followEvenIfNotSeen);
         this.priest = priest;
      }

      @Override
      public boolean canUse() {
         LivingEntity target = this.priest.getTarget();
         return target != null && target.isAlive() && this.priest.distanceToSqr(target) < 12.25 && super.canUse();
      }

      @Override
      protected void checkAndPerformAttack(LivingEntity target) {
         if (InfernalFaction.areAllies(this.priest, target)) {
            return;
         }
         super.checkAndPerformAttack(target);
      }
   }

   private static class PriestHexBoltGoal extends Goal {
      private final AntichristPriestEntity priest;
      private int windup;

      PriestHexBoltGoal(AntichristPriestEntity priest) {
         this.priest = priest;
         this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
      }

      @Override
      public boolean canUse() {
         LivingEntity target = this.priest.getTarget();
         if (target == null || !target.isAlive() || !this.priest.canFireHex()) {
            return false;
         }
         double dist = this.priest.distanceToSqr(target);
         return dist >= 25.0 && dist <= 400.0 && this.priest.getRandom().nextInt(6) == 0;
      }

      @Override
      public boolean canContinueToUse() {
         return this.windup > 0 && this.priest.getTarget() != null && this.priest.getTarget().isAlive();
      }

      @Override
      public void start() {
         this.windup = PROJECTILE_WINDUP_TICKS;
         this.priest.beginCastAnim();
         this.priest.getNavigation().stop();
      }

      @Override
      public void tick() {
         LivingEntity target = this.priest.getTarget();
         if (target == null) {
            return;
         }
         this.priest.getLookControl().setLookAt(target, 50.0F, 50.0F);
         this.windup--;
         if (this.windup == 6) {
            this.priest.fireHexBoltAt(target);
         }
      }
   }

   private static class PriestSummonGoal extends Goal {
      private final AntichristPriestEntity priest;
      private int windup;

      PriestSummonGoal(AntichristPriestEntity priest) {
         this.priest = priest;
         this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
      }

      @Override
      public boolean canUse() {
         LivingEntity target = this.priest.getTarget();
         if (target == null || !target.isAlive() || !this.priest.canSummon()) {
            return false;
         }
         if (this.priest.distanceToSqr(target) > SUMMON_TARGET_RANGE * SUMMON_TARGET_RANGE) {
            return false;
         }
         if (this.priest.countOwnedSummonsNearby() >= MAX_OWNED_SUMMONS) {
            return false;
         }
         if (this.priest.countLocalSummonedCultists() >= MAX_LOCAL_SUMMONS) {
            return false;
         }
         return this.priest.getRandom().nextInt(10) == 0;
      }

      @Override
      public boolean canContinueToUse() {
         return this.windup > 0 && this.priest.getTarget() != null && this.priest.getTarget().isAlive();
      }

      @Override
      public void start() {
         this.windup = SUMMON_WINDUP_TICKS;
         this.priest.beginSummonAnim();
         this.priest.getNavigation().stop();
         this.priest.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 0.9F, 0.7F);
         this.priest.spawnSummonCircleParticles();
      }

      @Override
      public void tick() {
         LivingEntity target = this.priest.getTarget();
         if (target != null) {
            this.priest.getLookControl().setLookAt(target, 40.0F, 40.0F);
         }
         if (this.windup % 6 == 0) {
            this.priest.spawnSummonCircleParticles();
         }
         this.windup--;
         if (this.windup == 0) {
            this.priest.trySummonCultists();
         }
      }
   }

   private static class PriestHealGoal extends Goal {
      private final AntichristPriestEntity priest;
      private int windup;

      PriestHealGoal(AntichristPriestEntity priest) {
         this.priest = priest;
         this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
      }

      @Override
      public boolean canUse() {
         if (!this.priest.canHeal() || this.priest.getTarget() == null) {
            return false;
         }
         AABB box = this.priest.getBoundingBox().inflate(HEAL_RADIUS);
         List<InfernalCultistEntity> injured = this.priest.level()
            .getEntitiesOfClass(
               InfernalCultistEntity.class, box, c -> c.isAlive() && c.getHealth() < c.getMaxHealth()
            );
         return !injured.isEmpty() && this.priest.getRandom().nextInt(8) == 0;
      }

      @Override
      public boolean canContinueToUse() {
         return this.windup > 0;
      }

      @Override
      public void start() {
         this.windup = HEAL_WINDUP_TICKS;
         this.priest.beginHealAnim();
         this.priest.getNavigation().stop();
      }

      @Override
      public void tick() {
         this.windup--;
         if (this.windup == 8) {
            this.priest.tryHealCultists();
         }
      }
   }
}
