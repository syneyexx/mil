package org.millenaire.companion;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerSpawnFactory;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

public final class CompanionService {
   private static final Logger LOGGER = LogUtils.getLogger();
   /** Permanent hire marker — hire expiry must not clear companions. */
   public static final long COMPANION_HIRE_UNTIL = Long.MAX_VALUE / 4L;

   private CompanionService() {
   }

   public static boolean enabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.companionEnabled.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   public static int summon(ServerPlayer player, @Nullable ResourceLocation villagerTypeId) {
      if (!enabled()) {
         player.sendSystemMessage(Component.translatable("command.millenaire.companion.disabled"));
         return 0;
      }
      ServerLevel level = player.serverLevel();
      CompanionBondSavedData data = CompanionBondSavedData.get(level);
      CompanionBond existing = data.get(player.getUUID());
      if (existing != null && existing.companionEntityId() != null && !existing.dismissed()) {
         Entity ent = level.getEntity(existing.companionEntityId());
         if (ent instanceof MillVillager alive && alive.isAlive()) {
            player.sendSystemMessage(Component.translatable("command.millenaire.companion.already_active"));
            return 0;
         }
      }

      int range = 256;
      try {
         range = MillenaireServerConfig.SERVER.companionSummonVillageRange.getAsInt();
      } catch (IllegalStateException ignored) {
      }
      Village village = VillageSavedData.get(level).getVillageManager().findNearestVillage(player.blockPosition(), range);
      if (village == null) {
         player.sendSystemMessage(Component.translatable("command.millenaire.companion.no_village"));
         return 0;
      }

      ResourceLocation typeId = villagerTypeId != null ? villagerTypeId : pickAdultType(village.getCultureId());
      if (typeId == null || ModCultures.getVillagerType(typeId) == null) {
         player.sendSystemMessage(Component.translatable("command.millenaire.companion.bad_type"));
         return 0;
      }

      BlockPos spawnPos = player.blockPosition().offset(player.getRandom().nextInt(3) - 1, 0, player.getRandom().nextInt(3) - 1);
      MillVillager villager = VillagerSpawnFactory.spawnInVillage(level, village, typeId, spawnPos, null);
      if (villager == null) {
         player.sendSystemMessage(Component.translatable("command.millenaire.companion.spawn_failed"));
         return 0;
      }

      bindEntity(player, villager, village, data, existing);
      player.sendSystemMessage(Component.translatable("command.millenaire.companion.summoned", villager.getName()));
      LOGGER.info("[Companion] Summoned {} for {}", villager.getUUID(), player.getGameProfile().getName());
      return 1;
   }

   public static int bind(ServerPlayer player, MillVillager villager) {
      if (!enabled()) {
         player.sendSystemMessage(Component.translatable("command.millenaire.companion.disabled"));
         return 0;
      }
      if (villager.getVillageId() == null) {
         player.sendSystemMessage(Component.translatable("command.millenaire.companion.need_village"));
         return 0;
      }
      ServerLevel level = player.serverLevel();
      Village village = Village.resolve(level, villager.getVillageId());
      if (village == null) {
         player.sendSystemMessage(Component.translatable("command.millenaire.companion.no_village"));
         return 0;
      }
      CompanionBondSavedData data = CompanionBondSavedData.get(level);
      CompanionBond existing = data.get(player.getUUID());
      if (existing != null && existing.companionEntityId() != null && !existing.dismissed()) {
         Entity ent = level.getEntity(existing.companionEntityId());
         if (ent instanceof MillVillager other && other.isAlive() && !other.getUUID().equals(villager.getUUID())) {
            player.sendSystemMessage(Component.translatable("command.millenaire.companion.already_active"));
            return 0;
         }
      }
      bindEntity(player, villager, village, data, existing);
      player.sendSystemMessage(Component.translatable("command.millenaire.companion.bound", villager.getName()));
      return 1;
   }

   public static int dismiss(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      CompanionBondSavedData data = CompanionBondSavedData.get(level);
      CompanionBond bond = data.get(player.getUUID());
      if (bond == null || bond.companionEntityId() == null) {
         player.sendSystemMessage(Component.translatable("command.millenaire.companion.none"));
         return 0;
      }
      Entity ent = level.getEntity(bond.companionEntityId());
      if (ent instanceof MillVillager villager) {
         CompanionBrain.clear(villager.getUUID());
         villager.clearCompanionState();
         villager.discard();
      }
      bond.setDismissed(true);
      data.markChanged();
      player.sendSystemMessage(Component.translatable("command.millenaire.companion.dismissed"));
      return 1;
   }

   public static int status(ServerPlayer player) {
      CompanionBondSavedData data = CompanionBondSavedData.get(player.serverLevel());
      CompanionBond bond = data.get(player.getUUID());
      if (bond == null) {
         player.sendSystemMessage(Component.translatable("command.millenaire.companion.status.none"));
         return 1;
      }
      boolean alive = false;
      if (bond.companionEntityId() != null) {
         Entity ent = player.serverLevel().getEntity(bond.companionEntityId());
         alive = ent instanceof MillVillager v && v.isAlive();
      }
      player.sendSystemMessage(
         Component.translatable(
            "command.millenaire.companion.status.detail",
            String.valueOf(alive),
            bond.stance().name(),
            String.valueOf(bond.dismissed()),
            String.join(", ", org.millenaire.companion.capability.CompanionCapabilityRegistry.availableIds())
         )
      );
      return 1;
   }

   private static void bindEntity(
      ServerPlayer player, MillVillager villager, Village village, CompanionBondSavedData data, @Nullable CompanionBond existing
   ) {
      CompanionBond bond = existing != null ? existing : data.getOrCreate(player.getUUID());
      bond.setCompanionEntityId(villager.getUUID());
      bond.setVillagerTypeId(villager.getVillagerTypeId());
      bond.setVillageId(village.getId());
      bond.setCultureId(village.getCultureId());
      bond.setStance(CompanionStance.FREE_ROAM);
      bond.setBondedGameDay(player.serverLevel().getDayTime() / 24000L);
      bond.setDismissed(false);
      data.put(bond);

      villager.setCompanionState(player.getUUID(), true);
      villager.setHireState(player.getUUID(), COMPANION_HIRE_UNTIL);
      villager.setAggressiveStance(true);
   }

   @Nullable
   private static ResourceLocation pickAdultType(@Nullable ResourceLocation cultureId) {
      if (cultureId == null) {
         return null;
      }
      Culture culture = ModCultures.getCulture(cultureId);
      if (culture == null) {
         return null;
      }
      List<ResourceLocation> adults = new ArrayList<>();
      for (Map.Entry<ResourceLocation, VillagerType> e : ModCultures.getAllVillagerTypes().entrySet()) {
         VillagerType t = e.getValue();
         if (t == null) {
            continue;
         }
         if (cultureId.equals(t.culture()) && !t.isChild()) {
            adults.add(e.getKey());
         }
      }
      if (adults.isEmpty()) {
         return null;
      }
      return adults.get(0);
   }
}
