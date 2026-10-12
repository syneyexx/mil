package org.millenaire.faction.crime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPurpose;
import org.millenaire.building.SpecialPoint;
import org.millenaire.combat.CombatHelper;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;

/**
 * Soft-death arrest: when a MillVillager of a bounty culture would kill a wanted player,
 * confiscate items and jail them (instead of vanilla death/respawn).
 */
public final class ArrestService {
   private static final Map<UUID, PendingRespawnJail> PENDING_RESPAWN_JAIL = new ConcurrentHashMap<>();

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
      if (!CombatHelper.isPlayerTargetable(player)) {
         return;
      }
      LivingEntity attackerEntity = event.getSource().getEntity() instanceof LivingEntity le ? le : null;
      if (!(attackerEntity instanceof MillVillager villager)) {
         return;
      }
      ArrestContext ctx = resolveArrestContext(level, player, villager);
      if (ctx == null) {
         return;
      }
      boolean creativePath = player.isCreative();
      float remaining = player.getHealth() - event.getNewDamage();
      boolean wouldKill = remaining <= 0.0F;
      if (creativePath) {
         // Creative often absorbs/zeros damage; any positive hit from the bounty culture counts.
         wouldKill = event.getNewDamage() > 0.0F;
      }
      if (!wouldKill) {
         return;
      }
      event.setNewDamage(0.0F);
      performArrest(level, player, ctx.village(), ctx.cultureId());
   }

   /**
    * Creative / missed-damage path: call after a MillVillager melee/ranged attack attempt.
    * Arrests when the player is WANTED+ for the villager's culture and treatCreativeAsSurvival applies.
    */
   public static boolean tryArrestFromVillagerAttack(ServerLevel level, ServerPlayer player, MillVillager villager) {
      if (!isEnabled() || level == null || player == null || villager == null) {
         return false;
      }
      if (!CombatHelper.isPlayerTargetable(player)) {
         return false;
      }
      if (!player.isCreative()) {
         return false;
      }
      ArrestContext ctx = resolveArrestContext(level, player, villager);
      if (ctx == null) {
         return false;
      }
      performArrest(level, player, ctx.village(), ctx.cultureId());
      return true;
   }

   public static void onLivingDeath(LivingDeathEvent event) {
      if (!isEnabled()) {
         return;
      }
      if (!(event.getEntity() instanceof ServerPlayer player)) {
         return;
      }
      if (!(player.level() instanceof ServerLevel level)) {
         return;
      }
      if (!CombatHelper.isPlayerTargetable(player)) {
         return;
      }
      if (!(event.getSource().getEntity() instanceof MillVillager villager)) {
         return;
      }
      ArrestContext ctx = resolveArrestContext(level, player, villager);
      if (ctx == null) {
         return;
      }
      event.setCanceled(true);
      player.setHealth(Math.max(1.0F, player.getMaxHealth() * 0.5F));
      performArrest(level, player, ctx.village(), ctx.cultureId());
   }

   public static void onPlayerRespawn(PlayerRespawnEvent event) {
      if (!(event.getEntity() instanceof ServerPlayer player)) {
         return;
      }
      PendingRespawnJail pending = PENDING_RESPAWN_JAIL.remove(player.getUUID());
      if (pending == null) {
         return;
      }
      if (!(player.level() instanceof ServerLevel level)) {
         return;
      }
      Village village = Village.resolve(level, new VillageId(pending.villageUuid()));
      BlockPos jailPos = village != null ? resolveJailPos(village) : pending.fallbackPos();
      if (jailPos == null) {
         jailPos = pending.fallbackPos();
      }
      if (jailPos != null) {
         player.teleportTo(jailPos.getX() + 0.5, jailPos.getY() + 0.1, jailPos.getZ() + 0.5);
      }
      player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
      if (village != null) {
         long lockTicks = arrestLockSeconds() * 20L;
         PlayerArrestSavedData.get(level)
            .put(
               new PlayerArrestSavedData.ArrestRecord(
                  player.getUUID(),
                  pending.cultureId(),
                  pending.villageUuid(),
                  level.getGameTime() + lockTicks,
                  resolveEvidenceChest(village),
                  jailPos
               )
            );
      }
      Culture culture = ModCultures.getCulture(pending.cultureId());
      String name = culture != null && culture.displayName() != null ? culture.displayName() : pending.cultureId().getPath();
      player.sendSystemMessage(Component.translatable("millenaire.crime.arrested", name));
      BountyHudSync.sync(level, player);
   }

   @Nullable
   private static ArrestContext resolveArrestContext(ServerLevel level, ServerPlayer player, MillVillager villager) {
      if (isAlreadyArrested(level, player)) {
         return null;
      }
      Village village = villager.getVillageId() != null ? Village.resolve(level, villager.getVillageId()) : null;
      if (village == null || village.getCultureId() == null) {
         return null;
      }
      if (village.isControlledBy(player.getUUID())) {
         return null;
      }
      ResourceLocation cultureId = village.getCultureId();
      WantedTier tier = CultureBountySavedData.get(level).getManager().getTier(player.getUUID(), cultureId);
      if (!tier.isAtLeast(WantedTier.WANTED)) {
         return null;
      }
      return new ArrestContext(village, cultureId);
   }

   private static boolean isAlreadyArrested(ServerLevel level, ServerPlayer player) {
      PlayerArrestSavedData.ArrestRecord record = PlayerArrestSavedData.get(level).get(player.getUUID());
      return record != null && level.getGameTime() < record.arrestedUntilGameTime();
   }

   public static void performArrest(ServerLevel level, ServerPlayer player, Village village, ResourceLocation cultureId) {
      if (isAlreadyArrested(level, player)) {
         return;
      }
      BlockPos chestPos = resolveEvidenceChest(village);
      BlockPos jailPos = resolveJailPos(village);
      if (!player.isCreative()) {
         depositInventory(level, player, chestPos);
      }

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

   private record ArrestContext(Village village, ResourceLocation cultureId) {
   }

   private record PendingRespawnJail(ResourceLocation cultureId, UUID villageUuid, @Nullable BlockPos fallbackPos) {
   }
}
