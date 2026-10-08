package org.millenaire.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;

public record PlacedLocation(BlockPos position, Rotation rotation) {
   public static BlockPos pivotForNwCorner(int worldX, int worldZ, int width, int depth, Rotation rotation) {
      return switch (rotation) {
         case CLOCKWISE_90 -> new BlockPos(worldX + depth - 1, 0, worldZ);
         case CLOCKWISE_180 -> new BlockPos(worldX + width - 1, 0, worldZ + depth - 1);
         case COUNTERCLOCKWISE_90 -> new BlockPos(worldX, 0, worldZ + width - 1);
         default -> new BlockPos(worldX, 0, worldZ);
      };
   }

   public static PlacedLocation.Bounds worldBoundsForPivot(int pivotX, int pivotZ, int width, int depth, Rotation rotation) {
      return switch (rotation) {
         case CLOCKWISE_90 -> new PlacedLocation.Bounds(pivotX - depth + 1, pivotZ, pivotX + 1, pivotZ + width);
         case CLOCKWISE_180 -> new PlacedLocation.Bounds(pivotX - width + 1, pivotZ - depth + 1, pivotX + 1, pivotZ + 1);
         case COUNTERCLOCKWISE_90 -> new PlacedLocation.Bounds(pivotX, pivotZ - width + 1, pivotX + depth, pivotZ + 1);
         default -> new PlacedLocation.Bounds(pivotX, pivotZ, pivotX + width, pivotZ + depth);
      };
   }

   public record Bounds(int minX, int minZ, int maxX, int maxZ) {
      public boolean containsXZ(int x, int z) {
         return x >= this.minX && x < this.maxX && z >= this.minZ && z < this.maxZ;
      }
   }
}
