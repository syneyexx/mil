package org.millenaire.entity;

import com.mojang.logging.LogUtils;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.millenaire.building.BuildingInstance;
import org.millenaire.combat.CombatHelper;
import org.millenaire.combat.raid.SpecialPointFallback;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.goal.impl.HuntMonsterGoal;
import org.millenaire.item.MillenaireBow;
import org.millenaire.tool.ToolCategory;
import org.millenaire.tool.ToolCategoryRegistry;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.slf4j.Logger;

public final class VillagerCombat {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final MillVillager villager;
   @Nullable
   private LivingEntity attackTarget;
   private int attackCooldown;
   private boolean raiderEntity;
   @Nullable
   private UUID pursuitTargetId;
   private int pursuitTicks;
   private int pursuitNullTicks;
   private static final int UNREACHABLE_PURSUIT_TICKS = 600;
   private static final int PURSUIT_NULL_GRACE = 5;
   private static final int PLAYER_HIT_GRACE = 2;
   private static final int PLAYER_HIT_WINDOW = 6000;
   @Nullable
   private UUID gracePlayerId;
   private int gracePlayerHits;
   private long gracePlayerLastHitTime;

   VillagerCombat(MillVillager villager) {
      this.villager = villager;
   }

   @Nullable
   public LivingEntity getAttackTarget() {
      return this.attackTarget;
   }

   public void setAttackTarget(@Nullable LivingEntity target) {
      this.attackTarget = target;
      this.villager.setTarget(target);
   }

   public int getAttackCooldown() {
      return this.attackCooldown;
   }

   public void setAttackCooldown(int cooldown) {
      this.attackCooldown = cooldown;
   }

   public boolean isRaiderEntity() {
      return this.raiderEntity;
   }

   public void setRaiderEntity(boolean raiderEntity) {
      this.raiderEntity = raiderEntity;
   }

   public int getAttackStrength() {
      VillagerType vt = ModCultures.getVillagerType(this.villager.getVillagerTypeId());
      int base = vt != null ? vt.baseAttackStrength() : 0;
      double weaponDmg = CombatHelper.weaponDamage(this.villager.getMainHandItem());
      return CombatHelper.entityAttackStrength(base, weaponDmg);
   }

   public ItemStack getCombatWeapon() {
      VillagerType vt = ModCultures.getVillagerType(this.villager.getVillagerTypeId());
      if (vt == null) {
         return ItemStack.EMPTY;
      }

      boolean combatRole = vt.isArcher() || vt.isHelpInAttacks() || vt.isRaider();
      if (combatRole && !this.isTargetWithinMeleeRange()) {
         Item ownedGun = org.millenaire.compat.tacz.TaczWeaponCompat.findOwnedGun(this.villager.getInventory());
         if (ownedGun != null) {
            return org.millenaire.compat.tacz.TaczWeaponCompat.createGunStack(ownedGun);
         }
      }

      if (vt.isArcher() && !this.isTargetWithinMeleeRange()) {
         ToolCategory ranged = ToolCategoryRegistry.get("weaponsranged");
         if (ranged != null) {
            ToolCategory.ToolEntry best = ranged.getBestOwned(item -> this.villager.getInventory().getCount(item) > 0);
            if (best != null && best.item() != null) {
               if (org.millenaire.compat.tacz.TaczWeaponCompat.isGunItem(best.item())) {
                  return org.millenaire.compat.tacz.TaczWeaponCompat.createGunStack(best.item());
               }
               return new ItemStack(best.item());
            }
         }
      }

      ToolCategory melee = ToolCategoryRegistry.get("weaponshandtohand");
      if (melee != null) {
         ToolCategory.ToolEntry best = melee.getBestOwned(item -> this.villager.getInventory().getCount(item) > 0);
         if (best != null && best.item() != null) {
            return new ItemStack(best.item());
         }
      }

      ResourceLocation dfltId = vt.defaultWeapon();
      if (dfltId != null) {
         Item dflt = (Item)BuiltInRegistries.ITEM.get(dfltId);
         if (dflt != Items.AIR) {
            return new ItemStack(dflt);
         }
      }

      return ItemStack.EMPTY;
   }

   private boolean isTargetWithinMeleeRange() {
      if (this.attackTarget == null) {
         return false;
      }

      double minSq = 25.0;
      return this.villager.distanceToSqr(this.attackTarget) <= minSq;
   }

