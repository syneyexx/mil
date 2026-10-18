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
 * Audiëntie nave — tall awe chamber with floating <b>De Ruit</b>
 * (octahedral geometry of the Orde). Layout v2 targets ≥24–32 blocks + processie ≥20.
 * Original Millénaire lore; not Leviathan / Hellraiser IP.
 */
public final class LabyrinthAudienceRoom {
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();
   private static final BlockState POLISHED = LabyrinthMaterials.ordeStone();
   private static final BlockState BRICKS = LabyrinthMaterials.ordePanel();
   private static final BlockState CRYING = LabyrinthMaterials.ruitHeart();
   private static final BlockState PACKED = LabyrinthMaterials.coldVault();
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
      int naveCeil = LabyrinthKeys.MEZZ_CEILING_Y;
      int width = maxX - minX + 1;
      int depth = maxZ - minZ + 1;

      // Hollow tall nave (full footprint — awe scale).
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
               // Open to the sky void — De Ruit beam continues above (layout v3 horizon).
               for (int y = floor + 1; y <= naveCeil; y++) {
                  set(chunk, x, y, z, AIR);
               }
            }
         }
      }
      // Wall-crown parapet on nave rim for walkable frame.
      for (int x = minX; x <= maxX; x++) {
         set(chunk, x, naveCeil, minZ, LabyrinthMaterials.parapet());
         set(chunk, x, naveCeil, maxZ, LabyrinthMaterials.parapet());
      }
      for (int z = minZ; z <= maxZ; z++) {
         set(chunk, minX, naveCeil, z, LabyrinthMaterials.parapet());
         set(chunk, maxX, naveCeil, z, LabyrinthMaterials.parapet());
      }

      // Processional carpet ≥20 blocks toward the south (Core) when footprint allows.
      int processionStart = Math.max(minZ + 2, centerZ - Math.max(10, depth / 2 - 2));
      for (int z = processionStart; z <= maxZ - 1; z++) {
         set(chunk, centerX, floor, z, CRYING);
         set(chunk, centerX - 1, floor, z, POLISHED);
         set(chunk, centerX + 1, floor, z, POLISHED);
         if (width >= 20) {
            set(chunk, centerX - 2, floor, z, BRICKS);
            set(chunk, centerX + 2, floor, z, BRICKS);
         }
      }

      // Viewing dais under De Ruit.
      set(chunk, centerX, floor, centerZ, LODESTONE);
      set(chunk, centerX - 1, floor, centerZ, GOLD);
      set(chunk, centerX + 1, floor, centerZ, GOLD);
      set(chunk, centerX, floor, centerZ - 1, GOLD);
      set(chunk, centerX, floor, centerZ + 1, GOLD);

      // Pillars / chains at nave corners + mid-edge galleries.
      for (int[] c : new int[][] {
         {minX + 2, minZ + 2}, {maxX - 2, minZ + 2}, {minX + 2, maxZ - 2}, {maxX - 2, maxZ - 2},
         {minX + 2, centerZ}, {maxX - 2, centerZ}, {centerX, minZ + 2}
      }) {
         for (int y = floor + 1; y <= floor + 5; y++) {
            set(chunk, c[0], y, c[1], BRICKS);
         }
         set(chunk, c[0], floor + 5, c[1], CHAIN);
         set(chunk, c[0], floor + 6, c[1], CHAIN);
         set(chunk, c[0], naveCeil - 1, c[1], HANGING);
      }

      // Larger De Ruit for multi-cell nave.
      int ruitY = floor + (depth >= 20 ? 8 : 6);
      stampRuit(chunk, centerX, ruitY, centerZ, depth >= 20 ? 1 : 0);

      for (int y = ruitY + 4; y < naveCeil; y++) {
         set(chunk, centerX, y, centerZ, CHAIN);
      }
      // Sky beam pillar through the open crown (visible across the wellgrid horizon).
      for (int y = naveCeil; y < LabyrinthKeys.MIN_Y + LabyrinthKeys.GEN_DEPTH; y++) {
         set(chunk, centerX, y, centerZ, LabyrinthMaterials.voidBeam());
      }

      // Silent pews — iron-bar congregation along both flanks (Approach/Audience presence).
      for (int z = minZ + 3; z <= maxZ - 3; z += 2) {
         set(chunk, minX + 3, floor + 1, z, BARS);
         set(chunk, maxX - 3, floor + 1, z, BARS);
         if (width >= 24) {
            set(chunk, minX + 4, floor + 1, z, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
            set(chunk, maxX - 4, floor + 1, z, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
         }
      }

      // Subtle beam: end-rod stand-ins via soul lanterns along the processional axis.
      for (int z = processionStart; z <= maxZ - 2; z += 4) {
         set(chunk, centerX, naveCeil - 2, z, HANGING);
      }
   }

   /**
    * Approximate octahedron / diamond (lozenge). {@code scaleBonus} grows equator for awe naves.
    */
   private static void stampRuit(ChunkAccess chunk, int cx, int cy, int cz, int scaleBonus) {
      int eq = 2 + scaleBonus;
      set(chunk, cx, cy + 3 + scaleBonus, cz, BLUE);
      ring(chunk, cx, cy + 2 + scaleBonus, cz, 1, PACKED);
      ring(chunk, cx, cy + 1, cz, eq, PACKED);
      ring(chunk, cx, cy, cz, eq, CRYING);
      set(chunk, cx, cy, cz, GOLD);
      ring(chunk, cx, cy - 1, cz, eq, PACKED);
      ring(chunk, cx, cy - 2, cz, 1, PACKED);
      set(chunk, cx, cy - 3 - scaleBonus, cz, BLUE);
      set(chunk, cx + eq, cy, cz, GOLD);
      set(chunk, cx - eq, cy, cz, GOLD);
      set(chunk, cx, cy, cz + eq, GOLD);
      set(chunk, cx, cy, cz - eq, GOLD);
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
