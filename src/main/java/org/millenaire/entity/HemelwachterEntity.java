package org.millenaire.entity;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.millenaire.advancement.MillAdvancements;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.item.ModItems;
import org.millenaire.worldboss.ActiveWorldBossEvent;
import org.millenaire.worldboss.WorldBossEligibility;
import org.millenaire.worldboss.WorldBossEventPlanner;
import org.millenaire.worldboss.WorldBossEventState;
import org.millenaire.worldboss.WorldBossAnnouncementService;

/**
 * Hemelwachter — neutral aerial world boss until player-attributed provocation.
 */
public class HemelwachterEntity extends Monster {
   public static final int ANIM_IDLE = 0;
   public static final int ANIM_ARRIVE = 1;
   public static final int ANIM_PROVOKE = 2;
   public static final int ANIM_BEAM = 3;
   public static final int ANIM_BUFFET = 4;
   public static final int ANIM_VOLLEY = 5;
   public static final int ANIM_DEPART = 6;
   public static final int ANIM_DEATH = 7;

   private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(HemelwachterEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(HemelwachterEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> DATA_ANIM_TICKS = SynchedEntityData.defineId(HemelwachterEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Boolean> DATA_PROVOKED = SynchedEntityData.defineId(HemelwachterEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(HemelwachterEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> DATA_HOVER_Y = SynchedEntityData.defineId(HemelwachterEntity.class, EntityDataSerializers.FLOAT);

   private final ServerBossEvent bossEvent = new ServerBossEvent(
      Component.translatable("entity.millenaire.hemelwachter"),
      BossEvent.BossBarColor.YELLOW,
      BossEvent.BossBarOverlay.NOTCHED_10
   );

   @Nullable
   private UUID eventId;
   @Nullable
   private ResourceLocation bossDefinitionId;
   @Nullable
   private UUID provokerId;
   private final Set<UUID> participants = new HashSet<>();
   private int neutralTicks;
   private int engageGraceTicks;
   private int combatIdleTicks;
   private int attackCooldown;
   private int beamChargeTicks;
   private Vec3 beamDirection = Vec3.ZERO;
   private boolean beamLocked;
   private int departTicks;
   private boolean departureFinished;
   private boolean rewardsGrantedLocal;
   private float targetHoverY;
   private int clientAnimTicks;
   private float wingPhase;
   private float ringPhase;

   public HemelwachterEntity(EntityType<? extends HemelwachterEntity> type, Level level) {
      super(type, level);
      this.moveControl = new FlyingMoveControl(this, 12, true);
      this.setNoGravity(true);
      this.xpReward = 0;
      this.bossEvent.setVisible(false);
   }

   public static AttributeSupplier.Builder createAttributes() {
      double health = 1200.0;
      double armor = 12.0;
      double toughness = 6.0;
      try {
         health = MillenaireServerConfig.SERVER.worldBossHemelwachterHealth.get();
         armor = MillenaireServerConfig.SERVER.worldBossHemelwachterArmor.get();
         toughness = MillenaireServerConfig.SERVER.worldBossHemelwachterToughness.get();
      } catch (IllegalStateException ignored) {
      }
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, health)
         .add(Attributes.MOVEMENT_SPEED, 0.28)
         .add(Attributes.FLYING_SPEED, 0.34)
         .add(Attributes.FOLLOW_RANGE, 64.0)
         .add(Attributes.ATTACK_DAMAGE, 16.0)
         .add(Attributes.ARMOR, armor)
         .add(Attributes.ARMOR_TOUGHNESS, toughness)
         .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
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
      this.goalSelector.addGoal(1, new HemelCombatGoal(this));
      this.goalSelector.addGoal(2, new HemelHoverGoal(this));
      this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0F));
   }

   @Override
   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(DATA_STATE, WorldBossEventState.NEUTRAL.ordinal());
      builder.define(DATA_ANIM, ANIM_IDLE);
      builder.define(DATA_ANIM_TICKS, 0);
      builder.define(DATA_PROVOKED, false);
      builder.define(DATA_PHASE, 0);
      builder.define(DATA_HOVER_Y, 0.0F);
   }

   public void bindWorldBossEvent(UUID eventId, ResourceLocation definitionId) {
      this.eventId = eventId;
      this.bossDefinitionId = definitionId;
   }

   public void beginArrival(double hoverY) {
      this.targetHoverY = (float) hoverY;
      this.entityData.set(DATA_HOVER_Y, this.targetHoverY);
      this.setEventState(WorldBossEventState.ARRIVING);
      this.playAnim(ANIM_ARRIVE, 100);
   }

   public void beginDeparture(String reason) {
      if (this.getEventState() == WorldBossEventState.DEPARTING || this.getEventState() == WorldBossEventState.DEFEATED) {
         return;
      }
      this.setEventState(WorldBossEventState.DEPARTING);
      this.departTicks = 80;
      this.playAnim(ANIM_DEPART, 80);
      this.bossEvent.setVisible(false);
      this.bossEvent.removeAllPlayers();
      this.setTarget(null);
      if (!this.level().isClientSide) {
         WorldBossAnnouncementService.announceKey(
            (ServerLevel) this.level(),
            this.blockPosition(),
            "millenaire.worldboss.hemelwachter.depart"
         );
      }
   }

   public boolean hasFinishedDeparture() {
      return this.departureFinished;
   }

   public void setEventState(WorldBossEventState state) {
      this.entityData.set(DATA_STATE, state.ordinal());
   }

   @Nullable
   public WorldBossEventState getEventState() {
      int ord = this.entityData.get(DATA_STATE);
      WorldBossEventState[] values = WorldBossEventState.values();
      if (ord < 0 || ord >= values.length) {
         return WorldBossEventState.NEUTRAL;
      }
      return values[ord];
   }

   public boolean isProvoked() {
      return this.entityData.get(DATA_PROVOKED);
   }

   public int getCombatPhase() {
      return this.entityData.get(DATA_PHASE);
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

   @Nullable
   public UUID getProvokerId() {
      return this.provokerId;
   }

   public Set<UUID> getParticipantIds() {
      return this.participants;
   }

   @Override
   public void travel(Vec3 travelVector) {
      if (this.isControlledByLocalInstance()) {
         this.moveRelative(this.getSpeed() * 0.15F, travelVector);
         this.move(MoverType.SELF, this.getDeltaMovement());
         this.setDeltaMovement(this.getDeltaMovement().scale(0.90));
      }
      this.calculateEntityAnimation(false);
   }

   @Override
   public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
      return false;
   }

   @Override
   protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
   }

