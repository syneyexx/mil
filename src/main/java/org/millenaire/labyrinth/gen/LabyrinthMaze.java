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
 *
 * <p>Layout v2: special chambers claim multi-cell footprints. Non-anchor cells are
 * {@link RoomType#CHAMBER_PAD}; stamps run only from the SW anchor.
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
      AUDIENCE,
      /** Vertical atrium / overlook connecting primary ↔ mezzanine. */
      ATRIUM,
      /** Occupied by a multi-cell chamber; do not stamp — resolve via anchor. */
      CHAMBER_PAD
   }

   public static boolean isLiminal(RoomType type) {
      return type == RoomType.ECHO_LOOP
         || type == RoomType.LIMINAL_BEDROOM
         || type == RoomType.LIMINAL_KITCHEN
         || type == RoomType.LIMINAL_STUDY
         || type == RoomType.LIMINAL_PARLOR
         || type == RoomType.LIMINAL_NURSERY;
   }

   public static boolean isStampAnchor(RoomType type) {
      return type != RoomType.NONE && type != RoomType.CHAMBER_PAD;
   }

   private static final ConcurrentHashMap<Long, LabyrinthMaze> CACHE = new ConcurrentHashMap<>();

   private final int regionX;
   private final int regionZ;
   private final int regionSize;
   /** Bitflags of OPEN walls per local cell. */
   private final int[][] openWalls;
   private final RoomType[][] roomTypes;
   /** Local SW anchor for each cell (−1 if corridor / unclaimed). */
   private final int[][] anchorLocalX;
   private final int[][] anchorLocalZ;
   /** Footprint span in cells for the chamber owning this cell (1 if none). */
   private final int[][] footprintSpan;

   private LabyrinthMaze(long worldSeed, int regionX, int regionZ, int regionSize) {
      this.regionX = regionX;
      this.regionZ = regionZ;
      this.regionSize = regionSize;
      this.openWalls = new int[regionSize][regionSize];
      this.roomTypes = new RoomType[regionSize][regionSize];
      this.anchorLocalX = new int[regionSize][regionSize];
      this.anchorLocalZ = new int[regionSize][regionSize];
      this.footprintSpan = new int[regionSize][regionSize];
      for (int z = 0; z < regionSize; z++) {
         Arrays.fill(this.roomTypes[z], RoomType.NONE);
         Arrays.fill(this.anchorLocalX[z], -1);
         Arrays.fill(this.anchorLocalZ[z], -1);
         Arrays.fill(this.footprintSpan[z], 1);
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

   /** Resolved stamp type (pads → their chamber type). */
   public RoomType getResolvedRoomType(int localCellX, int localCellZ) {
      if (!inBounds(localCellX, localCellZ)) {
         return RoomType.NONE;
      }
      RoomType type = this.roomTypes[localCellZ][localCellX];
      if (type != RoomType.CHAMBER_PAD) {
         return type;
      }
      int ax = this.anchorLocalX[localCellZ][localCellX];
      int az = this.anchorLocalZ[localCellZ][localCellX];
      if (ax < 0 || az < 0 || !inBounds(ax, az)) {
         return RoomType.NONE;
      }
      return this.roomTypes[az][ax];
   }

   public int getFootprintSpan(int localCellX, int localCellZ) {
      if (!inBounds(localCellX, localCellZ)) {
         return 1;
      }
      return Math.max(1, this.footprintSpan[localCellZ][localCellX]);
   }

   /** Local SW anchor X for a chamber cell, or −1 if unclaimed. */
   public int getAnchorLocalX(int localCellX, int localCellZ) {
      if (!inBounds(localCellX, localCellZ)) {
         return -1;
      }
      return this.anchorLocalX[localCellZ][localCellX];
   }

   /** Local SW anchor Z for a chamber cell, or −1 if unclaimed. */
   public int getAnchorLocalZ(int localCellX, int localCellZ) {
      if (!inBounds(localCellX, localCellZ)) {
         return -1;
      }
      return this.anchorLocalZ[localCellZ][localCellX];
   }

   public boolean isChamberInterior(int localCellX, int localCellZ) {
      RoomType type = getResolvedRoomType(localCellX, localCellZ);
      return isStampAnchor(type) || type == RoomType.CHAMBER_PAD;
   }

   /** True if both cells belong to the same multi-cell chamber. */
   public boolean sameChamber(int localX1, int localZ1, int localX2, int localZ2) {
      if (!inBounds(localX1, localZ1) || !inBounds(localX2, localZ2)) {
         return false;
      }
      int a1x = this.anchorLocalX[localZ1][localX1];
      int a1z = this.anchorLocalZ[localZ1][localX1];
      int a2x = this.anchorLocalX[localZ2][localX2];
      int a2z = this.anchorLocalZ[localZ2][localX2];
      if (a1x < 0 || a2x < 0) {
         return false;
      }
      return a1x == a2x && a1z == a2z;
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
      // Fixed naves / arenas first (layout v2 multi-cell).
      tryClaimGlobal(0, 0, LabyrinthKeys.ARRIVAL_SPAN, RoomType.ARRIVAL);
      tryClaimGlobal(LabyrinthKeys.CORE_CELL_X, LabyrinthKeys.CORE_CELL_Z, LabyrinthKeys.CORE_SPAN, RoomType.CORE);
      tryClaimGlobal(
         LabyrinthKeys.AUDIENCE_CELL_X,
         LabyrinthKeys.AUDIENCE_CELL_Z,
         LabyrinthKeys.AUDIENCE_SPAN,
         RoomType.AUDIENCE
      );
      linkCoreToAudience();

      for (int localZ = 0; localZ < this.regionSize; localZ++) {
         for (int localX = 0; localX < this.regionSize; localX++) {
            if (this.roomTypes[localZ][localX] != RoomType.NONE) {
               continue;
            }
            int globalX = this.regionX * this.regionSize + localX;
            int globalZ = this.regionZ * this.regionSize + localZ;

            int spacing = LabyrinthKeys.HUB_SPACING;
            if (globalX % spacing == 0 && globalZ % spacing == 0 && globalX != 0 && globalZ != 0) {
               int hubIndex = Math.floorMod(
                  (globalX / spacing) + (globalZ / spacing) * 3 + this.regionX + this.regionZ * 5,
                  7
               );
               RoomType hub = switch (hubIndex) {
                  case 0 -> RoomType.HOST_HALL;
                  case 1 -> RoomType.FORGE;
                  case 2 -> RoomType.ARCHIVE;
                  case 3 -> RoomType.CHAPEL;
                  case 4 -> RoomType.GATE;
                  case 5 -> RoomType.ROTARY;
                  default -> RoomType.ECHO_LOOP;
               };
               int span = isLiminal(hub) ? LabyrinthKeys.SUITE_SPAN : LabyrinthKeys.ROOM_SPAN;
               if (!claimLocal(localX, localZ, span, hub)) {
                  claimLocal(localX, localZ, 1, hub);
               }
               continue;
            }

            // Rotary waypoints on the approach path toward Core.
            if (globalX > 0 && globalZ > 0
               && globalX % (spacing / 2) == 0
               && globalZ % spacing == spacing / 2
               && LabyrinthKeys.manhattanFromArrival(globalX, globalZ) >= 8) {
               if (!claimLocal(localX, localZ, LabyrinthKeys.ROOM_SPAN, RoomType.ROTARY)) {
                  claimLocal(localX, localZ, 1, RoomType.ROTARY);
               }
               continue;
            }

            // Vertical atriums in Approach — overlook vocabulary (P1.4).
            LabyrinthKeys.Zone zone = LabyrinthKeys.zoneAt(globalX, globalZ);
            if (zone == LabyrinthKeys.Zone.APPROACH
               && globalX % spacing == spacing / 2
               && globalZ % spacing == 0
               && this.roomTypes[localZ][localX] == RoomType.NONE) {
               claimLocal(localX, localZ, LabyrinthKeys.ROOM_SPAN, RoomType.ATRIUM);
               continue;
            }

            // Reality-bleed suites — prefer 3×3 when free.
            if (LabyrinthKeys.manhattanFromArrival(globalX, globalZ) >= 5
               && this.roomTypes[localZ][localX] == RoomType.NONE) {
               int bleed = Math.floorMod(globalX * 17 + globalZ * 29 + this.regionX * 7, 11);
               if (bleed == 0 || bleed == 3 || bleed == 7) {
                  RoomType liminal = liminalVariant(globalX, globalZ, random);
                  if (!claimLocal(localX, localZ, LabyrinthKeys.SUITE_SPAN, liminal)
                     && !claimLocal(localX, localZ, LabyrinthKeys.ROOM_SPAN, liminal)) {
                     claimLocal(localX, localZ, 1, liminal);
                  }
                  continue;
               }
            }

            if (isDeadEnd(localX, localZ) && this.roomTypes[localZ][localX] == RoomType.NONE) {
               if (random.nextFloat() < 0.45f) {
                  RoomType liminal = liminalVariant(globalX, globalZ, random);
                  if (!claimLocal(localX, localZ, LabyrinthKeys.ROOM_SPAN, liminal)) {
                     claimLocal(localX, localZ, 1, liminal);
                  }
               } else if (random.nextFloat() < 0.35f) {
                  claimLocal(localX, localZ, 1, RoomType.NICHE);
               }
            }
         }
      }
   }

   private void linkCoreToAudience() {
      // Open the shared edge between Core north and Audience south (global).
      for (int dx = 0; dx < LabyrinthKeys.CORE_SPAN; dx++) {
         int coreGx = LabyrinthKeys.CORE_CELL_X + dx;
         int coreGz = LabyrinthKeys.CORE_CELL_Z;
         int audGx = coreGx;
         int audGz = LabyrinthKeys.CORE_CELL_Z - 1;
         int coreLx = coreGx - this.regionX * this.regionSize;
         int coreLz = coreGz - this.regionZ * this.regionSize;
         int audLx = audGx - this.regionX * this.regionSize;
         int audLz = audGz - this.regionZ * this.regionSize;
         if (inBounds(coreLx, coreLz) && inBounds(audLx, audLz)) {
            openMutual(coreLx, coreLz, 0, -1, WALL_N, WALL_S);
         }
      }
   }

   private void tryClaimGlobal(int globalX, int globalZ, int span, RoomType type) {
      int localX = globalX - this.regionX * this.regionSize;
      int localZ = globalZ - this.regionZ * this.regionSize;
      if (!inBounds(localX, localZ)) {
         return;
      }
      claimLocal(localX, localZ, span, type);
   }

   /**
    * Claims a square footprint with SW at (localX, localZ). Opens internal walls.
    * @return false if any cell is out of bounds or already claimed
    */
   private boolean claimLocal(int localX, int localZ, int span, RoomType type) {
      if (span < 1) {
         return false;
      }
      for (int dz = 0; dz < span; dz++) {
         for (int dx = 0; dx < span; dx++) {
            int x = localX + dx;
            int z = localZ + dz;
            if (!inBounds(x, z)) {
               return false;
            }
            if (this.roomTypes[z][x] != RoomType.NONE) {
               return false;
            }
         }
      }

      for (int dz = 0; dz < span; dz++) {
         for (int dx = 0; dx < span; dx++) {
            int x = localX + dx;
            int z = localZ + dz;
            this.roomTypes[z][x] = (dx == 0 && dz == 0) ? type : RoomType.CHAMBER_PAD;
            this.anchorLocalX[z][x] = localX;
            this.anchorLocalZ[z][x] = localZ;
            this.footprintSpan[z][x] = span;

            if (dx + 1 < span) {
               openMutual(x, z, 1, 0, WALL_E, WALL_W);
            }
            if (dz + 1 < span) {
               openMutual(x, z, 0, 1, WALL_S, WALL_N);
            }
         }
      }
      return true;
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
      mixed ^= (long) LabyrinthKeys.LAYOUT_VERSION * 0x9E3779B97F4A7C15L;
      mixed = mixed * 6364136223846793005L + 1442695040888963407L;
      mixed ^= (long) regionX * 341873128712L;
      mixed ^= (long) regionZ * 132897987541L;
      return mixed;
   }

   private static long cacheKey(long worldSeed, int regionX, int regionZ, int regionSize) {
      long key = worldSeed;
      key = key * 31L + LabyrinthKeys.LAYOUT_VERSION;
      key = key * 31L + regionX;
      key = key * 31L + regionZ;
      key = key * 31L + regionSize;
      return key;
   }
}
