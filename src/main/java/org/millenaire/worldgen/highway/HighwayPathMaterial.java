package org.millenaire.worldgen.highway;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.village.path.PathMaterials;

/** Resolves the sand-look highway surface (Macaw sand when present, else path_sandstone). */
public final class HighwayPathMaterial {
   private HighwayPathMaterial() {
   }

   public static BlockState resolve() {
      PathMaterials.MaterialPair macaw = PathMaterials.resolve("mcw_sand_path");
      if (macaw != null) {
         return macaw.fullBlock().defaultBlockState();
      }
      PathMaterials.MaterialPair sand = PathMaterials.resolve("pathsandstone");
      if (sand != null) {
         return sand.fullBlock().defaultBlockState();
      }
      return Blocks.SANDSTONE.defaultBlockState();
   }
}
