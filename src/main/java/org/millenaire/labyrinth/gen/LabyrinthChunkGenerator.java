package org.millenaire.labyrinth.gen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
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
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.core.Direction;
import org.millenaire.labyrinth.LabyrinthKeys;

/**
 * Maze chunk generator for the Labyrinth dimension.
 * Carves navigable corridors into a deepslate/blackstone shell between FLOOR_Y and CEILING_Y.
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
      // Maze geometry is authored in fillFromNoise; no vanilla carvers.
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
            long cellKey = (((long)cell[0]) << 32) | (cell[1] & 0xffffffffL);
            stampedCells.add(cellKey);
         }
      }

      RandomSource roomRandom = RandomSource.create(seed ^ chunkPos.toLong());
      for (long cellKey : stampedCells) {
         int globalCellX = (int)(cellKey >> 32);
         int globalCellZ = (int)cellKey;
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

   /**
    * Picks the block for a world position: solid shell outside the Y band,
    * maze walls/floor/ceiling inside, with sparse lanterns/bars/chains in corridors.
    */
   private BlockState chooseBlock(int x, int y, int z, long seed, RandomSource random) {
      if (y < LabyrinthKeys.FLOOR_Y) {
         return DEEPSLATE;
      }
      if (y > LabyrinthKeys.CEILING_Y) {
         return AIR;
      }
      if (y == LabyrinthKeys.CEILING_Y) {
         return isCorridorOpenAt(x, z, seed) ? DEEPSLATE : wallMaterial(x, y, z);
      }

      boolean corridor = isCorridorOpenAt(x, z, seed);
      if (!corridor) {
         return wallMaterial(x, y, z);
      }

      if (y == LabyrinthKeys.FLOOR_Y) {
         return BLACKSTONE;
      }

      // Interior air band with sparse accents.
      if (y == LabyrinthKeys.CEILING_Y - 1) {
         int hash = Long.hashCode(Mth.getSeed(x, y, z) ^ seed);
         if ((hash & 63) == 0) {
            return HANGING_LANTERN;
         }
         if ((hash & 63) == 1) {
            return CHAIN;
         }
      }
      if (y == LabyrinthKeys.FLOOR_Y + 1) {
         int hash = Long.hashCode(Mth.getSeed(x, 0, z) ^ (seed * 31L));
         if ((hash & 127) == 0) {
            return BARS;
         }
         if ((hash & 127) == 1) {
            return CHAIN;
         }
      }
      return AIR;
   }

   private BlockState wallMaterial(int x, int y, int z) {
      int mix = Math.floorMod(x * 3 + z * 7 + y * 11, 5);
      return switch (mix) {
         case 0, 1 -> DEEPSLATE;
         case 2, 3 -> BLACKSTONE;
         default -> POLISHED;
      };
   }

   /**
    * True when this block column is inside a navigable corridor (not a closed wall).
    */
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
         // Corners stay solid so openings read as doorways.
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

      // Cell interior is always walkable.
      return true;
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
            + " open=" + maze.isWallOpen(localX, localZ, LabyrinthMaze.WALL_N | LabyrinthMaze.WALL_E | LabyrinthMaze.WALL_S | LabyrinthMaze.WALL_W)
      );
   }
}
