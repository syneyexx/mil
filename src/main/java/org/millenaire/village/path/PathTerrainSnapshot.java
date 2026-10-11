package org.millenaire.village.path;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.millenaire.village.Village;

/**
 * Immutable column cache for off-thread {@link PathGridAStar} searches.
 * Captured on the server thread; workers only read.
 */
public final class PathTerrainSnapshot {
   private final Map<Long, Integer> heights;
   private final Set<Long> borderCells;
   private final Set<Long> affinityColumns;
   private final Set<Long> walkable;
   private final Set<Long> walkablePermissive;
   private final int minX;
   private final int maxX;
   private final int minZ;
   private final int maxZ;
   private final int fallbackY;

   private PathTerrainSnapshot(
      Map<Long, Integer> heights,
      Set<Long> borderCells,
      Set<Long> affinityColumns,
      Set<Long> walkable,
      Set<Long> walkablePermissive,
      int minX,
      int maxX,
      int minZ,
      int maxZ,
      int fallbackY
   ) {
      this.heights = Map.copyOf(heights);
      this.borderCells = Set.copyOf(borderCells);
      this.affinityColumns = Set.copyOf(affinityColumns);
      this.walkable = Set.copyOf(walkable);
      this.walkablePermissive = Set.copyOf(walkablePermissive);
      this.minX = minX;
      this.maxX = maxX;
      this.minZ = minZ;
      this.maxZ = maxZ;
      this.fallbackY = fallbackY;
   }

   public static PathTerrainSnapshot capture(
      ServerLevel level,
      Village village,
      List<PathRoute> routes,
      Map<Long, Integer> buildingFloors,
      Set<Long> borderCells,
      Set<Long> affinityColumns
   ) {
      int minX = village.getCenter().getX();
      int maxX = minX;
      int minZ = village.getCenter().getZ();
      int maxZ = minZ;
      for (PathRoute route : routes) {
         minX = Math.min(minX, Math.min(route.from().getX(), route.to().getX()));
         maxX = Math.max(maxX, Math.max(route.from().getX(), route.to().getX()));
         minZ = Math.min(minZ, Math.min(route.from().getZ(), route.to().getZ()));
         maxZ = Math.max(maxZ, Math.max(route.from().getZ(), route.to().getZ()));
      }
      return captureBounds(level, village, minX, maxX, minZ, maxZ, 48, buildingFloors, borderCells, affinityColumns);
   }

   public static PathTerrainSnapshot captureAroundAnchors(
      ServerLevel level,
      Village village,
      List<BlockPos> anchors,
      Map<Long, Integer> buildingFloors,
      Set<Long> borderCells,
      Set<Long> affinityColumns
   ) {
      int minX = village.getCenter().getX();
      int maxX = minX;
      int minZ = village.getCenter().getZ();
      int maxZ = minZ;
      for (BlockPos p : anchors) {
         minX = Math.min(minX, p.getX());
         maxX = Math.max(maxX, p.getX());
         minZ = Math.min(minZ, p.getZ());
         maxZ = Math.max(maxZ, p.getZ());
      }
      return captureBounds(level, village, minX, maxX, minZ, maxZ, 64, buildingFloors, borderCells, affinityColumns);
   }

