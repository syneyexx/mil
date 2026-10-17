package org.millenaire.labyrinth.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.millenaire.labyrinth.LabyrinthKeys;

/**
 * Deterministic per-region maze built with recursive backtracking plus light cycles.
 * Wall openness is stored as N/E/S/W bitflags on each cell.
 */
public final class LabyrinthMaze {
   public static final int WALL_N = 1;
   public static final int WALL_E = 2;
   public static final int WALL_S = 4;
   public static final int WALL_W = 8;

   public enum RoomType {
      NONE,
      ARRIVAL,
      HOST_HALL,
      FORGE,
      ARCHIVE,
      CHAPEL,
      GATE,
      CORE,
      NICHE,
      /** Spike-pit walkway chamber — Configuratie WIEL rotates bridges. */
      ROTARY,
      /** Looping chamber — walk backward to escape. */
      ECHO_LOOP,
      /** Reality-bleed furnished rooms (mind-fuck domestic interiors). */
      LIMINAL_BEDROOM,
      LIMINAL_KITCHEN,
      LIMINAL_STUDY,
      LIMINAL_PARLOR,
      LIMINAL_NURSERY,
      /**
       * Audience with De Ruit — geometric Orde presence north of Core.
       * Original lore; not Leviathan / Hellraiser IP.
       */
      AUDIENCE
   }

   public static boolean isLiminal(RoomType type) {
      return type == RoomType.ECHO_LOOP
         || type == RoomType.LIMINAL_BEDROOM
         || type == RoomType.LIMINAL_KITCHEN
         || type == RoomType.LIMINAL_STUDY
         || type == RoomType.LIMINAL_PARLOR
         || type == RoomType.LIMINAL_NURSERY;
   }

   private static final ConcurrentHashMap<Long, LabyrinthMaze> CACHE = new ConcurrentHashMap<>();

   private final int regionX;
   private final int regionZ;
   private final int regionSize;
   /** Bitflags of OPEN walls per local cell. */
   private final int[][] openWalls;
   private final RoomType[][] roomTypes;

   private LabyrinthMaze(long worldSeed, int regionX, int regionZ, int regionSize) {
      this.regionX = regionX;
      this.regionZ = regionZ;
      this.regionSize = regionSize;
      this.openWalls = new int[regionSize][regionSize];
      this.roomTypes = new RoomType[regionSize][regionSize];
      for (int z = 0; z < regionSize; z++) {
         Arrays.fill(this.roomTypes[z], RoomType.NONE);
      }

      long seed = mixSeed(worldSeed, regionX, regionZ);
      Random random = new Random(seed);
      carveRecursiveBacktracker(random);
      addCycles(random);
      assignSpecialRooms(random);
   }

   public static LabyrinthMaze forRegion(long worldSeed, int regionX, int regionZ) {
      return forRegion(worldSeed, regionX, regionZ, LabyrinthKeys.REGION_SIZE);
   }

   public static LabyrinthMaze forRegion(long worldSeed, int regionX, int regionZ, int regionSize) {
      long key = cacheKey(worldSeed, regionX, regionZ, regionSize);
      return CACHE.computeIfAbsent(key, ignored -> new LabyrinthMaze(worldSeed, regionX, regionZ, regionSize));
   }

   public static void clearCache() {
      CACHE.clear();
   }

   public int regionX() {
      return this.regionX;
   }

   public int regionZ() {
      return this.regionZ;
   }

   public int regionSize() {
      return this.regionSize;
   }

   public boolean isWallOpen(int localCellX, int localCellZ, int wallFlag) {
      if (!inBounds(localCellX, localCellZ)) {
         return false;
      }
      return (this.openWalls[localCellZ][localCellX] & wallFlag) != 0;
   }

   public RoomType getRoomType(int localCellX, int localCellZ) {
      if (!inBounds(localCellX, localCellZ)) {
         return RoomType.NONE;
      }
      return this.roomTypes[localCellZ][localCellX];
   }

   public RoomType getRoomTypeGlobal(int globalCellX, int globalCellZ) {
      int localX = globalCellX - this.regionX * this.regionSize;
      int localZ = globalCellZ - this.regionZ * this.regionSize;
      return getRoomType(localX, localZ);
   }

