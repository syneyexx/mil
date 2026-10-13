package org.millenaire.monster;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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

/** Ordinary hostile monster with shared attack-animation signalling for native models. */
public abstract class NativeMonster extends Monster {
   public static final byte ATTACK_EVENT = 60;
   private static final EntityDataAccessor<Byte> DATA_ANIM =
      SynchedEntityData.defineId(NativeMonster.class, EntityDataSerializers.BYTE);

   public static final byte ANIM_NONE = 0;
   public static final byte ANIM_ARRIVAL = 1;

   private int clientAttackTicks;

   protected NativeMonster(EntityType<? extends NativeMonster> type, Level level) {
      super(type, level);
   }

   public abstract String assetId();

   public static AttributeSupplier.Builder baseAttributes(double health, double damage, double armor, double speed) {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, health)
         .add(Attributes.ATTACK_DAMAGE, damage)
         .add(Attributes.ARMOR, armor)
         .add(Attributes.MOVEMENT_SPEED, speed)
         .add(Attributes.FOLLOW_RANGE, 40)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
         .add(Attributes.STEP_HEIGHT, 1.0);
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(DATA_ANIM, ANIM_NONE);
   }

   @Override
   protected void registerGoals() {
      goalSelector.addGoal(0, new FloatGoal(this));
      goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
      goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
      goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
      goalSelector.addGoal(9, new RandomLookAroundGoal(this));
      targetSelector.addGoal(1, new HurtByTargetGoal(this));
      targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
   }

   @Override
   public void onAddedToLevel() {
      super.onAddedToLevel();
      if (!level().isClientSide) {
         setAnimState(ANIM_ARRIVAL);
      }
   }

   @Override
   public void tick() {
      super.tick();
      if (clientAttackTicks > 0) {
         clientAttackTicks--;
      }
      if (!level().isClientSide && getAnimState() == ANIM_ARRIVAL && tickCount >= 60) {
         setAnimState(ANIM_NONE);
      }
   }

   @Override
   public boolean doHurtTarget(Entity target) {
      boolean hit = super.doHurtTarget(target);
      if (hit) {
         level().broadcastEntityEvent(this, ATTACK_EVENT);
      }
      return hit;
   }

   @Override
   public void handleEntityEvent(byte id) {
      if (id == ATTACK_EVENT) {
         clientAttackTicks = 16;
      } else {
         super.handleEntityEvent(id);
      }
   }

   /** Seconds into the authored attack clip, or -1 when inactive. */
   public float attackTime(float partialTick) {
      return clientAttackTicks > 0 ? (16 - clientAttackTicks + partialTick) / 20.0F : -1.0F;
   }

   public boolean isPlayingArrival() {
      return getAnimState() == ANIM_ARRIVAL || tickCount < 60;
   }

   protected void setAnimState(byte state) {
      entityData.set(DATA_ANIM, state);
   }

   public byte getAnimState() {
      return entityData.get(DATA_ANIM);
   }

   public void triggerAttackAnimation() {
      if (!level().isClientSide) {
         level().broadcastEntityEvent(this, ATTACK_EVENT);
      }
   }

   @Override
   protected abstract int getBaseExperienceReward();
}
