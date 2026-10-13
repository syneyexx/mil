package org.millenaire.world.road;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.block.ModBlocks;

/** Biome-aware surface blocks for inter-village roads (prefers Millenaire path blocks). */
public final class PathPalette {
   private PathPalette() {
   }

   public static BlockState pathBlock(Holder<Biome> biome, int noise) {
      if (biome.is(BiomeTags.IS_BADLANDS)) {
         return noise % 5 == 0 ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.SAND.defaultBlockState();
      }
      if (biome.is(BiomeTags.IS_OCEAN)) {
         return noise % 4 == 0 ? Blocks.MUD_BRICKS.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState();
      }
      if (biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_MOUNTAIN)) {
         return noise % 3 == 0 ? Blocks.GRAVEL.defaultBlockState() : ModBlocks.PATH_DIRT.get().defaultBlockState();
      }
      if (biome.is(BiomeTags.IS_JUNGLE)) {
         return noise % 3 == 0 ? Blocks.PODZOL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
      }
      // Default: Millenaire dirt path with flecks
      if (noise % 7 == 0) {
         return Blocks.COARSE_DIRT.defaultBlockState();
      }
      if (noise % 11 == 0) {
         return Blocks.GRAVEL.defaultBlockState();
      }
      return ModBlocks.PATH_DIRT.get().defaultBlockState();
   }

   public static BlockState bridgeDeck(Holder<Biome> biome) {
      if (biome.is(BiomeTags.IS_BADLANDS)) {
         return Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState();
      }
      if (biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_MOUNTAIN)) {
         return Blocks.SPRUCE_PLANKS.defaultBlockState();
      }
      if (biome.is(BiomeTags.IS_JUNGLE)) {
         return Blocks.JUNGLE_PLANKS.defaultBlockState();
      }
      return Blocks.OAK_PLANKS.defaultBlockState();
   }

   public static BlockState bridgeRail(Holder<Biome> biome) {
      if (biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_MOUNTAIN)) {
         return Blocks.SPRUCE_FENCE.defaultBlockState();
      }
      if (biome.is(BiomeTags.IS_JUNGLE)) {
         return Blocks.JUNGLE_FENCE.defaultBlockState();
      }
      return Blocks.OAK_FENCE.defaultBlockState();
   }

   public static BlockState tunnelWall(Holder<Biome> biome) {
      if (biome.is(BiomeTags.IS_BADLANDS)) {
         return Blocks.SANDSTONE.defaultBlockState();
      }
      if (biome.is(BiomeTags.IS_MOUNTAIN)) {
         return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
      }
      return Blocks.STONE_BRICKS.defaultBlockState();
   }

   public static BlockState fillBlock(Holder<Biome> biome) {
      if (biome.is(BiomeTags.IS_BADLANDS)) {
         return Blocks.SANDSTONE.defaultBlockState();
      }
      return Blocks.DIRT.defaultBlockState();
   }

   public static boolean isReplaceableNatural(BlockState state) {
      Block b = state.getBlock();
      if (state.isAir()) {
         return true;
      }
      if (b == Blocks.BEDROCK || b == Blocks.SPAWNER || b == Blocks.CHEST || b == Blocks.ENDER_CHEST || b == Blocks.BARREL) {
         return false;
      }
      if (state.is(Blocks.WATER) || state.is(Blocks.LAVA)) {
         return true;
      }
      return !state.hasBlockEntity();
   }
}
