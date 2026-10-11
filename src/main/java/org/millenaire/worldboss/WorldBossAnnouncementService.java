package org.millenaire.worldboss;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class WorldBossAnnouncementService {
   private WorldBossAnnouncementService() {
   }

   public static void broadcastNear(ServerLevel level, BlockPos pos, double range, Component message) {
      if (level == null || message == null) {
         return;
      }
      for (ServerPlayer player : level.players()) {
         if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= range * range) {
            player.displayClientMessage(message, false);
         }
      }
   }

   public static void announceKey(ServerLevel level, BlockPos pos, String key) {
      broadcastNear(level, pos, 128.0, Component.translatable(key));
      level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 0.85F, 1.35F);
   }

   public static void softSignal(ServerLevel level, BlockPos pos) {
      level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.7F, 1.4F);
      ((net.minecraft.server.level.ServerLevel) level)
         .sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 2.0, pos.getZ() + 0.5, 24, 1.2, 1.5, 1.2, 0.02);
   }
}
