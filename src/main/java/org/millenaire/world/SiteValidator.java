package org.millenaire.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public final class SiteValidator {
   private static final int MAX_HEIGHT_VARIANCE = 10;
   private static final int MAX_HEIGHT_VARIANCE_RELAXED = 15;
   private static final int SEA_LEVEL = 63;
   private static final int MAX_WATER_BLOCKS = 35;
   private static final int MAX_LAVA_BLOCKS = 4;
   private static final int MAX_LOW_BLOCKS = 15;
   private static final int MAX_HIGH_BLOCKS = 15;
   private static final float MIN_QUOTA_VILLAGE = 0.85F;
   private static final float MIN_QUOTA_LONE_BUILDING = 0.95F;
   private static final int SAMPLE_STEP = 8;
   private static final int RELAX_START_RADIUS = 50;
   private static final int RELAX_FULL_RADIUS = 120;
   private static final float LEGACY_QUOTA_FLOOR = 0.7F;
   private static final int FOOTPRINT_VALIDATION_RADIUS_CAP = 64;

   public static float radiusRelaxation(int radius) {
      if (radius <= 50) {
         return 0.0F;
      }

      float t = (radius - 50) / 70.0F;
      return Math.min(1.0F, t);
   }

   private SiteValidator() {
   }

   public static boolean validate(ServerLevel level, BlockPos center, int radius) {
      return validate(level, center, radius, false);
   }

   public static boolean validate(ServerLevel level, BlockPos center, int radius, boolean loneBuilding) {
      float baseQuota = loneBuilding ? 0.95F : 0.85F;
      float minQuota = baseQuota + radiusRelaxation(radius) * (0.7F - baseQuota);
      int sampleRadius = Math.min(radius, 64);
      int heightVariance = 10 + Math.round(radiusRelaxation(radius) * 5.0F);
      int centerY = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ());
      int yMax = centerY + heightVariance;
      int yMin = Math.max(centerY - heightVariance, 63);
      int xMin = center.getX() - sampleRadius;
      int xMax = center.getX() + sampleRadius;
      int zMin = center.getZ() - sampleRadius;
      int zMax = center.getZ() + sampleRadius;
      float numGood = 0.0F;
      float numTotal = 0.0F;
      int flatWater = 0;
      int flatLava = 0;
      int flatLow = 0;
      int flatHigh = 0;
      int debugWater = 0;

      for (int x = xMin; x < xMax; x += 8) {
         for (int z = zMin; z < zMax; z += 8) {
            numTotal++;
            int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockState surfaceBlock = y > level.getMinBuildHeight()
               ? level.getBlockState(new BlockPos(x, y - 1, z))
               : level.getBlockState(new BlockPos(x, y, z));
            if (y < yMin && y > 0) {
               flatLow++;
            } else if (y > yMax) {
               flatHigh++;
            } else {
               numGood++;
               BlockState blockAtY = level.getBlockState(new BlockPos(x, y, z));
               boolean hasLava = surfaceBlock.is(Blocks.LAVA) || blockAtY.is(Blocks.LAVA);
               if (hasLava) {
                  flatLava++;
               } else if (!level.getFluidState(new BlockPos(x, y - 1, z)).isEmpty()
                  || !level.getFluidState(new BlockPos(x, y, z)).isEmpty()
                  || isIceCappedWater(level, x, y, z)) {
                  flatWater++;
               }
            }
         }
      }

      if (flatWater > 10) {
         debugWater++;
      }

      numGood = numGood - flatLava - flatWater;
      if (numTotal == 0.0F) {
         return false;
      }

      float quota = numGood / numTotal;
      return quota > minQuota && flatLow < 15 && flatHigh < 15 && flatLava < 4 && flatWater < 35;
   }

   public static boolean isIceCappedWater(ServerLevel level, int x, int y, int z) {
      int capY = y - 1;
      if (capY <= level.getMinBuildHeight()) {
         return false;
      }

      BlockState cap = level.getBlockState(new BlockPos(x, capY, z));
      return !cap.is(BlockTags.ICE) ? false : !level.getFluidState(new BlockPos(x, capY - 1, z)).isEmpty();
   }

   @Deprecated
   public static boolean validate(ServerLevel level, BlockPos origin, int width, int depth) {
      return validate(level, origin, width, depth, 1);
   }

   @Deprecated
   public static boolean validate(ServerLevel level, BlockPos origin, int width, int depth, int step) {
      int centerX = origin.getX() + width / 2;
      int centerZ = origin.getZ() + depth / 2;
      int radius = Math.max(width, depth) / 2;
      BlockPos center = new BlockPos(centerX, origin.getY(), centerZ);
      return validate(level, center, radius, false);
   }
}
