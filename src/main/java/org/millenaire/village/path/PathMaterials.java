package org.millenaire.village.path;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.millenaire.block.ModBlocks;

public final class PathMaterials {
   private static Map<String, PathMaterials.MaterialPair> materials;

   private PathMaterials() {
   }

   private static Map<String, PathMaterials.MaterialPair> getMaterials() {
      if (materials == null) {
         materials = new HashMap<>();
         materials.put("pathdirt", new PathMaterials.MaterialPair((Block)ModBlocks.PATH_DIRT.get(), (SlabBlock)ModBlocks.PATH_DIRT_SLAB.get()));
         materials.put("pathgravel", new PathMaterials.MaterialPair((Block)ModBlocks.PATH_GRAVEL.get(), (SlabBlock)ModBlocks.PATH_GRAVEL_SLAB.get()));
         materials.put("pathslabs", new PathMaterials.MaterialPair((Block)ModBlocks.PATH_SLABS.get(), (SlabBlock)ModBlocks.PATH_SLABS_SLAB.get()));
         materials.put("pathsandstone", new PathMaterials.MaterialPair((Block)ModBlocks.PATH_SANDSTONE.get(), (SlabBlock)ModBlocks.PATH_SANDSTONE_SLAB.get()));
         materials.put(
            "pathochretiles", new PathMaterials.MaterialPair((Block)ModBlocks.PATH_OCHRE_TILES.get(), (SlabBlock)ModBlocks.PATH_OCHRE_TILES_SLAB.get())
         );
         materials.put(
            "pathgravelslabs", new PathMaterials.MaterialPair((Block)ModBlocks.PATH_GRAVEL_SLABS.get(), (SlabBlock)ModBlocks.PATH_GRAVEL_SLABS_SLAB.get())
         );
         materials.put("pathsnow", new PathMaterials.MaterialPair((Block)ModBlocks.PATH_SNOW.get(), (SlabBlock)ModBlocks.PATH_SNOW_SLAB.get()));
         registerCompatPaths(materials);
      }

      return materials;
   }

   private static void registerCompatPaths(Map<String, PathMaterials.MaterialPair> map) {
      // Macaw's Paths and Pavings — full block + slab when present; otherwise skip.
      registerExternal(map, "mcw_gravel_path", "mcwpaths:gravel_path", "mcwpaths:gravel_path_slab");
      registerExternal(map, "mcw_sand_path", "mcwpaths:sand_path", "mcwpaths:sand_path_slab");
      registerExternal(map, "mcw_red_sand_path", "mcwpaths:red_sand_path", "mcwpaths:red_sand_path_slab");
      registerExternal(map, "mcw_stone_path", "mcwpaths:stone_path", "mcwpaths:stone_path_slab");
      registerExternal(map, "mcw_andesite_path", "mcwpaths:andesite_path", "mcwpaths:andesite_path_slab");
      registerExternal(map, "mcw_cobblestone_path", "mcwpaths:cobblestone_path", "mcwpaths:cobblestone_path_slab");
      // Fallbacks using Macaw stairs as full + vanilla slabs when path mod missing but stairs present
      registerExternalFullOnly(map, "mcw_oak_path", "mcwpaths:oak_planks_path");
   }

   private static void registerExternal(Map<String, PathMaterials.MaterialPair> map, String key, String fullId, String slabId) {
      ResourceLocation fullRl = ResourceLocation.tryParse(fullId);
      ResourceLocation slabRl = ResourceLocation.tryParse(slabId);
      if (fullRl == null || slabRl == null) {
         return;
      }
      if (!ModList.get().isLoaded(fullRl.getNamespace())) {
         return;
      }
      Block full = BuiltInRegistries.BLOCK.getOptional(fullRl).orElse(null);
      Block slab = BuiltInRegistries.BLOCK.getOptional(slabRl).orElse(null);
      if (full != null && slab instanceof SlabBlock slabBlock) {
         map.put(key, new PathMaterials.MaterialPair(full, slabBlock));
      }
   }

   private static void registerExternalFullOnly(Map<String, PathMaterials.MaterialPair> map, String key, String fullId) {
      ResourceLocation fullRl = ResourceLocation.tryParse(fullId);
      if (fullRl == null || !ModList.get().isLoaded(fullRl.getNamespace())) {
         return;
      }
      Block full = BuiltInRegistries.BLOCK.getOptional(fullRl).orElse(null);
      if (full != null && Blocks.STONE_SLAB instanceof SlabBlock stoneSlab) {
         map.put(key, new PathMaterials.MaterialPair(full, stoneSlab));
      }
   }

   /**
    * Resolve a Millenaire path key, a compat alias, or a direct {@code namespace:block} id
    * (paired with stone slab when the block is not itself a slab pair).
    */
   @Nullable
   public static PathMaterials.MaterialPair resolve(String materialName) {
      if (materialName == null || materialName.isEmpty()) {
         return null;
      }
      PathMaterials.MaterialPair known = getMaterials().get(materialName);
      if (known != null) {
         return known;
      }
      if (materialName.contains(":")) {
         ResourceLocation rl = ResourceLocation.tryParse(materialName);
         if (rl == null || !ModList.get().isLoaded(rl.getNamespace())) {
            return null;
         }
         Block full = BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
         if (full == null) {
            return null;
         }
         if (full instanceof SlabBlock slabOnly) {
            return new PathMaterials.MaterialPair(full, slabOnly);
         }
         ResourceLocation slabGuess = ResourceLocation.fromNamespaceAndPath(rl.getNamespace(), rl.getPath() + "_slab");
         Block slab = BuiltInRegistries.BLOCK.getOptional(slabGuess).orElse(Blocks.STONE_SLAB);
         if (slab instanceof SlabBlock slabBlock) {
            return new PathMaterials.MaterialPair(full, slabBlock);
         }
      }
      return null;
   }

   public static int rankOf(BlockState state, List<String> orderedMaterials) {
      Block block = state.getBlock();

      for (int i = 0; i < orderedMaterials.size(); i++) {
         PathMaterials.MaterialPair mp = resolve(orderedMaterials.get(i));
         if (mp != null && (mp.fullBlock() == block || mp.slabBlock() == block)) {
            return i;
         }
      }

      return -1;
   }

   public record MaterialPair(Block fullBlock, SlabBlock slabBlock) {
   }
}
