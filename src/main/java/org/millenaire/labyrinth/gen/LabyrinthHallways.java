package org.millenaire.labyrinth.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.millenaire.labyrinth.LabyrinthKeys;

/**
 * Layout v3 hallway vocabulary — crazy gangen as the default, not the exception.
 * Each corridor cell ({@link LabyrinthMaze.RoomType#NONE}) gets a seed-stable archetype
 * stamped after the well skeleton is carved.
 */
public final class LabyrinthHallways {
   public enum Archetype {
      /** Narrow side walks + spike trench (classic Orde corridor kit). */
      SPIKE_TRENCH,
      /** Inner cloister ring with arch motifs looking into the well. */
      ARCADE_CLOISTER,
      /** N–S / E–W slab bridges across the open pit. */
      CROSS_BRIDGE,
      /** Two stacked gallery walks with rails. */
      GALLERY_STACK,
      /** Choked mid-channel with hooks and chains. */
      CHOKE_HOOK,
      /** Almost empty deep well — only wall-crown and sparse rim. */
      VOID_WELL
   }

   private static final BlockState AIR = Blocks.AIR.defaultBlockState();
   private static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);

   private LabyrinthHallways() {
   }

   /**
    * Zone-weighted archetype pick. Bleed leans cloister; Orde/Approach lean bridges & voids.
    */
   public static Archetype archetypeAt(int globalCellX, int globalCellZ, long seed) {
      LabyrinthKeys.Zone zone = LabyrinthKeys.zoneAt(globalCellX, globalCellZ);
      int hash = Long.hashCode(Mth.getSeed(globalCellX, 7, globalCellZ) ^ (seed * 0x9E3779B97F4A7C15L));
      int pick = Math.floorMod(hash, 100);
      return switch (zone) {
         case ARRIVAL -> pick < 40 ? Archetype.ARCADE_CLOISTER
            : pick < 70 ? Archetype.GALLERY_STACK
            : pick < 90 ? Archetype.CROSS_BRIDGE
            : Archetype.SPIKE_TRENCH;
         case BLEED -> pick < 35 ? Archetype.ARCADE_CLOISTER
            : pick < 55 ? Archetype.GALLERY_STACK
            : pick < 75 ? Archetype.CHOKE_HOOK
            : pick < 90 ? Archetype.SPIKE_TRENCH
            : Archetype.CROSS_BRIDGE;
         case ORDE -> pick < 22 ? Archetype.SPIKE_TRENCH
            : pick < 40 ? Archetype.CROSS_BRIDGE
            : pick < 58 ? Archetype.GALLERY_STACK
            : pick < 74 ? Archetype.ARCADE_CLOISTER
            : pick < 88 ? Archetype.CHOKE_HOOK
            : Archetype.VOID_WELL;
         case APPROACH, CORE -> pick < 20 ? Archetype.VOID_WELL
            : pick < 40 ? Archetype.CROSS_BRIDGE
            : pick < 60 ? Archetype.GALLERY_STACK
            : pick < 78 ? Archetype.ARCADE_CLOISTER
            : pick < 90 ? Archetype.SPIKE_TRENCH
            : Archetype.CHOKE_HOOK;
      };
   }

   public static void stampCorridorCell(
      ChunkAccess chunk,
      int globalCellX,
      int globalCellZ,
      long seed,
      RandomSource random
   ) {
      int minX = globalCellX * LabyrinthKeys.CELL_SIZE;
      int minZ = globalCellZ * LabyrinthKeys.CELL_SIZE;
      int maxX = minX + LabyrinthKeys.CELL_SIZE - 1;
      int maxZ = minZ + LabyrinthKeys.CELL_SIZE - 1;
      int midX = minX + LabyrinthKeys.CELL_SIZE / 2;
      int midZ = minZ + LabyrinthKeys.CELL_SIZE / 2;
      Archetype arch = archetypeAt(globalCellX, globalCellZ, seed);

      switch (arch) {
         case SPIKE_TRENCH -> stampSpikeTrench(chunk, minX, minZ, maxX, maxZ, midX, midZ, random);
         case ARCADE_CLOISTER -> stampArcadeCloister(chunk, minX, minZ, maxX, maxZ, midX, midZ, random);
         case CROSS_BRIDGE -> stampCrossBridge(chunk, minX, minZ, maxX, maxZ, midX, midZ, seed, globalCellX, globalCellZ);
         case GALLERY_STACK -> stampGalleryStack(chunk, minX, minZ, maxX, maxZ, midX, midZ, random);
         case CHOKE_HOOK -> stampChokeHook(chunk, minX, minZ, maxX, maxZ, midX, midZ, random);
         case VOID_WELL -> stampVoidWell(chunk, minX, minZ, maxX, maxZ, midX, midZ, random);
      }
   }

   private static void stampSpikeTrench(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int midX,
      int midZ,
      RandomSource random
   ) {
      int floor = LabyrinthKeys.FLOOR_Y;
      int vault = LabyrinthKeys.VAULT_FLOOR_Y;
      // Side walks at primary; trench spikes in the well floor.
      for (int x = minX + 1; x <= maxX - 1; x++) {
         for (int z = minZ + 1; z <= maxZ - 1; z++) {
            boolean onWalk = Math.abs(x - midX) <= 1 || Math.abs(z - midZ) <= 1;
            if (onWalk && (Math.abs(x - midX) <= 1 && Math.abs(z - midZ) <= 1 || Math.abs(x - midX) == 1 || Math.abs(z - midZ) == 1)) {
               set(chunk, x, floor, z, LabyrinthMaterials.ordeTile());
               if (random.nextInt(8) == 0) {
                  set(chunk, x, LabyrinthKeys.CEILING_Y - 1, z, CHAIN);
               }
            }
            set(chunk, x, vault, z, LabyrinthMaterials.grate());
            if (((x + z) & 1) == 0) {
               set(chunk, x, vault + 1, z, LabyrinthMaterials.spikeFloor());
            }
         }
      }
      // Hanging hooks along mid
      for (int y = floor + 4; y <= LabyrinthKeys.CEILING_Y - 1; y += 2) {
         set(chunk, midX, y, midZ, LabyrinthMaterials.hangingHook());
      }
   }

   private static void stampArcadeCloister(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int midX,
      int midZ,
      RandomSource random
   ) {
      int floor = LabyrinthKeys.FLOOR_Y;
      int mezz = LabyrinthKeys.MEZZ_FLOOR_Y;
      // Inner ring walk + arch trim facing the pit.
      for (int x = minX + 1; x <= maxX - 1; x++) {
         set(chunk, x, floor, minZ + 1, LabyrinthMaterials.ordeStone());
         set(chunk, x, floor, maxZ - 1, LabyrinthMaterials.ordeStone());
         set(chunk, x, floor + 1, minZ + 1, LabyrinthMaterials.galleryRail());
         set(chunk, x, floor + 1, maxZ - 1, LabyrinthMaterials.galleryRail());
         set(chunk, x, mezz, minZ + 1, LabyrinthMaterials.ordePanel());
         set(chunk, x, mezz, maxZ - 1, LabyrinthMaterials.ordePanel());
      }
      for (int z = minZ + 1; z <= maxZ - 1; z++) {
         set(chunk, minX + 1, floor, z, LabyrinthMaterials.ordeStone());
         set(chunk, maxX - 1, floor, z, LabyrinthMaterials.ordeStone());
         set(chunk, minX + 1, floor + 1, z, LabyrinthMaterials.galleryRail());
         set(chunk, maxX - 1, floor + 1, z, LabyrinthMaterials.galleryRail());
         set(chunk, minX + 1, mezz, z, LabyrinthMaterials.ordePanel());
         set(chunk, maxX - 1, mezz, z, LabyrinthMaterials.ordePanel());
      }
      // Arch motifs on wall interiors
      for (int[] p : new int[][] {
         {minX, midZ}, {maxX, midZ}, {midX, minZ}, {midX, maxZ}
      }) {
         set(chunk, p[0], floor + LabyrinthKeys.ARCH_CLEARANCE, p[1], LabyrinthMaterials.arch());
         set(chunk, p[0], mezz + 2, p[1], LabyrinthMaterials.arch());
      }
      set(chunk, midX, LabyrinthKeys.VAULT_FLOOR_Y, midZ, LabyrinthMaterials.coldVault());
      if (random.nextBoolean()) {
         set(chunk, midX, floor + 5, midZ, LabyrinthMaterials.lantern());
      }
   }

   private static void stampCrossBridge(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int midX,
      int midZ,
      long seed,
      int globalCellX,
      int globalCellZ
   ) {
      int floor = LabyrinthKeys.FLOOR_Y;
      int mezz = LabyrinthKeys.MEZZ_FLOOR_Y;
      // Always place both axes as walkable bridges over the void.
      for (int x = minX + 1; x <= maxX - 1; x++) {
         set(chunk, x, floor, midZ, LabyrinthMaterials.bridgeDeck());
         set(chunk, x, floor, midZ - 1, LabyrinthMaterials.bridgeDeckFull());
         set(chunk, x, floor, midZ + 1, LabyrinthMaterials.bridgeDeckFull());
         set(chunk, x, mezz, midZ, LabyrinthMaterials.ordePanel());
      }
      for (int z = minZ + 1; z <= maxZ - 1; z++) {
         set(chunk, midX, floor, z, LabyrinthMaterials.bridgeDeck());
         set(chunk, midX - 1, floor, z, LabyrinthMaterials.bridgeDeckFull());
         set(chunk, midX + 1, floor, z, LabyrinthMaterials.bridgeDeckFull());
         set(chunk, midX, mezz, z, LabyrinthMaterials.ordePanel());
      }
      set(chunk, midX, floor, midZ, LabyrinthMaterials.ordeTile());
      set(chunk, midX, LabyrinthKeys.VAULT_FLOOR_Y + 1, midZ, LabyrinthMaterials.spikeFloor());
      // Seed-stable which axis also gets a vault-level tunnel bridge
      int hash = Long.hashCode(Mth.getSeed(globalCellX, 3, globalCellZ) ^ seed);
      if ((hash & 1) == 0) {
         for (int x = minX + 1; x <= maxX - 1; x++) {
            set(chunk, x, LabyrinthKeys.VAULT_FLOOR_Y + 3, midZ, LabyrinthMaterials.bridgeDeck());
         }
      } else {
         for (int z = minZ + 1; z <= maxZ - 1; z++) {
            set(chunk, midX, LabyrinthKeys.VAULT_FLOOR_Y + 3, z, LabyrinthMaterials.bridgeDeck());
         }
      }
   }

   private static void stampGalleryStack(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int midX,
      int midZ,
      RandomSource random
   ) {
      int floor = LabyrinthKeys.FLOOR_Y;
      int mezz = LabyrinthKeys.MEZZ_FLOOR_Y;
      for (int x = minX + 1; x <= maxX - 1; x++) {
         for (int z = minZ + 1; z <= maxZ - 1; z++) {
            boolean ring = x == minX + 1 || x == maxX - 1 || z == minZ + 1 || z == maxZ - 1;
            boolean cross = Math.abs(x - midX) <= 1 || Math.abs(z - midZ) <= 1;
            if (ring || cross) {
               set(chunk, x, floor, z, LabyrinthMaterials.ordeStone());
               set(chunk, x, mezz, z, LabyrinthMaterials.ordePanel());
               if (ring && random.nextInt(5) == 0) {
                  set(chunk, x, floor + 1, z, LabyrinthMaterials.galleryRail());
                  set(chunk, x, mezz + 1, z, LabyrinthMaterials.galleryRail());
               }
            }
         }
      }
      // Vertical stair shaft in one corner of the ring
      int sx = minX + 2;
      int sz = minZ + 2;
      for (int y = LabyrinthKeys.VAULT_FLOOR_Y + 1; y < mezz; y++) {
         set(chunk, sx, y, sz, AIR);
      }
      for (int y = LabyrinthKeys.VAULT_FLOOR_Y + 1; y <= mezz; y += 2) {
         set(chunk, sx, y, sz, LabyrinthMaterials.ordeStone());
         set(chunk, sx + 1, y + 1, sz, LabyrinthMaterials.ordeStone());
      }
   }

   private static void stampChokeHook(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int midX,
      int midZ,
      RandomSource random
   ) {
      int floor = LabyrinthKeys.FLOOR_Y;
      // Only ±0 mid walk — choke. Margins fill with bars/spikes.
      for (int x = minX + 1; x <= maxX - 1; x++) {
         for (int z = minZ + 1; z <= maxZ - 1; z++) {
            boolean walk = x == midX || z == midZ;
            if (walk) {
               set(chunk, x, floor, z, LabyrinthMaterials.riveted());
            } else {
               set(chunk, x, floor, z, LabyrinthMaterials.spikeFloor());
               if (random.nextInt(3) == 0) {
                  set(chunk, x, floor + 1, z, LabyrinthMaterials.bars());
               }
            }
         }
      }
      for (int y = floor + 2; y <= LabyrinthKeys.CEILING_Y - 1; y++) {
         if ((y & 1) == 0) {
            set(chunk, midX, y, midZ, CHAIN);
         } else {
            set(chunk, midX, y, midZ, LabyrinthMaterials.hangingHook());
         }
      }
      set(chunk, midX, LabyrinthKeys.CEILING_Y - 1, midZ, LabyrinthMaterials.lantern());
   }

   private static void stampVoidWell(
      ChunkAccess chunk,
      int minX,
      int minZ,
      int maxX,
      int maxZ,
      int midX,
      int midZ,
      RandomSource random
   ) {
      int vault = LabyrinthKeys.VAULT_FLOOR_Y;
      for (int x = minX + 1; x <= maxX - 1; x++) {
         for (int z = minZ + 1; z <= maxZ - 1; z++) {
            set(chunk, x, vault, z, LabyrinthMaterials.coldVault());
            // Clear primary floor — pure depth
            set(chunk, x, LabyrinthKeys.FLOOR_Y, z, AIR);
            set(chunk, x, LabyrinthKeys.MEZZ_FLOOR_Y, z, AIR);
         }
      }
      // Sparse rim pads so the cell stays traversable from wall crowns / doorways
      for (int[] p : new int[][] {
         {minX + 1, midZ}, {maxX - 1, midZ}, {midX, minZ + 1}, {midX, maxZ - 1}
      }) {
         set(chunk, p[0], LabyrinthKeys.FLOOR_Y, p[1], LabyrinthMaterials.ordePanel());
         set(chunk, p[0], LabyrinthKeys.MEZZ_FLOOR_Y, p[1], LabyrinthMaterials.ordePanel());
      }
      if (random.nextInt(3) == 0) {
         set(chunk, midX, vault + 1, midZ, LabyrinthMaterials.voidSconce());
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
