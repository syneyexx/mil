package org.millenaire.monster.pack;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
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
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Shared baseline hostile ground mobs. Cosmetics (wings, staff, blade) are modeled, not equipment. */
public class PackMonster extends Monster implements GeoEntity {
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private final MobKind kind;

   public PackMonster(EntityType<? extends PackMonster> type, Level level, MobKind kind) {
      super(type, level);
      this.kind = kind;
      this.xpReward = kind.xp;
   }

   public MobKind kind() {
      return kind;
   }

   public static AttributeSupplier.Builder attributes(MobKind kind) {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, kind.hp)
         .add(Attributes.ATTACK_DAMAGE, kind.attack)
         .add(Attributes.MOVEMENT_SPEED, kind.speed)
         .add(Attributes.ARMOR, kind.armor)
         .add(Attributes.FOLLOW_RANGE, 24)
         .add(Attributes.KNOCKBACK_RESISTANCE, kind.knockback);
   }

   @Override
   protected void registerGoals() {
      // Called by the superclass constructor: do not read 'kind' here.
      goalSelector.addGoal(0, new FloatGoal(this));
      goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
      goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
      goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
      goalSelector.addGoal(7, new RandomLookAroundGoal(this));
      targetSelector.addGoal(1, new HurtByTargetGoal(this));
      targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
   }

   @Override
   public boolean doHurtTarget(Entity target) {
      if (!level().isClientSide) {
         triggerAnim("action", "attack");
      }
      return super.doHurtTarget(target);
   }

   @Override
   public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
      String prefix = "animation." + kind.id + ".";
      controllers.add(new AnimationController<>(this, "movement", 4, state -> {
         if (isDeadOrDying()) {
            return PlayState.STOP;
         }
         return state.setAndContinue(RawAnimation.begin().thenLoop(prefix + (state.isMoving() ? "walk" : "idle")));
      }));
      controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
         .triggerableAnim("attack", RawAnimation.begin().thenPlay(prefix + "attack")));
      // Death uses a separate controller; the renderer suppresses vanilla death tilt.
      controllers.add(new AnimationController<>(this, "death", 0, state -> {
         if (isDeadOrDying()) {
            return state.setAndContinue(RawAnimation.begin().thenPlayAndHold(prefix + "death"));
         }
         return PlayState.STOP;
      }));
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return cache;
   }

   @Override
   protected SoundEvent getAmbientSound() {
      return switch (kind) {
         case HORSE_WIZARD, JUNRA -> SoundEvents.WITCH_AMBIENT;
         case PEGASUS -> SoundEvents.HORSE_ANGRY;
         case ZUZU, CTHULU -> SoundEvents.RAVAGER_AMBIENT;
         case BIRDMAN, NEBULA -> SoundEvents.PHANTOM_AMBIENT;
         case SDA -> SoundEvents.ZOMBIE_AMBIENT;
         case ZAVA -> SoundEvents.SPIDER_AMBIENT;
         case DISCD -> SoundEvents.SKELETON_AMBIENT;
      };
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return SoundEvents.HOSTILE_HURT;
   }

   @Override
   protected SoundEvent getDeathSound() {
      return SoundEvents.HOSTILE_DEATH;
   }
}
