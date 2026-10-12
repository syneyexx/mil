package org.millenaire.world;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.building.ClearMargins;
import org.millenaire.tag.ModTags;
import org.millenaire.worldgen.terrain.WorldgenChunkAccess;

public class VillageTerrainMap {
   private static final int ALTITUDE_TOLERANCE = 10;
   private static final int SCAN_RANGE = 25;
   private static final int SMALL_BUILDING_FIXED_ERRORS = 10;
   private static final int MEDIUM_BUILDING_THRESHOLD = 200;
   private static final int HUGE_BUILDING_THRESHOLD = 2000;
   private final BlockPos origin;
   private final int size;
   private final int baseline;
   private final int[][] topGround;
   private final boolean[][] canBuild;
   private final boolean[][] danger;
   private final boolean[][] water;
   private final boolean[][] buildingForbidden;
   private final short[][] spaceAbove;
   private final boolean[][] occupied;

   private VillageTerrainMap(
      BlockPos origin,
      int size,
      int baseline,
      int[][] topGround,
      boolean[][] canBuild,
      boolean[][] danger,
      boolean[][] water,
      boolean[][] buildingForbidden,
      short[][] spaceAbove
   ) {
      this.origin = origin;
      this.size = size;
      this.baseline = baseline;
      this.topGround = topGround;
      this.canBuild = canBuild;
      this.danger = danger;
      this.water = water;
      this.buildingForbidden = buildingForbidden;
      this.spaceAbove = spaceAbove;
      this.occupied = new boolean[size][size];
   }

   static VillageTerrainMap forTest(BlockPos origin, int size, int baseline, int[][] topGround, boolean[][] canBuild) {
      boolean[][] danger = new boolean[size][size];
      boolean[][] water = new boolean[size][size];
      boolean[][] forbidden = new boolean[size][size];
      short[][] spaceAbove = new short[size][size];

      for (int x = 0; x < size; x++) {
         for (int z = 0; z < size; z++) {
            spaceAbove[x][z] = 3;
         }
      }

      return new VillageTerrainMap(origin, size, baseline, topGround, canBuild, danger, water, forbidden, spaceAbove);
   }

   static VillageTerrainMap forTest(
      BlockPos origin,
      int size,
      int baseline,
      int[][] topGround,
      boolean[][] canBuild,
      boolean[][] danger,
      boolean[][] water,
      boolean[][] buildingForbidden,
      short[][] spaceAbove
   ) {
      return new VillageTerrainMap(origin, size, baseline, topGround, canBuild, danger, water, buildingForbidden, spaceAbove);
   }

   private static boolean isDangerous(BlockState state) {
      return state.is(Blocks.LAVA)
         || state.is(Blocks.FIRE)
         || state.is(Blocks.SOUL_FIRE)
         || state.is(Blocks.CACTUS)
         || state.is(Blocks.TNT)
         || state.is(Blocks.MAGMA_BLOCK)
         || state.is(Blocks.SWEET_BERRY_BUSH)
         || state.is(Blocks.POWDER_SNOW);
   }

   private static boolean isForbidden(ServerLevel level, BlockPos pos, BlockState state) {
      if (state.isAir()) {
         return false;
      } else if (state.is(ModTags.Blocks.FORBIDDEN_EXCEPTIONS)) {
         return false;
      } else if (level.getBlockEntity(pos) != null) {
         return true;
      } else {
         return state.getBlock() instanceof StainedGlassBlock || state.getBlock() instanceof StainedGlassPaneBlock
            ? true
            : state.is(ModTags.Blocks.ARTIFICIAL_BLOCKS);
      }
   }

