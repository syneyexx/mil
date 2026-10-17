package org.millenaire.labyrinth.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.labyrinth.LabyrinthKeys;

/**
 * Scripted geometry shifts: Core arena (Architect phase 3) plus rare
 * gate-proximity corridor seals (Wave 4) when a player is near GATE hubs.
 */
@EventBusSubscriber(modid = LabyrinthKeys.MODID)
public final class LabyrinthShifter {
   private static final int RADIUS = 6;
   private static final int GATE_RADIUS = 4;

   private LabyrinthShifter() {
   }

   public static void shiftCoreArena(ServerLevel level, BlockPos around) {
      if (!shiftingEnabled()) {
         return;
      }

      int coreX = LabyrinthKeys.coreCenterBlockX();
      int coreZ = LabyrinthKeys.coreCenterBlockZ();
      BlockPos core = new BlockPos(coreX, LabyrinthKeys.FLOOR_Y + 1, coreZ);
      if (around.distSqr(core) > 40.0 * 40.0) {
         around = core;
      }

      pulseRing(level, coreX, coreZ, RADIUS, LabyrinthKeys.FLOOR_Y, LabyrinthKeys.CEILING_Y, level.getGameTime());
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent.Post event) {
      if (!shiftingEnabled()) {
         return;
      }
      ServerLevel overworld = event.getServer().overworld();
      if (overworld == null || overworld.getGameTime() % 200 != 0) {
         return;
      }
      ServerLevel labyrinth = event.getServer().getLevel(LabyrinthKeys.LABYRINTH_LEVEL);
      if (labyrinth == null) {
         return;
      }
      for (ServerPlayer player : labyrinth.players()) {
         maybeShiftNearGate(labyrinth, player);
      }
   }

   private static void maybeShiftNearGate(ServerLevel level, ServerPlayer player) {
      BlockPos feet = player.blockPosition();
      int[] cell = LabyrinthMaze.globalCellFromBlock(feet.getX(), feet.getZ());
      int regionX = LabyrinthMaze.regionCoordFromGlobalCell(cell[0]);
      int regionZ = LabyrinthMaze.regionCoordFromGlobalCell(cell[1]);
      LabyrinthMaze maze = LabyrinthMaze.forRegion(level.getSeed(), regionX, regionZ);
      if (maze.getRoomTypeGlobal(cell[0], cell[1]) != LabyrinthMaze.RoomType.GATE) {
         return;
      }
      int centerX = cell[0] * LabyrinthKeys.CELL_SIZE + LabyrinthKeys.CELL_SIZE / 2;
      int centerZ = cell[1] * LabyrinthKeys.CELL_SIZE + LabyrinthKeys.CELL_SIZE / 2;
      // Soft outer ring pulse — never seals the relic pedestals (inner 2).
      pulseRing(level, centerX, centerZ, GATE_RADIUS, LabyrinthKeys.FLOOR_Y, LabyrinthKeys.CEILING_Y, level.getGameTime());
   }

   private static void pulseRing(
      ServerLevel level,
      int centerX,
      int centerZ,
      int radius,
      int floorY,
      int ceilingY,
      long pulse
   ) {
      BlockState bars = Blocks.IRON_BARS.defaultBlockState();
      BlockState air = Blocks.AIR.defaultBlockState();
      BlockState chain = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
      BlockState crying = Blocks.CRYING_OBSIDIAN.defaultBlockState();

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
               continue;
            }
            boolean seal = ((dx + dz + (int) (pulse / 200)) & 1) == 0;
            for (int y = floorY + 1; y < ceilingY; y++) {
               BlockPos pos = new BlockPos(centerX + dx, y, centerZ + dz);
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

      for (int i = 0; i < 8; i++) {
         int ox = ((int) pulse + i * 3) % (radius * 2 + 1) - radius;
         int oz = ((int) (pulse / 3) + i * 5) % (radius * 2 + 1) - radius;
         BlockPos floor = new BlockPos(centerX + ox, floorY, centerZ + oz);
         if (level.isLoaded(floor)) {
            BlockState floorState = level.getBlockState(floor);
            if ((floorState.is(Blocks.POLISHED_BLACKSTONE_BRICKS) || floorState.is(Blocks.BLACKSTONE))
               && ((pulse + i) & 3) == 0) {
               level.setBlock(floor, crying, 3);
            }
         }
      }
   }

   private static boolean shiftingEnabled() {
      try {
         return MillenaireServerConfig.SERVER.labyrinthShiftingEnabled.getAsBoolean();
      } catch (IllegalStateException ignored) {
         return true;
      }
   }
}
