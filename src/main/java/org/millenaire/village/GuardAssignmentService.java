package org.millenaire.village;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;

/** Assign / relieve player-chosen guards via villager type swap. */
public final class GuardAssignmentService {
   private GuardAssignmentService() {
   }

   public static boolean assignGuard(ServerLevel level, Village village, UUID villagerUuid) {
      VillagerRecord record = village.getVillagerRecord(villagerUuid);
      if (record == null || record.isKilled()) {
         return false;
      }
      if (record.isPlayerAssignedGuard()) {
         return true;
      }
      ResourceLocation guardType = findGuardType(village.getCultureId());
      if (guardType == null) {
         return false;
      }
      ResourceLocation previous = record.getVillagerTypeId();
      record.setPreviousVillagerTypeId(previous);
      record.setVillagerTypeId(guardType);
      record.setPlayerAssignedGuard(true);
      applyLive(level, villagerUuid, guardType);
      village.markDirty();
      return true;
   }

   public static boolean relieveGuard(ServerLevel level, Village village, UUID villagerUuid) {
      VillagerRecord record = village.getVillagerRecord(villagerUuid);
      if (record == null || !record.isPlayerAssignedGuard()) {
         return false;
      }
      ResourceLocation previous = record.getPreviousVillagerTypeId();
      if (previous == null) {
         previous = findCivilianType(village.getCultureId());
      }
      if (previous == null) {
         return false;
      }
      record.setVillagerTypeId(previous);
      record.setPreviousVillagerTypeId(null);
      record.setPlayerAssignedGuard(false);
      applyLive(level, villagerUuid, previous);
      village.markDirty();
      return true;
   }

   private static void applyLive(ServerLevel level, UUID uuid, ResourceLocation typeId) {
      Entity entity = level.getEntity(uuid);
      if (entity instanceof MillVillager villager) {
         villager.setVillagerTypeId(typeId);
      }
   }

   @Nullable
   public static ResourceLocation findGuardType(@Nullable ResourceLocation cultureId) {
      if (cultureId == null) {
         return null;
      }
      ResourceLocation fallback = null;
      for (VillagerType type : ModCultures.getAllVillagerTypes().values()) {
         if (!cultureId.equals(type.culture()) || type.isHostile() || type.isChild()) {
            continue;
         }
         if (type.isHelpInAttacks()) {
            String path = type.id().getPath().toLowerCase();
            if (path.contains("guard") || path.contains("soldier")) {
               return type.id();
            }
            if (fallback == null) {
               fallback = type.id();
            }
         }
      }
      return fallback;
   }

   @Nullable
   private static ResourceLocation findCivilianType(@Nullable ResourceLocation cultureId) {
      if (cultureId == null) {
         return null;
      }
      for (VillagerType type : ModCultures.getAllVillagerTypes().values()) {
         if (!cultureId.equals(type.culture()) || type.isHostile() || type.isChild() || type.isHelpInAttacks()) {
            continue;
         }
         String path = type.id().getPath().toLowerCase();
         if (path.contains("peasant") || path.contains("farmer") || path.contains("wife") || path.contains("man")) {
            return type.id();
         }
      }
      for (VillagerType type : ModCultures.getAllVillagerTypes().values()) {
         if (cultureId.equals(type.culture()) && !type.isHostile() && !type.isChild() && !type.isHelpInAttacks()) {
            return type.id();
         }
      }
      return null;
   }
}
