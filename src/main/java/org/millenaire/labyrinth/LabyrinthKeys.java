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
 * <p>Layout v3 — <b>wellgrid</b>: each maze cell is a deep open well with walkable
 * wall crowns, arcade openings, gallery rings, and cross-cell bridges. Crazy hallway
 * archetypes are the default; ceremonial rooms are sparse anchors. Multi-cell
 * footprints (corridor / room / suite / nave) still apply to stamped chambers.
 *
 * <p>Floor bands (Revival multi-level, now visible together inside wells):
 * <ul>
 *   <li>Band 0 (vault) — Y 48–56, pit floors / lower tunnels</li>
 *   <li>Band 1 (primary) — Y 64–73, main gallery ring + bridges</li>
 *   <li>Band 2 (mezzanine) — Y 74–82, upper overlooks + wall crowns</li>
 *   <li>Above mezz — open void to the horizon (De Ruit sky anchor)</li>
 * </ul>
 */
public final class LabyrinthKeys {
   public static final String MODID = "millenaire";

   /** Breaking layout generation version — regenerate the Labyrinth dim after bumps. */
   public static final int LAYOUT_VERSION = 3;

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

   /**
    * Walkable wall-crown Y (parapet tops). Players can traverse the grid along well frames.
    */
   public static final int WALL_CROWN_Y = MEZZ_CEILING_Y;

   /** Open void starts above the wall crown — infinite-grid horizon. */
   public static final int SKY_VOID_Y = MEZZ_CEILING_Y + 1;

   /**
    * Primary arch opening heights inside shared cell walls (relative to floor).
    * Door lintels sit at {@code FLOOR_Y + ARCH_HEIGHT}.
    */
   public static final int ARCH_CLEARANCE = 3;

   /** Blocks per maze cell on X/Z (connectivity grid; footprints span multiple cells). */
   public static final int CELL_SIZE = 8;

   /**
    * Half-width of the walkable corridor channel around cell mid (±1 → 3 blocks wide).
    * Plus-shaped: N–S and E–W arms stay open; cell corners stay choked with bars/spikes.
    */
   public static final int CORRIDOR_WALK_HALF = 1;

   /**
    * @deprecated Use {@link #CORRIDOR_WALK_HALF}; kept so older call sites compile during migration.
    */
   @Deprecated
   public static final int CORRIDOR_MARGIN = 2;

   /** Arrival chamber world block origin (cell 0,0 southwest corner of the nave). */
   public static final int ARRIVAL_X = 0;
   public static final int ARRIVAL_Z = 0;

   /** Cells per maze region (square). */
   public static final int REGION_SIZE = 64;

   /** Global cell SW anchor of the Core chamber (far from arrival). */
   public static final int CORE_CELL_X = 56;
   public static final int CORE_CELL_Z = 56;

   /** Multi-cell spans (layout v2). */
   public static final int ARRIVAL_SPAN = 3;
   public static final int CORE_SPAN = 2;
   public static final int AUDIENCE_SPAN = 3;
   public static final int ROOM_SPAN = 2;
   public static final int SUITE_SPAN = 3;

   /**
    * Audience chamber SW anchor — north of Core, 3×3 nave for De Ruit.
    * Occupies cells [55..57] × [53..55]; Core occupies [56..57] × [56..57].
    */
   public static final int AUDIENCE_CELL_X = CORE_CELL_X - 1;
   public static final int AUDIENCE_CELL_Z = CORE_CELL_Z - AUDIENCE_SPAN;

   /** Place a themed hub every N cells on both axes. */
   public static final int HUB_SPACING = 8;

   /**
    * Manhattan cell distance from Arrival beyond which the vault band is carved
    * under the primary maze.
    */
   public static final int VAULT_START_DISTANCE = 12;

   /** Dimension generation height (matches dimension type). */
   public static final int GEN_DEPTH = 128;
   public static final int MIN_Y = 0;

   /** Zone Manhattan bands from Arrival (global cell). */
   public static final int ZONE_BLEED_END = 14;
   public static final int ZONE_ORDE_END = 36;
   public static final int ZONE_APPROACH_END = 52;

   /**
    * Footprint vocabulary for layout v2 stamps.
    * Block size ≈ span × {@link #CELL_SIZE}.
    */
   public enum Footprint {
      CORRIDOR(1),
      ROOM(2),
      SUITE(3),
      NAVE(3);

      public final int spanCells;

      Footprint(int spanCells) {
         this.spanCells = spanCells;
      }

      public int blockSize() {
         return this.spanCells * CELL_SIZE;
      }
   }

