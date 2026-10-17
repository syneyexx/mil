package org.millenaire.labyrinth;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Registry keys and spatial constants for the Millénaire Labyrinth dimension.
 *
 * <p>Floor bands (Revival multi-level):
 * <ul>
 *   <li>Band 0 (vault) — Y 48–56, deeper routes toward the Core</li>
 *   <li>Band 1 (primary) — Y 64–73, Arrival and main maze</li>
 *   <li>Band 2 (mezzanine) — Y 74–82, stamped only inside vertical hubs</li>
 * </ul>
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

   /** Primary walkable floor Y (Arrival / main maze). */
   public static final int FLOOR_Y = 64;
   /** Solid ceiling of the primary band. */
   public static final int CEILING_Y = 73;

   /** Vault (lower) band floor / ceiling. */
   public static final int VAULT_FLOOR_Y = 48;
   public static final int VAULT_CEILING_Y = 56;

   /** Mezzanine (hub upper) band floor / ceiling. */
   public static final int MEZZ_FLOOR_Y = 74;
   public static final int MEZZ_CEILING_Y = 82;

   /** Blocks per maze cell on X/Z. */
   public static final int CELL_SIZE = 8;

   /**
    * How many blocks inset from cell walls stay solid in corridor interiors,
    * narrowing the walkable cross-section for claustrophobia (Wave 1).
    * Value 1 → walkable 4×4 inside an 8×8 cell (plus doorway gaps).
    */
   public static final int CORRIDOR_MARGIN = 1;

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

   /**
    * Manhattan cell distance from Arrival beyond which the vault band is carved
    * under the primary maze (Wave 4).
    */
   public static final int VAULT_START_DISTANCE = 12;

   /** Dimension generation height (matches dimension type). */
   public static final int GEN_DEPTH = 128;
   public static final int MIN_Y = 0;

   private LabyrinthKeys() {
   }

   public static int floorY(int band) {
      return switch (band) {
         case 0 -> VAULT_FLOOR_Y;
         case 2 -> MEZZ_FLOOR_Y;
         default -> FLOOR_Y;
      };
   }

   public static int ceilingY(int band) {
      return switch (band) {
         case 0 -> VAULT_CEILING_Y;
         case 2 -> MEZZ_CEILING_Y;
         default -> CEILING_Y;
      };
   }

   public static int bandForY(int y) {
      if (y >= VAULT_FLOOR_Y && y <= VAULT_CEILING_Y) {
         return 0;
      }
      if (y >= MEZZ_FLOOR_Y && y <= MEZZ_CEILING_Y) {
         return 2;
      }
      return 1;
   }

   public static int manhattanFromArrival(int globalCellX, int globalCellZ) {
      return Math.abs(globalCellX) + Math.abs(globalCellZ);
   }
}
