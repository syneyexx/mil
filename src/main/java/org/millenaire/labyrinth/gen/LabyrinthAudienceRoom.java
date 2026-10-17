package org.millenaire.labyrinth.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.millenaire.labyrinth.LabyrinthKeys;

/**
 * Stamps the Audiëntie chamber — tall nave with floating <b>De Ruit</b>
 * (octahedral geometry of the Orde). Original Millénaire lore; not Leviathan.
 */
public final class LabyrinthAudienceRoom {
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();
   private static final BlockState POLISHED = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
   private static final BlockState BRICKS = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
   private static final BlockState CRYING = Blocks.CRYING_OBSIDIAN.defaultBlockState();
   private static final BlockState PACKED = Blocks.PACKED_ICE.defaultBlockState();
   private static final BlockState BLUE = Blocks.BLUE_ICE.defaultBlockState();
   private static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
   private static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
   private static final BlockState BARS = Blocks.IRON_BARS.defaultBlockState();
   private static final BlockState HANGING = Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
   private static final BlockState LODESTONE = Blocks.LODESTONE.defaultBlockState();

   private LabyrinthAudienceRoom() {
   }

   public static void stamp(ChunkAccess chunk, int minX, int minZ, int maxX, int maxZ, int centerX, int centerZ) {
      int floor = LabyrinthKeys.FLOOR_Y;
      int naveCeil = LabyrinthKeys.MEZZ_CEILING_Y; // tall chamber into mezzanine band

      // Hollow tall nave.
      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            if (!inChunk(chunk, x, z)) {
               continue;
            }
            boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
            set(chunk, x, floor, z, POLISHED);
            if (edge) {
               for (int y = floor + 1; y < naveCeil; y++) {
                  BlockState wall = ((x + z + y) & 5) == 0 ? CRYING : BRICKS;
                  set(chunk, x, y, z, wall);
               }
            } else {
               for (int y = floor + 1; y < naveCeil; y++) {
                  set(chunk, x, y, z, AIR);
               }
               set(chunk, x, naveCeil, z, PACKED);
            }
         }
      }

      // Processional carpet of crying toward the south (Core).
      for (int z = centerZ - 1; z <= maxZ - 1; z++) {
         set(chunk, centerX, floor, z, CRYING);
         set(chunk, centerX - 1, floor, z, POLISHED);
         set(chunk, centerX + 1, floor, z, POLISHED);
      }

      // Viewing dais under De Ruit.
      set(chunk, centerX, floor, centerZ, LODESTONE);
      set(chunk, centerX - 1, floor, centerZ, GOLD);
      set(chunk, centerX + 1, floor, centerZ, GOLD);
      set(chunk, centerX, floor, centerZ - 1, GOLD);
      set(chunk, centerX, floor, centerZ + 1, GOLD);

      // Pillars / chains at corners of the nave.
      for (int[] c : new int[][] {
         {minX + 1, minZ + 1}, {maxX - 1, minZ + 1}, {minX + 1, maxZ - 1}, {maxX - 1, maxZ - 1}
      }) {
         for (int y = floor + 1; y <= floor + 4; y++) {
            set(chunk, c[0], y, c[1], BRICKS);
         }
         set(chunk, c[0], floor + 5, c[1], CHAIN);
         set(chunk, c[0], floor + 6, c[1], CHAIN);
         set(chunk, c[0], naveCeil - 1, c[1], HANGING);
      }

      // De Ruit — floating octahedron / lozenge (god-geometry of the Orde).
      stampRuit(chunk, centerX, floor + 6, centerZ);

      // Suspend De Ruit from the nave ceiling with chains.
      for (int y = floor + 10; y < naveCeil; y++) {
         set(chunk, centerX, y, centerZ, CHAIN);
      }
      set(chunk, centerX, naveCeil - 1, centerZ, HANGING);

      // Side galleries of bars (congregation pews).
      for (int z = minZ + 2; z <= maxZ - 2; z++) {
         set(chunk, minX + 2, floor + 1, z, BARS);
         set(chunk, maxX - 2, floor + 1, z, BARS);
      }
   }

   /**
    * Approximate octahedron / diamond (lozenge) centered at (cx, cy, cz).
    */
   private static void stampRuit(ChunkAccess chunk, int cx, int cy, int cz) {
      // Tip (top)
      set(chunk, cx, cy + 3, cz, BLUE);
      // Upper pyramid
      ring(chunk, cx, cy + 2, cz, 1, PACKED);
      ring(chunk, cx, cy + 1, cz, 2, PACKED);
      // Equator
      ring(chunk, cx, cy, cz, 2, CRYING);
      set(chunk, cx, cy, cz, GOLD); // heart
      // Lower pyramid
      ring(chunk, cx, cy - 1, cz, 2, PACKED);
      ring(chunk, cx, cy - 2, cz, 1, PACKED);
      // Tip (bottom)
      set(chunk, cx, cy - 3, cz, BLUE);
      // Gold cardinal studs on equator
      set(chunk, cx + 2, cy, cz, GOLD);
      set(chunk, cx - 2, cy, cz, GOLD);
      set(chunk, cx, cy, cz + 2, GOLD);
      set(chunk, cx, cy, cz - 2, GOLD);
   }

   private static void ring(ChunkAccess chunk, int cx, int cy, int cz, int r, BlockState state) {
      for (int dx = -r; dx <= r; dx++) {
         for (int dz = -r; dz <= r; dz++) {
            if (Math.abs(dx) + Math.abs(dz) <= r) {
               set(chunk, cx + dx, cy, cz + dz, state);
            }
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
