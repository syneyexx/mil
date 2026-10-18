package org.millenaire.labyrinth.entity;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.millenaire.labyrinth.LabyrinthKeys;

/**
 * De Doolhofsoeverein — monumental god-presence of the Labyrinth (Audience nave).
 * Not a slash-boss by default: immobile awe figure that watches and acknowledges.
 * Visual model is 9 blocks tall; collision is slightly shorter for gameplay reach.
 */
public class DoolhofsoevereinEntity extends PathfinderMob {
   public static final ResourceLocation TYPE_ID =
      ResourceLocation.fromNamespaceAndPath(LabyrinthKeys.MODID, "doolhofsoeverein");

   /** Visual model height (blocks) — matches Blockbench pack (144 units). */
   public static final float MODEL_HEIGHT_BLOCKS = 9.0F;
   /** Collision height — allows looking up / projectiles without full 9-block wall. */
   public static final float COLLISION_HEIGHT = 7.5F;
   public static final float COLLISION_WIDTH = 2.4F;

   private boolean anchored;

   public DoolhofsoevereinEntity(EntityType<? extends DoolhofsoevereinEntity> type, Level level) {
      super(type, level);
      this.xpReward = 0;
      this.setPersistenceRequired();
      this.setNoGravity(true);
   }

   @SuppressWarnings("unchecked")
   public static Optional<EntityType<DoolhofsoevereinEntity>> entityType() {
      return BuiltInRegistries.ENTITY_TYPE.getOptional(TYPE_ID).map(t -> (EntityType<DoolhofsoevereinEntity>) t);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return PathfinderMob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 500.0)
         .add(Attributes.MOVEMENT_SPEED, 0.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
         .add(Attributes.ARMOR, 20.0)
         .add(Attributes.FOLLOW_RANGE, 48.0)
         .add(Attributes.STEP_HEIGHT, 1.0);
   }

   @Override
   protected void registerGoals() {
      // Immobile: look only — no stroll, no attack.
      this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 32.0F, 1.0F));
      this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
   }

   @Override
   public void tick() {
      super.tick();
      this.setDeltaMovement(0, 0, 0);
      if (!this.level().isClientSide && this.anchored) {
         // Soft ambient heartbeat near the sovereign.
         if (this.tickCount % 80 == 0) {
            this.level().playSound(
               null,
               this.blockPosition(),
               SoundEvents.WARDEN_HEARTBEAT,
               SoundSource.AMBIENT,
               0.35F,
               0.45F
            );
         }
      }
   }

   public void anchorInPlace() {
      this.anchored = true;
      this.setNoAi(false);
      this.getNavigation().stop();
   }

   public boolean isAnchored() {
      return this.anchored;
   }

   @Override
   public boolean isPushable() {
      return false;
   }

   @Override
   public boolean canBeCollidedWith() {
      return true;
   }

   @Override
   protected void doPush(net.minecraft.world.entity.Entity entity) {
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      // God presence: not meant as a farmable boss. Resists casual damage.
      if (source.getEntity() instanceof Player) {
         if (!this.level().isClientSide && this.level() instanceof ServerLevel server) {
            server.playSound(null, this.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.NEUTRAL, 0.8F, 0.5F);
         }
         return false;
      }
      return super.hurt(source, amount * 0.05F);
   }

   @Override
   public InteractionResult mobInteract(Player player, InteractionHand hand) {
      if (!this.level().isClientSide) {
         player.displayClientMessage(Component.translatable("labyrinth.soverein.interact"), true);
         this.level().playSound(
            null,
            this.blockPosition(),
            SoundEvents.BEACON_AMBIENT,
            SoundSource.NEUTRAL,
            0.7F,
            0.6F
         );
      }
      return InteractionResult.sidedSuccess(this.level().isClientSide);
   }

   @Override
   public boolean removeWhenFarAway(double distanceToClosestPlayer) {
      return false;
   }

   @Override
   public boolean shouldDespawnInPeaceful() {
      return false;
   }

   @Override
   protected Component getTypeName() {
      return Component.translatable("entity.millenaire.doolhofsoeverein");
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putBoolean("Anchored", this.anchored);
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      this.anchored = tag.getBoolean("Anchored");
   }
}
