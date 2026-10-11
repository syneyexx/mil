package org.millenaire.entity;

import java.util.EnumSet;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.faction.crime.hunter.BountyEligibilityService;
import org.millenaire.faction.crime.hunter.BountyHunterCaptureService;
import org.millenaire.faction.crime.hunter.BountyHunterDispatchManager;
import org.millenaire.faction.crime.hunter.BountyHunterPhase;

/**
 * Professional bounty hunter — conditionally hostile only to its assigned bounty target.
 */
public class BountyHunterEntity extends Monster {
   public static final float DEFAULT_MAX_HEALTH = 44.0F;
   public static final float DEFAULT_ATTACK_DAMAGE = 6.0F;
   public static final double DEFAULT_MOVEMENT_SPEED = 0.29;
   public static final double DEFAULT_FOLLOW_RANGE = 40.0;
   public static final double DEFAULT_KNOCKBACK_RESISTANCE = 0.35;
   public static final double DEFAULT_ARMOR = 8.0;
   public static final double ARREST_RANGE = 2.15;
   public static final double BOLA_MIN_RANGE = 5.0;
   public static final double BOLA_MAX_RANGE = 12.0;

   private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(BountyHunterEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(BountyHunterEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> DATA_ANIM_TICKS = SynchedEntityData.defineId(BountyHunterEntity.class, EntityDataSerializers.INT);

   public static final int ANIM_IDLE = 0;
   public static final int ANIM_WALK = 1;
   public static final int ANIM_RUN = 2;
   public static final int ANIM_OBSERVE = 3;
   public static final int ANIM_WARNING = 4;
   public static final int ANIM_BATON = 5;
   public static final int ANIM_BOLA = 6;
   public static final int ANIM_HANDCUFF = 7;
   public static final int ANIM_CAPTURE = 8;

   private UUID contractId;
   private UUID targetPlayerId;
   private ResourceLocation assignedCultureId;
   private long bolaReadyAtGameTime;
   private long warnCooldownUntil;
   private int observeTicks;
   private int restrainChannelTicks;
   private int lostTicks;
   private boolean warnedThisEncounter;
   private int clientAnimTicks;
   private float captureMeter;
   private int meleeCooldown;

   public BountyHunterEntity(EntityType<? extends BountyHunterEntity> type, Level level) {
      super(type, level);
      this.xpReward = 0;
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, DEFAULT_MAX_HEALTH)
         .add(Attributes.MOVEMENT_SPEED, DEFAULT_MOVEMENT_SPEED)
         .add(Attributes.FOLLOW_RANGE, DEFAULT_FOLLOW_RANGE)
         .add(Attributes.ATTACK_DAMAGE, DEFAULT_ATTACK_DAMAGE)
         .add(Attributes.ARMOR, DEFAULT_ARMOR)
         .add(Attributes.KNOCKBACK_RESISTANCE, DEFAULT_KNOCKBACK_RESISTANCE)
         .add(Attributes.STEP_HEIGHT, 1.0);
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(1, new BountyHunterBehaviorGoal(this));
      this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0F));
      this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(DATA_PHASE, BountyHunterPhase.INACTIVE.ordinal());
      builder.define(DATA_ANIM, ANIM_IDLE);
      builder.define(DATA_ANIM_TICKS, 0);
   }

   public void assignContract(UUID contractId, UUID targetPlayerId, ResourceLocation cultureId) {
      this.contractId = contractId;
      this.targetPlayerId = targetPlayerId;
      this.assignedCultureId = cultureId;
   }

   @Nullable
   public UUID getContractId() {
      return this.contractId;
   }

   @Nullable
   public UUID getTargetPlayerId() {
      return this.targetPlayerId;
   }

   @Nullable
   public ResourceLocation getAssignedCultureId() {
      return this.assignedCultureId;
   }

   public BountyHunterPhase getHunterPhase() {
      int ord = this.entityData.get(DATA_PHASE);
      BountyHunterPhase[] values = BountyHunterPhase.values();
      if (ord < 0 || ord >= values.length) {
         return BountyHunterPhase.INACTIVE;
      }
      return values[ord];
   }

   public void setHunterPhase(BountyHunterPhase phase) {
      this.entityData.set(DATA_PHASE, phase.ordinal());
   }

   public int getAnimState() {
      return this.entityData.get(DATA_ANIM);
   }

   public void playAnim(int anim, int ticks) {
      this.entityData.set(DATA_ANIM, anim);
      this.entityData.set(DATA_ANIM_TICKS, ticks);
      if (this.level().isClientSide) {
         this.clientAnimTicks = ticks;
      }
   }

   public float getAnimProgress(float partialTick) {
      int total = this.entityData.get(DATA_ANIM_TICKS);
      if (total <= 0) {
         return 0.0F;
      }
      float remaining = Math.max(0.0F, this.clientAnimTicks - partialTick);
      return 1.0F - Mth.clamp(remaining / total, 0.0F, 1.0F);
   }

   public void enterLogicalTracking() {
      this.setHunterPhase(BountyHunterPhase.SEARCHING);
      this.setTarget(null);
      this.getNavigation().stop();
   }

   public void discardAfterCapture() {
      this.setHunterPhase(BountyHunterPhase.COMPLETE);
      this.discard();
   }

   @Override
   public void aiStep() {
      super.aiStep();
      if (this.level().isClientSide) {
         if (this.clientAnimTicks > 0) {
            this.clientAnimTicks--;
         } else if (this.getAnimState() != ANIM_IDLE && this.getAnimState() != ANIM_WALK && this.getAnimState() != ANIM_RUN) {
            // keep last frame briefly
         }
         return;
      }

      if (!(this.level() instanceof ServerLevel server)) {
         return;
      }
      if (this.meleeCooldown > 0) {
         this.meleeCooldown--;
      }

      // Immediate abandon if bounty revoked.
      if (this.targetPlayerId != null && !BountyEligibilityService.hasActiveEnforceableBounty(server, this.targetPlayerId)) {
         this.setHunterPhase(BountyHunterPhase.DESPAWNING);
         this.setTarget(null);
         this.getNavigation().stop();
         this.discard();
         return;
      }

      BountyHunterPhase phase = this.getHunterPhase();
      if (phase.shouldDespawnWhenIdle() && phase != BountyHunterPhase.INACTIVE) {
         this.discard();
      }
   }

   @Override
   public boolean doHurtTarget(Entity target) {
      if (!(this.level() instanceof ServerLevel server)) {
         return false;
      }
      if (!(target instanceof ServerPlayer player)) {
         return false;
      }
      if (!isAssignedValidTarget(server, player)) {
         return false;
      }

      this.playAnim(ANIM_BATON, 12);
      this.level().broadcastEntityEvent(this, (byte)4);
      this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.75F);

      float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
      float healthBefore = player.getHealth();
      float maxNonlethal = Math.max(0.0F, healthBefore - 1.0F);
      float applied = Math.min(damage, maxNonlethal);
      if (applied <= 0.0F) {
         this.captureMeter = Math.min(1.0F, this.captureMeter + 0.12F);
         return true;
      }

      boolean hurt = player.hurt(this.damageSources().mobAttack(this), applied);
      if (hurt) {
         // Hard nonlethal floor against this hunter.
         if (player.getHealth() < 1.0F) {
            player.setHealth(1.0F);
         }
         this.captureMeter = Math.min(1.0F, this.captureMeter + 0.08F);
         if (player.getHealth() / player.getMaxHealth() <= 0.25F) {
            this.captureMeter = Math.min(1.0F, this.captureMeter + 0.15F);
         }
      }
      return hurt;
   }

   boolean isAssignedValidTarget(ServerLevel level, ServerPlayer player) {
      if (this.targetPlayerId == null || !this.targetPlayerId.equals(player.getUUID())) {
         return false;
      }
      if (this.assignedCultureId == null) {
         return false;
      }
      return BountyEligibilityService.isStillValidTarget(level, player, this.assignedCultureId);
   }

   boolean canAttemptArrest(ServerPlayer player) {
      if (player.getHealth() / player.getMaxHealth() <= 0.25F) {
         return true;
      }
      return this.captureMeter >= 1.0F;
   }

   void throwBola(ServerPlayer target) {
      if (!(this.level() instanceof ServerLevel server)) {
         return;
      }
      long now = server.getGameTime();
      if (now < this.bolaReadyAtGameTime) {
         return;
      }
      BountyHunterBolaEntity bola = new BountyHunterBolaEntity(server, this);
      bola.setIntendedTarget(target.getUUID());
      Vec3 dir = target.getEyePosition().subtract(this.getEyePosition()).normalize();
      bola.shoot(dir.x, dir.y + 0.08, dir.z, 1.15F, 1.5F);
      server.addFreshEntity(bola);
      this.playAnim(ANIM_BOLA, 16);
      this.playSound(SoundEvents.TRIDENT_THROW.value(), 0.9F, 0.7F);
      this.bolaReadyAtGameTime = now + bolaCooldownTicks();
   }

   private static long bolaCooldownTicks() {
      try {
         return Math.max(40L, MillenaireServerConfig.SERVER.bountyHunterBolaCooldownSeconds.getAsInt() * 20L);
      } catch (IllegalStateException e) {
         return 240L;
      }
   }

   static int arrestChannelTicks() {
      try {
         return Math.max(10, MillenaireServerConfig.SERVER.bountyHunterArrestChannelSeconds.getAsInt() * 20);
      } catch (IllegalStateException e) {
         return 40;
      }
   }

   @Override
   public void handleEntityEvent(byte id) {
      if (id == 4) {
         this.clientAnimTicks = 12;
         this.entityData.set(DATA_ANIM, ANIM_BATON);
         this.entityData.set(DATA_ANIM_TICKS, 12);
      } else if (id == 5) {
         this.clientAnimTicks = 16;
         this.entityData.set(DATA_ANIM, ANIM_BOLA);
         this.entityData.set(DATA_ANIM_TICKS, 16);
      } else if (id == 6) {
         this.clientAnimTicks = arrestChannelTicks();
         this.entityData.set(DATA_ANIM, ANIM_HANDCUFF);
         this.entityData.set(DATA_ANIM_TICKS, arrestChannelTicks());
      } else {
         super.handleEntityEvent(id);
      }
   }

   @Override
   public void die(DamageSource source) {
      super.die(source);
      if (this.level() instanceof ServerLevel server) {
         BountyHunterDispatchManager.onHunterDefeated(server, this);
      }
   }

   @Override
   public boolean removeWhenFarAway(double distanceToClosestPlayer) {
      return false;
   }

   @Override
   public void checkDespawn() {
      // Event-driven NPC — never despawn by idle distance rules.
   }

   @Override
   protected SoundEvent getAmbientSound() {
      return SoundEvents.PILLAGER_AMBIENT;
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return SoundEvents.PILLAGER_HURT;
   }

   @Override
   protected SoundEvent getDeathSound() {
      return SoundEvents.PILLAGER_DEATH;
   }

   @Override
   protected void playStepSound(BlockPos pos, BlockState state) {
      this.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.25F, 0.7F);
   }

   @Nullable
   @Override
   public SpawnGroupData finalizeSpawn(
      ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnData
   ) {
      return super.finalizeSpawn(level, difficulty, spawnType, spawnData);
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      if (this.contractId != null) {
         tag.putUUID("contract", this.contractId);
      }
      if (this.targetPlayerId != null) {
         tag.putUUID("target", this.targetPlayerId);
      }
      if (this.assignedCultureId != null) {
         tag.putString("culture", this.assignedCultureId.toString());
      }
      tag.putString("phase", this.getHunterPhase().name());
      tag.putLong("bolaReady", this.bolaReadyAtGameTime);
      tag.putFloat("captureMeter", this.captureMeter);
      tag.putBoolean("warned", this.warnedThisEncounter);
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      if (tag.hasUUID("contract")) {
         this.contractId = tag.getUUID("contract");
      }
      if (tag.hasUUID("target")) {
         this.targetPlayerId = tag.getUUID("target");
      }
      if (tag.contains("culture")) {
         this.assignedCultureId = ResourceLocation.tryParse(tag.getString("culture"));
      }
      try {
         this.setHunterPhase(BountyHunterPhase.valueOf(tag.getString("phase")));
      } catch (Exception ignored) {
         this.setHunterPhase(BountyHunterPhase.TRAVELLING);
      }
      this.bolaReadyAtGameTime = tag.getLong("bolaReady");
      this.captureMeter = tag.getFloat("captureMeter");
      this.warnedThisEncounter = tag.getBoolean("warned");
   }

   /** Primary behaviour state machine. */
   static final class BountyHunterBehaviorGoal extends Goal {
      private final BountyHunterEntity hunter;
      private int repathCooldown;

      BountyHunterBehaviorGoal(BountyHunterEntity hunter) {
         this.hunter = hunter;
         this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
      }

      @Override
      public boolean canUse() {
         return this.hunter.targetPlayerId != null && this.hunter.isAlive();
      }

      @Override
      public boolean canContinueToUse() {
         return this.canUse();
      }

      @Override
      public void tick() {
         if (!(this.hunter.level() instanceof ServerLevel server)) {
            return;
         }
         ServerPlayer target = server.getServer().getPlayerList().getPlayer(this.hunter.targetPlayerId);
         BountyHunterPhase phase = this.hunter.getHunterPhase();

         if (target == null || target.level().dimension() != server.dimension()) {
            this.hunter.setHunterPhase(BountyHunterPhase.SEARCHING);
            this.hunter.setTarget(null);
            this.hunter.getNavigation().stop();
            this.hunter.playAnim(ANIM_IDLE, 0);
            return;
         }
         if (!this.hunter.isAssignedValidTarget(server, target)) {
            this.hunter.setHunterPhase(BountyHunterPhase.DESPAWNING);
            this.hunter.discard();
            return;
         }

         double distSq = this.hunter.distanceToSqr(target);
         double dist = Math.sqrt(distSq);
         boolean los = this.hunter.hasLineOfSight(target);

         switch (phase) {
            case TRAVELLING, SEARCHING, TARGET_LOST, INVESTIGATING -> this.tickTravelSearch(server, target, dist, los);
            case OBSERVING -> this.tickObserve(target);
            case APPROACHING -> this.tickApproach(server, target, dist, los);
            case PURSUING -> this.tickPursue(server, target, dist, los);
            case RESTRAINING -> this.tickRestrain(server, target, dist, los);
            default -> {
               if (phase.isActivePursuit()) {
                  this.hunter.setHunterPhase(BountyHunterPhase.TRAVELLING);
               }
            }
         }
      }

      private void tickTravelSearch(ServerLevel server, ServerPlayer target, double dist, boolean los) {
         this.hunter.setTarget(null);
         if (dist < 28.0 && los) {
            this.hunter.setHunterPhase(BountyHunterPhase.OBSERVING);
            this.hunter.observeTicks = 35;
            this.hunter.playAnim(ANIM_OBSERVE, 35);
            this.hunter.getNavigation().stop();
            this.hunter.getLookControl().setLookAt(target, 30.0F, 30.0F);
            this.hunter.playSound(SoundEvents.PILLAGER_AMBIENT, 1.0F, 0.6F);
            return;
         }
         this.hunter.playAnim(dist > 16.0 ? ANIM_RUN : ANIM_WALK, 0);
         this.moveToward(target, dist > 20.0 ? 1.15 : 0.95);
         if (!los) {
            this.hunter.lostTicks++;
            if (this.hunter.lostTicks > 100) {
               this.hunter.setHunterPhase(BountyHunterPhase.TARGET_LOST);
            }
         } else {
            this.hunter.lostTicks = 0;
            this.hunter.setHunterPhase(BountyHunterPhase.TRAVELLING);
         }
      }

      private void tickObserve(ServerPlayer target) {
         this.hunter.getNavigation().stop();
         this.hunter.getLookControl().setLookAt(target, 40.0F, 40.0F);
         this.hunter.observeTicks--;
         if (this.hunter.observeTicks <= 0) {
            this.hunter.setHunterPhase(BountyHunterPhase.APPROACHING);
            if (!this.hunter.warnedThisEncounter && serverTime() >= this.hunter.warnCooldownUntil) {
               target.displayClientMessage(Component.translatable("millenaire.bounty_hunter.warning"), true);
               target.sendSystemMessage(Component.translatable("millenaire.bounty_hunter.warning"));
               this.hunter.playAnim(ANIM_WARNING, 30);
               this.hunter.playSound(SoundEvents.PILLAGER_CELEBRATE, 0.9F, 0.55F);
               this.hunter.warnedThisEncounter = true;
               this.hunter.warnCooldownUntil = serverTime() + 200L;
            }
         }
      }

      private void tickApproach(ServerLevel server, ServerPlayer target, double dist, boolean los) {
         this.hunter.getLookControl().setLookAt(target, 30.0F, 30.0F);
         if (!los || dist > 36.0) {
            this.hunter.setHunterPhase(BountyHunterPhase.TARGET_LOST);
            return;
         }
         if (target.getDeltaMovement().horizontalDistanceSqr() > 0.01 || dist > 10.0) {
            this.hunter.setHunterPhase(BountyHunterPhase.PURSUING);
            return;
         }
         this.hunter.playAnim(ANIM_WALK, 0);
         this.moveToward(target, 0.85);
         if (dist <= 6.0) {
            this.hunter.setHunterPhase(BountyHunterPhase.PURSUING);
         }
      }

      private void tickPursue(ServerLevel server, ServerPlayer target, double dist, boolean los) {
         this.hunter.setTarget(target);
         this.hunter.getLookControl().setLookAt(target, 40.0F, 40.0F);
         if (!los && dist > 24.0) {
            this.hunter.lostTicks++;
            if (this.hunter.lostTicks > 80) {
               this.hunter.setHunterPhase(BountyHunterPhase.TARGET_LOST);
               this.hunter.setTarget(null);
               return;
            }
         } else {
            this.hunter.lostTicks = 0;
         }

         this.hunter.playAnim(ANIM_RUN, 0);
         this.moveToward(target, 1.25);

         if (los && dist >= BOLA_MIN_RANGE && dist <= BOLA_MAX_RANGE && server.getGameTime() >= this.hunter.bolaReadyAtGameTime) {
            this.hunter.throwBola(target);
         }

         if (dist <= this.hunter.getBbWidth() + ARREST_RANGE && los) {
            if (this.hunter.canAttemptArrest(target)) {
               this.hunter.setHunterPhase(BountyHunterPhase.RESTRAINING);
               this.hunter.restrainChannelTicks = arrestChannelTicks();
               this.hunter.playAnim(ANIM_HANDCUFF, this.hunter.restrainChannelTicks);
               this.hunter.level().broadcastEntityEvent(this.hunter, (byte)6);
               this.hunter.playSound(SoundEvents.CHAIN_PLACE, 1.0F, 0.85F);
               this.hunter.getNavigation().stop();
            } else if (this.hunter.meleeCooldown <= 0) {
               if (this.hunter.doHurtTarget(target)) {
                  this.hunter.meleeCooldown = 16;
               }
            }
         }
      }

      private void tickRestrain(ServerLevel server, ServerPlayer target, double dist, boolean los) {
         this.hunter.getNavigation().stop();
         this.hunter.getLookControl().setLookAt(target, 40.0F, 40.0F);
         if (!los || dist > ARREST_RANGE + 1.25 || !this.hunter.canAttemptArrest(target)) {
            this.hunter.setHunterPhase(BountyHunterPhase.PURSUING);
            this.hunter.restrainChannelTicks = 0;
            return;
         }
         this.hunter.restrainChannelTicks--;
         if (this.hunter.restrainChannelTicks <= 0) {
            boolean ok = BountyHunterCaptureService.tryCompleteArrest(server, this.hunter, target);
            if (!ok) {
               this.hunter.setHunterPhase(BountyHunterPhase.PURSUING);
            }
         }
      }

      private void moveToward(ServerPlayer target, double speed) {
         if (this.repathCooldown > 0) {
            this.repathCooldown--;
            return;
         }
         PathNavigation nav = this.hunter.getNavigation();
         nav.moveTo(target, speed);
         this.repathCooldown = 10;
      }

      private long serverTime() {
         return this.hunter.level().getGameTime();
      }
   }
}
