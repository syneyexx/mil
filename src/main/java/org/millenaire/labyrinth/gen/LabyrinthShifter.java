package org.millenaire.labyrinth.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.labyrinth.LabyrinthKeys;

/**
 * Scripted Core-arena shifts during Architect phase 3.
 * Only mutates a tight Core radius; never rewrites arbitrary loaded corridors.
 */
public final class LabyrinthShifter {
   private static final int RADIUS = 6;

   private LabyrinthShifter() {
   }

   public static void shiftCoreArena(ServerLevel level, BlockPos around) {
      try {
         if (!MillenaireServerConfig.SERVER.labyrinthShiftingEnabled.getAsBoolean()) {
            return;
         }
      } catch (IllegalStateException ignored) {
         return;
      }

      int coreX = LabyrinthKeys.CORE_CELL_X * LabyrinthKeys.CELL_SIZE + LabyrinthKeys.CELL_SIZE / 2;
      int coreZ = LabyrinthKeys.CORE_CELL_Z * LabyrinthKeys.CELL_SIZE + LabyrinthKeys.CELL_SIZE / 2;
      BlockPos core = new BlockPos(coreX, LabyrinthKeys.FLOOR_Y + 1, coreZ);
      if (around.distSqr(core) > 40.0 * 40.0) {
         // Safety: only shift near the true Core.
         around = core;
      }

      long pulse = level.getGameTime();
      BlockState bars = Blocks.IRON_BARS.defaultBlockState();
      BlockState air = Blocks.AIR.defaultBlockState();
      BlockState chain = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
      BlockState crying = Blocks.CRYING_OBSIDIAN.defaultBlockState();

      for (int dx = -RADIUS; dx <= RADIUS; dx++) {
         for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            if (Math.abs(dx) != RADIUS && Math.abs(dz) != RADIUS) {
               continue;
            }
            // Alternate gate segments open/closed each pulse.
            boolean seal = ((dx + dz + (int) (pulse / 200)) & 1) == 0;
            for (int y = LabyrinthKeys.FLOOR_Y + 1; y < LabyrinthKeys.CEILING_Y; y++) {
               BlockPos pos = new BlockPos(coreX + dx, y, coreZ + dz);
               if (!level.isLoaded(pos)) {
                  continue;
               }
               BlockState current = level.getBlockState(pos);
               if (seal) {
                  if (current.isAir() || current.is(Blocks.IRON_BARS) || current.is(Blocks.CHAIN)) {
                     level.setBlock(pos, (y & 1) == 0 ? bars : chain, 3);
                  }
               } else if (current.is(Blocks.IRON_BARS) || current.is(Blocks.CHAIN)) {
                  level.setBlock(pos, air, 3);
               }
            }
         }
      }

      // Pulse floor accents.
      for (int i = 0; i < 8; i++) {
         int ox = ((int) pulse + i * 3) % (RADIUS * 2 + 1) - RADIUS;
         int oz = ((int) (pulse / 3) + i * 5) % (RADIUS * 2 + 1) - RADIUS;
         BlockPos floor = new BlockPos(coreX + ox, LabyrinthKeys.FLOOR_Y, coreZ + oz);
         if (level.isLoaded(floor)) {
            BlockState floorState = level.getBlockState(floor);
            if ((floorState.is(Blocks.POLISHED_BLACKSTONE_BRICKS) || floorState.is(Blocks.BLACKSTONE))
               && ((pulse + i) & 3) == 0) {
               level.setBlock(floor, crying, 3);
            }
         }
      }
   }
}
