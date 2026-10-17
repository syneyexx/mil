package org.millenaire.labyrinth.puzzle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.gen.LabyrinthMaze;
import org.millenaire.labyrinth.portal.ConfiguratieVorm;

/**
 * Rotates N-S / E-W walkways in ROTARY rooms when Configuratie is used in WIEL form.
 */
public final class RotaryPuzzle {
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();
   private static final BlockState POLISHED = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
   private static final BlockState SPIKE = Blocks.POINTED_DRIPSTONE.defaultBlockState();
   private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();

   private RotaryPuzzle() {
   }

   public static boolean tryRotate(ServerPlayer player, ServerLevel level) {
      if (!puzzlesEnabled()) {
         return false;
      }
      BlockPos feet = player.blockPosition();
      int[] cell = LabyrinthMaze.globalCellFromBlock(feet.getX(), feet.getZ());
      int regionX = LabyrinthMaze.regionCoordFromGlobalCell(cell[0]);
      int regionZ = LabyrinthMaze.regionCoordFromGlobalCell(cell[1]);
      LabyrinthMaze maze = LabyrinthMaze.forRegion(level.getSeed(), regionX, regionZ);
      LabyrinthMaze.RoomType type = maze.getRoomTypeGlobal(cell[0], cell[1]);
      if (type != LabyrinthMaze.RoomType.ROTARY) {
         return false;
      }

      int cellMinX = cell[0] * LabyrinthKeys.CELL_SIZE;
      int cellMinZ = cell[1] * LabyrinthKeys.CELL_SIZE;
      int centerX = cellMinX + LabyrinthKeys.CELL_SIZE / 2;
      int centerZ = cellMinZ + LabyrinthKeys.CELL_SIZE / 2;
      BlockPos hub = new BlockPos(centerX, LabyrinthKeys.FLOOR_Y + 1, centerZ);
      if (feet.distManhattan(hub) > 3) {
         player.displayClientMessage(net.minecraft.network.chat.Component.translatable("labyrinth.puzzle.rotary.closer"), true);
         return true; // consumed feedback, still a "handled" attempt
      }

      int next = LabyrinthPuzzleData.get(level).rotateRotary(cell[0], cell[1]);
      applyWalkways(level, cellMinX, cellMinZ, centerX, centerZ, next);
      level.playSound(null, hub, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.9F, 0.7F);
      level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, hub.getX() + 0.5, hub.getY() + 1.0, hub.getZ() + 0.5, 16, 0.6, 0.4, 0.6, 0.01);
      player.displayClientMessage(
         net.minecraft.network.chat.Component.translatable("labyrinth.puzzle.rotary.turned", next % 2 == 0 ? "N/S" : "E/W"),
         true
      );
      return true;
   }

   public static boolean isWielUse(ConfiguratieVorm vorm) {
      return vorm == ConfiguratieVorm.WIEL;
   }

   /** Re-apply a saved rotation after chunk load. */
   public static void applySavedRotation(ServerLevel level, int cellX, int cellZ, int rotation) {
      int cellMinX = cellX * LabyrinthKeys.CELL_SIZE;
      int cellMinZ = cellZ * LabyrinthKeys.CELL_SIZE;
      int centerX = cellMinX + LabyrinthKeys.CELL_SIZE / 2;
      int centerZ = cellMinZ + LabyrinthKeys.CELL_SIZE / 2;
      applyWalkways(level, cellMinX, cellMinZ, centerX, centerZ, rotation);
   }

   private static void applyWalkways(ServerLevel level, int minX, int minZ, int centerX, int centerZ, int rotation) {
      int maxX = minX + LabyrinthKeys.CELL_SIZE - 1;
      int maxZ = minZ + LabyrinthKeys.CELL_SIZE - 1;
      int floor = LabyrinthKeys.FLOOR_Y;
      boolean ns = (rotation & 1) == 0;

      for (int x = minX + 1; x <= maxX - 1; x++) {
         for (int z = minZ + 1; z <= maxZ - 1; z++) {
            if (x == centerX && z == centerZ) {
               continue;
            }
            boolean onNS = x == centerX;
            boolean onEW = z == centerZ;
            BlockPos walk = new BlockPos(x, floor + 1, z);
            if (!level.isLoaded(walk)) {
               continue;
            }
            if (ns && onNS) {
               level.setBlock(walk, POLISHED, 3);
               level.setBlock(walk.above(), AIR, 3);
            } else if (!ns && onEW) {
               level.setBlock(walk, POLISHED, 3);
               level.setBlock(walk.above(), AIR, 3);
            } else if (onNS || onEW) {
               // Clear the inactive arm back to spikes.
               level.setBlock(new BlockPos(x, floor, z), BLACKSTONE, 3);
               level.setBlock(walk, SPIKE, 3);
               level.setBlock(walk.above(), AIR, 3);
            }
         }
      }
   }

   private static boolean puzzlesEnabled() {
      try {
         return MillenaireServerConfig.SERVER.labyrinthPuzzlesEnabled.getAsBoolean();
      } catch (IllegalStateException ignored) {
         return true;
      }
   }
}
