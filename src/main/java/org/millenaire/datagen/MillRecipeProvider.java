package org.millenaire.datagen;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredBlock;
import org.millenaire.block.ModBlocks;
import org.millenaire.item.ModItems;

public class MillRecipeProvider extends RecipeProvider {
   private static final MillRecipeProvider.PathPair[] PATH_PAIRS = new MillRecipeProvider.PathPair[]{
      new MillRecipeProvider.PathPair("path_dirt", ModBlocks.PATH_DIRT, ModBlocks.PATH_DIRT_SLAB),
      new MillRecipeProvider.PathPair("path_gravel", ModBlocks.PATH_GRAVEL, ModBlocks.PATH_GRAVEL_SLAB),
      new MillRecipeProvider.PathPair("path_gravel_slabs", ModBlocks.PATH_GRAVEL_SLABS, ModBlocks.PATH_GRAVEL_SLABS_SLAB),
      new MillRecipeProvider.PathPair("path_ochre_tiles", ModBlocks.PATH_OCHRE_TILES, ModBlocks.PATH_OCHRE_TILES_SLAB),
      new MillRecipeProvider.PathPair("path_sandstone", ModBlocks.PATH_SANDSTONE, ModBlocks.PATH_SANDSTONE_SLAB),
      new MillRecipeProvider.PathPair("path_slabs", ModBlocks.PATH_SLABS, ModBlocks.PATH_SLABS_SLAB),
      new MillRecipeProvider.PathPair("path_snow", ModBlocks.PATH_SNOW, ModBlocks.PATH_SNOW_SLAB)
   };

   public MillRecipeProvider(PackOutput output, CompletableFuture<Provider> registries) {
      super(output, registries);
   }

   protected void buildRecipes(RecipeOutput out) {
      smelting(out);
      foodAndDrink(out);
      paintedBricks(out);
      paintBuckets(out);
      pathSlabs(out);
      byzantineTiles(out);
      japaneseTiles(out);
      normanBlocks(out);
      inuitBlocks(out);
   }

   private static void smelting(RecipeOutput out) {
      smeltingOf(
         RecipeCategory.BUILDING_BLOCKS,
         (ItemLike)ModBlocks.MUD_BRICK.get(),
         "mud_brick",
         (ItemLike)ModBlocks.PAINTED_BRICKS.get(DyeColor.WHITE).get(),
         "painted_brick_white",
         out
      );
      smeltingOf(RecipeCategory.FOOD, (ItemLike)ModItems.BEARMEAT_RAW.get(), "bearmeat_raw", (ItemLike)ModItems.BEARMEAT_COOKED.get(), "bearmeat_cooked", out);
      smeltingOf(RecipeCategory.FOOD, (ItemLike)ModItems.WOLFMEAT_RAW.get(), "wolfmeat_raw", (ItemLike)ModItems.WOLFMEAT_COOKED.get(), "wolfmeat_cooked", out);
      smeltingOf(RecipeCategory.FOOD, (ItemLike)ModItems.SEAFOOD_RAW.get(), "seafood_raw", (ItemLike)ModItems.SEAFOOD_COOKED.get(), "seafood_cooked", out);
      campfireAndSmokingOf((ItemLike)ModItems.BEARMEAT_RAW.get(), "bearmeat_raw", (ItemLike)ModItems.BEARMEAT_COOKED.get(), "bearmeat_cooked", out);
      campfireAndSmokingOf((ItemLike)ModItems.WOLFMEAT_RAW.get(), "wolfmeat_raw", (ItemLike)ModItems.WOLFMEAT_COOKED.get(), "wolfmeat_cooked", out);
      campfireAndSmokingOf((ItemLike)ModItems.SEAFOOD_RAW.get(), "seafood_raw", (ItemLike)ModItems.SEAFOOD_COOKED.get(), "seafood_cooked", out);
   }

   private static void smeltingOf(RecipeCategory category, ItemLike input, String inputName, ItemLike result, String resultName, RecipeOutput out) {
      SimpleCookingRecipeBuilder.smelting(Ingredient.of(new ItemLike[]{input}), category, result, 1.0F, 200)
         .unlockedBy("has_" + inputName, has(input))
         .save(out, id(resultName + "_from_smelting"));
   }

