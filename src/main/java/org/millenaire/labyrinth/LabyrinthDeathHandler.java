package org.millenaire.labyrinth;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.labyrinth.portal.LabyrinthTeleporter;

/**
 * Respawn players who die in the Labyrinth inside the Arrival chamber (config-gated).
 */
@EventBusSubscriber(modid = LabyrinthKeys.MODID)
public final class LabyrinthDeathHandler {
   private LabyrinthDeathHandler() {
   }

   @SubscribeEvent
   public static void onDeath(LivingDeathEvent event) {
      if (!(event.getEntity() instanceof ServerPlayer player)) {
         return;
      }
      if (!player.level().dimension().equals(LabyrinthKeys.LABYRINTH_LEVEL)) {
         return;
      }
      if (!MillenaireServerConfig.SERVER.labyrinthArrivalRespawn.getAsBoolean()) {
         return;
      }
      LabyrinthTeleporter.markDiedInside(player);
   }

   @SubscribeEvent
   public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
      Player entity = event.getEntity();
      if (!(entity instanceof ServerPlayer player)) {
         return;
      }
      if (!MillenaireServerConfig.SERVER.labyrinthArrivalRespawn.getAsBoolean()) {
         return;
      }
      if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
         return;
      }
      if (!LabyrinthTeleporter.wasInLabyrinthBeforeDeath(player)) {
         return;
      }
      ServerLevel labyrinth = LabyrinthTeleporter.getOrCreateLabyrinth(player.server);
      if (labyrinth == null) {
         return;
      }
      LabyrinthTeleporter.respawnAtArrival(player, labyrinth);
   }
}
