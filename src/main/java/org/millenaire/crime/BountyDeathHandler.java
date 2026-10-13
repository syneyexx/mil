package org.millenaire.crime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import org.millenaire.config.MillenaireServerConfig;

/**
 * Clears per-culture bounty on player death (config-gated) and refreshes the HUD on respawn.
 */
public final class BountyDeathHandler {
   private BountyDeathHandler() {
   }

   public static void onLivingDeath(LivingDeathEvent event) {
      if (!(event.getEntity() instanceof ServerPlayer player)) {
         return;
      }
      if (!MillenaireServerConfig.SERVER.resetBountyOnDeath.getAsBoolean()) {
         return;
      }
      if (!(player.level() instanceof ServerLevel level)) {
         return;
      }
      PlayerBountySavedData.get(level).clearAll(player.getUUID());
   }

   public static void onPlayerRespawn(PlayerRespawnEvent event) {
      if (!(event.getEntity() instanceof ServerPlayer player)) {
         return;
      }
      BountyHudSync.syncToPlayer(player);
   }
}