   @Override
   public boolean isSensitiveToWater() {
      return false;
   }

   @Override
   public boolean fireImmune() {
      return true;
   }

   @Override
   public boolean hurt(DamageSource source, float amount) {
      if (this.getEventState() == WorldBossEventState.DEPARTING || this.getEventState() == WorldBossEventState.ARRIVING) {
         return false;
      }
      UUID attributed = attributePlayer(source);
      if (!this.isProvoked()) {
         if (attributed == null) {
            // Environment / unattributed — no aggression, no damage consumption as provoke
            return false;
         }
         this.provoke(attributed);
         // Short grace: reduced first hit still applies
         amount *= 0.35F;
      } else if (attributed != null) {
         this.participants.add(attributed);
      }
      boolean hurt = super.hurt(source, amount);
      if (hurt && !this.level().isClientSide) {
         this.updatePhaseFromHealth();
         this.combatIdleTicks = 0;
      }
      return hurt;
   }

   @Nullable
   private UUID attributePlayer(DamageSource source) {
      Entity direct = source.getDirectEntity();
      Entity root = source.getEntity();
      Player player = null;
      if (root instanceof Player p) {
         player = p;
      } else if (direct instanceof Player p) {
         player = p;
      } else if (direct instanceof Projectile projectile && projectile.getOwner() instanceof Player p) {
         player = p;
      } else if (root instanceof TamableAnimal tamable && tamable.getOwner() instanceof Player p) {
         player = p;
      } else if (root instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player p) {
         player = p;
      }
      if (player instanceof ServerPlayer serverPlayer && WorldBossEligibility.isCombatTarget(serverPlayer)) {
         return serverPlayer.getUUID();
      }
      return null;
   }

