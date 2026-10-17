package org.millenaire.labyrinth.entity;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.labyrinth.LabyrinthKeys;

/**
 * Bound inhabitant of the Labyrinth. Extends {@link PathfinderMob} (not Monster):
 * hospitable by default, hostile only after attitude escalates to {@link GebondeneAttitude#JACHT}.
 */
public class GebondeneEntity extends PathfinderMob {
   public static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath(LabyrinthKeys.MODID, "gebondene");

   private static final EntityDataAccessor<String> DATA_KIND =
      SynchedEntityData.defineId(GebondeneEntity.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<String> DATA_ATTITUDE =
      SynchedEntityData.defineId(GebondeneEntity.class, EntityDataSerializers.STRING);

   private int playerHitCount;

   public GebondeneEntity(EntityType<? extends GebondeneEntity> type, Level level) {
      super(type, level);
      this.xpReward = GebondeneKind.RAMMELAAR.xpReward();
   }

   @SuppressWarnings("unchecked")
   public static Optional<EntityType<GebondeneEntity>> entityType() {
      return BuiltInRegistries.ENTITY_TYPE.getOptional(TYPE_ID).map(t -> (EntityType<GebondeneEntity>) t);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return PathfinderMob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 48.0)
         .add(Attributes.ATTACK_DAMAGE, 8.0)
         .add(Attributes.MOVEMENT_SPEED, 0.28)
         .add(Attributes.ARMOR, 7.0)
         .add(Attributes.FOLLOW_RANGE, 24.0)
         .add(Attributes.STEP_HEIGHT, 1.0);
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(DATA_KIND, GebondeneKind.RAMMELAAR.id());
      builder.define(DATA_ATTITUDE, GebondeneAttitude.GASTVRIJ.name());
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.05, false) {
         @Override
         public boolean canUse() {
            return GebondeneEntity.this.getAttitude() == GebondeneAttitude.JACHT && super.canUse();
         }

         @Override
         public boolean canContinueToUse() {
            return GebondeneEntity.this.getAttitude() == GebondeneAttitude.JACHT && super.canContinueToUse();
         }
      });
      this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.85));
      this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
      this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
      rebuildTargetGoals();
   }

   private void rebuildTargetGoals() {
      this.targetSelector.removeAllGoals(goal -> true);
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
         @Override
         public void start() {
            super.start();
            if (GebondeneEntity.this.getAttitude() == GebondeneAttitude.GASTVRIJ) {
               GebondeneEntity.this.setAttitude(GebondeneAttitude.GEALARMEERD);
            } else if (GebondeneEntity.this.getAttitude() == GebondeneAttitude.GEALARMEERD) {
               GebondeneEntity.this.setAttitude(GebondeneAttitude.JACHT);
            }
         }
      });
      if (getAttitude() == GebondeneAttitude.JACHT) {
         this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
      }
   }

   public GebondeneKind getKind() {
      return GebondeneKind.byIdOrDefault(this.entityData.get(DATA_KIND), GebondeneKind.RAMMELAAR);
   }

   public void setKind(GebondeneKind kind) {
      if (kind == null) {
         kind = GebondeneKind.RAMMELAAR;
      }
      this.entityData.set(DATA_KIND, kind.id());
      this.xpReward = kind.xpReward();
      applyKindAttributes(kind);
      refreshDimensions();
   }

   public GebondeneAttitude getAttitude() {
      return GebondeneAttitude.byName(this.entityData.get(DATA_ATTITUDE));
   }

   public void setAttitude(GebondeneAttitude attitude) {
      if (attitude == null) {
         attitude = GebondeneAttitude.GASTVRIJ;
      }
      GebondeneAttitude previous = getAttitude();
      this.entityData.set(DATA_ATTITUDE, attitude.name());
      if (previous != attitude && !this.level().isClientSide) {
         rebuildTargetGoals();
         if (attitude != GebondeneAttitude.JACHT) {
            this.setTarget(null);
         }
      }
   }

   private void applyKindAttributes(GebondeneKind kind) {
      setAttr(Attributes.MAX_HEALTH, kind.health());
      setAttr(Attributes.ATTACK_DAMAGE, kind.damage());
      setAttr(Attributes.MOVEMENT_SPEED, kind.speed());
      setAttr(Attributes.ARMOR, kind.armor());
      if (this.getHealth() > kind.health()) {
         this.setHealth((float) kind.health());
      } else if (this.getHealth() <= 0.0F || this.tickCount < 5) {
         this.setHealth((float) kind.health());
      }
   }

   private void setAttr(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
      AttributeInstance instance = this.getAttribute(attribute);
      if (instance != null) {
         instance.setBaseValue(value);
      }
   }

   @Override
   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (DATA_KIND.equals(key)) {
         refreshDimensions();
      }
   }

   @Override
   protected EntityDimensions getDefaultDimensions(Pose pose) {
      GebondeneKind kind = getKind();
      return EntityDimensions.scalable(kind.width(), kind.height()).withEyeHeight(kind.height() * 0.85F);
   }

   @Override
   protected InteractionResult mobInteract(Player player, InteractionHand hand) {
      if (hand != InteractionHand.MAIN_HAND) {
         return InteractionResult.PASS;
      }
      if (getAttitude() == GebondeneAttitude.GASTVRIJ) {
         if (!this.level().isClientSide) {
            try {
               if (!MillenaireServerConfig.SERVER.labyrinthFrenemyInteractions.getAsBoolean()) {
                  return InteractionResult.PASS;
               }
            } catch (IllegalStateException ignored) {
               // config not loaded
            }
            return GebondeneInteractions.tryInteract(this, player);
         }
         return InteractionResult.sidedSuccess(true);
      }
      if (getAttitude() == GebondeneAttitude.GEALARMEERD && !this.level().isClientSide) {
         player.displayClientMessage(Component.translatable("labyrinth.attitude.alarmed"), true);
         this.level().playSound(
            null, this.getX(), this.getY(), this.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 0.8F
         );
         return InteractionResult.CONSUME;
      }
      return super.mobInteract(player, hand);
   }

   @Override
   public void aiStep() {
      super.aiStep();
      if (!this.level().isClientSide) {
         ArchitectCombat.tick(this);
      } else if (getKind() == GebondeneKind.ECHO && this.tickCount % 8 == 0) {
         this.level().addParticle(
            net.minecraft.core.particles.ParticleTypes.END_ROD,
            this.getX(),
            this.getY() + 1.0,
            this.getZ(),
            0.0,
            0.01,
            0.0
         );
      }
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (source.getEntity() instanceof Player) {
         escalateFromPlayerHit(amount);
      }
      return super.hurt(source, amount);
   }

   @Override
   protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource source, boolean recentlyHit) {
      super.dropCustomDeathLoot(level, source, recentlyHit);
      GebondeneKind kind = getKind();
      if (kind == GebondeneKind.ARCHITECT) {
         this.spawnAtLocation(new net.minecraft.world.item.ItemStack(org.millenaire.item.ModItems.CONFIGURATIE.get()));
         this.spawnAtLocation(new net.minecraft.world.item.ItemStack(org.millenaire.item.ModItems.DENIER_OR.get(), 4));
      } else if (kind.tier() == GebondeneKind.Tier.LEADER || kind.tier() == GebondeneKind.Tier.GATE) {
         this.spawnAtLocation(new net.minecraft.world.item.ItemStack(org.millenaire.item.ModItems.DENIER.get(), 4 + this.random.nextInt(6)));
      } else if (this.random.nextFloat() < 0.35F) {
         this.spawnAtLocation(new net.minecraft.world.item.ItemStack(org.millenaire.item.ModItems.DENIER.get(), 1 + this.random.nextInt(3)));
      }
   }

   private void escalateFromPlayerHit(float amount) {
      this.playerHitCount++;
      GebondeneAttitude attitude = getAttitude();
      if (attitude == GebondeneAttitude.JACHT) {
         return;
      }
      if (amount > 4.0F || this.playerHitCount >= 2) {
         setAttitude(GebondeneAttitude.JACHT);
      } else if (attitude == GebondeneAttitude.GASTVRIJ) {
         setAttitude(GebondeneAttitude.GEALARMEERD);
      } else {
         setAttitude(GebondeneAttitude.JACHT);
      }
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putString("Kind", getKind().id());
      tag.putString("Attitude", getAttitude().name());
      tag.putInt("PlayerHits", this.playerHitCount);
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      if (tag.contains("Kind")) {
         setKind(GebondeneKind.byIdOrDefault(tag.getString("Kind"), GebondeneKind.RAMMELAAR));
      }
      if (tag.contains("Attitude")) {
         setAttitude(GebondeneAttitude.byName(tag.getString("Attitude")));
      }
      this.playerHitCount = tag.getInt("PlayerHits");
   }

   /** PathfinderMob is not Monster; never block sleep. Explicit for clarity. */
   public boolean isPreventingPlayerRest(Player player) {
      return false;
   }

   @Override
   public boolean removeWhenFarAway(double distanceToClosestPlayer) {
      if (this.level().dimension().equals(LabyrinthKeys.LABYRINTH_LEVEL)) {
         return false;
      }
      return super.removeWhenFarAway(distanceToClosestPlayer);
   }

   @Override
   protected Component getTypeName() {
      return Component.translatable("labyrinth.gebondene." + getKind().displayKeySuffix());
   }

   @Override
   public void die(DamageSource damageSource) {
      if (!this.level().isClientSide && damageSource.getEntity() instanceof Player player && this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
         LabyrinthOrdeData data = LabyrinthOrdeData.get(serverLevel);
         data.setHostile(player.getUUID(), true);
         int[] cell = org.millenaire.labyrinth.gen.LabyrinthMaze.globalCellFromBlock(this.blockPosition().getX(), this.blockPosition().getZ());
         int regionX = org.millenaire.labyrinth.gen.LabyrinthMaze.regionCoordFromGlobalCell(cell[0]);
         int regionZ = org.millenaire.labyrinth.gen.LabyrinthMaze.regionCoordFromGlobalCell(cell[1]);
         data.addReputation(player.getUUID(), regionX, regionZ, -25);
         // Escalate nearby Bound Ones in the region after a kill.
         for (GebondeneEntity nearby : serverLevel.getEntitiesOfClass(GebondeneEntity.class, this.getBoundingBox().inflate(48.0))) {
            if (nearby.getAttitude() != GebondeneAttitude.JACHT) {
               nearby.setAttitude(GebondeneAttitude.JACHT);
            }
         }
         player.displayClientMessage(Component.translatable("labyrinth.attitude.hunting"), true);
      }
      super.die(damageSource);
   }
}