   /**
    * Converts a world block position to global maze cell coordinates.
    */
   public static int[] globalCellFromBlock(int blockX, int blockZ) {
      return new int[] {
         Math.floorDiv(blockX, LabyrinthKeys.CELL_SIZE),
         Math.floorDiv(blockZ, LabyrinthKeys.CELL_SIZE)
      };
   }

   public static int regionCoordFromGlobalCell(int globalCell) {
      return Math.floorDiv(globalCell, LabyrinthKeys.REGION_SIZE);
   }

   public static int localCellFromGlobal(int globalCell) {
      return Math.floorMod(globalCell, LabyrinthKeys.REGION_SIZE);
   }

   private boolean inBounds(int x, int z) {
      return x >= 0 && z >= 0 && x < this.regionSize && z < this.regionSize;
   }

   private void carveRecursiveBacktracker(Random random) {
      boolean[][] visited = new boolean[this.regionSize][this.regionSize];
      int startX = random.nextInt(this.regionSize);
      int startZ = random.nextInt(this.regionSize);
      carveFrom(startX, startZ, visited, random);
   }

   private void carveFrom(int x, int z, boolean[][] visited, Random random) {
      visited[z][x] = true;
      List<int[]> dirs = new ArrayList<>(4);
      dirs.add(new int[] {0, -1, WALL_N, WALL_S});
      dirs.add(new int[] {1, 0, WALL_E, WALL_W});
      dirs.add(new int[] {0, 1, WALL_S, WALL_N});
      dirs.add(new int[] {-1, 0, WALL_W, WALL_E});
      Collections.shuffle(dirs, random);

      for (int[] dir : dirs) {
         int nx = x + dir[0];
         int nz = z + dir[1];
         if (!inBounds(nx, nz) || visited[nz][nx]) {
            continue;
         }
         this.openWalls[z][x] |= dir[2];
         this.openWalls[nz][nx] |= dir[3];
         carveFrom(nx, nz, visited, random);
      }
   }

   /**
    * Opens additional walls so the labyrinth has loops (not a pure spanning tree).
    */
   private void addCycles(Random random) {
      int attempts = Math.max(8, (this.regionSize * this.regionSize) / 12);
      for (int i = 0; i < attempts; i++) {
         int x = random.nextInt(this.regionSize);
         int z = random.nextInt(this.regionSize);
         int choice = random.nextInt(4);
         int nx;
         int nz;
         int fromFlag;
         int toFlag;
         switch (choice) {
            case 0 -> {
               nx = x;
               nz = z - 1;
               fromFlag = WALL_N;
               toFlag = WALL_S;
            }
            case 1 -> {
               nx = x + 1;
               nz = z;
               fromFlag = WALL_E;
               toFlag = WALL_W;
            }
            case 2 -> {
               nx = x;
               nz = z + 1;
               fromFlag = WALL_S;
               toFlag = WALL_N;
            }
            default -> {
               nx = x - 1;
               nz = z;
               fromFlag = WALL_W;
               toFlag = WALL_E;
            }
         }
         if (!inBounds(nx, nz)) {
            continue;
         }
         if ((this.openWalls[z][x] & fromFlag) != 0) {
            continue;
         }
         this.openWalls[z][x] |= fromFlag;
         this.openWalls[nz][nx] |= toFlag;
      }
   }

