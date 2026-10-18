package org.millenaire.labyrinth.gen;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.block.ModBlocks;

/**
 * Logical Orde / Bleed material palette for Labyrinth layout v3 (wellgrid).
 * Prefers dedicated Orde catalog blocks; corridors and wells use this everywhere.
 */
public final class LabyrinthMaterials {
   private LabyrinthMaterials() {
   }

   /** Cold Orde masonry — primary walls. */
   public static BlockState ordeStone() {
      return ModBlocks.ORDE_STONE.get().defaultBlockState();
   }

   /** Panel / trim for naves and Approach. */
   public static BlockState ordePanel() {
      return ModBlocks.ORDE_PANEL.get().defaultBlockState();
   }

   public static BlockState ordeBricks() {
      return ModBlocks.ORDE_BRICKS.get().defaultBlockState();
   }

   public static BlockState riveted() {
      return ModBlocks.RIVETED_STONE.get().defaultBlockState();
   }

   public static BlockState ordeTile() {
      return ModBlocks.ORDE_TILE.get().defaultBlockState();
   }

   public static BlockState ordePillar() {
      return ModBlocks.ORDE_PILLAR.get().defaultBlockState();
   }

   /** Domestic bleed flooring (reality-bleed suites). */
   public static BlockState bleedPlanks() {
      return ModBlocks.BLEED_PLANKS.get().defaultBlockState();
   }

   public static BlockState bleedOak() {
      return Blocks.OAK_PLANKS.defaultBlockState();
   }

   public static BlockState coldVault() {
      return ModBlocks.COLD_VAULT.get().defaultBlockState();
   }

   public static BlockState ruitHeart() {
      return ModBlocks.RUIT_HEART.get().defaultBlockState();
   }

   public static BlockState bars() {
      return ModBlocks.ORDE_BARS.get().defaultBlockState();
   }

   public static BlockState grate() {
      return ModBlocks.ORDE_GRATE.get().defaultBlockState();
   }

   public static BlockState spikeFloor() {
      return ModBlocks.SPIKE_FLOOR.get().defaultBlockState();
   }

   public static BlockState spikeWall() {
      return ModBlocks.SPIKE_WALL.get().defaultBlockState();
   }

   public static BlockState hangingHook() {
      return ModBlocks.HANGING_HOOK.get().defaultBlockState();
   }

   public static BlockState lantern() {
      return ModBlocks.ORDE_LANTERN.get().defaultBlockState();
   }

   public static BlockState voidSconce() {
      return ModBlocks.VOID_SCONCE.get().defaultBlockState();
   }

   public static BlockState arch() {
      return ModBlocks.ORDE_ARCH.get().defaultBlockState();
   }

   public static BlockState parapet() {
      return ModBlocks.ORDE_PARAPET.get().defaultBlockState();
   }

   public static BlockState galleryRail() {
      return ModBlocks.GALLERY_RAIL.get().defaultBlockState();
   }

   public static BlockState voidBeam() {
      return ModBlocks.VOID_BEAM.get().defaultBlockState();
   }

   public static BlockState bridgeDeck() {
      return ModBlocks.ORDE_STONE_SLAB.get().defaultBlockState();
   }

   public static BlockState bridgeDeckFull() {
      return ModBlocks.ORDE_PANEL_SLAB.get().defaultBlockState();
   }

   /**
    * Mixed wall masonry — seed-stable variation so wells do not read as flat plastic.
    */
   public static BlockState wallMix(int x, int y, int z, boolean cold) {
      int mix = Math.floorMod(x * 3 + z * 7 + y * 11, 9);
      if (cold) {
         return switch (mix) {
            case 0, 1 -> ordeStone();
            case 2, 3 -> ordePanel();
            case 4 -> riveted();
            case 5 -> coldVault();
            case 6 -> ordeBricks();
            case 7 -> ordePillar();
            default -> ordeStone();
         };
      }
      return switch (mix) {
         case 0, 1 -> ordeStone();
         case 2, 3 -> ordePanel();
         case 4 -> riveted();
         case 5 -> ordeBricks();
         default -> ordeStone();
      };
   }

   public static BlockState foundation() {
      return ModBlocks.ORDE_STONE.get().defaultBlockState();
   }
}
