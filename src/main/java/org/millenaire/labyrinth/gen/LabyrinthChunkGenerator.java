package org.millenaire.labyrinth.gen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.labyrinth.LabyrinthKeys;

/**
 * Maze chunk generator for the Labyrinth dimension.
 * Primary band (Y64–73) plus optional vault band (Y48–56) beyond vault distance.
 * Corridors are inset for claustrophobia; spikes/chains denser under cold atmosphere.
 */
public class LabyrinthChunkGenerator extends ChunkGenerator {
   public static final MapCodec<LabyrinthChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
         )
         .apply(instance, instance.stable(LabyrinthChunkGenerator::new))
   );

   private static final BlockState AIR = Blocks.AIR.defaultBlockState();
   private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
   private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
   private static final BlockState POLISHED = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
   private static final BlockState BARS = Blocks.IRON_BARS.defaultBlockState();
   private static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
   private static final BlockState HANGING_LANTERN = Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
   private static final BlockState SPIKE = Blocks.POINTED_DRIPSTONE.defaultBlockState();
   private static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
   private static final BlockState BLUE_ICE = Blocks.BLUE_ICE.defaultBlockState();

   private volatile long worldSeed;

   public LabyrinthChunkGenerator(BiomeSource biomeSource) {
      super(biomeSource);
   }

   @Override
   protected MapCodec<? extends ChunkGenerator> codec() {
      return CODEC;
   }

   @Override
   public ChunkGeneratorStructureState createState(
      HolderLookup<StructureSet> structureSetLookup,
      RandomState randomState,
      long seed
   ) {
      this.worldSeed = seed;
      return super.createState(structureSetLookup, randomState, seed);
   }

   @Override
   public void applyCarvers(
      WorldGenRegion level,
      long seed,
      RandomState random,
      BiomeManager biomeManager,
      StructureManager structureManager,
      ChunkAccess chunk,
      GenerationStep.Carving step
   ) {
   }

   @Override
   public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
   }

   @Override
   public void spawnOriginalMobs(WorldGenRegion level) {
   }

   @Override
   public int getGenDepth() {
      return LabyrinthKeys.GEN_DEPTH;
   }

   @Override
   public CompletableFuture<ChunkAccess> fillFromNoise(
      Blender blender,
      RandomState randomState,
      StructureManager structureManager,
      ChunkAccess chunk
   ) {
      ChunkPos chunkPos = chunk.getPos();
      int minBlockX = chunkPos.getMinBlockX();
      int minBlockZ = chunkPos.getMinBlockZ();
      long seed = this.worldSeed;

      Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
      Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
      BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
      Set<Long> stampedCells = new HashSet<>();

      for (int localX = 0; localX < 16; localX++) {
         for (int localZ = 0; localZ < 16; localZ++) {
            int worldX = minBlockX + localX;
            int worldZ = minBlockZ + localZ;
            RandomSource columnRandom = RandomSource.create(seed ^ (worldX * 341873128712L + worldZ * 132897987541L));

            for (int y = LabyrinthKeys.MIN_Y; y < LabyrinthKeys.MIN_Y + LabyrinthKeys.GEN_DEPTH; y++) {
               BlockState state = chooseBlock(worldX, y, worldZ, seed, columnRandom);
               cursor.set(worldX, y, worldZ);
               chunk.setBlockState(cursor, state, false);
               oceanFloor.update(localX, y, localZ, state);
               worldSurface.update(localX, y, localZ, state);
            }

            int[] cell = LabyrinthMaze.globalCellFromBlock(worldX, worldZ);
            long cellKey = (((long) cell[0]) << 32) | (cell[1] & 0xffffffffL);
            stampedCells.add(cellKey);
         }
      }

      RandomSource roomRandom = RandomSource.create(seed ^ chunkPos.toLong());
      for (long cellKey : stampedCells) {
         int globalCellX = (int) (cellKey >> 32);
         int globalCellZ = (int) cellKey;
         int regionX = LabyrinthMaze.regionCoordFromGlobalCell(globalCellX);
         int regionZ = LabyrinthMaze.regionCoordFromGlobalCell(globalCellZ);
         LabyrinthMaze maze = LabyrinthMaze.forRegion(seed, regionX, regionZ);
         int localCellX = LabyrinthMaze.localCellFromGlobal(globalCellX);
         int localCellZ = LabyrinthMaze.localCellFromGlobal(globalCellZ);
         LabyrinthMaze.RoomType type = maze.getRoomType(localCellX, localCellZ);
         if (type == LabyrinthMaze.RoomType.NONE) {
            continue;
         }
         int cellMinX = globalCellX * LabyrinthKeys.CELL_SIZE;
         int cellMinZ = globalCellZ * LabyrinthKeys.CELL_SIZE;
         LabyrinthRooms.stampRoom(chunk, type, cellMinX, cellMinZ, seed, roomRandom);
      }

      return CompletableFuture.completedFuture(chunk);
   }

   private BlockState chooseBlock(int x, int y, int z, long seed, RandomSource random) {
      // Fill between bands with solid rock so levels feel sealed.
      if (y < LabyrinthKeys.VAULT_FLOOR_Y) {
         return DEEPSLATE;
      }
      if (y > LabyrinthKeys.MEZZ_CEILING_Y) {
         return AIR;
      }

      boolean inPrimary = y >= LabyrinthKeys.FLOOR_Y && y <= LabyrinthKeys.CEILING_Y;
      boolean inVault = y >= LabyrinthKeys.VAULT_FLOOR_Y && y <= LabyrinthKeys.VAULT_CEILING_Y;
      boolean betweenBands = y > LabyrinthKeys.VAULT_CEILING_Y && y < LabyrinthKeys.FLOOR_Y;

      if (betweenBands) {
         return shaftOrFill(x, y, z, seed);
      }
      if (y > LabyrinthKeys.CEILING_Y && y < LabyrinthKeys.MEZZ_FLOOR_Y) {
         return shaftOrFill(x, y, z, seed);
      }
      // Mezzanine air above primary is filled solid except where room stamps carve later.
      if (y >= LabyrinthKeys.MEZZ_FLOOR_Y && y <= LabyrinthKeys.MEZZ_CEILING_Y) {
         return wallMaterial(x, y, z);
      }

      if (inVault) {
         return chooseBandBlock(x, y, z, seed, random, LabyrinthKeys.VAULT_FLOOR_Y, LabyrinthKeys.VAULT_CEILING_Y, true);
      }
      if (inPrimary) {
         return chooseBandBlock(x, y, z, seed, random, LabyrinthKeys.FLOOR_Y, LabyrinthKeys.CEILING_Y, false);
      }
      return DEEPSLATE;
   }

   private BlockState chooseBandBlock(
      int x,
      int y,
      int z,
      long seed,
      RandomSource random,
      int floorY,
      int ceilingY,
      boolean vaultBand
   ) {
      if (vaultBand && !vaultEnabled()) {
         return DEEPSLATE;
      }
      if (vaultBand && !hasVaultAt(x, z)) {
         return DEEPSLATE;
      }

      if (y == ceilingY) {
         return isCorridorOpenAt(x, z, seed) ? (coldPalette() ? PACKED_ICE : DEEPSLATE) : wallMaterial(x, y, z);
      }

      boolean corridor = isCorridorOpenAt(x, z, seed);
      if (!corridor) {
         return wallMaterial(x, y, z);
      }

      if (y == floorY) {
         if (vaultBand) {
            return coldPalette() && ((x + z) & 7) == 0 ? BLUE_ICE : BLACKSTONE;
         }
         return BLACKSTONE;
      }

      // Narrow interior: margin columns stay as bars/spikes instead of open air.
      if (isMarginColumn(x, z) && !isDoorwayColumn(x, z, seed)) {
         if (y == floorY + 1) {
            int hash = Long.hashCode(Mth.getSeed(x, y, z) ^ seed);
            if ((hash & 3) == 0) {
               return SPIKE;
            }
            return BARS;
         }
         if (y < ceilingY) {
            return BARS;
         }
      }

      if (y == ceilingY - 1) {
         int hash = Long.hashCode(Mth.getSeed(x, y, z) ^ seed);
         int mask = coldPalette() ? 31 : 63;
         if ((hash & mask) == 0) {
            return HANGING_LANTERN;
         }
         if ((hash & mask) <= 2) {
            return CHAIN;
         }
         if (coldPalette() && (hash & mask) == 3) {
            return SPIKE;
         }
      }
      if (y == floorY + 1) {
         int hash = Long.hashCode(Mth.getSeed(x, 0, z) ^ (seed * 31L));
         int mask = coldPalette() ? 63 : 127;
         if ((hash & mask) == 0) {
            return BARS;
         }
         if ((hash & mask) == 1) {
            return CHAIN;
         }
         if ((hash & mask) == 2) {
            return SPIKE;
         }
      }
      return AIR;
   }

   /**
    * Between floor bands: solid fill unless this column is a vertical shaft cell
    * (hub rooms stamp actual shafts; generator pre-opens a seed-stable sparse set).
    */
   private BlockState shaftOrFill(int x, int y, int z, long seed) {
      int[] cell = LabyrinthMaze.globalCellFromBlock(x, z);
      int lx = Math.floorMod(x, LabyrinthKeys.CELL_SIZE);
      int lz = Math.floorMod(z, LabyrinthKeys.CELL_SIZE);
      int mid = LabyrinthKeys.CELL_SIZE / 2;
      boolean centerColumn = lx == mid && lz == mid;
      if (!centerColumn) {
         return DEEPSLATE;
      }
      int regionX = LabyrinthMaze.regionCoordFromGlobalCell(cell[0]);
      int regionZ = LabyrinthMaze.regionCoordFromGlobalCell(cell[1]);
      LabyrinthMaze maze = LabyrinthMaze.forRegion(seed, regionX, regionZ);
      LabyrinthMaze.RoomType type = maze.getRoomTypeGlobal(cell[0], cell[1]);
      if (type == LabyrinthMaze.RoomType.HOST_HALL
         || type == LabyrinthMaze.RoomType.GATE
         || type == LabyrinthMaze.RoomType.CHAPEL
         || type == LabyrinthMaze.RoomType.ROTARY
         || type == LabyrinthMaze.RoomType.CORE) {
         // Leave air so stamps / shafts connect bands.
         return AIR;
      }
      return DEEPSLATE;
   }

   private static boolean isMarginColumn(int blockX, int blockZ) {
      int lx = Math.floorMod(blockX, LabyrinthKeys.CELL_SIZE);
      int lz = Math.floorMod(blockZ, LabyrinthKeys.CELL_SIZE);
      int margin = LabyrinthKeys.CORRIDOR_MARGIN;
      int last = LabyrinthKeys.CELL_SIZE - 1;
      boolean nearWest = lx > 0 && lx <= margin;
      boolean nearEast = lx < last && lx >= last - margin;
      boolean nearNorth = lz > 0 && lz <= margin;
      boolean nearSouth = lz < last && lz >= last - margin;
      return nearWest || nearEast || nearNorth || nearSouth;
   }

   private boolean isDoorwayColumn(int blockX, int blockZ, long seed) {
      int lx = Math.floorMod(blockX, LabyrinthKeys.CELL_SIZE);
      int lz = Math.floorMod(blockZ, LabyrinthKeys.CELL_SIZE);
      int last = LabyrinthKeys.CELL_SIZE - 1;
      int mid = LabyrinthKeys.CELL_SIZE / 2;
      // Doorways sit on cell edges at mid — keep them fully open.
      if ((lx == 0 || lx == last) && Math.abs(lz - mid) <= 1) {
         return isCorridorOpenAt(blockX, blockZ, seed);
      }
      if ((lz == 0 || lz == last) && Math.abs(lx - mid) <= 1) {
         return isCorridorOpenAt(blockX, blockZ, seed);
      }
      return false;
   }

   private BlockState wallMaterial(int x, int y, int z) {
      int mix = Math.floorMod(x * 3 + z * 7 + y * 11, 7);
      if (coldPalette()) {
         return switch (mix) {
            case 0, 1 -> DEEPSLATE;
            case 2, 3 -> BLACKSTONE;
            case 4 -> POLISHED;
            case 5 -> PACKED_ICE;
            default -> DEEPSLATE;
         };
      }
      return switch (mix) {
         case 0, 1 -> DEEPSLATE;
         case 2, 3 -> BLACKSTONE;
         default -> POLISHED;
      };
   }

   boolean isCorridorOpenAt(int blockX, int blockZ, long seed) {
      int[] global = LabyrinthMaze.globalCellFromBlock(blockX, blockZ);
      int globalCellX = global[0];
      int globalCellZ = global[1];
      int regionX = LabyrinthMaze.regionCoordFromGlobalCell(globalCellX);
      int regionZ = LabyrinthMaze.regionCoordFromGlobalCell(globalCellZ);
      LabyrinthMaze maze = LabyrinthMaze.forRegion(seed, regionX, regionZ);
      int localCellX = LabyrinthMaze.localCellFromGlobal(globalCellX);
      int localCellZ = LabyrinthMaze.localCellFromGlobal(globalCellZ);

      int lx = Math.floorMod(blockX, LabyrinthKeys.CELL_SIZE);
      int lz = Math.floorMod(blockZ, LabyrinthKeys.CELL_SIZE);
      int last = LabyrinthKeys.CELL_SIZE - 1;

      boolean onWest = lx == 0;
      boolean onEast = lx == last;
      boolean onNorth = lz == 0;
      boolean onSouth = lz == last;

      if (onWest && onNorth || onWest && onSouth || onEast && onNorth || onEast && onSouth) {
         return false;
      }

      if (onWest) {
         return maze.isWallOpen(localCellX, localCellZ, LabyrinthMaze.WALL_W);
      }
      if (onEast) {
         return maze.isWallOpen(localCellX, localCellZ, LabyrinthMaze.WALL_E);
      }
      if (onNorth) {
         return maze.isWallOpen(localCellX, localCellZ, LabyrinthMaze.WALL_N);
      }
      if (onSouth) {
         return maze.isWallOpen(localCellX, localCellZ, LabyrinthMaze.WALL_S);
      }

      return true;
   }

   private static boolean hasVaultAt(int blockX, int blockZ) {
      int[] cell = LabyrinthMaze.globalCellFromBlock(blockX, blockZ);
      return LabyrinthKeys.manhattanFromArrival(cell[0], cell[1]) >= LabyrinthKeys.VAULT_START_DISTANCE;
   }

   private static boolean vaultEnabled() {
      try {
         return MillenaireServerConfig.SERVER.labyrinthVaultEnabled.getAsBoolean();
      } catch (IllegalStateException ignored) {
         return true;
      }
   }

   private static boolean coldPalette() {
      try {
         return MillenaireServerConfig.SERVER.labyrinthAtmosphere.get()
            != org.millenaire.labyrinth.LabyrinthAtmosphere.CLASSIC;
      } catch (IllegalStateException ignored) {
         return true;
      }
   }

   @Override
   public int getSeaLevel() {
      return 0;
   }

   @Override
   public int getMinY() {
      return LabyrinthKeys.MIN_Y;
   }

   @Override
   public int getSpawnHeight(LevelHeightAccessor level) {
      return LabyrinthKeys.FLOOR_Y + 1;
   }

   @Override
   public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
      return LabyrinthKeys.CEILING_Y + 1;
   }

   @Override
   public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
      int minY = this.getMinY();
      int depth = this.getGenDepth();
      BlockState[] column = new BlockState[depth];
      long seed = this.worldSeed;
      RandomSource columnRandom = RandomSource.create(seed ^ (x * 341873128712L + z * 132897987541L));
      for (int i = 0; i < depth; i++) {
         column[i] = chooseBlock(x, minY + i, z, seed, columnRandom);
      }
      return new NoiseColumn(minY, column);
   }

   @Override
   public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
      int[] cell = LabyrinthMaze.globalCellFromBlock(pos.getX(), pos.getZ());
      int regionX = LabyrinthMaze.regionCoordFromGlobalCell(cell[0]);
      int regionZ = LabyrinthMaze.regionCoordFromGlobalCell(cell[1]);
      LabyrinthMaze maze = LabyrinthMaze.forRegion(this.worldSeed, regionX, regionZ);
      int localX = LabyrinthMaze.localCellFromGlobal(cell[0]);
      int localZ = LabyrinthMaze.localCellFromGlobal(cell[1]);
      info.add(
         "Labyrinth cell " + cell[0] + "," + cell[1]
            + " room=" + maze.getRoomType(localX, localZ)
            + " band=" + LabyrinthKeys.bandForY(pos.getY())
            + " open=" + maze.isWallOpen(localX, localZ, LabyrinthMaze.WALL_N | LabyrinthMaze.WALL_E | LabyrinthMaze.WALL_S | LabyrinthMaze.WALL_W)
      );
   }
}