   /**
    * Psyche acts along the journey: Arrival → Bleed → Orde → Approach → Core/Audience.
    */
   public enum Zone {
      ARRIVAL,
      BLEED,
      ORDE,
      APPROACH,
      CORE
   }

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

   /** World X of the Arrival nave center (Scharnier pedestal). */
   public static int arrivalCenterBlockX() {
      return ARRIVAL_X + (ARRIVAL_SPAN * CELL_SIZE) / 2;
   }

   /** World Z of the Arrival nave center (Scharnier pedestal). */
   public static int arrivalCenterBlockZ() {
      return ARRIVAL_Z + (ARRIVAL_SPAN * CELL_SIZE) / 2;
   }

   /**
    * Player spawn beside the Scharnier — offset so feet do not embed in the pedestal.
    */
   public static double arrivalSpawnX() {
      return arrivalCenterBlockX() + 0.5;
   }

   public static double arrivalSpawnZ() {
      return arrivalCenterBlockZ() + 2.5;
   }

   public static double arrivalSpawnY() {
      return FLOOR_Y + 1.0;
   }

   /** World block center of the Core arena (layout v2 multi-cell). */
   public static int coreCenterBlockX() {
      return CORE_CELL_X * CELL_SIZE + (CORE_SPAN * CELL_SIZE) / 2;
   }

   public static int coreCenterBlockZ() {
      return CORE_CELL_Z * CELL_SIZE + (CORE_SPAN * CELL_SIZE) / 2;
   }

   /** World block center of the Audience / De Ruit nave. */
   public static int audienceCenterBlockX() {
      return AUDIENCE_CELL_X * CELL_SIZE + (AUDIENCE_SPAN * CELL_SIZE) / 2;
   }

   public static int audienceCenterBlockZ() {
      return AUDIENCE_CELL_Z * CELL_SIZE + (AUDIENCE_SPAN * CELL_SIZE) / 2;
   }

   public static boolean inArrivalFootprint(int globalCellX, int globalCellZ) {
      return globalCellX >= 0 && globalCellX < ARRIVAL_SPAN
         && globalCellZ >= 0 && globalCellZ < ARRIVAL_SPAN;
   }

   public static boolean inCoreFootprint(int globalCellX, int globalCellZ) {
      return globalCellX >= CORE_CELL_X && globalCellX < CORE_CELL_X + CORE_SPAN
         && globalCellZ >= CORE_CELL_Z && globalCellZ < CORE_CELL_Z + CORE_SPAN;
   }

   public static boolean inAudienceFootprint(int globalCellX, int globalCellZ) {
      return globalCellX >= AUDIENCE_CELL_X && globalCellX < AUDIENCE_CELL_X + AUDIENCE_SPAN
         && globalCellZ >= AUDIENCE_CELL_Z && globalCellZ < AUDIENCE_CELL_Z + AUDIENCE_SPAN;
   }

   public static Zone zoneAt(int globalCellX, int globalCellZ) {
      if (inArrivalFootprint(globalCellX, globalCellZ) || manhattanFromArrival(globalCellX, globalCellZ) <= 3) {
         return Zone.ARRIVAL;
      }
      if (inCoreFootprint(globalCellX, globalCellZ) || inAudienceFootprint(globalCellX, globalCellZ)) {
         return Zone.CORE;
      }
      int m = manhattanFromArrival(globalCellX, globalCellZ);
      if (m <= ZONE_BLEED_END) {
         return Zone.BLEED;
      }
      if (m <= ZONE_ORDE_END) {
         return Zone.ORDE;
      }
      if (m <= ZONE_APPROACH_END) {
         return Zone.APPROACH;
      }
      return Zone.CORE;
   }

   public static Footprint footprintForRoomName(String roomTypeName) {
      return switch (roomTypeName) {
         case "ARRIVAL", "AUDIENCE" -> Footprint.NAVE;
         case "CORE", "HOST_HALL", "FORGE", "ARCHIVE", "CHAPEL", "GATE", "ROTARY", "ATRIUM",
              "WELL_ATRIUM", "BRIDGE_NODE", "CLOISTER", "DEPTH_SHAFT" -> Footprint.ROOM;
         case "ECHO_LOOP", "MIRROR_LOOP", "LIMINAL_BEDROOM", "LIMINAL_KITCHEN", "LIMINAL_STUDY",
              "LIMINAL_PARLOR", "LIMINAL_NURSERY" -> Footprint.SUITE;
         default -> Footprint.CORRIDOR;
      };
   }
}
