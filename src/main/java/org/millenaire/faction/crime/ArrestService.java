package org.millenaire.faction.crime;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPurpose;
import org.millenaire.building.SpecialPoint;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.Village;

/**
 * Soft-death arrest: when a guard would kill a wanted player, confiscate items and jail them.
 */
public final class ArrestService {
   private ArrestService() {
   }

   public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
      if (!isEnabled()) {
         return;
      }
      if (!(event.getEntity() instanceof ServerPlayer player)) {
         return;
      }
      if (!(player.level() instanceof ServerLevel level)) {
         return;
      }
      LivingEntity attackerEntity = event.getSource().getEntity() instanceof LivingEntity le ? le : null;
      if (!(attackerEntity instanceof MillVillager guard)) {
         return;
      }
      VillagerType type = guard.getVillagerTypeId() != null ? ModCultures.getVillagerType(guard.getVillagerTypeId()) : null;
      if (type == null || !type.isHelpInAttacks()) {
         return;
      }
      Village village = guard.getVillageId() != null ? Village.resolve(level, guard.getVillageId()) : null;
      if (village == null || village.getCultureId() == null) {
         return;
      }
      if (village.isControlledBy(player.getUUID())) {
         return;
      }
      WantedTier tier = CultureBountySavedData.get(level).getManager().getTier(player.getUUID(), village.getCultureId());
      if (!tier.isAtLeast(WantedTier.WANTED)) {
         return;
      }
      float remaining = player.getHealth() - event.getNewDamage();
      if (remaining > 0.0F) {
         return;
      }
      // Intercept lethal blow.
      event.setNewDamage(0.0F);
      performArrest(level, player, village, village.getCultureId());
   }

   public static void performArrest(ServerLevel level, ServerPlayer player, Village village, ResourceLocation cultureId) {
      BlockPos chestPos = resolveEvidenceChest(village);
      BlockPos jailPos = resolveJailPos(village);
      depositInventory(level, player, chestPos);

      player.setHealth(Math.max(player.getHealth(), player.getMaxHealth() * 0.5F));
      player.removeAllEffects();
      player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
      if (jailPos != null) {
         player.teleportTo(jailPos.getX() + 0.5, jailPos.getY() + 0.1, jailPos.getZ() + 0.5);
      } else if (village.getCenter() != null) {
         BlockPos c = village.getCenter();
         player.teleportTo(c.getX() + 0.5, c.getY() + 1.0, c.getZ() + 0.5);
      }

      // Clear combat targeting from nearby guards.
      for (MillVillager v : level.getEntitiesOfClass(MillVillager.class, player.getBoundingBox().inflate(64.0))) {
         if (v.getAttackTarget() == player) {
            v.setAttackTarget(null);
         }
      }

      long lockTicks = arrestLockSeconds() * 20L;
      PlayerArrestSavedData.get(level)
         .put(
            new PlayerArrestSavedData.ArrestRecord(
               player.getUUID(),
               cultureId,
               village.getId().uuid(),
               level.getGameTime() + lockTicks,
               chestPos,
               jailPos
            )
         );

      int bountyDelta = arrestBountyDelta();
      if (bountyDelta < 0) {
         CultureBountySavedData.get(level).getManager().reduceBounty(level, player.getUUID(), cultureId, -bountyDelta);
      } else {
         BountyHudSync.sync(level, player);
      }

      Culture culture = ModCultures.getCulture(cultureId);
      String name = culture != null && culture.displayName() != null ? culture.displayName() : cultureId.getPath();
      player.sendSystemMessage(Component.translatable("millenaire.crime.arrested", name));
   }

   @Nullable
   private static BlockPos resolveEvidenceChest(Village village) {
      BuildingInstance jail = findPurposeBuilding(village, BuildingPurpose.JAIL);
      if (jail != null) {
         BlockPos chest = jail.getFirstPointPos(SpecialPoint.CHEST);
         if (chest != null) {
            return chest;
         }
      }
      BuildingInstance th = village.getTownhall();
      if (th != null) {
         BlockPos chest = th.getFirstPointPos(SpecialPoint.CHEST);
         if (chest != null) {
            return chest;
         }
      }
      return null;
   }

   @Nullable
   private static BlockPos resolveJailPos(Village village) {
      BuildingInstance jail = findPurposeBuilding(village, BuildingPurpose.JAIL);
      if (jail != null) {
         BlockPos sleep = jail.getFirstPointPos(SpecialPoint.SLEEPING_POS);
         if (sleep != null) {
            return sleep;
         }
         BlockPos sell = jail.getFirstPointPos(SpecialPoint.SELLING_POS);
         if (sell != null) {
            return sell;
         }
         if (jail.getOrigin() != null) {
            return jail.getOrigin();
         }
      }
      BuildingInstance th = village.getTownhall();
      if (th != null && th.getOrigin() != null) {
         return th.getOrigin().above();
      }
      return village.getCenter();
   }

   @Nullable
   private static BuildingInstance findPurposeBuilding(Village village, BuildingPurpose purpose) {
      for (BuildingInstance building : village.getBuildings()) {
         if (building != null && building.getPurpose() == purpose) {
            return building;
         }
      }
      return null;
   }

   private static void depositInventory(ServerLevel level, ServerPlayer player, @Nullable BlockPos chestPos) {
      ChestBlockEntity chest = null;
      if (chestPos != null) {
         BlockEntity be = level.getBlockEntity(chestPos);
         if (be instanceof ChestBlockEntity c) {
            chest = c;
         }
      }
      for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (stack.isEmpty()) {
            continue;
         }
         ItemStack copy = stack.copy();
         player.getInventory().setItem(i, ItemStack.EMPTY);
         if (chest != null) {
            ItemStack leftover = putInChest(chest, copy);
            if (!leftover.isEmpty()) {
               player.drop(leftover, false);
            }
         } else {
            player.drop(copy, false);
         }
      }
      player.getInventory().setChanged();
      if (chest != null) {
         chest.setChanged();
      }
   }

   private static ItemStack putInChest(ChestBlockEntity chest, ItemStack stack) {
      ItemStack remaining = stack.copy();
      for (int slot = 0; slot < chest.getContainerSize() && !remaining.isEmpty(); slot++) {
         ItemStack inSlot = chest.getItem(slot);
         if (inSlot.isEmpty()) {
            chest.setItem(slot, remaining);
            return ItemStack.EMPTY;
         }
         if (ItemStack.isSameItemSameComponents(inSlot, remaining)) {
            int space = inSlot.getMaxStackSize() - inSlot.getCount();
            if (space > 0) {
               int move = Math.min(space, remaining.getCount());
               inSlot.grow(move);
               remaining.shrink(move);
               chest.setItem(slot, inSlot);
            }
         }
      }
      return remaining;
   }

   private static boolean isEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.arrestEnabled.get())
            && Boolean.TRUE.equals(MillenaireServerConfig.SERVER.cultureBountyEnabled.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   private static int arrestLockSeconds() {
      try {
         return MillenaireServerConfig.SERVER.arrestLockSeconds.get();
      } catch (IllegalStateException e) {
         return 60;
      }
   }

   private static int arrestBountyDelta() {
      try {
         return MillenaireServerConfig.SERVER.arrestBountyOnArrest.get();
      } catch (IllegalStateException e) {
         return 0;
      }
   }
}
