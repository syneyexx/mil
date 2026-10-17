package org.millenaire.labyrinth.gen;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Logical Orde / Bleed material palette (P5.2).
 * Named for lore/docs; currently maps to vanilla stand-ins so stamps share one vocabulary.
 * Resource packs / future dedicated blocks can replace these getters without rewriting callers.
 */
public final class LabyrinthMaterials {
   private LabyrinthMaterials() {
   }

   /** Cold Orde masonry — primary walls. */
   public static BlockState ordeStone() {
      return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
   }

   /** Panel / trim for naves and Approach. */
   public static BlockState ordePanel() {
      return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
   }

   /** Domestic bleed flooring (reality-bleed suites). */
   public static BlockState bleedPlanks() {
      return Blocks.SPRUCE_PLANKS.defaultBlockState();
   }

   public static BlockState bleedOak() {
      return Blocks.OAK_PLANKS.defaultBlockState();
   }

   public static BlockState coldVault() {
      return Blocks.PACKED_ICE.defaultBlockState();
   }

   public static BlockState ruitHeart() {
      return Blocks.CRYING_OBSIDIAN.defaultBlockState();
   }
}
