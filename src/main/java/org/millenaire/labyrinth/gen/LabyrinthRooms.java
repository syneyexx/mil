package org.millenaire.labyrinth.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.millenaire.block.ModBlocks;
import org.millenaire.labyrinth.LabyrinthKeys;

/**
 * Stamps themed room interiors into a chunk given cell world bounds.
 * Only writes blocks that fall inside the current chunk.
 */
public final class LabyrinthRooms {
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();
   private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
   private static final BlockState POLISHED = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
   private static final BlockState BRICKS = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
   private static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
   private static final BlockState LODESTONE = Blocks.LODESTONE.defaultBlockState();
   private static final BlockState CRYING = Blocks.CRYING_OBSIDIAN.defaultBlockState();
   private static final BlockState BARS = Blocks.IRON_BARS.defaultBlockState();
   private static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
   private static final BlockState HANGING_LANTERN = Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
   private static final BlockState CANDLE = Blocks.BLACK_CANDLE.defaultBlockState()
      .setValue(CandleBlock.CANDLES, 2)
      .setValue(CandleBlock.LIT, true);

   private LabyrinthRooms() {
   }

   public static void stampRoom(
      ChunkAccess chunk,
      LabyrinthMaze.RoomType type,
      int cellMinX,
      int cellMinZ,
      long worldSeed,
      RandomSource random
   ) {
      int cellMaxX = cellMinX + LabyrinthKeys.CELL_SIZE - 1;
      int cellMaxZ = cellMinZ + LabyrinthKeys.CELL_SIZE - 1;
      int centerX = cellMinX + LabyrinthKeys.CELL_SIZE / 2;
      int centerZ = cellMinZ + LabyrinthKeys.CELL_SIZE / 2;

      switch (type) {
         case ARRIVAL -> stampArrival(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ);
         case CORE -> stampCore(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ, random);
         case HOST_HALL -> stampHostHall(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ);
         case FORGE -> stampForge(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ);
         case ARCHIVE -> stampArchive(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ);
         case CHAPEL -> stampChapel(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ);
         case GATE -> stampGate(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ);
         case NICHE -> stampNiche(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ, random);
         default -> {
         }
      }
   }