   private static void campfireAndSmokingOf(ItemLike input, String inputName, ItemLike result, String resultName, RecipeOutput out) {
      SimpleCookingRecipeBuilder.campfireCooking(Ingredient.of(new ItemLike[]{input}), RecipeCategory.FOOD, result, 1.0F, 600)
         .unlockedBy("has_" + inputName, has(input))
         .save(out, id(resultName + "_from_campfire_cooking"));
      SimpleCookingRecipeBuilder.smoking(Ingredient.of(new ItemLike[]{input}), RecipeCategory.FOOD, result, 1.0F, 100)
         .unlockedBy("has_" + inputName, has(input))
         .save(out, id(resultName + "_from_smoking"));
   }

   private static void foodAndDrink(RecipeOutput out) {
      ShapedRecipeBuilder.shaped(RecipeCategory.FOOD, (ItemLike)ModItems.CIDER.get())
         .group("cider")
         .pattern("##")
         .pattern("##")
         .pattern("##")
         .define('#', (ItemLike)ModItems.CIDERAPPLE.get())
         .unlockedBy("has_cider_apple", has((ItemLike)ModItems.CIDERAPPLE.get()))
         .save(out, id("cider"));
      ShapedRecipeBuilder.shaped(RecipeCategory.FOOD, (ItemLike)ModItems.WINEBASIC.get())
         .group("winebasic")
         .pattern("##")
         .pattern("##")
         .pattern("##")
         .define('#', (ItemLike)ModItems.GRAPES.get())
         .unlockedBy("has_grapes", has((ItemLike)ModItems.GRAPES.get()))
         .save(out, id("winebasic"));
      ShapedRecipeBuilder.shaped(RecipeCategory.FOOD, (ItemLike)ModItems.OLIVEOIL.get())
         .group("olives")
         .pattern("##")
         .pattern("##")
         .define('#', (ItemLike)ModItems.OLIVES.get())
         .unlockedBy("has_olives", has((ItemLike)ModItems.OLIVES.get()))
         .save(out, id("oliveoil"));
      ShapedRecipeBuilder.shaped(RecipeCategory.FOOD, (ItemLike)ModItems.CHICKENCURRY.get())
         .group("chickencurry")
         .pattern("T")
         .pattern("C")
         .pattern("R")
         .define('T', (ItemLike)ModItems.TURMERIC.get())
         .define('C', Items.CHICKEN)
         .define('R', (ItemLike)ModItems.RICE.get())
         .unlockedBy("has_rice", has((ItemLike)ModItems.RICE.get()))
         .unlockedBy("has_turmeric", has((ItemLike)ModItems.TURMERIC.get()))
         .save(out, id("chickencurry"));
      ShapedRecipeBuilder.shaped(RecipeCategory.FOOD, (ItemLike)ModItems.VEGCURRY.get())
         .group("vegcurry")
         .pattern("#")
         .pattern("X")
         .define('#', (ItemLike)ModItems.TURMERIC.get())
         .define('X', (ItemLike)ModItems.RICE.get())
         .unlockedBy("has_rice", has((ItemLike)ModItems.RICE.get()))
         .unlockedBy("has_turmeric", has((ItemLike)ModItems.TURMERIC.get()))
         .save(out, id("vegcurry"));
      ShapedRecipeBuilder.shaped(RecipeCategory.FOOD, (ItemLike)ModItems.MASA.get())
         .group("masa")
         .pattern("###")
         .define('#', (ItemLike)ModItems.MAIZE.get())
         .unlockedBy("has_maize", has((ItemLike)ModItems.MAIZE.get()))
         .save(out, id("masa"));
      ShapedRecipeBuilder.shaped(RecipeCategory.FOOD, (ItemLike)ModItems.WAH.get())
         .group("wah")
         .pattern("#X#")
         .define('#', (ItemLike)ModItems.MAIZE.get())
         .define('X', Items.CHICKEN)
         .unlockedBy("has_maize", has((ItemLike)ModItems.MAIZE.get()))
         .save(out, id("wah"));
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, Items.WHITE_WOOL)
         .pattern("##")
         .pattern("##")
         .define('#', (ItemLike)ModItems.COTTON.get())
         .unlockedBy("has_cotton", has((ItemLike)ModItems.COTTON.get()))
         .save(out, id("cotton_to_wool"));
   }

   private static void paintedBricks(RecipeOutput out) {
      for (DyeColor color : DyeColor.values()) {
         ItemLike brick = (ItemLike)ModBlocks.PAINTED_BRICKS.get(color).get();
         String name = "painted_brick_" + color.getSerializedName();
         unlockedByAnyPaintedBrick(slabOf((ItemLike)ModBlocks.PAINTED_BRICK_SLABS.get(color).get(), brick, "slab_painted_brick")).save(out, id(name + "_slab"));
         unlockedByAnyPaintedBrick(stairsOf((ItemLike)ModBlocks.PAINTED_BRICK_STAIRS.get(color).get(), brick, "stairs_painted_brick"))
            .save(out, id(name + "_stairs"));
         unlockedByAnyPaintedBrick(wallOf((ItemLike)ModBlocks.PAINTED_BRICK_WALLS.get(color).get(), brick, "wall_painted_brick")).save(out, id(name + "_wall"));
      }
   }

   private static ShapedRecipeBuilder unlockedByAnyPaintedBrick(ShapedRecipeBuilder builder) {
      for (DyeColor color : DyeColor.values()) {
         builder.unlockedBy("has_painted_brick_" + color.getSerializedName(), has((ItemLike)ModBlocks.PAINTED_BRICKS.get(color).get()));
      }

      return builder;
   }

   private static void paintBuckets(RecipeOutput out) {
      ItemLike whiteBucket = (ItemLike)ModItems.PAINT_BUCKETS.get(DyeColor.WHITE).get();

      for (DyeColor color : DyeColor.values()) {
         if (color != DyeColor.WHITE) {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, (ItemLike)ModItems.PAINT_BUCKETS.get(color).get())
               .group("paint_bucket")
               .requires(whiteBucket)
               .requires(DyeItem.byColor(color))
               .unlockedBy("has_paint_bucket_white", has(whiteBucket))
               .save(out, id("paint_bucket_" + color.getSerializedName()));
         }
      }
   }

   private static void pathSlabs(RecipeOutput out) {
      for (MillRecipeProvider.PathPair pair : PATH_PAIRS) {
         ShapedRecipeBuilder builder = slabOf((ItemLike)pair.slab().get(), (ItemLike)pair.block().get(), "millenairepathslab");

         for (MillRecipeProvider.PathPair trigger : PATH_PAIRS) {
            builder.unlockedBy("has_" + trigger.name(), has((ItemLike)trigger.block().get()));
         }

         builder.save(out, id(pair.name() + "_slab"));
      }
   }

   private static void byzantineTiles(RecipeOutput out) {
      ItemLike tiles = (ItemLike)ModBlocks.BYZANTINE_TILES.get();
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)ModBlocks.BYZANTINE_STONE_TILES.get(), 6)
         .group("byzantine_stone_tiles")
         .pattern("###")
         .pattern("SSS")
         .define('#', tiles)
         .define('S', Items.STONE_BRICKS)
         .unlockedBy("has_byzantine_tiles", has(tiles))
         .save(out, id("byzantine_stone_tiles"));
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)ModBlocks.BYZANTINE_SANDSTONE_TILES.get(), 6)
         .group("byzantine_sandstone_tiles")
         .pattern("###")
         .pattern("SSS")
         .define('#', tiles)
         .define('S', Items.SANDSTONE)
         .unlockedBy("has_byzantine_tiles", has(tiles))
         .save(out, id("byzantine_sandstone_tiles"));
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)ModBlocks.BYZANTINE_STONE_ORNAMENT.get(), 6)
         .group("byzantine_stone_ornament")
         .pattern(" # ")
         .pattern("###")
         .pattern("SSS")
         .define('#', tiles)
         .define('S', Items.STONE_BRICKS)
         .unlockedBy("has_byzantine_tiles", has(tiles))
         .save(out, id("byzantine_stone_ornament"));
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)ModBlocks.BYZANTINE_SANDSTONE_ORNAMENT.get(), 6)
         .group("byzantine_sandstone_ornament")
         .pattern(" # ")
         .pattern("###")
         .pattern("SSS")
         .define('#', tiles)
         .define('S', Items.SANDSTONE)
         .unlockedBy("has_byzantine_tiles", has(tiles))
         .save(out, id("byzantine_sandstone_ornament"));
      slabOf((ItemLike)ModBlocks.BYZANTINE_TILES_SLAB.get(), tiles, "byzantine_tiles_slab")
         .unlockedBy("has_byzantine_tiles", has(tiles))
         .save(out, id("byzantine_tiles_slab"));
      stairsOf((ItemLike)ModBlocks.BYZANTINE_TILES_STAIRS.get(), tiles, null)
         .unlockedBy("has_byzantine_tiles", has(tiles))
         .save(out, id("byzantine_tiles_stairs"));
   }

   private static void japaneseTiles(RecipeOutput out) {
      ItemLike grayTiles = (ItemLike)ModBlocks.GRAY_TILES.get();
      slabOf((ItemLike)ModBlocks.GRAY_TILES_SLAB.get(), grayTiles, "gray_tiles_slab")
         .unlockedBy("has_gray_tiles", has(grayTiles))
         .save(out, id("gray_tiles_slab"));
      stairsOf((ItemLike)ModBlocks.GRAY_TILES_STAIRS.get(), grayTiles, null).unlockedBy("has_gray_tiles", has(grayTiles)).save(out, id("gray_tiles_stairs"));
   }

   private static void normanBlocks(RecipeOutput out) {
      ItemLike timberFrame = (ItemLike)ModBlocks.TIMBER_FRAME_PLAIN.get();
      ItemLike thatch = (ItemLike)ModBlocks.THATCH.get();
      ItemLike mudBrick = (ItemLike)ModBlocks.MUD_BRICK.get();
      slabOf((ItemLike)ModBlocks.TIMBER_FRAME_SLAB.get(), timberFrame, null)
         .unlockedBy("has_timber_frame", has(timberFrame))
         .save(out, id("timber_frame_slab"));
      stairsOf((ItemLike)ModBlocks.TIMBER_FRAME_STAIRS.get(), timberFrame, null)
         .unlockedBy("has_timber_frame", has(timberFrame))
         .save(out, id("timber_frame_stairs"));
      slabOf((ItemLike)ModBlocks.THATCH_SLAB.get(), thatch, null).unlockedBy("has_thatch", has(thatch)).save(out, id("thatch_slab"));
      stairsOf((ItemLike)ModBlocks.THATCH_STAIRS.get(), thatch, null).unlockedBy("has_thatch", has(thatch)).save(out, id("thatch_stairs"));
      slabOf((ItemLike)ModBlocks.MUD_BRICK_SLAB.get(), mudBrick, null).unlockedBy("has_mud_brick", has(mudBrick)).save(out, id("mud_brick_slab"));
      stairsOf((ItemLike)ModBlocks.MUD_BRICK_STAIRS.get(), mudBrick, null).unlockedBy("has_mud_brick", has(mudBrick)).save(out, id("mud_brick_stairs"));
      wallOf((ItemLike)ModBlocks.MUD_BRICK_WALL.get(), mudBrick, null).unlockedBy("has_mud_brick", has(mudBrick)).save(out, id("mud_brick_wall"));
   }

   private static void inuitBlocks(RecipeOutput out) {
      ItemLike snowBrick = (ItemLike)ModBlocks.SNOW_BRICK.get();
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, snowBrick, 4)
         .pattern("##")
         .pattern("##")
         .define('#', Items.SNOW_BLOCK)
         .unlockedBy("has_snow_block", has(Items.SNOW_BLOCK))
         .unlockedBy("has_snow_brick", has(snowBrick))
         .save(out, id("snow_brick"));
      wallOf((ItemLike)ModBlocks.SNOW_WALL.get(), snowBrick, null)
         .unlockedBy("has_snow_block", has(Items.SNOW_BLOCK))
         .unlockedBy("has_snow_brick", has(snowBrick))
         .save(out, id("snow_wall"));
      ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, (ItemLike)ModBlocks.ICE_BRICK.get(), 4)
         .pattern("##")
         .pattern("##")
         .define('#', Items.ICE)
         .unlockedBy("has_ice", has(Items.ICE))
         .save(out, id("ice_brick"));
   }

   private static ShapedRecipeBuilder slabOf(ItemLike result, ItemLike input, String group) {
      return ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, result, 6).group(group).pattern("###").define('#', input);
   }

   private static ShapedRecipeBuilder stairsOf(ItemLike result, ItemLike input, String group) {
      return ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, result, 4).group(group).pattern("#  ").pattern("## ").pattern("###").define('#', input);
   }

   private static ShapedRecipeBuilder wallOf(ItemLike result, ItemLike input, String group) {
      return ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, result, 6).group(group).pattern("###").pattern("###").define('#', input);
   }

   private static ResourceLocation id(String path) {
      return ResourceLocation.fromNamespaceAndPath("millenaire", path);
   }

   private record PathPair(String name, DeferredBlock<?> block, DeferredBlock<?> slab) {
   }
}