   public void performRangedAttack(LivingEntity target, float distanceFactor) {
      if (this.villager.level() instanceof ServerLevel) {
         Arrow arrow = new Arrow(this.villager.level(), this.villager, new ItemStack(Items.ARROW), null);
         double dx = target.getX() - this.villager.getX();
         double dy = target.getY(0.3333) - arrow.getY();
         double dz = target.getZ() - this.villager.getZ();
         double dist = Math.sqrt(dx * dx + dz * dz);
         float speedFactor = 1.0F;
         float damageBonus = 0.0F;
         ItemStack weapon = this.villager.getMainHandItem();
         if (weapon.getItem() instanceof MillenaireBow bow) {
            speedFactor = Math.max(speedFactor, bow.getSpeedFactor());
            damageBonus = Math.max(damageBonus, bow.getDamageBonus());
         }

         arrow.setBaseDamage(arrow.getBaseDamage() + damageBonus);
         float inaccuracy = (14 - this.villager.level().getDifficulty().getId() * 4) * speedFactor;
         arrow.shoot(dx, dy + dist * 0.2, dz, 1.6F, inaccuracy);
         this.villager.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.villager.getRandom().nextFloat() * 0.4F + 0.8F));
         this.villager.level().addFreshEntity(arrow);
      }
   }

   public boolean performAttack(LivingEntity target) {
      VillagerType vt = ModCultures.getVillagerType(this.villager.getVillagerTypeId());
      boolean isArcher = vt != null && vt.isArcher();
      boolean combatRole = vt != null && (vt.isArcher() || vt.isHelpInAttacks() || vt.isRaider());
      ItemStack main = this.villager.getMainHandItem();
      boolean hasGun = org.millenaire.compat.tacz.TaczWeaponCompat.isGun(main);
      boolean hasBow = main.getItem() instanceof BowItem;
      double distance = Math.sqrt(this.villager.distanceToSqr(target));
      boolean verticalOverlap = target.getBoundingBox().maxY > this.villager.getBoundingBox().minY
         && target.getBoundingBox().minY < this.villager.getBoundingBox().maxY;

      if (combatRole && hasGun && distance > 5.0 && distance < 32.0 && this.getAttackCooldown() <= 0) {
         this.villager.swing(InteractionHand.MAIN_HAND);
         if (org.millenaire.compat.tacz.TaczWeaponCompat.tryFire(this.villager, target)) {
            this.setAttackCooldown(40);
            this.pursuitTicks = 0;
            return true;
         }
      }

      CombatHelper.AttackType type = CombatHelper.decideAttack(
         distance, isArcher, hasBow || hasGun, this.getAttackCooldown(), verticalOverlap
      );
      switch (type) {
         case RANGED:
            float df = (float)Math.min(1.0, Math.max(0.1, distance / 20.0));
            this.villager.swing(InteractionHand.MAIN_HAND);
            if (hasGun) {
               org.millenaire.compat.tacz.TaczWeaponCompat.tryFire(this.villager, target);
               this.setAttackCooldown(40);
            } else {
               this.performRangedAttack(target, df);
               this.setAttackCooldown(100);
            }
            this.pursuitTicks = 0;
            return true;
         case MELEE:
            target.hurt(this.villager.damageSources().mobAttack(this.villager), this.getAttackStrength());
            this.setAttackCooldown(20);
            this.villager.swing(InteractionHand.MAIN_HAND);
            this.pursuitTicks = 0;
            return true;
         case NONE:
            return false;
         default:
            return false;
      }
   }

   public void ensureCombatWeaponEquipped() {
      ItemStack weapon = this.getCombatWeapon();
      if (!ItemStack.matches(this.villager.getMainHandItem(), weapon)) {
         this.villager.setItemSlot(EquipmentSlot.MAINHAND, weapon);
      }
   }

   void tickCombatMaintenance() {
      if (this.attackCooldown > 0) {
         this.attackCooldown--;
      }

      if (this.attackTarget != null && this.villager.getHiredBy() == null && this.isDefensiveType() && this.straysFromDefendingPos()) {
         this.setAttackTarget(null);
      }

      if (this.attackTarget != null) {
         double maxSq = 6400.0;
         if (!this.attackTarget.isAlive()
            || this.villager.distanceToSqr(this.attackTarget) > maxSq
            || this.villager.level().getDifficulty() == Difficulty.PEACEFUL) {
            this.setAttackTarget(null);
         }
      }

      if (this.attackTarget != null) {
         this.pursuitNullTicks = 0;
         this.tickUnreachableTargetWatchdog();
      } else if (this.pursuitTargetId != null && ++this.pursuitNullTicks > 5) {
         this.pursuitTargetId = null;
         this.pursuitTicks = 0;
      }
   }

   private void tickUnreachableTargetWatchdog() {
      LivingEntity t = this.attackTarget;
      if (!this.raiderEntity && HuntMonsterGoal.isHuntableEntity(t)) {
         UUID id = t.getUUID();
         if (!id.equals(this.pursuitTargetId)) {
            this.pursuitTargetId = id;
            this.pursuitTicks = 0;
         } else if (++this.pursuitTicks >= 600) {
            VillageId villageId = this.villager.getVillageId();
            if (villageId != null && this.villager.level() instanceof ServerLevel serverLevel) {
               Village village = Village.resolve(serverLevel, villageId);
               if (village != null) {
                  village.markHuntUnreachable(t.blockPosition(), serverLevel.getGameTime());
                  LOGGER.debug(
                     "[Millenaire] {} gave up unreachable target {} at {} — cell blacklisted",
                     new Object[]{this.villager.getVillagerTypeId(), t.getType().toShortString(), t.blockPosition().toShortString()}
                  );
               }
            }

            this.setAttackTarget(null);
            this.pursuitTargetId = null;
            this.pursuitTicks = 0;
         }
      } else {
         this.pursuitTargetId = null;
         this.pursuitTicks = 0;
      }
   }

   void triggerMobAttacks() {
      AABB box = new AABB(
         this.villager.getX() - 16.0,
         this.villager.getY() - 5.0,
         this.villager.getZ() - 16.0,
         this.villager.getX() + 16.0,
         this.villager.getY() + 5.0,
         this.villager.getZ() + 16.0
      );

      for (Monster mob : this.villager.level().getEntitiesOfClass(Monster.class, box)) {
         if (mob.getTarget() == null && mob.hasLineOfSight(this.villager)) {
            mob.setTarget(this.villager);
         }
      }
   }

   private boolean isDefensiveType() {
      ResourceLocation vtId = this.villager.getVillagerTypeId();
      VillagerType vt = vtId != null ? ModCultures.getVillagerType(vtId) : null;
      return vt != null && vt.isDefensive();
   }

   private boolean straysFromDefendingPos() {
      VillageId villageId = this.villager.getVillageId();
      if (villageId != null && this.villager.level() instanceof ServerLevel serverLevel) {
         Village village = Village.resolve(serverLevel, villageId);
         if (village == null) {
            return false;
         }

         BuildingInstance th = village.getTownhall();
         if (th == null) {
            return false;
         }

         BlockPos defendingPos = SpecialPointFallback.resolveOrFallback(th.getFirstPointPos("defendingPos"), th.getOrigin());
         if (defendingPos == null) {
            return false;
         }

         double maxSq = 400.0;
         return this.villager.distanceToSqr(defendingPos.getX() + 0.5, defendingPos.getY() + 0.5, defendingPos.getZ() + 0.5) > maxSq;
      } else {
         return false;
      }
   }

   void onHurt(DamageSource source, float amount, boolean hadFullHealth) {
      if (source.getEntity() instanceof ServerPlayer player
         && CombatHelper.isPlayerTargetable(player)
         && !this.isRaiderEntity()) {
         ResourceLocation vtId = this.villager.getVillagerTypeId();
         VillagerType vtype = vtId != null ? ModCultures.getVillagerType(vtId) : null;
         boolean nonHostileType = vtype != null && !vtype.isHostile();
         VillageId villageId = this.villager.getVillageId();
         if (nonHostileType && villageId != null && this.villager.level() instanceof ServerLevel serverLevel) {
            Village village = Village.resolve(serverLevel, villageId);
            if (village != null) {
               int repChange = -((int)(amount * 10.0F));
               village.adjustReputation(serverLevel, player.getUUID(), repChange);
               org.millenaire.faction.crime.CrimeService.onVillagerAssault(serverLevel, player, this.villager, village);
               LOGGER.debug(
                  "[Millenaire] {} hit {} : rep {} (village {})",
                  new Object[]{player.getGameProfile().getName(), this.villager.getVillagerTypeId(), repChange, village.getVillageName()}
               );
            }
         }

         if (this.villager.level().getDifficulty() != Difficulty.PEACEFUL) {
            if (this.registerPlayerHitAndCheckGrace(player)) {
               this.villager.speakCombatCall("hitbyplayer");
            } else {
               this.setAttackTarget(player);
               CombatHelper.callForHelp(this.villager, player);
            }
         }

         if (nonHostileType && hadFullHealth && this.villager.level() instanceof ServerLevel) {
            ItemStack mainHand = player.getMainHandItem();
            if (mainHand.isEmpty() || CombatHelper.weaponDamage(mainHand) <= 1.0) {
               player.sendSystemMessage(Component.translatable("message.millenaire.communication_explanations").withStyle(ChatFormatting.GOLD));
            }
         }
      }

      if (!this.villager.level().isClientSide() && source.getEntity() instanceof MillVillager attacker && attacker != this.villager) {
         VillageId villageId = this.villager.getVillageId();
         boolean sameVillage = villageId != null && attacker.getVillageId() != null && villageId.equals(attacker.getVillageId());
         if (CombatHelper.isVillagerHostile(this.raiderEntity, attacker.isRaiderEntity(), sameVillage)) {
            this.setAttackTarget(attacker);
            CombatHelper.callForHelp(this.villager, attacker);
         }
      }

      if (!this.villager.level().isClientSide()
         && source.getEntity() instanceof LivingEntity mobAttacker
         && !(mobAttacker instanceof Player)
         && !(mobAttacker instanceof MillVillager)
         && !this.isRaiderEntity()) {
         this.setAttackTarget(mobAttacker);
         CombatHelper.callForHelp(this.villager, mobAttacker);
      }
   }

   private boolean registerPlayerHitAndCheckGrace(ServerPlayer player) {
      long now = this.villager.level().getGameTime();
      boolean sameAggressor = player.getUUID().equals(this.gracePlayerId) && now - this.gracePlayerLastHitTime <= 6000L;
      if (sameAggressor) {
         this.gracePlayerHits++;
      } else {
         this.gracePlayerId = player.getUUID();
         this.gracePlayerHits = 1;
      }

      this.gracePlayerLastHitTime = now;
      return this.gracePlayerHits <= 2;
   }
}
