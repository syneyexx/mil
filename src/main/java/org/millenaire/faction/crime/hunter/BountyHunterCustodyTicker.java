package org.millenaire.faction.crime.hunter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.millenaire.faction.crime.PlayerArrestSavedData;

/**
 * Enforces jail sentence duration, containment, and safe release.
 */
public final class BountyHunterCustodyTicker {
   private static final double ESCAPE_RADIUS_SQ = 12.0 * 12.0;
   private static final int ACTIONBAR_INTERVAL = 40;

   private BountyHunterCustodyTicker() {
   }

   public static void tick(ServerLevel level) {
      if (level == null || !BountyEligibilityService.isHunterSystemEnabled()) {
         // Still process releases even if system disabled so players are never stuck.
      }
      BountyHunterCustodySavedData data = BountyHunterCustodySavedData.get(level);
      long now = level.getGameTime();
      List<UUID> toClear = new ArrayList<>();

      for (BountyHunterCustodySavedData.CustodyRecord record : data.all()) {
         ServerPlayer player = level.getServer().getPlayerList().getPlayer(record.playerId());
         if (player == null) {
            continue;
         }
         if (!(player.level() instanceof ServerLevel playerLevel)) {
            continue;
         }

         if (now >= record.releaseAtGameTime()) {
            releasePlayer(playerLevel, player, record);
            toClear.add(record.playerId());
            continue;
         }

         // Soft containment: return to cell if they wander too far while jailed.
         if (record.jailPos() != null && playerLevel.dimension() == level.dimension()) {
            double distSq = player.position().distanceToSqr(Vec3.atCenterOf(record.jailPos()));
            if (distSq > ESCAPE_RADIUS_SQ) {
               safeTeleport(player, record.jailPos());
            }
         }

         if (now % ACTIONBAR_INTERVAL == 0L) {
            long remainingSec = Math.max(0L, (record.releaseAtGameTime() - now + 19L) / 20L);
            player.displayClientMessage(
               Component.translatable("millenaire.bounty_hunter.jail_remaining", remainingSec),
               true
            );
         }
      }

      for (UUID id : toClear) {
         data.clear(id);
         PlayerArrestSavedData.get(level).clear(id);
      }
   }

   public static void onPlayerLogin(ServerPlayer player) {
      if (player == null || !(player.level() instanceof ServerLevel level)) {
         return;
      }
      BountyHunterCustodySavedData.CustodyRecord record = BountyHunterCustodySavedData.get(level).get(player.getUUID());
      if (record == null) {
         return;
      }
      long now = level.getGameTime();
      if (now >= record.releaseAtGameTime()) {
         releasePlayer(level, player, record);
         BountyHunterCustodySavedData.get(level).clear(player.getUUID());
         PlayerArrestSavedData.get(level).clear(player.getUUID());
         return;
      }
      if (record.jailPos() != null) {
         safeTeleport(player, record.jailPos());
      }
      player.sendSystemMessage(Component.translatable("millenaire.bounty_hunter.still_jailed"));
   }

   private static void releasePlayer(ServerLevel level, ServerPlayer player, BountyHunterCustodySavedData.CustodyRecord record) {
      BlockPos release = record.releasePos() != null ? record.releasePos() : record.jailPos();
      if (release != null) {
         safeTeleport(player, release);
      }
      player.removeAllEffects();
      player.sendSystemMessage(Component.translatable("millenaire.bounty_hunter.released"));
      player.displayClientMessage(Component.translatable("millenaire.bounty_hunter.released"), true);
   }

   private static void safeTeleport(ServerPlayer player, BlockPos pos) {
      if (player == null || pos == null) {
         return;
      }
      BlockPos stand = pos;
      if (player.level() instanceof ServerLevel level) {
         for (int dy = 0; dy < 4; dy++) {
            BlockPos probe = pos.above(dy);
            if (BountyHunterJailResolver.isSafeStandPos(level, probe)) {
               stand = probe;
               break;
            }
         }
      }
      player.teleportTo(stand.getX() + 0.5, stand.getY() + 0.1, stand.getZ() + 0.5);
   }
}
