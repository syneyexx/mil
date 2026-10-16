package org.millenaire.labyrinth;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Registry keys and spatial constants for the Millénaire Labyrinth dimension.
 */
public final class LabyrinthKeys {
   public static final String MODID = "millenaire";

   public static final ResourceKey<Level> LABYRINTH_LEVEL = ResourceKey.create(
      Registries.DIMENSION,
      ResourceLocation.fromNamespaceAndPath(MODID, "labyrinth")
   );

   public static final ResourceKey<DimensionType> LABYRINTH_TYPE = ResourceKey.create(
      Registries.DIMENSION_TYPE,
      ResourceLocation.fromNamespaceAndPath(MODID, "labyrinth")
   );

   public static final ResourceKey<Biome> LABYRINTH_BIOME = ResourceKey.create(
      Registries.BIOME,
      ResourceLocation.fromNamespaceAndPath(MODID, "labyrinth")
   );

   /** Walkable floor Y (blackstone). */
   public static final int FLOOR_Y = 64;
   /** Solid ceiling cap Y (deepslate). */
   public static final int CEILING_Y = 73;
   /** Blocks per maze cell on X/Z. */
   public static final int CELL_SIZE = 8;

   /** Arrival chamber world block origin (cell 0,0 southwest corner). */
   public static final int ARRIVAL_X = 0;
   public static final int ARRIVAL_Z = 0;

   /** Cells per maze region (square). */
   public static final int REGION_SIZE = 64;

   /** Global cell coordinates of the Core chamber (far from arrival). */
   public static final int CORE_CELL_X = 56;
   public static final int CORE_CELL_Z = 56;

   /** Place a themed hub every N cells on both axes. */
   public static final int HUB_SPACING = 8;

   /** Dimension generation height (matches a compact dimension type). */
   public static final int GEN_DEPTH = 128;
   public static final int MIN_Y = 0;

   private LabyrinthKeys() {
   }
}
