package org.millenaire.labyrinth.puzzle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.gen.LabyrinthMaze;

/**
 * Echo loop chambers: walking "forward" (toward +Z of the cell) resets the player
 * to the south entry with a subtle distortion. Walking back through the south
 * (crying-obsidian) threshold escapes after at least one loop iteration.
 */
@EventBusSubscriber(modid = LabyrinthKeys.MODID)
public final class EchoLoopHandler {
   private static final Map<UUID, Integer> LOOP_COUNTS = new HashMap<>();
   private static final Map<UUID, Long> LAST_TRIGGER = new HashMap<>();

   private EchoLoopHandler() {
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent.Post event) {
      if (!loopsEnabled()) {
         return;
      }
      if (event.getServer().overworld() == null || event.getServer().overworld().getGameTime() % 5 != 0) {
         return;
      }
      ServerLevel labyrinth = event.getServer().getLevel(LabyrinthKeys.LABYRINTH_LEVEL);
      if (labyrinth == null) {
         return;
      }
      for (ServerPlayer player : labyrinth.players()) {
         tickPlayer(labyrinth, player);
      }
   }

   private static void tickPlayer(ServerLevel level, ServerPlayer player) {
      BlockPos feet = player.blockPosition();
      if (LabyrinthKeys.bandForY(feet.getY()) != 1) {
         return;
      }
      int[] cell = LabyrinthMaze.globalCellFromBlock(feet.getX(), feet.getZ());
      int regionX = LabyrinthMaze.regionCoordFromGlobalCell(cell[0]);
      int regionZ = LabyrinthMaze.regionCoordFromGlobalCell(cell[1]);
      LabyrinthMaze maze = LabyrinthMaze.forRegion(level.getSeed(), regionX, regionZ);
      if (maze.getRoomTypeGlobal(cell[0], cell[1]) != LabyrinthMaze.RoomType.ECHO_LOOP) {
         LOOP_COUNTS.remove(player.getUUID());
         return;
      }

      int cellMinX = cell[0] * LabyrinthKeys.CELL_SIZE;
      int cellMinZ = cell[1] * LabyrinthKeys.CELL_SIZE;
      int centerX = cellMinX + LabyrinthKeys.CELL_SIZE / 2;
      int maxZ = cellMinZ + LabyrinthKeys.CELL_SIZE - 1;
      int minZ = cellMinZ;

      long now = level.getGameTime();
      long last = LAST_TRIGGER.getOrDefault(player.getUUID(), -100L);
      if (now - last < 40) {
         return;
      }

      // Forward threshold near north wall — loop reset.
      if (Math.abs(feet.getX() - centerX) <= 1 && feet.getZ() >= maxZ - 2) {
         int loops = LOOP_COUNTS.merge(player.getUUID(), 1, Integer::sum);
         LAST_TRIGGER.put(player.getUUID(), now);
         double destX = centerX + 0.5;
         double destY = LabyrinthKeys.FLOOR_Y + 1.0;
         double destZ = minZ + 2.5;
         player.teleportTo(destX, destY, destZ);
         player.setYRot(0.0F);
         level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_AMBIENT, SoundSource.AMBIENT, 0.5F, 0.5F);
         level.sendParticles(ParticleTypes.REVERSE_PORTAL, destX, destY + 1.0, destZ, 24, 0.4, 0.6, 0.4, 0.02);
         if (loops == 1) {
            player.displayClientMessage(Component.translatable("labyrinth.puzzle.loop.enter"), true);
         } else if (loops == 3) {
            player.displayClientMessage(Component.translatable("labyrinth.puzzle.loop.hint"), true);
         }
         return;
      }

      // Escape: south threshold after at least one loop, moving roughly southbound.
      int loops = LOOP_COUNTS.getOrDefault(player.getUUID(), 0);
      Vec3 look = player.getLookAngle();
      boolean facingBack = look.z < -0.35;
      if (loops > 0 && facingBack && Math.abs(feet.getX() - centerX) <= 1 && feet.getZ() <= minZ + 2) {
         LAST_TRIGGER.put(player.getUUID(), now);
         LOOP_COUNTS.remove(player.getUUID());
         double destX = centerX + 0.5;
         double destY = LabyrinthKeys.FLOOR_Y + 1.0;
         double destZ = minZ - 1.5;
         player.teleportTo(destX, destY, destZ);
         level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.6F, 1.4F);
         player.displayClientMessage(Component.translatable("labyrinth.puzzle.loop.escape"), true);
      }
   }

   private static boolean loopsEnabled() {
      try {
         return MillenaireServerConfig.SERVER.labyrinthLoopRoomsEnabled.getAsBoolean();
      } catch (IllegalStateException ignored) {
         return true;
      }
   }
}