   private void provoke(UUID playerId) {
      this.provokerId = playerId;
      this.participants.add(playerId);
      this.entityData.set(DATA_PROVOKED, true);
      this.setEventState(WorldBossEventState.ENGAGED);
      this.engageGraceTicks = 40;
      this.playAnim(ANIM_PROVOKE, 40);
      this.bossEvent.setVisible(true);
      this.level().playSound(null, this.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.1F, 0.7F);
      if (!this.level().isClientSide) {
         WorldBossAnnouncementService.announceKey(
            (ServerLevel) this.level(),
            this.blockPosition(),
            "millenaire.worldboss.hemelwachter.engage"
         );
         ServerPlayer provoker = ((ServerLevel) this.level()).getServer().getPlayerList().getPlayer(playerId);
         if (provoker != null) {
            this.setTarget(provoker);
         }
      }
   }

   private void updatePhaseFromHealth() {
      float pct = this.getHealth() / this.getMaxHealth();
      int phase = pct > 0.6F ? 0 : pct > 0.3F ? 1 : 2;
      this.entityData.set(DATA_PHASE, phase);
      // Lower core for melee access as fight progresses
      float hover = this.entityData.get(DATA_HOVER_Y);
      float desired = hover - phase * 1.5F;
      this.targetHoverY = desired;
   }

   @Override
   public void aiStep() {
      super.aiStep();
      this.wingPhase += 0.08F + this.getCombatPhase() * 0.03F;
      this.ringPhase += 0.04F + (this.isProvoked() ? 0.05F : 0.0F);
      if (this.level().isClientSide) {
         if (this.clientAnimTicks > 0) {
            this.clientAnimTicks--;
         }
         if (this.tickCount % 20 == 0) {
            this.level()
               .addParticle(
                  ParticleTypes.END_ROD,
                  this.getX() + (this.random.nextDouble() - 0.5) * 1.5,
                  this.getY() + 2.0 + this.random.nextDouble(),
                  this.getZ() + (this.random.nextDouble() - 0.5) * 1.5,
                  0.0,
                  0.02,
                  0.0
               );
         }
         return;
      }

      WorldBossEventState state = this.getEventState();
      if (state == WorldBossEventState.ARRIVING) {
         tickArrive();
      } else if (state == WorldBossEventState.NEUTRAL) {
         tickNeutral();
      } else if (state == WorldBossEventState.ENGAGED || state == WorldBossEventState.DISENGAGING) {
         tickEngaged();
      } else if (state == WorldBossEventState.DEPARTING) {
         tickDepart();
      }

      if (this.attackCooldown > 0) {
         this.attackCooldown--;
      }
      if (this.beamChargeTicks > 0) {
         this.beamChargeTicks--;
         if (this.beamChargeTicks == 0 && this.beamLocked) {
            fireBeam();
         }
      }
      this.syncBossBar();
      this.maintainHover();
   }

   private void tickArrive() {
      double dy = this.targetHoverY - this.getY();
      this.setDeltaMovement(this.getDeltaMovement().x * 0.9, Mth.clamp(dy * 0.08, -0.35, 0.05), this.getDeltaMovement().z * 0.9);
      if (Math.abs(dy) < 0.4) {
         this.setEventState(WorldBossEventState.NEUTRAL);
         this.playAnim(ANIM_IDLE, 0);
         this.neutralTicks = 0;
      }
   }

   private void tickNeutral() {
      // Unbound (spawn-egg / command) bosses do not auto-depart on the event dwell timer.
      if (this.eventId == null) {
         return;
      }
      this.neutralTicks++;
      int dwell = 6000;
      try {
         dwell = MillenaireServerConfig.SERVER.worldBossNeutralDwellTicks.getAsInt();
      } catch (IllegalStateException ignored) {
      }
      if (this.neutralTicks >= dwell) {
         this.beginDeparture("timeout");
      }
   }

