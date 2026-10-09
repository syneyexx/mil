package org.millenaire.worldgen.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

/**
 * Bounded height/biome sampling for planning. Uses heightmaps — no full column scans.
 */
public final class WorldgenTerrainSampler {
   private WorldgenTerrainSampler() {
   }

   public static int surfaceY(ServerLevel level, int x, int z) {
      return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
   }

   public static Holder<Biome> biomeAt(ServerLevel level, int x, int z) {
      int y = Math.min(level.getMaxBuildHeight() - 1, Math.max(level.getMinBuildHeight(), surfaceY(level, x, z)));
      return level.getBiome(new BlockPos(x, y, z));
   }

   public static TerrainMetrics sample(ServerLevel level, BlockPos center, int radius, int step) {
      int sampleStep = Math.max(4, step);
      int minY = Integer.MAX_VALUE;
      int maxY = Integer.MIN_VALUE;
      int samples = 0;
      int water = 0;
      int lava = 0;
      int buildable = 0;
      double slopeAccum = 0.0;
      int slopePairs = 0;
      int prevY = Integer.MIN_VALUE;

      for (int dx = -radius; dx <= radius; dx += sampleStep) {
         for (int dz = -radius; dz <= radius; dz += sampleStep) {
            int x = center.getX() + dx;
            int z = center.getZ() + dz;
            int y = surfaceY(level, x, z);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            samples++;
            BlockPos surface = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(surface);
            BlockState below = level.getBlockState(surface.below());
            if (state.getFluidState().is(Fluids.WATER) || below.getFluidState().is(Fluids.WATER)) {
               water++;
            } else if (state.getFluidState().is(Fluids.LAVA) || below.getFluidState().is(Fluids.LAVA)) {
               lava++;
            } else if (!state.isAir() || !below.isAir()) {
               buildable++;
            }
            if (prevY != Integer.MIN_VALUE) {
               slopeAccum += Math.abs(y - prevY);
               slopePairs++;
            }
            prevY = y;
         }
         prevY = Integer.MIN_VALUE;
      }

      float waterFrac = samples == 0 ? 1.0F : (float)water / samples;
      float lavaFrac = samples == 0 ? 1.0F : (float)lava / samples;
      float buildableFrac = samples == 0 ? 0.0F : (float)buildable / samples;
      float avgSlope = slopePairs == 0 ? 0.0F : (float)(slopeAccum / slopePairs);
      int range = samples == 0 ? 0 : maxY - minY;
      return new TerrainMetrics(minY, maxY, range, avgSlope, waterFrac, lavaFrac, buildableFrac, samples);
   }

   public static float biomeValidity(ServerLevel level, BlockPos center, int radius, java.util.List<TagKey<Biome>> requiredTags, int step) {
      if (requiredTags == null || requiredTags.isEmpty()) {
         return 1.0F;
      }
      int sampleStep = Math.max(8, step);
      int total = 0;
      int matched = 0;
      for (int dx = -radius; dx <= radius; dx += sampleStep) {
         for (int dz = -radius; dz <= radius; dz += sampleStep) {
            Holder<Biome> biome = biomeAt(level, center.getX() + dx, center.getZ() + dz);
            total++;
            for (TagKey<Biome> tag : requiredTags) {
               if (biome.is(tag)) {
                  matched++;
                  break;
               }
            }
         }
      }
      return total == 0 ? 0.0F : (float)matched / total;
   }

   public record TerrainMetrics(
      int minY,
      int maxY,
      int heightRange,
      float averageStepSlope,
      float waterFraction,
      float lavaFraction,
      float buildableFraction,
      int sampleCount
   ) {
      public boolean isSteep(float maxSlope) {
         return this.averageStepSlope > maxSlope || this.heightRange > maxSlope * 8.0F;
      }

      public boolean tooWet(float maxWater) {
         return this.waterFraction > maxWater;
      }

      public boolean hasLava(float maxLava) {
         return this.lavaFraction > maxLava;
      }

      public boolean insufficientBuildable(float minBuildable) {
         return this.buildableFraction < minBuildable;
      }
   }
}