   private void assignSpecialRooms(Random random) {
      for (int localZ = 0; localZ < this.regionSize; localZ++) {
         for (int localX = 0; localX < this.regionSize; localX++) {
            int globalX = this.regionX * this.regionSize + localX;
            int globalZ = this.regionZ * this.regionSize + localZ;

            if (globalX == 0 && globalZ == 0) {
               this.roomTypes[localZ][localX] = RoomType.ARRIVAL;
               continue;
            }
            if (globalX == LabyrinthKeys.CORE_CELL_X && globalZ == LabyrinthKeys.CORE_CELL_Z) {
               this.roomTypes[localZ][localX] = RoomType.CORE;
               // Doorway north into the Audience / De Ruit nave.
               openMutual(localX, localZ, 0, -1, WALL_N, WALL_S);
               continue;
            }
            if (globalX == LabyrinthKeys.AUDIENCE_CELL_X && globalZ == LabyrinthKeys.AUDIENCE_CELL_Z) {
               this.roomTypes[localZ][localX] = RoomType.AUDIENCE;
               // Guarantee a doorway into the Core (south wall open).
               openMutual(localX, localZ, 0, 1, WALL_S, WALL_N);
               continue;
            }

            int spacing = LabyrinthKeys.HUB_SPACING;
            if (globalX % spacing == 0 && globalZ % spacing == 0) {
               int hubIndex = Math.floorMod(
                  (globalX / spacing) + (globalZ / spacing) * 3 + this.regionX + this.regionZ * 5,
                  7
               );
               this.roomTypes[localZ][localX] = switch (hubIndex) {
                  case 0 -> RoomType.HOST_HALL;
                  case 1 -> RoomType.FORGE;
                  case 2 -> RoomType.ARCHIVE;
                  case 3 -> RoomType.CHAPEL;
                  case 4 -> RoomType.GATE;
                  case 5 -> RoomType.ROTARY;
                  default -> RoomType.ECHO_LOOP;
               };
               continue;
            }

            // Place a rotary on the approach path toward Core (deterministic waypoints).
            if (globalX > 0 && globalZ > 0
               && globalX % (spacing / 2) == 0
               && globalZ % spacing == spacing / 2
               && LabyrinthKeys.manhattanFromArrival(globalX, globalZ) >= 8) {
               this.roomTypes[localZ][localX] = RoomType.ROTARY;
               continue;
            }

            // Reality-bleed furnished rooms — scattered so the maze feels domestic, then wrong.
            if (LabyrinthKeys.manhattanFromArrival(globalX, globalZ) >= 5
               && this.roomTypes[localZ][localX] == RoomType.NONE) {
               int bleed = Math.floorMod(globalX * 17 + globalZ * 29 + this.regionX * 7, 11);
               if (bleed == 0 || bleed == 3 || bleed == 7) {
                  this.roomTypes[localZ][localX] = liminalVariant(globalX, globalZ, random);
                  continue;
               }
            }

            if (isDeadEnd(localX, localZ)) {
               if (random.nextFloat() < 0.45f) {
                  this.roomTypes[localZ][localX] = liminalVariant(globalX, globalZ, random);
               } else if (random.nextFloat() < 0.35f) {
                  this.roomTypes[localZ][localX] = RoomType.NICHE;
               }
            }
         }
      }
   }

   private static RoomType liminalVariant(int globalX, int globalZ, Random random) {
      int pick = Math.floorMod(globalX * 13 + globalZ * 31 + random.nextInt(3), 5);
      return switch (pick) {
         case 0 -> RoomType.LIMINAL_BEDROOM;
         case 1 -> RoomType.LIMINAL_KITCHEN;
         case 2 -> RoomType.LIMINAL_STUDY;
         case 3 -> RoomType.LIMINAL_PARLOR;
         default -> RoomType.LIMINAL_NURSERY;
      };
   }

   private boolean isDeadEnd(int x, int z) {
      int flags = this.openWalls[z][x];
      int count = Integer.bitCount(flags);
      return count == 1;
   }

   private void openMutual(int x, int z, int dx, int dz, int fromFlag, int toFlag) {
      int nx = x + dx;
      int nz = z + dz;
      if (!inBounds(x, z)) {
         return;
      }
      this.openWalls[z][x] |= fromFlag;
      if (inBounds(nx, nz)) {
         this.openWalls[nz][nx] |= toFlag;
      }
   }

   private static long mixSeed(long worldSeed, int regionX, int regionZ) {
      long mixed = worldSeed ^ 0x5DEECE66DL;
      mixed = mixed * 6364136223846793005L + 1442695040888963407L;
      mixed ^= (long)regionX * 341873128712L;
      mixed ^= (long)regionZ * 132897987541L;
      return mixed;
   }

   private static long cacheKey(long worldSeed, int regionX, int regionZ, int regionSize) {
      long key = worldSeed;
      key = key * 31L + regionX;
      key = key * 31L + regionZ;
      key = key * 31L + regionSize;
      return key;
   }
}
