package org.millenaire.datagen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.loot.LootTableProvider.SubProviderEntry;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.millenaire.block.ModBlocks;
import org.millenaire.block.mock.MockBlock;

public class MillLootTableProvider {
   public static LootTableProvider create(PackOutput output, CompletableFuture<Provider> lookupProvider) {
      return new LootTableProvider(
         output, Set.of(), List.of(new SubProviderEntry(MillLootTableProvider.BlockLoot::new, LootContextParamSets.BLOCK)), lookupProvider
      );
   }

   private static class BlockLoot extends BlockLootSubProvider {
      private static final Set<String> MANUAL_LOOT_TABLES = Set.of(
         "apple_tree_leaves",
         "byzantine_sandstone_ornament",
         "byzantine_sandstone_tiles",
         "byzantine_stone_ornament",
         "byzantine_stone_tiles",
         "charpoy",
         "crop_cotton",
         "crop_maize",
         "crop_rice",
         "crop_turmeric",
         "crop_vine",
         "futon",
         "japanese_sliding_door",
         "mayan_statue",
         "olive_tree_leaves",
         "olive_tree_sapling",
         "paper_wall",
         "pistachio_tree_leaves",
         "pistachio_tree_sapling",
         "rice_paddy",
         "silk_worm",
         "snail_soil",
         "straw_bed",
         "wet_brick",
         "wooden_bars_indian",
         "wooden_sliding_door"
      );

      protected BlockLoot(Provider lookupProvider) {
         super(Set.of(), FeatureFlags.REGISTRY.allFlags(), lookupProvider);
      }

