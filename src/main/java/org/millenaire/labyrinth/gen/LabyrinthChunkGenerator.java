package org.millenaire.labyrinth.gen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
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
 * Labyrinth chunk generator — layout v3 <b>wellgrid</b>.
 *
 * <p>Each maze cell is a deep open well framed by thick Orde walls with arcade openings,
 * walkable wall crowns, and gallery rings. Corridor cells stamp crazy hallway archetypes;
 * special chambers stamp from SW anchors. Above the crown: open void + De Ruit sky beam.
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
      Set<Long> stampedAnchors = new HashSet<>();
      Set<Long> stampedHallCells = new HashSet<>();
      for (long cellKey : stampedCells) {
         int globalCellX = (int) (cellKey >> 32);
         int globalCellZ = (int) cellKey;
         int regionX = LabyrinthMaze.regionCoordFromGlobalCell(globalCellX);
         int regionZ = LabyrinthMaze.regionCoordFromGlobalCell(globalCellZ);
         LabyrinthMaze maze = LabyrinthMaze.forRegion(seed, regionX, regionZ);
         int localCellX = LabyrinthMaze.localCellFromGlobal(globalCellX);
         int localCellZ = LabyrinthMaze.localCellFromGlobal(globalCellZ);
         LabyrinthMaze.RoomType raw = maze.getRoomType(localCellX, localCellZ);

         // Layout v3: stamp crazy hallway archetypes into corridor wells.
         if (raw == LabyrinthMaze.RoomType.NONE && stampedHallCells.add(cellKey)) {
            LabyrinthHallways.stampCorridorCell(chunk, globalCellX, globalCellZ, seed, roomRandom);
            continue;
         }
         if (raw == LabyrinthMaze.RoomType.NONE || raw == LabyrinthMaze.RoomType.CHAMBER_PAD) {
            continue;
         }
         int anchorLocalX = maze.getAnchorLocalX(localCellX, localCellZ);
         int anchorLocalZ = maze.getAnchorLocalZ(localCellX, localCellZ);
         if (anchorLocalX < 0 || anchorLocalZ < 0) {
            continue;
         }
         LabyrinthMaze.RoomType type = maze.getRoomType(anchorLocalX, anchorLocalZ);
         if (!LabyrinthMaze.isStampAnchor(type)) {
            continue;
         }
         int globalAnchorX = regionX * maze.regionSize() + anchorLocalX;
         int globalAnchorZ = regionZ * maze.regionSize() + anchorLocalZ;
         long anchorKey = (((long) globalAnchorX) << 32) | (globalAnchorZ & 0xffffffffL);
         if (!stampedAnchors.add(anchorKey)) {
            continue;
         }
         int span = maze.getFootprintSpan(anchorLocalX, anchorLocalZ);
         int cellMinX = globalAnchorX * LabyrinthKeys.CELL_SIZE;
         int cellMinZ = globalAnchorZ * LabyrinthKeys.CELL_SIZE;
         LabyrinthRooms.stampRoom(chunk, type, cellMinX, cellMinZ, span, seed, roomRandom);
      }

      return CompletableFuture.completedFuture(chunk);
   }

   private BlockState chooseBlock(int x, int y, int z, long seed, RandomSource random) {
      if (y < LabyrinthKeys.VAULT_FLOOR_Y) {
         return y < LabyrinthKeys.VAULT_FLOOR_Y - 4 ? DEEPSLATE : LabyrinthMaterials.foundation();
      }

      // Open horizon above wall crowns — with De Ruit sky beam near Audience/Core.
      if (y >= LabyrinthKeys.SKY_VOID_Y) {
         return skyOrBeam(x, y, z);
      }

      int[] cell = LabyrinthMaze.globalCellFromBlock(x, z);
      int regionX = LabyrinthMaze.regionCoordFromGlobalCell(cell[0]);
      int regionZ = LabyrinthMaze.regionCoordFromGlobalCell(cell[1]);
      LabyrinthMaze maze = LabyrinthMaze.forRegion(seed, regionX, regionZ);
      int localCellX = LabyrinthMaze.localCellFromGlobal(cell[0]);
      int localCellZ = LabyrinthMaze.localCellFromGlobal(cell[1]);
      boolean chamber = maze.isChamberInterior(localCellX, localCellZ);

      int lx = Math.floorMod(x, LabyrinthKeys.CELL_SIZE);
      int lz = Math.floorMod(z, LabyrinthKeys.CELL_SIZE);
      int last = LabyrinthKeys.CELL_SIZE - 1;
      boolean onPerimeter = lx == 0 || lz == 0 || lx == last || lz == last;
      boolean corner = (lx == 0 || lx == last) && (lz == 0 || lz == last);

      if (onPerimeter) {
         return chooseWallColumn(x, y, z, seed, maze, localCellX, localCellZ, lx, lz, last, corner, chamber);
      }

      // Interior well — open vertically; stamps add floors/bridges/props.
      return chooseWellInterior(x, y, z, seed, chamber, lx, lz);
   }

   private BlockState chooseWallColumn(
      int x,
      int y,
      int z,
      long seed,
      LabyrinthMaze maze,
      int localCellX,
      int localCellZ,
      int lx,
      int lz,
      int last,
      boolean corner,
      boolean chamber
   ) {
      // Shared chamber walls are fully open (multi-cell hollow).
      if (chamber && isInternalChamberWall(maze, localCellX, localCellZ, lx, lz, last)) {
         return AIR;
      }

      boolean doorway = isDoorwaySlot(maze, localCellX, localCellZ, lx, lz, last);
      if (doorway && !corner) {
         // Primary arch
         if (y > LabyrinthKeys.FLOOR_Y && y <= LabyrinthKeys.FLOOR_Y + LabyrinthKeys.ARCH_CLEARANCE) {
            return AIR;
         }
         if (y == LabyrinthKeys.FLOOR_Y + LabyrinthKeys.ARCH_CLEARANCE + 1) {
            return LabyrinthMaterials.arch();
         }
         // Mezzanine arch
         if (y > LabyrinthKeys.MEZZ_FLOOR_Y && y <= LabyrinthKeys.MEZZ_FLOOR_Y + 2) {
            return AIR;
         }
         if (y == LabyrinthKeys.MEZZ_FLOOR_Y + 3) {
            return LabyrinthMaterials.arch();
         }
         // Vault pass-through when vault is active
         if (vaultEnabled() && hasVaultAt(x, z)
            && y > LabyrinthKeys.VAULT_FLOOR_Y
            && y <= LabyrinthKeys.VAULT_FLOOR_Y + 2) {
            return AIR;
         }
      }

      if (y == LabyrinthKeys.WALL_CROWN_Y) {
         return LabyrinthMaterials.parapet();
      }
      if (y == LabyrinthKeys.FLOOR_Y || y == LabyrinthKeys.MEZZ_FLOOR_Y || y == LabyrinthKeys.VAULT_FLOOR_Y) {
         return LabyrinthMaterials.riveted();
      }
      // Arcade motif rows on solid walls (cloister look from photos)
      if (!doorway && !corner && isMidWallFace(lx, lz, last)
         && (y == LabyrinthKeys.FLOOR_Y + 2 || y == LabyrinthKeys.MEZZ_FLOOR_Y + 1)) {
         return LabyrinthMaterials.arch();
      }
      return LabyrinthMaterials.wallMix(x, y, z, coldPalette());
   }

   private BlockState chooseWellInterior(int x, int y, int z, long seed, boolean chamber, int lx, int lz) {
      // Chambers stay mostly hollow so room stamps own the interior; leave skeleton floors.
      if (chamber) {
         if (y == LabyrinthKeys.VAULT_FLOOR_Y && vaultEnabled() && hasVaultAt(x, z)) {
            return LabyrinthMaterials.ordeStone();
         }
         if (y == LabyrinthKeys.FLOOR_Y) {
            return LabyrinthMaterials.ordeStone();
         }
         if (y == LabyrinthKeys.WALL_CROWN_Y) {
            return AIR; // open nave tops; stamps may roof
         }
         return AIR;
      }

      // Corridor well skeleton
      if (y == LabyrinthKeys.VAULT_FLOOR_Y) {
         if (!vaultEnabled() || !hasVaultAt(x, z)) {
            // Near Arrival: shallow well floor at primary instead
            return LabyrinthMaterials.ordeStone();
         }
         return LabyrinthMaterials.grate();
      }

      // Default open volume — hallway stamps place walks/bridges.
      if (y == LabyrinthKeys.WALL_CROWN_Y) {
         return AIR;
      }

      // Sparse default rim so cells are never soft-locked before stamp (edge walk pads)
      boolean innerRing = lx == 1 || lz == 1 || lx == LabyrinthKeys.CELL_SIZE - 2 || lz == LabyrinthKeys.CELL_SIZE - 2;
      int mid = LabyrinthKeys.CELL_SIZE / 2;
      boolean cross = Math.abs(lx - mid) <= LabyrinthKeys.CORRIDOR_WALK_HALF
         || Math.abs(lz - mid) <= LabyrinthKeys.CORRIDOR_WALK_HALF;

      if (y == LabyrinthKeys.FLOOR_Y && (innerRing || cross)) {
         return LabyrinthMaterials.ordePanel();
      }
      if (y == LabyrinthKeys.MEZZ_FLOOR_Y && innerRing) {
         return LabyrinthMaterials.ordePanel();
      }

      // Between vault and sky: air (true depth)
      return AIR;
   }

   private BlockState skyOrBeam(int x, int y, int z) {
      int ax = LabyrinthKeys.audienceCenterBlockX();
      int az = LabyrinthKeys.audienceCenterBlockZ();
      // Vertical void beam over De Ruit — visible across the open horizon.
      if (x == ax && z == az) {
         return LabyrinthMaterials.voidBeam();
      }
      // Soft glow columns at Core corners for distant reading
      int cx = LabyrinthKeys.coreCenterBlockX();
      int cz = LabyrinthKeys.coreCenterBlockZ();
      if ((x == cx && z == cz) || (Math.abs(x - ax) + Math.abs(z - az) == 0)) {
         return LabyrinthMaterials.voidBeam();
      }
      // Sparse twin beams offset for silhouette
      if (y < LabyrinthKeys.SKY_VOID_Y + 20
         && ((x == ax + 3 && z == az) || (x == ax - 3 && z == az) || (x == ax && z == az + 3) || (x == ax && z == az - 3))
         && ((y - LabyrinthKeys.SKY_VOID_Y) & 1) == 0) {
         return LabyrinthMaterials.voidBeam();
      }
      return AIR;
   }

   private static boolean isInternalChamberWall(
      LabyrinthMaze maze,
      int localCellX,
      int localCellZ,
      int lx,
      int lz,
      int last
   ) {
      if (lx == 0 && maze.sameChamber(localCellX, localCellZ, localCellX - 1, localCellZ)) {
         return true;
      }
      if (lx == last && maze.sameChamber(localCellX, localCellZ, localCellX + 1, localCellZ)) {
         return true;
      }
      if (lz == 0 && maze.sameChamber(localCellX, localCellZ, localCellX, localCellZ - 1)) {
         return true;
      }
      if (lz == last && maze.sameChamber(localCellX, localCellZ, localCellX, localCellZ + 1)) {
         return true;
      }
      return false;
   }

   private static boolean isDoorwaySlot(
      LabyrinthMaze maze,
      int localCellX,
      int localCellZ,
      int lx,
      int lz,
      int last
   ) {
      int mid = LabyrinthKeys.CELL_SIZE / 2;
      if ((lx == 0 || lx == last) && Math.abs(lz - mid) <= 1) {
         int flag = lx == 0 ? LabyrinthMaze.WALL_W : LabyrinthMaze.WALL_E;
         return maze.isWallOpen(localCellX, localCellZ, flag)
            || (lx == 0 && maze.sameChamber(localCellX, localCellZ, localCellX - 1, localCellZ))
            || (lx == last && maze.sameChamber(localCellX, localCellZ, localCellX + 1, localCellZ));
      }
      if ((lz == 0 || lz == last) && Math.abs(lx - mid) <= 1) {
         int flag = lz == 0 ? LabyrinthMaze.WALL_N : LabyrinthMaze.WALL_S;
         return maze.isWallOpen(localCellX, localCellZ, flag)
            || (lz == 0 && maze.sameChamber(localCellX, localCellZ, localCellX, localCellZ - 1))
            || (lz == last && maze.sameChamber(localCellX, localCellZ, localCellX, localCellZ + 1));
      }
      return false;
   }

   private static boolean isMidWallFace(int lx, int lz, int last) {
      int mid = LabyrinthKeys.CELL_SIZE / 2;
      if ((lx == 0 || lx == last) && Math.abs(lz - mid) <= 2 && Math.abs(lz - mid) >= 2) {
         return true;
      }
      if ((lz == 0 || lz == last) && Math.abs(lx - mid) <= 2 && Math.abs(lx - mid) >= 2) {
         return true;
      }
      // Broader arcade row along each wall face (not corners)
      if ((lx == 0 || lx == last) && lz > 0 && lz < last && (lz & 1) == 0) {
         return true;
      }
      if ((lz == 0 || lz == last) && lx > 0 && lx < last && (lx & 1) == 0) {
         return true;
      }
      return false;
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
      return LabyrinthKeys.WALL_CROWN_Y + 1;
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
      LabyrinthMaze.RoomType room = maze.getResolvedRoomType(localX, localZ);
      String hall = room == LabyrinthMaze.RoomType.NONE
         ? LabyrinthHallways.archetypeAt(cell[0], cell[1], this.worldSeed).name()
         : "-";
      info.add(
         "Labyrinth v" + LabyrinthKeys.LAYOUT_VERSION
            + " cell " + cell[0] + "," + cell[1]
            + " room=" + room
            + " hall=" + hall
            + " zone=" + LabyrinthKeys.zoneAt(cell[0], cell[1])
            + " span=" + maze.getFootprintSpan(localX, localZ)
            + " band=" + LabyrinthKeys.bandForY(pos.getY())
      );
   }
}