   private void tickEngaged() {
      if (this.engageGraceTicks > 0) {
         this.engageGraceTicks--;
         return;
      }
      LivingEntity target = this.resolveCombatTarget();
      if (target == null) {
         this.combatIdleTicks++;
         this.setEventState(WorldBossEventState.DISENGAGING);
         int timeout = 600;
         try {
            timeout = MillenaireServerConfig.SERVER.worldBossCombatTimeoutTicks.getAsInt();
         } catch (IllegalStateException ignored) {
         }
         if (this.combatIdleTicks > timeout) {
            this.beginDeparture("no_targets");
         }
         return;
      }
      this.combatIdleTicks = 0;
      this.setEventState(WorldBossEventState.ENGAGED);
      this.setTarget(target);
      // Keep core reachable
      double preferredY = Math.max(target.getY() + 2.5, this.targetHoverY - 4.0);
      if (this.getY() > preferredY + 1.5) {
         this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.04, 0.0));
      }
   }

   private void tickDepart() {
      this.departTicks--;
      this.setDeltaMovement(this.getDeltaMovement().x * 0.9, 0.25, this.getDeltaMovement().z * 0.9);
      if (this.level() instanceof ServerLevel serverLevel) {
         int budget = MillenaireServerConfig.SERVER.worldBossEffectBudgetPerTick.getAsInt();
         int count = Mth.clamp(budget / 12, 1, 2);
         serverLevel.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 1.5, this.getZ(), count, 1.0, 1.0, 1.0, 0.02);
      }
      if (this.departTicks <= 0) {
         this.departureFinished = true;
         if (this.level() instanceof ServerLevel serverLevel) {
            WorldBossEventPlanner.onBossDeparted(serverLevel, this, "departed");
         }
         this.discard();
      }
   }

   @Nullable
   private LivingEntity resolveCombatTarget() {
      if (this.level() instanceof ServerLevel serverLevel) {
         if (this.provokerId != null) {
            ServerPlayer provoker = serverLevel.getServer().getPlayerList().getPlayer(this.provokerId);
            if (provoker != null && WorldBossEligibility.isCombatTarget(provoker) && provoker.level() == this.level() && provoker.distanceTo(this) < 72.0F) {
               return provoker;
            }
         }
         ServerPlayer nearest = null;
         double best = Double.MAX_VALUE;
         for (UUID id : this.participants) {
            ServerPlayer p = serverLevel.getServer().getPlayerList().getPlayer(id);
            if (p == null || !WorldBossEligibility.isCombatTarget(p) || p.level() != this.level()) {
               continue;
            }
            double d = p.distanceToSqr(this);
            if (d < best && d < 72.0 * 72.0) {
               best = d;
               nearest = p;
            }
         }
         return nearest;
      }
      return this.getTarget();
   }

   private void maintainHover() {
      if (this.getEventState() == WorldBossEventState.DEPARTING || this.getEventState() == WorldBossEventState.ARRIVING) {
         return;
      }
      double dy = this.targetHoverY - this.getY();
      if (Math.abs(dy) > 0.5) {
         this.setDeltaMovement(this.getDeltaMovement().add(0.0, Mth.clamp(dy * 0.03, -0.15, 0.15), 0.0));
      }
   }

   private void syncBossBar() {
      if (!this.isProvoked() || this.getEventState() == WorldBossEventState.DEPARTING) {
         this.bossEvent.setVisible(false);
         this.bossEvent.removeAllPlayers();
         return;
      }
      boolean wasVisible = this.bossEvent.isVisible();
      this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
      this.bossEvent.setVisible(true);
      // Player membership is the expensive part — refresh every 10 ticks (immediate on first show).
      if (wasVisible && this.tickCount % 10 != 0) {
         return;
      }
      if (this.level() instanceof ServerLevel serverLevel) {
         for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(this) < 96.0 * 96.0 && WorldBossEligibility.isCombatTarget(player)) {
               this.bossEvent.addPlayer(player);
            } else {
               this.bossEvent.removePlayer(player);
            }
         }
      }
   }

   void tryStartBeam(LivingEntity target) {
      if (this.attackCooldown > 0 || this.beamChargeTicks > 0 || this.engageGraceTicks > 0) {
         return;
      }
      Vec3 from = this.getEyePosition();
      this.beamDirection = target.getEyePosition().subtract(from).normalize();
      this.beamLocked = true;
      this.beamChargeTicks = 30;
      this.playAnim(ANIM_BEAM, 36);
      this.level().playSound(null, this.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.0F, 0.6F);
   }

   private void fireBeam() {
      this.beamLocked = false;
      if (!(this.level() instanceof ServerLevel serverLevel)) {
         return;
      }
      float damage = 14.0F;
      double range = 40.0;
      try {
         damage = MillenaireServerConfig.SERVER.worldBossBeamDamage.get().floatValue();
         range = MillenaireServerConfig.SERVER.worldBossBeamRange.get();
      } catch (IllegalStateException ignored) {
      }
      Vec3 start = this.getEyePosition();
      Vec3 end = start.add(this.beamDirection.scale(range));
      var hit = serverLevel.clip(
         new net.minecraft.world.level.ClipContext(
            start,
            end,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            this
         )
      );
      Vec3 stop = hit.getLocation();
      AABB sweep = new AABB(start, stop).inflate(0.6);
      LivingEntity victim = null;
      double best = Double.MAX_VALUE;
      for (LivingEntity living : serverLevel.getEntitiesOfClass(LivingEntity.class, sweep, e -> e != this && e.isAlive())) {
         if (living instanceof Player player && player instanceof ServerPlayer sp && !WorldBossEligibility.isCombatTarget(sp)) {
            continue;
         }
         // line proximity
         Vec3 closest = closestPointOnSegment(start, stop, living.getEyePosition());
         if (closest.distanceToSqr(living.getEyePosition()) > 1.1) {
            continue;
         }
         if (!this.hasLineOfSight(living)) {
            continue;
         }
         double d = this.distanceToSqr(living);
         if (d < best) {
            best = d;
            victim = living;
         }
      }
      serverLevel.sendParticles(ParticleTypes.CRIT, stop.x, stop.y, stop.z, 12, 0.2, 0.2, 0.2, 0.05);
      if (victim != null) {
         victim.hurt(this.damageSources().mobAttack(this), damage);
      }
      this.attackCooldown = cooldownForPhase(50);
      this.level().playSound(null, this.blockPosition(), SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 1.0F, 1.3F);
   }

   private static Vec3 closestPointOnSegment(Vec3 a, Vec3 b, Vec3 p) {
      Vec3 ab = b.subtract(a);
      double len2 = ab.lengthSqr();
      if (len2 < 1.0E-6) {
         return a;
      }
      double t = Mth.clamp(p.subtract(a).dot(ab) / len2, 0.0, 1.0);
      return a.add(ab.scale(t));
   }

   void tryBuffet(LivingEntity target) {
      if (this.attackCooldown > 0 || this.engageGraceTicks > 0) {
         return;
      }
      if (this.distanceTo(target) > 6.5F) {
         return;
      }
      this.playAnim(ANIM_BUFFET, 18);
      this.level().playSound(null, this.blockPosition(), SoundEvents.PHANTOM_BITE, SoundSource.HOSTILE, 1.0F, 0.5F);
      float damage = 16.0F;
      try {
         damage = MillenaireServerConfig.SERVER.worldBossBuffetDamage.get().floatValue();
      } catch (IllegalStateException ignored) {
      }
      // Wind-up handled by delaying damage one tick via cooldown gate — apply now after short anim start
      if (target.hurt(this.damageSources().mobAttack(this), damage)) {
         Vec3 push = target.position().subtract(this.position()).normalize().scale(0.85).add(0.0, 0.25, 0.0);
         target.push(push.x, push.y, push.z);
      }
      this.attackCooldown = cooldownForPhase(40);
   }

   void tryVolley(LivingEntity target) {
      if (this.attackCooldown > 0 || this.engageGraceTicks > 0 || !(this.level() instanceof ServerLevel)) {
         return;
      }
      this.playAnim(ANIM_VOLLEY, 28);
      this.level().playSound(null, this.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.9F, 1.5F);
      Vec3 eye = this.getEyePosition();
      for (int i = 0; i < 3; i++) {
         Vec3 aim = target.getEyePosition().subtract(eye);
         double spread = (i - 1) * 0.18;
         Vec3 side = aim.cross(new Vec3(0.0, 1.0, 0.0));
         if (side.lengthSqr() < 1.0E-4) {
            side = new Vec3(0.2, 0.0, 0.0);
         }
         side = side.normalize().scale(spread);
         Vec3 dir = aim.add(side).normalize().scale(0.5);
         CelestialBoltEntity bolt = new CelestialBoltEntity(this.level(), this, dir);
         bolt.setPos(eye.x, eye.y, eye.z);
         this.level().addFreshEntity(bolt);
      }
      this.attackCooldown = cooldownForPhase(55);
   }

   private int cooldownForPhase(int base) {
      int phase = this.getCombatPhase();
      if (phase >= 2) {
         return Math.max(20, base - 18);
      }
      if (phase == 1) {
         return Math.max(24, base - 10);
      }
      return base;
   }

   public void grantDefeatRewards(ActiveWorldBossEvent event) {
      if (this.rewardsGrantedLocal || event.rewardsGranted()) {
         return;
      }
      this.rewardsGrantedLocal = true;
      if (!(this.level() instanceof ServerLevel serverLevel)) {
         return;
      }
      this.spawnAtLocation(new net.minecraft.world.item.ItemStack(ModItems.HEMELKERN.get()));
      int fragments = 3 + this.random.nextInt(3);
      this.spawnAtLocation(new net.minecraft.world.item.ItemStack(ModItems.HEMELFRAGMENT.get(), fragments));
      for (UUID id : event.participants()) {
         ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(id);
         if (player != null) {
            player.giveExperiencePoints(120);
            MillAdvancements.grant(player, MillAdvancements.HEMELWACHTER_DEFEAT);
         }
      }
      WorldBossAnnouncementService.announceKey(serverLevel, this.blockPosition(), "millenaire.worldboss.hemelwachter.defeat");
   }

   @Override
   public void die(DamageSource damageSource) {
      this.playAnim(ANIM_DEATH, 40);
      this.setEventState(WorldBossEventState.DEFEATED);
      this.bossEvent.setVisible(false);
      this.bossEvent.removeAllPlayers();
      if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
         WorldBossEventPlanner.onBossDefeated(serverLevel, this);
      }
      super.die(damageSource);
   }

   @Override
   public void remove(RemovalReason reason) {
      this.bossEvent.removeAllPlayers();
      super.remove(reason);
   }

   @Override
   public void startSeenByPlayer(ServerPlayer player) {
      super.startSeenByPlayer(player);
      if (this.bossEvent.isVisible()) {
         this.bossEvent.addPlayer(player);
      }
   }

   @Override
   public void stopSeenByPlayer(ServerPlayer player) {
      super.stopSeenByPlayer(player);
      this.bossEvent.removePlayer(player);
   }

   @Override
   protected SoundEvent getAmbientSound() {
      return SoundEvents.ELDER_GUARDIAN_AMBIENT;
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource source) {
      return SoundEvents.ELDER_GUARDIAN_HURT;
   }

   @Override
   protected SoundEvent getDeathSound() {
      return SoundEvents.ELDER_GUARDIAN_DEATH;
   }

   public float getWingPhase(float partial) {
      return this.wingPhase + partial * (0.08F + this.getCombatPhase() * 0.03F);
   }

   public float getRingPhase(float partial) {
      return this.ringPhase + partial * (0.04F + (this.isProvoked() ? 0.05F : 0.0F));
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      if (this.eventId != null) {
         tag.putUUID("eventId", this.eventId);
      }
      if (this.bossDefinitionId != null) {
         tag.putString("bossDefinitionId", this.bossDefinitionId.toString());
      }
      if (this.provokerId != null) {
         tag.putUUID("provokerId", this.provokerId);
      }
      ListTag parts = new ListTag();
      for (UUID id : this.participants) {
         parts.add(StringTag.valueOf(id.toString()));
      }
      tag.put("participants", parts);
      tag.putInt("neutralTicks", this.neutralTicks);
      tag.putInt("combatIdleTicks", this.combatIdleTicks);
      tag.putBoolean("provoked", this.isProvoked());
      tag.putInt("eventState", this.getEventState().ordinal());
      tag.putFloat("targetHoverY", this.targetHoverY);
      tag.putBoolean("rewardsGrantedLocal", this.rewardsGrantedLocal);
   }

   @Override
   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      if (tag.hasUUID("eventId")) {
         this.eventId = tag.getUUID("eventId");
      }
      if (tag.contains("bossDefinitionId")) {
         this.bossDefinitionId = ResourceLocation.parse(tag.getString("bossDefinitionId"));
      }
      if (tag.hasUUID("provokerId")) {
         this.provokerId = tag.getUUID("provokerId");
      }
      this.participants.clear();
      ListTag parts = tag.getList("participants", Tag.TAG_STRING);
      for (int i = 0; i < parts.size(); i++) {
         try {
            this.participants.add(UUID.fromString(parts.getString(i)));
         } catch (IllegalArgumentException ignored) {
         }
      }
      this.neutralTicks = tag.getInt("neutralTicks");
      this.combatIdleTicks = tag.getInt("combatIdleTicks");
      this.entityData.set(DATA_PROVOKED, tag.getBoolean("provoked"));
      this.entityData.set(DATA_STATE, tag.getInt("eventState"));
      this.targetHoverY = tag.getFloat("targetHoverY");
      this.entityData.set(DATA_HOVER_Y, this.targetHoverY);
      this.rewardsGrantedLocal = tag.getBoolean("rewardsGrantedLocal");
      this.updatePhaseFromHealth();
      if (this.isProvoked()) {
         this.bossEvent.setVisible(true);
      }
   }

   /** Dev/preview helper — forces provoked presentation without dealing damage. */
   public void previewForceProvoked() {
      this.entityData.set(DATA_PROVOKED, true);
      this.setEventState(WorldBossEventState.ENGAGED);
      this.playAnim(ANIM_PROVOKE, 40);
      this.bossEvent.setVisible(false);
   }

   private static final class HemelHoverGoal extends Goal {
      private final HemelwachterEntity mob;
      private int repath;

      HemelHoverGoal(HemelwachterEntity mob) {
         this.mob = mob;
         this.setFlags(EnumSet.of(Flag.MOVE));
      }

      @Override
      public boolean canUse() {
         WorldBossEventState state = this.mob.getEventState();
         return state == WorldBossEventState.NEUTRAL || state == WorldBossEventState.ENGAGED || state == WorldBossEventState.DISENGAGING;
      }

      @Override
      public void tick() {
         if (--this.repath > 0) {
            return;
         }
         this.repath = 30 + this.mob.getRandom().nextInt(40);
         LivingEntity target = this.mob.getTarget();
         double cx = this.mob.getX();
         double cz = this.mob.getZ();
         if (target != null && this.mob.isProvoked()) {
            double ang = this.mob.tickCount * 0.04;
            cx = target.getX() + Math.cos(ang) * 10.0;
            cz = target.getZ() + Math.sin(ang) * 10.0;
         } else {
            cx += (this.mob.getRandom().nextDouble() - 0.5) * 8.0;
            cz += (this.mob.getRandom().nextDouble() - 0.5) * 8.0;
         }
         this.mob.getMoveControl().setWantedPosition(cx, this.mob.targetHoverY, cz, 0.85);
      }
   }

   private static final class HemelCombatGoal extends Goal {
      private final HemelwachterEntity mob;

      HemelCombatGoal(HemelwachterEntity mob) {
         this.mob = mob;
         this.setFlags(EnumSet.of(Flag.LOOK, Flag.TARGET));
      }

      @Override
      public boolean canUse() {
         return this.mob.isProvoked()
            && this.mob.getEventState() == WorldBossEventState.ENGAGED
            && this.mob.engageGraceTicks <= 0
            && this.mob.getTarget() != null;
      }

      @Override
      public void tick() {
         LivingEntity target = this.mob.getTarget();
         if (target == null) {
            return;
         }
         this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
         if (this.mob.attackCooldown > 0 || this.mob.beamChargeTicks > 0) {
            return;
         }
         double dist = this.mob.distanceTo(target);
         int choice = this.mob.getRandom().nextInt(3);
         if (dist < 6.0 && choice != 2) {
            this.mob.tryBuffet(target);
         } else if (choice == 0) {
            this.mob.tryStartBeam(target);
         } else {
            this.mob.tryVolley(target);
         }
      }
   }
}
