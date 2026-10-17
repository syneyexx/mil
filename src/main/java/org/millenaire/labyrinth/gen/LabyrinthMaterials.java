package org.millenaire.labyrinth.gen;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.block.ModBlocks;

/**
 * Logical Orde / Bleed material palette.
 * Prefers dedicated Orde catalog blocks when registered; falls back to vanilla stand-ins.
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
}
