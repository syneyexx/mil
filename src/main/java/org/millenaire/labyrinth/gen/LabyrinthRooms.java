package org.millenaire.labyrinth.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
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
   private static final BlockState SPIKE = Blocks.POINTED_DRIPSTONE.defaultBlockState();
   private static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
   private static final BlockState CHISELED = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
   private static final BlockState LADDER_NORTH = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);

   private LabyrinthRooms() {
   }

   public static void stampRoom(
      ChunkAccess chunk,
      LabyrinthMaze.RoomType type,
      int cellMinX,
      int cellMinZ,
      int spanCells,
      long worldSeed,
      RandomSource random
   ) {
      int span = Math.max(1, spanCells);
      int cellMaxX = cellMinX + span * LabyrinthKeys.CELL_SIZE - 1;
      int cellMaxZ = cellMinZ + span * LabyrinthKeys.CELL_SIZE - 1;
      int centerX = (cellMinX + cellMaxX) / 2;
      int centerZ = (cellMinZ + cellMaxZ) / 2;

      switch (type) {
         case ARRIVAL -> stampArrival(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ);
         case CORE -> stampCore(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ, random);
         case HOST_HALL -> {
            stampHostHall(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ);
            stampMezzanine(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ);
         }
         case FORGE -> stampForge(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ);
         case ARCHIVE -> stampArchive(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ);
         case CHAPEL -> {
            stampChapel(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ);
            stampMezzanine(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ);
         }
         case GATE -> {
            stampGate(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ, worldSeed);
            stampMezzanine(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ);
            stampVaultShaft(chunk, centerX, centerZ);
         }
         case NICHE -> stampNiche(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ, random);
         case ROTARY -> stampRotary(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ, worldSeed);
         case ECHO_LOOP -> stampEchoLoop(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ);
         case LIMINAL_BEDROOM, LIMINAL_KITCHEN, LIMINAL_STUDY, LIMINAL_PARLOR, LIMINAL_NURSERY -> {
            if (liminalEnabled()) {
               LabyrinthLiminalRooms.stamp(chunk, type, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ, random);
            } else {
               stampNiche(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ, random);
            }
         }
         case AUDIENCE -> LabyrinthAudienceRoom.stamp(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ);
         case ATRIUM -> stampAtrium(chunk, cellMinX, cellMinZ, cellMaxX, cellMaxZ, centerX, centerZ);
         default -> {
         }
      }
   }

   /**
    * Arrival 2.0 — cold 3×3 nave/vestibule. Scharnier on a lodestone dais is the
    * readable first composition; packed-ice vault and chain processional set liminality.
    */
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
      int naveCeil = LabyrinthKeys.MEZZ_CEILING_Y;

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            if (!inChunk(chunk, x, z)) {
               continue;
            }
            boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
            set(chunk, x, floor, z, BRICKS);
            if (edge) {
               for (int y = floor + 1; y < naveCeil; y++) {
                  BlockState wall = ((x + z + y) & 5) == 0 ? PACKED_ICE : BRICKS;
                  set(chunk, x, y, z, wall);
               }
            } else {
               for (int y = floor + 1; y < naveCeil; y++) {
                  set(chunk, x, y, z, AIR);
               }
               set(chunk, x, naveCeil, z, PACKED_ICE);
            }
         }
      }

      // Processional aisle toward +Z exits into the maze.
      for (int z = minZ + 2; z <= maxZ - 2; z++) {
         set(chunk, centerX, floor, z, POLISHED);
         set(chunk, centerX - 1, floor, z, BRICKS);
         set(chunk, centerX + 1, floor, z, BRICKS);
      }

      // Scharnier dais — readable first object.
      set(chunk, centerX, floor - 1, centerZ, GOLD);
      set(chunk, centerX, floor, centerZ, LODESTONE);
      set(chunk, centerX - 1, floor, centerZ, POLISHED);
      set(chunk, centerX + 1, floor, centerZ, POLISHED);
      set(chunk, centerX, floor, centerZ - 1, POLISHED);
      set(chunk, centerX, floor, centerZ + 1, POLISHED);
      set(chunk, centerX, floor + 1, centerZ, scharnierState());

      // Tall chain columns at nave corners (scale beat within 60s).
      for (int[] c : new int[][] {
         {minX + 2, minZ + 2}, {maxX - 2, minZ + 2}, {minX + 2, maxZ - 2}, {maxX - 2, maxZ - 2}
      }) {
         for (int y = floor + 1; y <= floor + 5; y++) {
            set(chunk, c[0], y, c[1], BRICKS);
         }
         for (int y = floor + 6; y <= floor + 10; y++) {
            set(chunk, c[0], y, c[1], CHAIN);
         }
      }

      placeHangingLantern(chunk, centerX, naveCeil - 1, centerZ);
      placeHangingLantern(chunk, centerX - 4, naveCeil - 1, centerZ - 4);
      placeHangingLantern(chunk, centerX + 4, naveCeil - 1, centerZ + 4);
      placeHangingLantern(chunk, centerX - 4, naveCeil - 1, centerZ + 4);
      placeHangingLantern(chunk, centerX + 4, naveCeil - 1, centerZ - 4);

      set(chunk, centerX, floor + 1, centerZ - 3, CHAIN);
      set(chunk, centerX, floor + 2, centerZ - 3, CHAIN);
      set(chunk, centerX, floor + 3, centerZ - 3, CHAIN);
      set(chunk, minX + 1, floor + 1, minZ + 1, SPIKE);
      set(chunk, maxX - 1, floor + 1, maxZ - 1, SPIKE);
      set(chunk, minX + 1, floor + 1, maxZ - 1, BARS);
      set(chunk, maxX - 1, floor + 1, minZ + 1, BARS);

      // Soft liminal cue: domestic carpet patch near the east wall (wrong, but quiet).
      set(chunk, maxX - 3, floor + 1, centerZ, Blocks.GRAY_CARPET.defaultBlockState());
      set(chunk, maxX - 4, floor + 1, centerZ, Blocks.GRAY_CARPET.defaultBlockState());
      set(chunk, maxX - 3, floor + 1, centerZ + 1, Blocks.WHITE_CARPET.defaultBlockState());
   }

   /** Vertical overlook — open shaft + mezzanine gallery (P1.4). */
   private static void stampAtrium(
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
      stampMezzanine(chunk, minX, minZ, maxX, maxZ, centerX, centerZ);
      // Gallery rails around the overlook hole.
      int mezz = LabyrinthKeys.MEZZ_FLOOR_Y;
      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
               set(chunk, centerX + dx, mezz + 1, centerZ + dz, BARS);
            }
         }
      }
      set(chunk, minX + 1, LabyrinthKeys.FLOOR_Y + 1, minZ + 1, SPIKE);
      set(chunk, maxX - 1, LabyrinthKeys.FLOOR_Y + 1, maxZ - 1, SPIKE);
      placeHangingLantern(chunk, centerX + 2, LabyrinthKeys.CEILING_Y - 1, centerZ + 2);
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
      set(chunk, centerX - 1, floor, centerZ, CRYING);
      set(chunk, centerX + 1, floor, centerZ, CRYING);
      set(chunk, centerX, floor, centerZ - 1, CRYING);
      set(chunk, centerX, floor, centerZ + 1, CRYING);
      placeHangingLantern(chunk, centerX, ceiling - 1, centerZ);
      placeHangingLantern(chunk, centerX - 3, ceiling - 1, centerZ - 3);
      placeHangingLantern(chunk, centerX + 3, ceiling - 1, centerZ + 3);
      set(chunk, centerX - 3, floor + 1, centerZ - 3, BRICKS);
      set(chunk, centerX - 3, floor + 2, centerZ - 3, BRICKS);
      set(chunk, centerX - 3, floor + 3, centerZ - 3, CHAIN);
      set(chunk, centerX + 3, floor + 1, centerZ + 3, BRICKS);
      set(chunk, centerX + 3, floor + 2, centerZ + 3, BRICKS);
      set(chunk, centerX + 3, floor + 3, centerZ + 3, CHAIN);
      set(chunk, centerX - 3, floor + 1, centerZ + 3, SPIKE);
      set(chunk, centerX + 3, floor + 1, centerZ - 3, SPIKE);
      if (random.nextBoolean()) {
         set(chunk, centerX - 2, floor + 1, centerZ - 2, CANDLE);
         set(chunk, centerX + 2, floor + 1, centerZ + 2, CANDLE);
      }
      stampVaultShaft(chunk, centerX, centerZ);
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
      set(chunk, minX + 1, LabyrinthKeys.FLOOR_Y + 1, maxZ - 1, SPIKE);
      set(chunk, maxX - 1, LabyrinthKeys.FLOOR_Y + 1, maxZ - 1, SPIKE);
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

   /**
    * Three sealed relic gates + pedestals. Opening is handled at runtime by GatePuzzle.
    */
   private static void stampGate(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int centerX,
      int centerZ,
      long worldSeed
   ) {
      paveFloor(chunk, minX, minZ, maxX, maxZ, BLACKSTONE);
      clearInterior(chunk, minX, minZ, maxX, maxZ);

      // Three gate frames along +Z wall of the cell interior.
      placeGateFrame(chunk, centerX - 2, LabyrinthKeys.FLOOR_Y + 1, maxZ - 1);
      placeGateFrame(chunk, centerX, LabyrinthKeys.FLOOR_Y + 1, maxZ - 1);
      placeGateFrame(chunk, centerX + 2, LabyrinthKeys.FLOOR_Y + 1, maxZ - 1);

      // Relic pedestals (chiseled) — GatePuzzle maps them to Zegel / Masker / Koord.
      set(chunk, centerX - 2, LabyrinthKeys.FLOOR_Y + 1, centerZ - 1, CHISELED);
      set(chunk, centerX, LabyrinthKeys.FLOOR_Y + 1, centerZ - 1, CHISELED);
      set(chunk, centerX + 2, LabyrinthKeys.FLOOR_Y + 1, centerZ - 1, CHISELED);
      set(chunk, centerX - 2, LabyrinthKeys.FLOOR_Y + 2, centerZ - 1, CANDLE);
      set(chunk, centerX, LabyrinthKeys.FLOOR_Y + 2, centerZ - 1, CANDLE);
      set(chunk, centerX + 2, LabyrinthKeys.FLOOR_Y + 2, centerZ - 1, CANDLE);

      set(chunk, centerX, LabyrinthKeys.FLOOR_Y + 1, centerZ + 1, CHAIN);
      set(chunk, centerX, LabyrinthKeys.FLOOR_Y + 2, centerZ + 1, CHAIN);
      placeHangingLantern(chunk, centerX, LabyrinthKeys.CEILING_Y - 1, centerZ);

      // Seed-stable loot niche: place a chest marker via gold under floor.
      int niche = Math.floorMod((int) (worldSeed ^ (centerX * 31L + centerZ)), 3);
      if (niche == 0) {
         set(chunk, minX + 1, LabyrinthKeys.FLOOR_Y, centerZ, GOLD);
      }
   }

   private static void placeGateFrame(ChunkAccess chunk, int x, int yBase, int z) {
      for (int y = yBase; y <= yBase + 2; y++) {
         set(chunk, x, y, z, BARS);
      }
      set(chunk, x, yBase + 3, z, CHAIN);
   }

   /**
    * Cross-shaped walkways over a spike pit. Initial state: only N-S bridge solid.
    * Configuratie WIEL rotates to E-W (see RotaryPuzzle).
    */
   private static void stampRotary(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int centerX,
      int centerZ,
      long worldSeed
   ) {
      int floor = LabyrinthKeys.FLOOR_Y;
      int ceiling = LabyrinthKeys.CEILING_Y;

      // Pit floor of spikes.
      for (int x = minX + 1; x <= maxX - 1; x++) {
         for (int z = minZ + 1; z <= maxZ - 1; z++) {
            set(chunk, x, floor, z, BLACKSTONE);
            set(chunk, x, floor + 1, z, SPIKE);
            for (int y = floor + 2; y < ceiling; y++) {
               set(chunk, x, y, z, AIR);
            }
            set(chunk, x, ceiling, z, PACKED_ICE);
         }
      }

      // Hub pedestal (always solid — Configuratie use point).
      set(chunk, centerX, floor, centerZ, LODESTONE);
      set(chunk, centerX, floor + 1, centerZ, CHISELED);
      set(chunk, centerX, floor + 2, centerZ, AIR);

      // Initial N-S walkway (rotation index 0).
      for (int z = minZ + 1; z <= maxZ - 1; z++) {
         if (z == centerZ) {
            continue;
         }
         set(chunk, centerX, floor + 1, z, POLISHED);
         set(chunk, centerX, floor + 2, z, AIR);
      }
      // E-W remains spikes (closed).

      placeHangingLantern(chunk, centerX, ceiling - 1, centerZ);
      set(chunk, centerX - 2, ceiling - 1, centerZ, CHAIN);
      set(chunk, centerX + 2, ceiling - 1, centerZ, CHAIN);

      // Marker under hub for RotaryPuzzle discovery.
      set(chunk, centerX, floor - 1, centerZ, GOLD);
   }

   /**
    * Domestic-looking chamber that EchoLoopHandler uses for perspective loops.
    */
   private static void stampEchoLoop(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int centerX,
      int centerZ
   ) {
      // Full domestic shell + parlor furniture, then loop markers.
      LabyrinthLiminalRooms.stamp(
         chunk,
         LabyrinthMaze.RoomType.LIMINAL_PARLOR,
         minX,
         minZ,
         maxX,
         maxZ,
         centerX,
         centerZ,
         net.minecraft.util.RandomSource.create(centerX * 31L + centerZ)
      );
      // North doorway marker (forward) — loop handler watches crossing.
      set(chunk, centerX, LabyrinthKeys.FLOOR_Y - 1, maxZ - 1, GOLD);
      set(chunk, centerX, LabyrinthKeys.FLOOR_Y - 1, minZ + 1, CRYING);
      // Crooked clock stand-in: note block on the parlor table.
      set(chunk, centerX, LabyrinthKeys.FLOOR_Y + 2, centerZ, Blocks.NOTE_BLOCK.defaultBlockState());
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
      if (random.nextInt(3) == 0) {
         set(chunk, centerX + 1, LabyrinthKeys.FLOOR_Y + 1, centerZ, SPIKE);
      }
   }

   /**
    * Opens a mezzanine band above the hub with ladder access (Wave 1 / 4).
    */
   private static void stampMezzanine(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int centerX,
      int centerZ
   ) {
      int mezzFloor = LabyrinthKeys.MEZZ_FLOOR_Y;
      int mezzCeil = LabyrinthKeys.MEZZ_CEILING_Y;

      // Punch shaft through primary ceiling into mezzanine.
      for (int y = LabyrinthKeys.CEILING_Y; y <= mezzFloor; y++) {
         set(chunk, centerX, y, centerZ, AIR);
         set(chunk, centerX, y, centerZ - 1, AIR);
      }
      for (int y = LabyrinthKeys.FLOOR_Y + 1; y < mezzFloor; y++) {
         set(chunk, centerX, y, centerZ - 1, LADDER_NORTH);
      }

      for (int x = minX + 1; x <= maxX - 1; x++) {
         for (int z = minZ + 1; z <= maxZ - 1; z++) {
            set(chunk, x, mezzFloor, z, POLISHED);
            for (int y = mezzFloor + 1; y < mezzCeil; y++) {
               set(chunk, x, y, z, AIR);
            }
            set(chunk, x, mezzCeil, z, PACKED_ICE);
         }
      }
      placeHangingLantern(chunk, centerX, mezzCeil - 1, centerZ);
      set(chunk, centerX - 2, mezzFloor + 1, centerZ, CHAIN);
      set(chunk, centerX + 2, mezzFloor + 1, centerZ, SPIKE);
      // Overlook hole back down.
      set(chunk, centerX, mezzFloor, centerZ, AIR);
      set(chunk, centerX, mezzFloor, centerZ - 1, AIR);
   }

   /** Connects primary floor down to vault band at cell center. */
   private static void stampVaultShaft(ChunkAccess chunk, int centerX, int centerZ) {
      for (int y = LabyrinthKeys.VAULT_CEILING_Y; y <= LabyrinthKeys.FLOOR_Y; y++) {
         set(chunk, centerX, y, centerZ, AIR);
      }
      for (int y = LabyrinthKeys.VAULT_FLOOR_Y + 1; y < LabyrinthKeys.FLOOR_Y; y++) {
         set(chunk, centerX - 1, y, centerZ, LADDER_NORTH);
      }
      // Small vault landing.
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            set(chunk, centerX + dx, LabyrinthKeys.VAULT_FLOOR_Y, centerZ + dz, BLACKSTONE);
            set(chunk, centerX + dx, LabyrinthKeys.VAULT_CEILING_Y, centerZ + dz, PACKED_ICE);
            for (int y = LabyrinthKeys.VAULT_FLOOR_Y + 1; y < LabyrinthKeys.VAULT_CEILING_Y; y++) {
               if (!(dx == 0 && dz == 0) && !(dx == -1 && dz == 0)) {
                  set(chunk, centerX + dx, y, centerZ + dz, AIR);
               }
            }
         }
      }
      placeHangingLantern(chunk, centerX, LabyrinthKeys.VAULT_CEILING_Y - 1, centerZ);
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

   private static boolean liminalEnabled() {
      try {
         return org.millenaire.config.MillenaireServerConfig.SERVER.labyrinthLiminalRoomsEnabled.getAsBoolean();
      } catch (IllegalStateException ignored) {
         return true;
      }
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
