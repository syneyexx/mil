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
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.millenaire.labyrinth.LabyrinthKeys;

/**
 * Reality-bleed domestic interiors — look almost normal, then wrong.
 * Revival-style mind-fuck: furnished rooms woven into the Labyrinth.
 */
public final class LabyrinthLiminalRooms {
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();
   private static final BlockState PLANKS = Blocks.SPRUCE_PLANKS.defaultBlockState();
   private static final BlockState OAK = Blocks.OAK_PLANKS.defaultBlockState();
   private static final BlockState FLOOR_OAK = Blocks.OAK_PLANKS.defaultBlockState();
   private static final BlockState CARPET = Blocks.RED_CARPET.defaultBlockState();
   private static final BlockState CARPET_GRAY = Blocks.GRAY_CARPET.defaultBlockState();
   private static final BlockState CARPET_WHITE = Blocks.WHITE_CARPET.defaultBlockState();
   private static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
   private static final BlockState BARS = Blocks.IRON_BARS.defaultBlockState();
   private static final BlockState CRYING = Blocks.CRYING_OBSIDIAN.defaultBlockState();
   private static final BlockState SPIKE = Blocks.POINTED_DRIPSTONE.defaultBlockState();
   private static final BlockState CANDLE = Blocks.WHITE_CANDLE.defaultBlockState()
      .setValue(CandleBlock.CANDLES, 1)
      .setValue(CandleBlock.LIT, true);
   private static final BlockState BLACK_CANDLE = Blocks.BLACK_CANDLE.defaultBlockState()
      .setValue(CandleBlock.CANDLES, 2)
      .setValue(CandleBlock.LIT, true);
   private static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();
   private static final BlockState HANGING = Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);

   private LabyrinthLiminalRooms() {
   }

   public static void stamp(
      ChunkAccess chunk,
      LabyrinthMaze.RoomType type,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int centerX,
      int centerZ,
      RandomSource random
   ) {
      int wrongness = wrongnessIntensity(minX, minZ, maxX, maxZ);
      shellDomestic(chunk, minX, minZ, maxX, maxZ, random);
      switch (type) {
         case LIMINAL_BEDROOM -> stampBedroom(chunk, minX, minZ, maxX, maxZ, centerX, centerZ, random);
         case LIMINAL_KITCHEN -> stampKitchen(chunk, minX, minZ, maxX, maxZ, centerX, centerZ, random);
         case LIMINAL_STUDY -> stampStudy(chunk, minX, minZ, maxX, maxZ, centerX, centerZ, random);
         case LIMINAL_PARLOR -> stampParlor(chunk, minX, minZ, maxX, maxZ, centerX, centerZ, random);
         case LIMINAL_NURSERY -> stampNursery(chunk, minX, minZ, maxX, maxZ, centerX, centerZ, random);
         default -> {
         }
      }
      densifyProps(chunk, minX, minZ, maxX, maxZ, centerX, centerZ, random);
      paintWrongness(chunk, minX, minZ, maxX, maxZ, centerX, centerZ, random, wrongness);
   }

   /** Seed-stable wrongness 0–3 for the suite footprint. */
   public static int wrongnessIntensity(int minX, int minZ, int maxX, int maxZ) {
      return Math.floorMod(minX * 31 + minZ * 17 + maxX * 13 + maxZ * 7, 4);
   }

   /** Shared domestic shell: wood walls/floor/ceiling instead of blackstone hell. */
   private static void shellDomestic(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      RandomSource random
   ) {
      int floor = LabyrinthKeys.FLOOR_Y;
      int ceiling = LabyrinthKeys.CEILING_Y;
      BlockState wall = random.nextBoolean() ? LabyrinthMaterials.bleedPlanks() : LabyrinthMaterials.bleedOak();
      BlockState floorBlock = LabyrinthMaterials.bleedOak();

      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
            set(chunk, x, floor, z, floorBlock);
            if (edge) {
               for (int y = floor + 1; y < ceiling; y++) {
                  set(chunk, x, y, z, wall);
               }
               // Fake doorway gaps on mid-edges so maze connections still read as doors.
               int midZ = (minZ + maxZ) / 2;
               int midX = (minX + maxX) / 2;
               boolean doorGap =
                  (x == minX || x == maxX) && Math.abs(z - midZ) <= 1
                     || (z == minZ || z == maxZ) && Math.abs(x - midX) <= 1;
               if (doorGap) {
                  for (int y = floor + 1; y <= floor + 2; y++) {
                     set(chunk, x, y, z, AIR);
                  }
               }
            } else {
               for (int y = floor + 1; y < ceiling; y++) {
                  set(chunk, x, y, z, AIR);
               }
               set(chunk, x, ceiling, z, wall);
            }
         }
      }
   }

   private static void stampBedroom(
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
      // Bed against south wall.
      placeBed(chunk, centerX, floor + 1, maxZ - 2, Direction.NORTH);
      set(chunk, centerX - 2, floor + 1, maxZ - 2, Blocks.OAK_STAIRS.defaultBlockState());
      set(chunk, centerX + 2, floor + 1, maxZ - 2, Blocks.CHEST.defaultBlockState());
      set(chunk, minX + 1, floor + 1, minZ + 1, Blocks.FLOWER_POT.defaultBlockState());
      set(chunk, maxX - 1, floor + 1, minZ + 1, CANDLE);
      carpetArea(chunk, minX + 2, minZ + 2, maxX - 2, maxZ - 3, CARPET);
      // Wrong: soul lantern over the pillow, chain down to the mattress.
      set(chunk, centerX, LabyrinthKeys.CEILING_Y - 1, maxZ - 2, HANGING);
      set(chunk, centerX, floor + 3, maxZ - 2, CHAIN);
      set(chunk, centerX, floor + 2, maxZ - 2, CHAIN);
      if (random.nextBoolean()) {
         set(chunk, centerX + 1, floor, maxZ - 2, CRYING); // under carpet/floor — wrong foundation
      }
   }

   private static void stampKitchen(
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
      set(chunk, minX + 1, floor + 1, centerZ, Blocks.FURNACE.defaultBlockState());
      set(chunk, minX + 1, floor + 1, centerZ - 1, Blocks.SMOKER.defaultBlockState());
      set(chunk, minX + 1, floor + 1, centerZ + 1, Blocks.CRAFTING_TABLE.defaultBlockState());
      set(chunk, minX + 2, floor + 1, centerZ, Blocks.CAULDRON.defaultBlockState());
      set(chunk, maxX - 1, floor + 1, centerZ, Blocks.BARREL.defaultBlockState());
      set(chunk, centerX, floor + 1, minZ + 1, Blocks.OAK_SLAB.defaultBlockState()); // table
      set(chunk, centerX - 1, floor + 1, minZ + 1, Blocks.OAK_FENCE.defaultBlockState());
      set(chunk, centerX + 1, floor + 1, minZ + 1, Blocks.OAK_FENCE.defaultBlockState());
      set(chunk, centerX, floor + 1, maxZ - 1, Blocks.COMPOSTER.defaultBlockState());
      carpetArea(chunk, minX + 2, minZ + 2, maxX - 2, maxZ - 2, CARPET_GRAY);
      // Wrong: spikes in the pantry corner, black candles on the table.
      set(chunk, maxX - 1, floor + 1, maxZ - 1, SPIKE);
      set(chunk, centerX, floor + 2, minZ + 1, BLACK_CANDLE);
      set(chunk, centerX, LabyrinthKeys.CEILING_Y - 1, centerZ, CHAIN);
      if (random.nextBoolean()) {
         // Oven that faces the wall — domestic 90° off.
         set(chunk, minX + 1, floor + 2, centerZ, BARS);
      }
   }

   private static void stampStudy(
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
      set(chunk, minX + 1, floor + 1, minZ + 1, Blocks.BOOKSHELF.defaultBlockState());
      set(chunk, minX + 1, floor + 2, minZ + 1, Blocks.BOOKSHELF.defaultBlockState());
      set(chunk, minX + 1, floor + 1, minZ + 2, Blocks.BOOKSHELF.defaultBlockState());
      set(chunk, minX + 1, floor + 2, minZ + 2, Blocks.BOOKSHELF.defaultBlockState());
      set(chunk, centerX, floor + 1, centerZ, Blocks.LECTERN.defaultBlockState());
      set(chunk, centerX + 1, floor + 1, centerZ, Blocks.OAK_STAIRS.defaultBlockState()); // chair
      set(chunk, maxX - 1, floor + 1, centerZ, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
      set(chunk, maxX - 1, floor + 1, maxZ - 1, LANTERN);
      carpetArea(chunk, minX + 2, minZ + 2, maxX - 2, maxZ - 2, CARPET_GRAY);
      // Wrong: bookshelf of crying obsidian; upside-down feel via hanging lantern at floor height.
      set(chunk, minX + 1, floor + 3, minZ + 1, CRYING);
      set(chunk, centerX - 2, floor + 1, centerZ - 2, HANGING);
      set(chunk, centerX, LabyrinthKeys.CEILING_Y - 1, centerZ, CHAIN);
      if (random.nextBoolean()) {
         set(chunk, maxX - 2, floor + 1, minZ + 1, Blocks.SKELETON_SKULL.defaultBlockState());
      }
   }

   private static void stampParlor(
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
      // Conversation circle of stairs (chairs) around a carpet.
      set(chunk, centerX, floor + 1, centerZ - 2, Blocks.OAK_STAIRS.defaultBlockState());
      set(chunk, centerX, floor + 1, centerZ + 2, Blocks.SPRUCE_STAIRS.defaultBlockState());
      set(chunk, centerX - 2, floor + 1, centerZ, Blocks.BIRCH_STAIRS.defaultBlockState());
      set(chunk, centerX + 2, floor + 1, centerZ, Blocks.DARK_OAK_STAIRS.defaultBlockState());
      set(chunk, centerX, floor + 1, centerZ, Blocks.OAK_SLAB.defaultBlockState());
      set(chunk, minX + 1, floor + 1, maxZ - 1, Blocks.NOTE_BLOCK.defaultBlockState());
      set(chunk, maxX - 1, floor + 1, minZ + 1, Blocks.JUKEBOX.defaultBlockState());
      set(chunk, minX + 1, floor + 1, minZ + 1, Blocks.FLOWER_POT.defaultBlockState());
      carpetArea(chunk, centerX - 1, centerZ - 1, centerX + 1, centerZ + 1, CARPET);
      // Wrong: one chair is iron bars; piano corner has spikes; door frame is a false door into bars.
      set(chunk, centerX + 2, floor + 2, centerZ, BARS);
      set(chunk, maxX - 1, floor + 1, maxZ - 1, SPIKE);
      placeFalseDoor(chunk, minX, floor + 1, centerZ);
      set(chunk, centerX, LabyrinthKeys.CEILING_Y - 1, centerZ, HANGING);
   }

   private static void stampNursery(
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
      // Tiny bed (half) + toys as flower pots / carpet play mat.
      placeBed(chunk, minX + 2, floor + 1, centerZ, Direction.EAST);
      set(chunk, maxX - 2, floor + 1, centerZ, Blocks.OAK_FENCE.defaultBlockState()); // crib rail
      set(chunk, maxX - 2, floor + 2, centerZ, Blocks.OAK_FENCE.defaultBlockState());
      set(chunk, maxX - 1, floor + 1, centerZ, Blocks.OAK_FENCE.defaultBlockState());
      set(chunk, centerX, floor + 1, minZ + 1, Blocks.FLOWER_POT.defaultBlockState());
      set(chunk, centerX + 1, floor + 1, minZ + 1, Blocks.FLOWER_POT.defaultBlockState());
      set(chunk, centerX - 1, floor + 1, maxZ - 1, CANDLE);
      carpetArea(chunk, minX + 2, minZ + 2, maxX - 2, maxZ - 2, CARPET_WHITE);
      // Wrong: mobile of chains; black candle by the crib; one pot is a wither rose via skull.
      set(chunk, maxX - 2, LabyrinthKeys.CEILING_Y - 1, centerZ, CHAIN);
      set(chunk, maxX - 2, floor + 3, centerZ, CHAIN);
      set(chunk, maxX - 1, floor + 1, centerZ + 1, BLACK_CANDLE);
      if (random.nextBoolean()) {
         set(chunk, centerX, floor + 1, maxZ - 1, Blocks.SKELETON_SKULL.defaultBlockState());
      }
   }

   /**
    * Extra domestic clutter so suites read as furnished homes (≥30 props on 24×24).
    * Placed before wrongness so the room still reads as a house for ~3s.
    */
   private static void densifyProps(
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
      int width = maxX - minX;
      if (width < 10) {
         return;
      }
      // Shelf strips, rugs, chairs, pots — density scales with footprint.
      for (int x = minX + 2; x <= maxX - 2; x += 3) {
         set(chunk, x, floor + 1, minZ + 1, Blocks.OAK_SLAB.defaultBlockState());
         set(chunk, x, floor + 1, maxZ - 1, Blocks.FLOWER_POT.defaultBlockState());
      }
      for (int z = minZ + 3; z <= maxZ - 3; z += 3) {
         set(chunk, minX + 1, floor + 1, z, Blocks.BOOKSHELF.defaultBlockState());
         set(chunk, maxX - 1, floor + 1, z, Blocks.BARREL.defaultBlockState());
      }
      set(chunk, centerX - 3, floor + 1, centerZ - 3, Blocks.OAK_STAIRS.defaultBlockState());
      set(chunk, centerX + 3, floor + 1, centerZ + 3, Blocks.SPRUCE_STAIRS.defaultBlockState());
      set(chunk, centerX - 3, floor + 1, centerZ + 3, CANDLE);
      set(chunk, centerX + 3, floor + 1, centerZ - 3, LANTERN);
      carpetArea(chunk, centerX - 2, centerZ - 2, centerX + 2, centerZ + 2, CARPET_GRAY);
      // Memory lectern (P3.5) — lore stand-in; text filled by ambience / revisit.
      set(chunk, centerX, floor + 1, minZ + 2, Blocks.LECTERN.defaultBlockState());
      if (random.nextBoolean()) {
         placeFalseDoor(chunk, maxX - 2, floor + 1, centerZ);
      }
   }

   /** Shared “something is off” accents after furniture. Intensity 0–3. */
   private static void paintWrongness(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int centerX,
      int centerZ,
      RandomSource random,
      int intensity
   ) {
      int floor = LabyrinthKeys.FLOOR_Y;
      // One ceiling plank replaced with packed ice (cold bleed) — always, so exit feels colder.
      set(chunk, centerX + 1, LabyrinthKeys.CEILING_Y, centerZ - 1, LabyrinthMaterials.coldVault());
      if (intensity >= 1 || random.nextFloat() < 0.7F) {
         set(chunk, minX + 2, floor, minZ + 2, CRYING);
         set(chunk, minX + 2, floor + 1, minZ + 2, CARPET);
      }
      if (intensity >= 2) {
         set(chunk, maxX - 2, floor + 1, maxZ - 2, SPIKE);
         set(chunk, centerX, floor + 3, centerZ, CHAIN);
         set(chunk, minX + 3, LabyrinthKeys.CEILING_Y, centerZ, CRYING);
      }
      if (intensity >= 3) {
         set(chunk, centerX - 1, floor + 1, centerZ, BARS);
         set(chunk, centerX + 1, floor + 1, centerZ, Blocks.SKELETON_SKULL.defaultBlockState());
         placeFalseDoor(chunk, minX, floor + 1, centerZ - 2);
      }
      if (intensity >= 1 && random.nextFloat() < 0.4F + intensity * 0.15F) {
         set(chunk, maxX - 1, floor + 1, minZ + 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
         set(chunk, maxX - 1, floor + 2, minZ + 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
         set(chunk, maxX - 1, floor + 3, minZ + 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
      }
   }

   private static void placeBed(ChunkAccess chunk, int x, int y, int z, Direction facing) {
      BlockState foot = Blocks.RED_BED.defaultBlockState()
         .setValue(BedBlock.FACING, facing)
         .setValue(BedBlock.PART, BedPart.FOOT);
      BlockState head = Blocks.RED_BED.defaultBlockState()
         .setValue(BedBlock.FACING, facing)
         .setValue(BedBlock.PART, BedPart.HEAD);
      set(chunk, x, y, z, foot);
      BlockPos headPos = new BlockPos(x, y, z).relative(facing);
      set(chunk, headPos.getX(), headPos.getY(), headPos.getZ(), head);
   }

   private static void placeFalseDoor(ChunkAccess chunk, int x, int y, int z) {
      // Decorative door flush against outer wall — opens onto iron bars behind.
      BlockState lower = Blocks.OAK_DOOR.defaultBlockState()
         .setValue(DoorBlock.FACING, Direction.EAST)
         .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
      BlockState upper = Blocks.OAK_DOOR.defaultBlockState()
         .setValue(DoorBlock.FACING, Direction.EAST)
         .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
      set(chunk, x + 1, y, z, lower);
      set(chunk, x + 1, y + 1, z, upper);
      set(chunk, x, y, z, BARS);
      set(chunk, x, y + 1, z, BARS);
   }

   private static void carpetArea(ChunkAccess chunk, int x0, int z0, int x1, int z1, BlockState carpet) {
      int floor = LabyrinthKeys.FLOOR_Y;
      for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
         for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            set(chunk, x, floor + 1, z, carpet);
         }
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
