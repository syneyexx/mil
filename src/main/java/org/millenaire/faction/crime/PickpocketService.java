package org.millenaire.faction.crime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.millenaire.combat.CombatHelper;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.item.ModItems;
import org.millenaire.village.Village;

/**
 * Sneak + empty-hand pickpocket against Millenaire villagers.
 */
public final class PickpocketService {
   private static final long COOLDOWN_TICKS = 60L;
   private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();

   private PickpocketService() {
   }

   public static boolean tryPickpocket(ServerPlayer player, MillVillager villager) {
      if (player == null || villager == null || !(villager.level() instanceof ServerLevel level)) {
         return false;
      }
      if (!isEnabled()) {
         return false;
      }
      if (!CombatHelper.isPlayerTargetable(player)) {
         return false;
      }
      if (!player.getMainHandItem().isEmpty()) {
         return false;
      }
      VillagerType type = villager.getVillagerTypeId() != null ? ModCultures.getVillagerType(villager.getVillagerTypeId()) : null;
      if (type != null && type.isHostile()) {
         return false;
      }
      Village village = villager.getVillageId() != null ? Village.resolve(level, villager.getVillageId()) : null;
      if (village != null && village.isControlledBy(player.getUUID())) {
         return false;
      }
      long now = level.getGameTime();
      String key = player.getUUID() + "|" + villager.getUUID();
      Long last = COOLDOWNS.get(key);
      if (last != null && now - last < COOLDOWN_TICKS) {
         return true;
      }
      COOLDOWNS.put(key, now);

      double chance = baseChance();
      if (hasNearbyGuard(level, villager, 16.0)) {
         chance -= 0.25;
      }
      if (level.isDay()) {
         chance -= 0.15;
      }
      chance = Math.max(0.05, Math.min(0.95, chance));
      boolean success = player.getRandom().nextDouble() < chance;
      if (success) {
         ItemStack stolen = stealOneItem(villager);
         if (!stolen.isEmpty()) {
            if (!player.getInventory().add(stolen)) {
               player.drop(stolen, false);
            }
            player.sendSystemMessage(
               Component.translatable("millenaire.crime.pickpocket.success", villager.getVillagerDisplayName())
            );
         } else {
            // Empty pockets still counts as a quiet success (no bounty).
            player.sendSystemMessage(
               Component.translatable("millenaire.crime.pickpocket.success", villager.getVillagerDisplayName())
            );
         }
         return true;
      }

      player.sendSystemMessage(Component.translatable("millenaire.crime.pickpocket.fail"));
      CrimeService.onPickpocket(level, player, villager, village);
      alertGuards(villager, player);
      return true;
   }

   private static ItemStack stealOneItem(MillVillager villager) {
      List<Entry<Item, Integer>> entries = new ArrayList<>(villager.getInventory().getAll().entrySet());
      if (!entries.isEmpty()) {
         Entry<Item, Integer> pick = entries.get(villager.getRandom().nextInt(entries.size()));
         int removed = villager.getInventory().remove(pick.getKey(), 1);
         if (removed > 0) {
            return new ItemStack(pick.getKey(), removed);
         }
      }
      // Fallback: a denier if culture inventory empty.
      Item denier = ModItems.DENIER.get();
      if (denier != null && denier != Items.AIR) {
         return new ItemStack(denier, 1);
      }
      return ItemStack.EMPTY;
   }

   private static boolean hasNearbyGuard(ServerLevel level, MillVillager villager, double radius) {
      AABB box = villager.getBoundingBox().inflate(radius);
      for (MillVillager other : level.getEntitiesOfClass(MillVillager.class, box)) {
         if (other == villager || !other.isAlive()) {
            continue;
         }
         VillagerType t = other.getVillagerTypeId() != null ? ModCultures.getVillagerType(other.getVillagerTypeId()) : null;
         if (t != null && t.isHelpInAttacks()) {
            return true;
         }
      }
      return false;
   }

   private static void alertGuards(MillVillager victim, ServerPlayer player) {
      victim.setAttackTarget(player);
      CombatHelper.callForHelp(victim, player);
   }

   private static boolean isEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.pickpocketEnabled.get())
            && Boolean.TRUE.equals(MillenaireServerConfig.SERVER.cultureBountyEnabled.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   private static double baseChance() {
      try {
         return MillenaireServerConfig.SERVER.pickpocketBaseChance.get();
      } catch (IllegalStateException e) {
         return 0.55;
      }
   }
}