      protected void generate() {
         for (DyeColor color : DyeColor.values()) {
            this.dropSelf((Block)ModBlocks.PAINTED_BRICKS.get(color).get());
            this.dropSelf((Block)ModBlocks.DECORATED_BRICKS.get(color).get());
            this.dropSelf((Block)ModBlocks.PAINTED_BRICK_STAIRS.get(color).get());
            this.slab((Block)ModBlocks.PAINTED_BRICK_SLABS.get(color).get());
            this.dropSelf((Block)ModBlocks.PAINTED_BRICK_WALLS.get(color).get());
         }

         this.dropSelf((Block)ModBlocks.TIMBER_FRAME_PLAIN.get());
         this.dropSelf((Block)ModBlocks.TIMBER_FRAME_CROSS.get());
         this.dropSelf((Block)ModBlocks.TIMBER_FRAME_STAIRS.get());
         this.slab((Block)ModBlocks.TIMBER_FRAME_SLAB.get());
         this.dropSelf((Block)ModBlocks.THATCH.get());
         this.dropSelf((Block)ModBlocks.THATCH_STAIRS.get());
         this.slab((Block)ModBlocks.THATCH_SLAB.get());
         this.dropSelf((Block)ModBlocks.MUD_BRICK.get());
         this.dropSelf((Block)ModBlocks.MUD_BRICK_STAIRS.get());
         this.slab((Block)ModBlocks.MUD_BRICK_SLAB.get());
         this.dropSelf((Block)ModBlocks.MUD_BRICK_WALL.get());
         this.dropSelf((Block)ModBlocks.DIRT_WALL.get());
         this.dropSelf((Block)ModBlocks.ROSETTE.get());
         this.dropSelf((Block)ModBlocks.WOODEN_BARS.get());
         this.dropSelf((Block)ModBlocks.WOODEN_BARS_ROSETTE.get());
         this.dropSelf((Block)ModBlocks.STAINED_GLASS_WHITE.get());
         this.dropSelf((Block)ModBlocks.STAINED_GLASS_YELLOW.get());
         this.dropSelf((Block)ModBlocks.STAINED_GLASS_YELLOW_RED.get());
         this.dropSelf((Block)ModBlocks.STAINED_GLASS_RED_BLUE.get());
         this.dropSelf((Block)ModBlocks.STAINED_GLASS_GREEN_BLUE.get());
         this.dropSelf((Block)ModBlocks.APPLE_TREE_SAPLING.get());
         this.dropSelf((Block)ModBlocks.PATH_DIRT.get());
         this.slab((Block)ModBlocks.PATH_DIRT_SLAB.get());
         this.dropSelf((Block)ModBlocks.PATH_GRAVEL.get());
         this.slab((Block)ModBlocks.PATH_GRAVEL_SLAB.get());
         this.dropSelf((Block)ModBlocks.PATH_GRAVEL_SLABS.get());
         this.slab((Block)ModBlocks.PATH_GRAVEL_SLABS_SLAB.get());
         this.dropSelf((Block)ModBlocks.PATH_OCHRE_TILES.get());
         this.slab((Block)ModBlocks.PATH_OCHRE_TILES_SLAB.get());
         this.dropSelf((Block)ModBlocks.PATH_SANDSTONE.get());
         this.slab((Block)ModBlocks.PATH_SANDSTONE_SLAB.get());
         this.dropSelf((Block)ModBlocks.PATH_SLABS.get());
         this.slab((Block)ModBlocks.PATH_SLABS_SLAB.get());
         this.dropSelf((Block)ModBlocks.PATH_SNOW.get());
         this.slab((Block)ModBlocks.PATH_SNOW_SLAB.get());
         this.dropSelf((Block)ModBlocks.SANDSTONE_CARVED.get());
         this.dropSelf((Block)ModBlocks.SANDSTONE_CARVED_STAIRS.get());
         this.slab((Block)ModBlocks.SANDSTONE_CARVED_SLAB.get());
         this.dropSelf((Block)ModBlocks.SANDSTONE_CARVED_WALL.get());
         this.dropSelf((Block)ModBlocks.RED_SANDSTONE_CARVED.get());
         this.dropSelf((Block)ModBlocks.RED_SANDSTONE_CARVED_STAIRS.get());
         this.slab((Block)ModBlocks.RED_SANDSTONE_CARVED_SLAB.get());
         this.dropSelf((Block)ModBlocks.RED_SANDSTONE_CARVED_WALL.get());
         this.dropSelf((Block)ModBlocks.OCHRE_SANDSTONE_CARVED.get());
         this.dropSelf((Block)ModBlocks.OCHRE_SANDSTONE_CARVED_STAIRS.get());
         this.slab((Block)ModBlocks.OCHRE_SANDSTONE_CARVED_SLAB.get());
         this.dropSelf((Block)ModBlocks.OCHRE_SANDSTONE_CARVED_WALL.get());
         this.dropSelf((Block)ModBlocks.MAYAN_GOLD_BLOCK.get());
         this.dropSelf((Block)ModBlocks.BYZANTINE_TILES.get());
         this.dropSelf((Block)ModBlocks.BYZANTINE_TILES_STAIRS.get());
         this.slab((Block)ModBlocks.BYZANTINE_TILES_SLAB.get());
         this.dropSelf((Block)ModBlocks.BYZANTINE_MOSAIC_RED.get());
         this.dropSelf((Block)ModBlocks.BYZANTINE_FRESCO.get());
         this.dropSelf((Block)ModBlocks.JAPANESE_TILES.get());
         this.dropSelf((Block)ModBlocks.JAPANESE_TILES_STAIRS.get());
         this.slab((Block)ModBlocks.JAPANESE_TILES_SLAB.get());
         this.dropSelf((Block)ModBlocks.JAPANESE_STONE_TILES.get());
         this.dropSelf((Block)ModBlocks.GRAY_TILES.get());
         this.dropSelf((Block)ModBlocks.GRAY_TILES_STAIRS.get());
         this.slab((Block)ModBlocks.GRAY_TILES_SLAB.get());
         this.dropSelf((Block)ModBlocks.GREEN_TILES.get());
         this.dropSelf((Block)ModBlocks.GREEN_TILES_STAIRS.get());
         this.slab((Block)ModBlocks.GREEN_TILES_SLAB.get());
         this.dropSelf((Block)ModBlocks.RED_TILES.get());
         this.dropSelf((Block)ModBlocks.RED_TILES_STAIRS.get());
         this.slab((Block)ModBlocks.RED_TILES_SLAB.get());
         this.dropSelf((Block)ModBlocks.MUD_BRICK_SELJUK_ORNAMENTED.get());
         this.dropSelf((Block)ModBlocks.MUD_BRICK_SELJUK_DECORATED.get());
         this.dropSelf((Block)ModBlocks.MUD_BRICK_SMOOTH.get());
         this.dropSelf((Block)ModBlocks.SNOW_BRICK.get());
         this.dropSelf((Block)ModBlocks.ICE_BRICK.get());
         this.dropSelf((Block)ModBlocks.SNOW_WALL.get());
         this.dropSelf((Block)ModBlocks.SOD_OAK.get());
         this.dropSelf((Block)ModBlocks.SOD_SPRUCE.get());
         this.dropSelf((Block)ModBlocks.SOD_BIRCH.get());
         this.dropSelf((Block)ModBlocks.SOD_JUNGLE.get());
         this.dropSelf((Block)ModBlocks.SOD_ACACIA.get());
         this.dropSelf((Block)ModBlocks.SOD_DARK_OAK.get());
         this.dropSelf((Block)ModBlocks.INUIT_CARVING.get());
         this.dropSelf((Block)ModBlocks.FIRE_PIT.get());
         this.dropSelf((Block)ModBlocks.IMPORT_TABLE.get());
         this.add((Block)ModBlocks.LOCKED_CHEST.get(), noDrop());
         this.add((Block)ModBlocks.VILLAGE_PANEL.get(), noDrop());
         this.add((Block)ModBlocks.TAPESTRY.get(), noDrop());
      }

      private void slab(Block block) {
         this.add(block, x$0 -> this.createSlabItemTable(x$0));
      }

      protected Iterable<Block> getKnownBlocks() {
         List<Block> known = new ArrayList<>();

         for (DeferredHolder<Block, ? extends Block> holder : ModBlocks.BLOCKS.getEntries()) {
            Block block = (Block)holder.get();
            if (!(block instanceof MockBlock) && !MANUAL_LOOT_TABLES.contains(holder.getId().getPath())) {
               known.add(block);
            }
         }

         return known;
      }
   }
}