   private static PathTerrainSnapshot captureBounds(
      ServerLevel level,
      Village village,
      int minX,
      int maxX,
      int minZ,
      int maxZ,
      int pad,
      Map<Long, Integer> buildingFloors,
      Set<Long> borderCells,
      Set<Long> affinityColumns
   ) {
      minX -= pad;
      maxX += pad;
      minZ -= pad;
      maxZ += pad;

      Map<Long, Integer> heights = new HashMap<>();
      Set<Long> walkable = new HashSet<>();
      Set<Long> walkablePermissive = new HashSet<>();
      MutableBlockPos mutable = new MutableBlockPos();
      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            long key = packXZ(x, z);
            int y = heightForColumn(level, buildingFloors, x, z);
            heights.put(key, y);
            if (borderCells.contains(key)) {
               walkable.add(key);
               walkablePermissive.add(key);
               continue;
            }
            mutable.set(x, y, z);
            BlockState here = level.getBlockState(mutable);
            if (here.liquid() || !here.getFluidState().isEmpty()) {
               continue;
            }
            mutable.set(x, y - 1, z);
            BlockState surface = level.getBlockState(mutable);
            if (surface.liquid() || !surface.getFluidState().isEmpty() || surface.isAir()) {
               continue;
            }
            mutable.set(x, y, z);
            boolean fullCollision = here.isCollisionShapeFullBlock(level, mutable);
            walkablePermissive.add(key);
            if (!fullCollision) {
               walkable.add(key);
            }
         }
      }
      return new PathTerrainSnapshot(
         heights,
         borderCells,
         affinityColumns,
         walkable,
         walkablePermissive,
         minX,
         maxX,
         minZ,
         maxZ,
         village.getCenter().getY()
      );
   }

   public int heightAt(int x, int z, BlockPos from, BlockPos to) {
      if (x == from.getX() && z == from.getZ()) {
         return from.getY();
      }
      if (x == to.getX() && z == to.getZ()) {
         return to.getY();
      }
      Integer y = this.heights.get(packXZ(x, z));
      return y != null ? y : this.fallbackY;
   }

   public boolean allows(int x, int z, BlockPos from, BlockPos to, boolean permissive) {
      if (x == from.getX() && z == from.getZ()) {
         return true;
      }
      if (x == to.getX() && z == to.getZ()) {
         return true;
      }
      if (x < this.minX || x > this.maxX || z < this.minZ || z > this.maxZ) {
         return false;
      }
      long key = packXZ(x, z);
      if (this.borderCells.contains(key)) {
         return true;
      }
      return permissive ? this.walkablePermissive.contains(key) : this.walkable.contains(key);
   }

   public boolean onAffinity(int x, int z) {
      return this.affinityColumns.contains(packXZ(x, z));
   }

   @Nullable
   public VillagePathManager.TraceResult searchTrace(PathRoute route) {
      BlockPos from = route.from();
      BlockPos to = route.to();
      PathGridAStar.GroundHeightProvider ground = (x, z) -> heightAt(x, z, from, to);
      PathGridAStar.TraversabilityCheck traversable = (x, z) -> allows(x, z, from, to, false);
      PathGridAStar.PathAffinity affinity = this::onAffinity;
      PathGridAStar.Result first = PathGridAStar.search(
         from, to, ground, traversable, PathGridAStar.Weights.defaults(), 5000, affinity, 0.5
      );
      if (first.success()) {
         return new VillagePathManager.TraceResult(
            first.path(), new AStarFailureDetail(first.nodesExplored(), 0, 0, 0, 0, 0, first.reason(), null, null), null
         );
      }
      PathGridAStar.Result second = PathGridAStar.search(
         from, to, ground, traversable, PathGridAStar.Weights.relaxed(), 5000, affinity, 0.5
      );
      if (second.success()) {
         return new VillagePathManager.TraceResult(
            second.path(),
            new AStarFailureDetail(first.nodesExplored(), second.nodesExplored(), 0, 0, 0, 0, first.reason(), second.reason(), null),
            null
         );
      }
      PathGridAStar.TraversabilityCheck permissive = (x, z) -> allows(x, z, from, to, true);
      PathGridAStar.Result third = PathGridAStar.search(
         from, to, ground, permissive, PathGridAStar.Weights.relaxed(), 5000, affinity, 0.5
      );
      if (third.success()) {
         return new VillagePathManager.TraceResult(
            third.path(),
            new AStarFailureDetail(
               first.nodesExplored(),
               second.nodesExplored(),
               third.nodesExplored(),
               0,
               0,
               0,
               first.reason(),
               second.reason(),
               third.reason()
            ),
            null
         );
      }
      return new VillagePathManager.TraceResult(
         null,
         new AStarFailureDetail(
            first.nodesExplored(),
            second.nodesExplored(),
            third.nodesExplored(),
            0,
            0,
            0,
            first.reason(),
            second.reason(),
            third.reason()
         ),
         PathFailureReason.A_STAR_FAILED
      );
   }

   private static int heightForColumn(ServerLevel level, Map<Long, Integer> buildingFloors, int x, int z) {
      Integer floorY = buildingFloors.get(packXZ(x, z));
      if (floorY != null) {
         return floorY;
      }
      int top = level.getHeight(Types.WORLD_SURFACE, x, z);
      if (top <= level.getMinBuildHeight()) {
         top = level.getMaxBuildHeight();
      }
      MutableBlockPos mutable = new MutableBlockPos();
      return VillagePathManager.scanGroundIgnoringPaths(top, level.getMinBuildHeight(), cy -> {
         mutable.set(x, cy, z);
         return VillagePathManager.classifyColumnCell(level.getBlockState(mutable));
      });
   }

   private static long packXZ(int x, int z) {
      return (long)x << 32 | z & 4294967295L;
   }
}
