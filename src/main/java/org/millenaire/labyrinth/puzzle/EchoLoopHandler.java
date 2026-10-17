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
 * Soft-loop chambers (P3.4):
 * <ul>
 *   <li>{@link LabyrinthMaze.RoomType#ECHO_LOOP} — north traps, south escape (face back)</li>
 *   <li>{@link LabyrinthMaze.RoomType#MIRROR_LOOP} — east traps, west escape (face back)</li>
 * </ul>
 * Escape is always possible after ≥1 loop. After 4 fails, a clear hint is shown (anti soft-lock).
 * Supports multi-cell suite footprints via resolved anchors.
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
      int localX = LabyrinthMaze.localCellFromGlobal(cell[0]);
      int localZ = LabyrinthMaze.localCellFromGlobal(cell[1]);
      LabyrinthMaze.RoomType type = maze.getResolvedRoomType(localX, localZ);
      if (!LabyrinthMaze.isLoopRoom(type)) {
         LOOP_COUNTS.remove(player.getUUID());
         return;
      }

      int anchorLocalX = maze.getAnchorLocalX(localX, localZ);
      int anchorLocalZ = maze.getAnchorLocalZ(localX, localZ);
      int span = maze.getFootprintSpan(localX, localZ);
      int globalAnchorX = regionX * maze.regionSize() + anchorLocalX;
      int globalAnchorZ = regionZ * maze.regionSize() + anchorLocalZ;
      int minX = globalAnchorX * LabyrinthKeys.CELL_SIZE;
      int minZ = globalAnchorZ * LabyrinthKeys.CELL_SIZE;
      int maxX = minX + span * LabyrinthKeys.CELL_SIZE - 1;
      int maxZ = minZ + span * LabyrinthKeys.CELL_SIZE - 1;
      int centerX = (minX + maxX) / 2;
      int centerZ = (minZ + maxZ) / 2;

      long now = level.getGameTime();
      long last = LAST_TRIGGER.getOrDefault(player.getUUID(), -100L);
      if (now - last < 40) {
         return;
      }

      if (type == LabyrinthMaze.RoomType.ECHO_LOOP) {
         tickEcho(level, player, feet, centerX, minZ, maxZ, now);
      } else {
         tickMirror(level, player, feet, centerZ, minX, maxX, now);
      }
   }

   private static void tickEcho(
      ServerLevel level,
      ServerPlayer player,
      BlockPos feet,
      int centerX,
      int minZ,
      int maxZ,
      long now
   ) {
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
         announceLoop(player, loops, "echo");
         return;
      }

      int loops = LOOP_COUNTS.getOrDefault(player.getUUID(), 0);
      Vec3 look = player.getLookAngle();
      boolean facingBack = look.z < -0.35;
      if (loops > 0 && facingBack && Math.abs(feet.getX() - centerX) <= 1 && feet.getZ() <= minZ + 2) {
         escape(level, player, centerX + 0.5, minZ - 1.5, now);
      }
   }

   private static void tickMirror(
      ServerLevel level,
      ServerPlayer player,
      BlockPos feet,
      int centerZ,
      int minX,
      int maxX,
      long now
   ) {
      // East wall traps — reset to west entry.
      if (Math.abs(feet.getZ() - centerZ) <= 1 && feet.getX() >= maxX - 2) {
         int loops = LOOP_COUNTS.merge(player.getUUID(), 1, Integer::sum);
         LAST_TRIGGER.put(player.getUUID(), now);
         double destX = minX + 2.5;
         double destY = LabyrinthKeys.FLOOR_Y + 1.0;
         double destZ = centerZ + 0.5;
         player.teleportTo(destX, destY, destZ);
         player.setYRot(-90.0F);
         level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_AMBIENT, SoundSource.AMBIENT, 0.5F, 0.65F);
         level.sendParticles(ParticleTypes.REVERSE_PORTAL, destX, destY + 1.0, destZ, 24, 0.4, 0.6, 0.4, 0.02);
         announceLoop(player, loops, "mirror");
         return;
      }

      int loops = LOOP_COUNTS.getOrDefault(player.getUUID(), 0);
      Vec3 look = player.getLookAngle();
      boolean facingBack = look.x < -0.35;
      if (loops > 0 && facingBack && Math.abs(feet.getZ() - centerZ) <= 1 && feet.getX() <= minX + 2) {
         escape(level, player, minX - 1.5, centerZ + 0.5, now);
      }
   }

   private static void announceLoop(ServerPlayer player, int loops, String layout) {
      if (loops == 1) {
         player.displayClientMessage(Component.translatable("labyrinth.puzzle.loop.enter"), true);
      } else if (loops == 3) {
         player.displayClientMessage(Component.translatable("labyrinth.puzzle.loop.hint"), true);
      } else if (loops >= 4) {
         // Guaranteed escape hint after repeated fails (Gids/Archivaris-style).
         player.displayClientMessage(Component.translatable("labyrinth.puzzle.loop.hint_strong." + layout), false);
      }
   }

   private static void escape(ServerLevel level, ServerPlayer player, double destX, double destZ, long now) {
      LAST_TRIGGER.put(player.getUUID(), now);
      LOOP_COUNTS.remove(player.getUUID());
      double destY = LabyrinthKeys.FLOOR_Y + 1.0;
      player.teleportTo(destX, destY, destZ);
      level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.6F, 1.4F);
      player.displayClientMessage(Component.translatable("labyrinth.puzzle.loop.escape"), true);
   }

   private static boolean loopsEnabled() {
      try {
         return MillenaireServerConfig.SERVER.labyrinthLoopRoomsEnabled.getAsBoolean();
      } catch (IllegalStateException ignored) {
         return true;
      }
   }
}
