package org.millenaire.labyrinth.liminal;

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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.gen.LabyrinthMaze;

/**
 * Mind-fuck cues inside furnished liminal rooms: enter whisper, clock ticks,
 * occasional "something moved" particle pops — domestic calm, then wrong.
 */
@EventBusSubscriber(modid = LabyrinthKeys.MODID)
public final class LiminalAmbienceHandler {
   private static final Map<UUID, LabyrinthMaze.RoomType> LAST_ROOM = new HashMap<>();
   private static final Map<UUID, Long> LAST_WHISPER = new HashMap<>();

   private LiminalAmbienceHandler() {
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent.Post event) {
      if (!liminalEnabled()) {
         return;
      }
      ServerLevel overworld = event.getServer().overworld();
      if (overworld == null || overworld.getGameTime() % 10 != 0) {
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
      int[] cell = LabyrinthMaze.globalCellFromBlock(feet.getX(), feet.getZ());
      int regionX = LabyrinthMaze.regionCoordFromGlobalCell(cell[0]);
      int regionZ = LabyrinthMaze.regionCoordFromGlobalCell(cell[1]);
      LabyrinthMaze maze = LabyrinthMaze.forRegion(level.getSeed(), regionX, regionZ);
      int localX = LabyrinthMaze.localCellFromGlobal(cell[0]);
      int localZ = LabyrinthMaze.localCellFromGlobal(cell[1]);
      LabyrinthMaze.RoomType type = maze.getResolvedRoomType(localX, localZ);

      LabyrinthMaze.RoomType previous = LAST_ROOM.put(player.getUUID(), type);
      if (!LabyrinthMaze.isLiminal(type)) {
         // Subtle cold cue when leaving a domestic suite (warm→cold, no UI theater).
         if (previous != null && LabyrinthMaze.isLiminal(previous)) {
            level.playSound(null, feet, SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.2F, 0.45F);
         }
         return;
      }

      long now = level.getGameTime();
      if (previous != type) {
         String key = switch (type) {
            case LIMINAL_BEDROOM -> "labyrinth.liminal.enter.bedroom";
            case LIMINAL_KITCHEN -> "labyrinth.liminal.enter.kitchen";
            case LIMINAL_STUDY -> "labyrinth.liminal.enter.study";
            case LIMINAL_PARLOR -> "labyrinth.liminal.enter.parlor";
            case LIMINAL_NURSERY -> "labyrinth.liminal.enter.nursery";
            case ECHO_LOOP -> "labyrinth.liminal.enter.echo";
            case MIRROR_LOOP -> "labyrinth.liminal.enter.mirror";
            default -> "labyrinth.liminal.enter.generic";
         };
         player.displayClientMessage(Component.translatable(key), true);
         level.playSound(null, feet, SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.35F, 0.6F);
         LAST_WHISPER.put(player.getUUID(), now);
      }

      // Sparse domestic audio that shouldn't exist here.
      if (now - LAST_WHISPER.getOrDefault(player.getUUID(), 0L) > 80 && level.getRandom().nextInt(14) == 0) {
         LAST_WHISPER.put(player.getUUID(), now);
         if (level.getRandom().nextBoolean()) {
            level.playSound(null, feet, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.AMBIENT, 0.25F, 0.5F);
         } else {
            level.playSound(null, feet, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.AMBIENT, 0.3F, 0.8F);
            player.displayClientMessage(Component.translatable("labyrinth.liminal.whisper"), true);
         }
         level.sendParticles(
            ParticleTypes.SMOKE,
            player.getX(),
            player.getY() + 1.2,
            player.getZ(),
            6,
            0.4,
            0.3,
            0.4,
            0.01
         );
      }
   }

   private static boolean liminalEnabled() {
      try {
         return MillenaireServerConfig.SERVER.labyrinthLiminalRoomsEnabled.getAsBoolean();
      } catch (IllegalStateException ignored) {
         return true;
      }
   }
}