   static boolean isGround(BlockState state) {
      if (state.isAir()) {
         return false;
      } else if (!state.getFluidState().isEmpty()) {
         return false;
      } else if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
         return false;
      } else if (TerrainPreparer.isHugeMushroomBlock(state)) {
         return false;
      } else {
         return state.is(BlockTags.REPLACEABLE) ? false : state.canOcclude();
      }
   }

   /**
    * Full synchronous scan (may generate chunks via {@link ServerLevel#getBlockState}).
    * Prefer {@link #beginScan} + {@link ScanSession#advance} on the worldgen tick path.
    */
   public static VillageTerrainMap compute(ServerLevel level, BlockPos villageCenter, int radius) {
      ScanSession session = beginScan(villageCenter, radius, level.getMinBuildHeight(), level.getMaxBuildHeight());
      while (session.status() == ScanSession.Status.IN_PROGRESS) {
         session.advance(level, Integer.MAX_VALUE, Long.MAX_VALUE, true);
      }
      if (session.status() != ScanSession.Status.COMPLETE || session.result() == null) {
         throw new IllegalStateException("VillageTerrainMap.compute failed: " + session.status());
      }
      return session.result();
   }

   public static ScanSession beginScan(BlockPos villageCenter, int radius, int minBuildHeight, int maxBuildHeight) {
      return new ScanSession(villageCenter, radius, minBuildHeight, maxBuildHeight);
   }

   /**
    * Resumable column scanner. Missing chunks yield {@link Status#WAITING_CHUNKS} when
    * {@code allowGeneration} is false — never treated as invalid terrain.
    */
   public static final class ScanSession {
      public enum Status {
         IN_PROGRESS,
         WAITING_CHUNKS,
         COMPLETE
      }

      private final BlockPos origin;
      private final int size;
      private final int baseline;
      private final int miny;
      private final int maxy;
      private final int[][] topGround;
      private final boolean[][] canBuild;
      private final boolean[][] danger;
      private final boolean[][] water;
      private final boolean[][] buildingForbidden;
      private final short[][] spaceAbove;
      private int cursorLx;
      private int cursorLz;
      private Status status = Status.IN_PROGRESS;
      @Nullable
      private VillageTerrainMap result;

      private ScanSession(BlockPos villageCenter, int radius, int minBuildHeight, int maxBuildHeight) {
         int size = 2 * radius + 1;
         int originX = villageCenter.getX() - radius;
         int originZ = villageCenter.getZ() - radius;
         this.origin = new BlockPos(originX, villageCenter.getY(), originZ);
         this.size = size;
         this.baseline = villageCenter.getY();
         this.miny = Math.max(this.baseline - 25, minBuildHeight);
         this.maxy = Math.min(this.baseline + 25, maxBuildHeight);
         this.topGround = new int[size][size];
         this.canBuild = new boolean[size][size];
         this.danger = new boolean[size][size];
         this.water = new boolean[size][size];
         this.buildingForbidden = new boolean[size][size];
         this.spaceAbove = new short[size][size];
      }

      public Status status() {
         return this.status;
      }

      @Nullable
      public VillageTerrainMap result() {
         return this.result;
      }

      public int cursorIndex() {
         return this.cursorLx * this.size + this.cursorLz;
      }

      public int totalColumns() {
         return this.size * this.size;
      }

      /**
       * Scan up to {@code maxColumns} columns or until {@code deadlineNanos}.
       * @param allowGeneration when false, only {@link WorldgenChunkAccess} reads are used
       */
      public Status advance(ServerLevel level, int maxColumns, long deadlineNanos, boolean allowGeneration) {
         if (this.status == Status.COMPLETE) {
            return this.status;
         }
         this.status = Status.IN_PROGRESS;
         int scanned = 0;
         while (this.cursorLx < this.size) {
            // Sample wall-clock every 16 columns — nanoTime per column dominated the scan.
            if (scanned >= maxColumns || ((scanned & 15) == 0 && System.nanoTime() >= deadlineNanos)) {
               return this.status;
            }
            int wx = this.origin.getX() + this.cursorLx;
            int wz = this.origin.getZ() + this.cursorLz;
            int distSq = (wx - (this.origin.getX() + this.size / 2)) * (wx - (this.origin.getX() + this.size / 2))
               + (wz - (this.origin.getZ() + this.size / 2)) * (wz - (this.origin.getZ() + this.size / 2));
            // Core columns near the village centre must be loaded; peripheral AABB corners often
            // sit outside the circular view-distance disc — mark those unbuildable and continue.
            boolean coreColumn = distSq <= 32 * 32;
            if (!allowGeneration && !WorldgenChunkAccess.isColumnLoaded(level, wx, wz)) {
               if (coreColumn) {
                  this.status = Status.WAITING_CHUNKS;
                  return this.status;
               }
               markUnbuildable(this.cursorLx, this.cursorLz);
               scanned++;
               this.cursorLz++;
               if (this.cursorLz >= this.size) {
                  this.cursorLz = 0;
                  this.cursorLx++;
               }
               continue;
            }
            if (!scanColumn(level, this.cursorLx, this.cursorLz, wx, wz, allowGeneration)) {
               if (coreColumn) {
                  this.status = Status.WAITING_CHUNKS;
                  return this.status;
               }
               markUnbuildable(this.cursorLx, this.cursorLz);
            }
            scanned++;
            this.cursorLz++;
            if (this.cursorLz >= this.size) {
               this.cursorLz = 0;
               this.cursorLx++;
            }
         }
         this.result = new VillageTerrainMap(
            this.origin,
            this.size,
            this.baseline,
            this.topGround,
            this.canBuild,
            this.danger,
            this.water,
            this.buildingForbidden,
            this.spaceAbove
         );
         this.status = Status.COMPLETE;
         return this.status;
      }

      private boolean scanColumn(ServerLevel level, int lx, int lz, int wx, int wz, boolean allowGeneration) {
         boolean hasForbidden = false;
         int groundY = this.miny;

         for (int y = this.maxy; y >= this.miny; y--) {
            BlockPos pos = new BlockPos(wx, y, wz);
            Optional<BlockState> stateOpt = readState(level, pos, allowGeneration);
            if (stateOpt.isEmpty()) {
               return false;
            }
            BlockState state = stateOpt.get();
            if (isForbidden(level, pos, state)) {
               hasForbidden = true;
            }
            if (isGround(state)) {
               groundY = y;
               break;
            }
         }

         boolean onGround = true;
         int lastLiquid = groundY;
         int surfaceY = groundY;

         for (int y = groundY + 1; y <= this.maxy; surfaceY = y++) {
            Optional<BlockState> stateOpt = readState(level, new BlockPos(wx, y, wz), allowGeneration);
            if (stateOpt.isEmpty()) {
               return false;
            }
            BlockState state = stateOpt.get();
            if (!state.getFluidState().isEmpty()) {
               onGround = false;
               lastLiquid = y;
            } else {
               if (!state.canOcclude() || state.is(BlockTags.REPLACEABLE)) {
                  surfaceY = y;
                  break;
               }
               onGround = true;
            }
         }

         if (!onGround) {
            surfaceY = lastLiquid;
         }

         this.topGround[lx][lz] = surfaceY;
         Optional<BlockState> groundStateOpt = readState(level, new BlockPos(wx, groundY, wz), allowGeneration);
         if (groundStateOpt.isEmpty()) {
            return false;
         }
         boolean hasDanger = isDangerous(groundStateOpt.get());
         if (!hasDanger) {
            Optional<BlockState> surfaceStateOpt = readState(level, new BlockPos(wx, surfaceY, wz), allowGeneration);
            if (surfaceStateOpt.isEmpty()) {
               return false;
            }
            hasDanger = isDangerous(surfaceStateOpt.get());
         }

         boolean hasWater;
         if (allowGeneration) {
            hasWater = !level.getFluidState(new BlockPos(wx, surfaceY, wz)).isEmpty();
         } else {
            var fluid = WorldgenChunkAccess.fluidState(level, new BlockPos(wx, surfaceY, wz));
            if (fluid.isEmpty()) {
               return false;
            }
            hasWater = !fluid.get().isEmpty();
         }

         for (int y = surfaceY; y <= this.maxy; y++) {
            BlockPos pos = new BlockPos(wx, y, wz);
            Optional<BlockState> stateOpt = readState(level, pos, allowGeneration);
            if (stateOpt.isEmpty()) {
               return false;
            }
            BlockState state = stateOpt.get();
            if (isForbidden(level, pos, state)) {
               hasForbidden = true;
            }
            if (isDangerous(state)) {
               hasDanger = true;
            }
         }

         short sa = 0;
         boolean blocked = false;
         for (int y = surfaceY; y < surfaceY + 3 && y <= this.maxy; y++) {
            Optional<BlockState> stateOpt = readState(level, new BlockPos(wx, y, wz), allowGeneration);
            if (stateOpt.isEmpty()) {
               return false;
            }
            BlockState state = stateOpt.get();
            if (blocked || state.canOcclude() && !state.is(BlockTags.REPLACEABLE)) {
               blocked = true;
            } else {
               sa++;
            }
         }

         this.danger[lx][lz] = hasDanger;
         this.water[lx][lz] = hasWater;
         this.buildingForbidden[lx][lz] = hasForbidden;
         this.spaceAbove[lx][lz] = sa;
         boolean altitudeOk = surfaceY > this.baseline - 10 && surfaceY < this.baseline + 10;
         this.canBuild[lx][lz] = altitudeOk && !hasDanger && !hasForbidden;
         return true;
      }

      private void markUnbuildable(int lx, int lz) {
         this.topGround[lx][lz] = this.baseline;
         this.canBuild[lx][lz] = false;
         this.danger[lx][lz] = false;
         this.water[lx][lz] = false;
         this.buildingForbidden[lx][lz] = true;
         this.spaceAbove[lx][lz] = 0;
      }

      private static Optional<BlockState> readState(ServerLevel level, BlockPos pos, boolean allowGeneration) {
         if (allowGeneration) {
            return Optional.of(level.getBlockState(pos));
         }
         return WorldgenChunkAccess.blockState(level, pos);
      }
   }

   public int toLocalX(int worldX) {
      return worldX - this.origin.getX();
   }

   public int toLocalZ(int worldZ) {
      return worldZ - this.origin.getZ();
   }

   public boolean inBounds(int localX, int localZ) {
      return localX >= 0 && localX < this.size && localZ >= 0 && localZ < this.size;
   }

   public int testFootprint(int worldX, int worldZ, int width, int depth, ClearMargins margins, Rotation rotation) {
      VillageTerrainMap.FootprintRect rect = computeFootprintRect(worldX, worldZ, width, depth, margins, rotation);
      int surface = rect.width() * rect.depth();
      boolean hugeBuilding = surface > 2000;
      int allowedErrors;
      if (surface > 2000) {
         allowedErrors = surface / 10;
      } else if (surface > 200) {
         allowedErrors = surface / 20;
      } else {
         allowedErrors = 10;
      }

      int nbError = 0;

      for (int dx = 0; dx < rect.width(); dx++) {
         for (int dz = 0; dz < rect.depth(); dz++) {
            int lx = this.toLocalX(rect.startX() + dx);
            int lz = this.toLocalZ(rect.startZ() + dz);
            if (!this.inBounds(lx, lz)) {
               return -1;
            }

            if (this.occupied[lx][lz]) {
               return -1;
            }

            if (this.buildingForbidden[lx][lz]) {
               if (!hugeBuilding || nbError > allowedErrors) {
                  return -1;
               }

               nbError++;
            } else if (this.danger[lx][lz]) {
               if (nbError > allowedErrors) {
                  return -1;
               }

               nbError++;
            } else if (this.water[lx][lz]) {
               if (nbError > allowedErrors) {
                  return -1;
               }

               nbError++;
            } else if (!this.canBuild[lx][lz]) {
               if (nbError > allowedErrors) {
                  return -1;
               }

               nbError++;
            }
         }
      }

      return nbError;
   }

   public int computeAverageAltitude(int worldX, int worldZ, int width, int depth, ClearMargins margins, Rotation rotation) {
      VillageTerrainMap.FootprintRect rect = computeFootprintRect(worldX, worldZ, width, depth, margins, rotation);
      long altitudeTotal = 0L;
      int nbPoints = 0;

      for (int dx = 0; dx < rect.width(); dx++) {
         for (int dz = 0; dz < rect.depth(); dz++) {
            int lx = this.toLocalX(rect.startX() + dx);
            int lz = this.toLocalZ(rect.startZ() + dz);
            if (this.inBounds(lx, lz)) {
               altitudeTotal += this.topGround[lx][lz];
               nbPoints++;
            }
         }
      }

      return nbPoints == 0 ? this.baseline : Math.round((float)altitudeTotal / nbPoints);
   }

   public void markBuildingFootprint(int worldX, int worldZ, int width, int depth, ClearMargins margins, Rotation rotation, int buildingY) {
      VillageTerrainMap.FootprintRect fullRect = computeFootprintRect(worldX, worldZ, width, depth, margins, rotation);
      VillageTerrainMap.FootprintRect coreRect = computeFootprintRect(worldX, worldZ, width, depth, ClearMargins.symmetric(0), rotation);

      for (int dx = 0; dx < coreRect.width(); dx++) {
         for (int dz = 0; dz < coreRect.depth(); dz++) {
            int lx = this.toLocalX(coreRect.startX() + dx);
            int lz = this.toLocalZ(coreRect.startZ() + dz);
            if (this.inBounds(lx, lz)) {
               this.occupied[lx][lz] = true;
            }
         }
      }

      for (int dx = 0; dx < coreRect.width(); dx++) {
         for (int dz = 0; dz < coreRect.depth(); dz++) {
            int lx = this.toLocalX(coreRect.startX() + dx);
            int lz = this.toLocalZ(coreRect.startZ() + dz);
            if (this.inBounds(lx, lz)) {
               this.topGround[lx][lz] = buildingY;
               this.spaceAbove[lx][lz] = 3;
               this.water[lx][lz] = false;
               this.danger[lx][lz] = false;
               this.canBuild[lx][lz] = true;
            }
         }
      }

      for (int dx = 0; dx < fullRect.width(); dx++) {
         for (int dz = 0; dz < fullRect.depth(); dz++) {
            int wx = fullRect.startX() + dx;
            int wz = fullRect.startZ() + dz;
            if (wx < coreRect.startX() || wx >= coreRect.startX() + coreRect.width() || wz < coreRect.startZ() || wz >= coreRect.startZ() + coreRect.depth()) {
               int lx = this.toLocalX(wx);
               int lz = this.toLocalZ(wz);
               if (this.inBounds(lx, lz) && !this.occupied[lx][lz]) {
                  this.canBuild[lx][lz] = true;
                  this.water[lx][lz] = false;
                  this.danger[lx][lz] = false;
               }
            }
         }
      }
   }

   public void markOccupied(int worldX, int worldZ, int width, int depth, Rotation rotation) {
      this.markOccupied(worldX, worldZ, width, depth, ClearMargins.symmetric(0), rotation);
   }

   public void markOccupied(int worldX, int worldZ, int width, int depth, ClearMargins margins, Rotation rotation) {
      VillageTerrainMap.FootprintRect rect = computeFootprintRect(worldX, worldZ, width, depth, margins, rotation);

      for (int dx = 0; dx < rect.width(); dx++) {
         for (int dz = 0; dz < rect.depth(); dz++) {
            int lx = this.toLocalX(rect.startX() + dx);
            int lz = this.toLocalZ(rect.startZ() + dz);
            if (this.inBounds(lx, lz)) {
               this.occupied[lx][lz] = true;
            }
         }
      }
   }

   public int getSize() {
      return this.size;
   }

   public int getBaseline() {
      return this.baseline;
   }

   public int getGroundHeight(int worldX, int worldZ) {
      int lx = this.toLocalX(worldX);
      int lz = this.toLocalZ(worldZ);
      return !this.inBounds(lx, lz) ? this.baseline : this.topGround[lx][lz];
   }

   public int getTopGround(int localX, int localZ) {
      return this.inBounds(localX, localZ) ? this.topGround[localX][localZ] : 0;
   }

   public boolean canBuildAt(int localX, int localZ) {
      return this.inBounds(localX, localZ) && this.canBuild[localX][localZ];
   }

   public boolean isDangerAt(int localX, int localZ) {
      return this.inBounds(localX, localZ) && this.danger[localX][localZ];
   }

   public boolean isWaterAt(int localX, int localZ) {
      return this.inBounds(localX, localZ) && this.water[localX][localZ];
   }

   public boolean isBuildingForbiddenAt(int localX, int localZ) {
      return this.inBounds(localX, localZ) && this.buildingForbidden[localX][localZ];
   }

   public short getSpaceAbove(int localX, int localZ) {
      return this.inBounds(localX, localZ) ? this.spaceAbove[localX][localZ] : 0;
   }

   public boolean isOccupied(int localX, int localZ) {
      return this.inBounds(localX, localZ) && this.occupied[localX][localZ];
   }

   public static VillageTerrainMap.FootprintRect computeFootprintRect(int worldX, int worldZ, int width, int depth, ClearMargins margins, Rotation rotation) {
      ClearMargins effective = margins.forRotation(rotation);
      int startX;
      int startZ;
      int effectiveWidth;
      int effectiveDepth;
      switch (rotation) {
         case CLOCKWISE_90:
            startX = worldX - depth + 1 - effective.lengthBefore();
            startZ = worldZ - effective.widthBefore();
            effectiveWidth = depth + effective.lengthBefore() + effective.lengthAfter();
            effectiveDepth = width + effective.widthBefore() + effective.widthAfter();
            break;
         case CLOCKWISE_180:
            startX = worldX - width + 1 - effective.lengthBefore();
            startZ = worldZ - depth + 1 - effective.widthBefore();
            effectiveWidth = width + effective.lengthBefore() + effective.lengthAfter();
            effectiveDepth = depth + effective.widthBefore() + effective.widthAfter();
            break;
         case COUNTERCLOCKWISE_90:
            startX = worldX - effective.lengthBefore();
            startZ = worldZ - width + 1 - effective.widthBefore();
            effectiveWidth = depth + effective.lengthBefore() + effective.lengthAfter();
            effectiveDepth = width + effective.widthBefore() + effective.widthAfter();
            break;
         default:
            startX = worldX - effective.lengthBefore();
            startZ = worldZ - effective.widthBefore();
            effectiveWidth = width + effective.lengthBefore() + effective.lengthAfter();
            effectiveDepth = depth + effective.widthBefore() + effective.widthAfter();
      }

      return new VillageTerrainMap.FootprintRect(startX, startZ, effectiveWidth, effectiveDepth);
   }

   public record FootprintRect(int startX, int startZ, int width, int depth) {
   }
}