   private static void stampArrival(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int centerX,
      int centerZ
   ) {
      int floor = LabyrinthKeys.FLOOR_Y;
      int ceiling = LabyrinthKeys.CEILING_Y;

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            if (!inChunk(chunk, x, z)) {
               continue;
            }
            boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
            set(chunk, x, floor, z, BRICKS);
            if (edge) {
               for (int y = floor + 1; y < ceiling; y++) {
                  set(chunk, x, y, z, BRICKS);
               }
            } else {
               for (int y = floor + 1; y < ceiling; y++) {
                  set(chunk, x, y, z, AIR);
               }
               set(chunk, x, ceiling, z, POLISHED);
            }
         }
      }

      // Exit Scharnier on a lodestone pedestal (block registered as millenaire:scharnier).
      set(chunk, centerX, floor - 1, centerZ, GOLD);
      set(chunk, centerX, floor, centerZ, LODESTONE);
      set(chunk, centerX, floor + 1, centerZ, scharnierState());

      placeHangingLantern(chunk, centerX, ceiling - 1, centerZ);
      placeHangingLantern(chunk, centerX - 2, ceiling - 1, centerZ - 2);
      placeHangingLantern(chunk, centerX + 2, ceiling - 1, centerZ + 2);

      set(chunk, centerX, floor + 1, centerZ - 2, CHAIN);
      set(chunk, centerX, floor + 2, centerZ - 2, CHAIN);
      set(chunk, minX + 1, floor + 1, minZ + 1, BARS);
      set(chunk, maxX - 1, floor + 1, maxZ - 1, BARS);
   }

   private static void stampCore(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int centerX,
      int centerZ,
      RandomSource random
   ) {
      int floor = LabyrinthKeys.FLOOR_Y;
      int ceiling = LabyrinthKeys.CEILING_Y;

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            if (!inChunk(chunk, x, z)) {
               continue;
            }
            boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
            set(chunk, x, floor, z, POLISHED);
            if (edge) {
               for (int y = floor + 1; y < ceiling; y++) {
                  BlockState wall = ((x + z + y) & 3) == 0 ? CRYING : BRICKS;
                  set(chunk, x, y, z, wall);
               }
            } else {
               for (int y = floor + 1; y < ceiling; y++) {
                  set(chunk, x, y, z, AIR);
               }
               set(chunk, x, ceiling, z, CRYING);
            }
         }
      }

      set(chunk, centerX, floor, centerZ, CRYING);
      set(chunk, centerX, floor + 1, centerZ, CRYING);
      placeHangingLantern(chunk, centerX, ceiling - 1, centerZ);
      set(chunk, centerX - 1, floor + 1, centerZ, CHAIN);
      set(chunk, centerX + 1, floor + 1, centerZ, CHAIN);
      set(chunk, centerX, floor + 1, centerZ - 1, BARS);
      set(chunk, centerX, floor + 1, centerZ + 1, BARS);
      if (random.nextBoolean()) {
         set(chunk, centerX - 2, floor + 1, centerZ - 2, CANDLE);
      }
   }

   private static void stampHostHall(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int centerX,
      int centerZ
   ) {
      paveFloor(chunk, minX, minZ, maxX, maxZ, BRICKS);
      clearInterior(chunk, minX, minZ, maxX, maxZ);
      set(chunk, centerX, LabyrinthKeys.FLOOR_Y + 1, centerZ, Blocks.LECTERN.defaultBlockState()
         .setValue(LecternBlock.FACING, Direction.SOUTH)
         .setValue(LecternBlock.HAS_BOOK, true));
      set(chunk, centerX - 2, LabyrinthKeys.FLOOR_Y + 1, centerZ, CANDLE);
      set(chunk, centerX + 2, LabyrinthKeys.FLOOR_Y + 1, centerZ, CANDLE);
      placeHangingLantern(chunk, centerX, LabyrinthKeys.CEILING_Y - 1, centerZ);
      set(chunk, centerX, LabyrinthKeys.FLOOR_Y + 2, centerZ - 2, CHAIN);
      set(chunk, minX + 1, LabyrinthKeys.FLOOR_Y + 1, maxZ - 1, BARS);
      set(chunk, maxX - 1, LabyrinthKeys.FLOOR_Y + 1, maxZ - 1, BARS);
   }

   private static void stampForge(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int centerX,
      int centerZ
   ) {
      paveFloor(chunk, minX, minZ, maxX, maxZ, BLACKSTONE);
      clearInterior(chunk, minX, minZ, maxX, maxZ);
      set(chunk, centerX, LabyrinthKeys.FLOOR_Y + 1, centerZ, Blocks.ANVIL.defaultBlockState());
      set(chunk, centerX + 2, LabyrinthKeys.FLOOR_Y + 1, centerZ, Blocks.BLAST_FURNACE.defaultBlockState());
      set(chunk, centerX - 2, LabyrinthKeys.FLOOR_Y + 1, centerZ, Blocks.SMITHING_TABLE.defaultBlockState());
      set(chunk, centerX, LabyrinthKeys.FLOOR_Y + 1, centerZ + 2, Blocks.IRON_BLOCK.defaultBlockState());
      placeHangingLantern(chunk, centerX, LabyrinthKeys.CEILING_Y - 1, centerZ);
      set(chunk, centerX - 1, LabyrinthKeys.CEILING_Y - 1, centerZ, CHAIN);
      set(chunk, centerX + 1, LabyrinthKeys.CEILING_Y - 1, centerZ, CHAIN);
   }

   private static void stampArchive(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int centerX,
      int centerZ
   ) {
      paveFloor(chunk, minX, minZ, maxX, maxZ, POLISHED);
      clearInterior(chunk, minX, minZ, maxX, maxZ);
      set(chunk, centerX, LabyrinthKeys.FLOOR_Y + 1, centerZ, Blocks.LECTERN.defaultBlockState()
         .setValue(LecternBlock.FACING, Direction.EAST));
      set(chunk, centerX - 2, LabyrinthKeys.FLOOR_Y + 1, centerZ - 1, Blocks.BOOKSHELF.defaultBlockState());
      set(chunk, centerX - 2, LabyrinthKeys.FLOOR_Y + 2, centerZ - 1, Blocks.BOOKSHELF.defaultBlockState());
      set(chunk, centerX - 2, LabyrinthKeys.FLOOR_Y + 1, centerZ + 1, Blocks.BOOKSHELF.defaultBlockState());
      set(chunk, centerX - 2, LabyrinthKeys.FLOOR_Y + 2, centerZ + 1, Blocks.BOOKSHELF.defaultBlockState());
      set(chunk, centerX + 2, LabyrinthKeys.FLOOR_Y + 1, centerZ, CANDLE);
      placeHangingLantern(chunk, centerX, LabyrinthKeys.CEILING_Y - 1, centerZ);
      set(chunk, centerX + 1, LabyrinthKeys.FLOOR_Y + 1, centerZ - 2, CHAIN);
   }

   private static void stampChapel(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int centerX,
      int centerZ
   ) {
      paveFloor(chunk, minX, minZ, maxX, maxZ, BRICKS);
      clearInterior(chunk, minX, minZ, maxX, maxZ);
      set(chunk, centerX, LabyrinthKeys.FLOOR_Y + 1, centerZ, Blocks.BREWING_STAND.defaultBlockState());
      set(chunk, centerX - 2, LabyrinthKeys.FLOOR_Y + 1, centerZ, CANDLE);
      set(chunk, centerX + 2, LabyrinthKeys.FLOOR_Y + 1, centerZ, CANDLE);
      set(chunk, centerX, LabyrinthKeys.FLOOR_Y + 1, centerZ - 2, CANDLE);
      placeHangingLantern(chunk, centerX, LabyrinthKeys.CEILING_Y - 1, centerZ);
      set(chunk, centerX, LabyrinthKeys.CEILING_Y - 2, centerZ, CHAIN);
   }

   private static void stampGate(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int centerX,
      int centerZ
   ) {
      paveFloor(chunk, minX, minZ, maxX, maxZ, BLACKSTONE);
      clearInterior(chunk, minX, minZ, maxX, maxZ);
      for (int y = LabyrinthKeys.FLOOR_Y + 1; y <= LabyrinthKeys.FLOOR_Y + 3; y++) {
         set(chunk, centerX - 1, y, centerZ, BARS);
         set(chunk, centerX + 1, y, centerZ, BARS);
         set(chunk, centerX, y, centerZ - 1, BARS);
         set(chunk, centerX, y, centerZ + 1, BARS);
      }
      set(chunk, centerX, LabyrinthKeys.FLOOR_Y + 1, centerZ, CHAIN);
      set(chunk, centerX, LabyrinthKeys.FLOOR_Y + 2, centerZ, CHAIN);
      placeHangingLantern(chunk, centerX, LabyrinthKeys.CEILING_Y - 1, centerZ);
   }

   private static void stampNiche(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int centerX,
      int centerZ,
      RandomSource random
   ) {
      paveFloor(chunk, minX, minZ, maxX, maxZ, BLACKSTONE);
      clearInterior(chunk, minX, minZ, maxX, maxZ);
      set(chunk, centerX, LabyrinthKeys.FLOOR_Y + 1, centerZ, CANDLE);
      if (random.nextBoolean()) {
         set(chunk, centerX, LabyrinthKeys.CEILING_Y - 1, centerZ, CHAIN);
         placeHangingLantern(chunk, centerX, LabyrinthKeys.CEILING_Y - 2, centerZ);
      } else {
         placeHangingLantern(chunk, centerX, LabyrinthKeys.CEILING_Y - 1, centerZ);
      }
   }

   private static void paveFloor(ChunkAccess chunk, int minX, int minZ, int maxX, int maxZ, BlockState floor) {
      for (int x = minX + 1; x <= maxX - 1; x++) {
         for (int z = minZ + 1; z <= maxZ - 1; z++) {
            set(chunk, x, LabyrinthKeys.FLOOR_Y, z, floor);
         }
      }
   }

   private static void clearInterior(ChunkAccess chunk, int minX, int minZ, int maxX, int maxZ) {
      for (int x = minX + 1; x <= maxX - 1; x++) {
         for (int z = minZ + 1; z <= maxZ - 1; z++) {
            for (int y = LabyrinthKeys.FLOOR_Y + 1; y < LabyrinthKeys.CEILING_Y; y++) {
               set(chunk, x, y, z, AIR);
            }
         }
      }
   }

   private static void placeHangingLantern(ChunkAccess chunk, int x, int y, int z) {
      set(chunk, x, y, z, HANGING_LANTERN);
   }

   private static BlockState scharnierState() {
      return ModBlocks.SCHARNIER.get().defaultBlockState();
   }

   private static boolean inChunk(ChunkAccess chunk, int worldX, int worldZ) {
      ChunkPos pos = chunk.getPos();
      return worldX >= pos.getMinBlockX()
         && worldX <= pos.getMaxBlockX()
         && worldZ >= pos.getMinBlockZ()
         && worldZ <= pos.getMaxBlockZ();
   }

   private static void set(ChunkAccess chunk, int x, int y, int z, BlockState state) {
      if (!inChunk(chunk, x, z)) {
         return;
      }
      if (y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) {
         return;
      }
      chunk.setBlockState(new BlockPos(x, y, z), state, false);
   }
}
