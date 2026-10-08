package org.millenaire.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.village.Village;

public final class ServerPayloadGuards {
   public static final double VILLAGE_INTERACTION_RANGE = 64.0;
   private static final double VILLAGE_EDGE_MARGIN = 30.0;
   public static final double WAND_VILLAGE_PICKUP_RANGE = 100.0;

   private ServerPayloadGuards() {
   }

   public static boolean isWithinVillageReach(ServerPlayer player, Village village) {
      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      double allowed = Math.max(100.0, (vt != null ? vt.radius() : 0) + 30.0);
      return isWithinDistance(player, village.getCenter(), allowed);
   }

   public static boolean isOperatorOrSingleplayerOwner(ServerPlayer player) {
      return player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile());
   }

   public static boolean isWithinDistance(ServerPlayer player, Entity target, double maxBlocks) {
      return player.distanceToSqr(target) <= maxBlocks * maxBlocks;
   }

   public static boolean isWithinDistance(ServerPlayer player, BlockPos pos, double maxBlocks) {
      return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= maxBlocks * maxBlocks;
   }
}
