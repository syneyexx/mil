package org.millenaire.worldgen.terrain;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Bounded height/biome sampling for planning. Uses heightmaps on already-loaded chunks only —
 * missing columns yield {@link SampleStatus#WAITING}, never implicit chunk generation.
 */
public final class WorldgenTerrainSampler {
   public enum SampleStatus {
      READY,
      WAITING
   }

   public record IntSample(SampleStatus status, int value) {
      public static IntSample ready(int value) {
         return new IntSample(SampleStatus.READY, value);
      }

      public static IntSample waiting() {
         return new IntSample(SampleStatus.WAITING, 0);
      }

      public boolean isWaiting() {
         return this.status == SampleStatus.WAITING;
      }
   }

   public record BiomeSample(SampleStatus status, Holder<Biome> biome) {
      public static BiomeSample ready(Holder<Biome> biome) {
         return new BiomeSample(SampleStatus.READY, biome);
      }

      public static BiomeSample waiting() {
         return new BiomeSample(SampleStatus.WAITING, null);
      }

      public boolean isWaiting() {
         return this.status == SampleStatus.WAITING;
      }
   }

   public record MetricsSample(SampleStatus status, TerrainMetrics metrics) {
      public static MetricsSample ready(TerrainMetrics metrics) {
         return new MetricsSample(SampleStatus.READY, metrics);
      }

      public static MetricsSample waiting() {
         return new MetricsSample(SampleStatus.WAITING, null);
      }

      public boolean isWaiting() {
         return this.status == SampleStatus.WAITING;
      }
   }

   public record ValiditySample(SampleStatus status, float validity) {
      public static ValiditySample ready(float validity) {
         return new ValiditySample(SampleStatus.READY, validity);
      }

      public static ValiditySample waiting() {
         return new ValiditySample(SampleStatus.WAITING, 0.0F);
      }

      public boolean isWaiting() {
         return this.status == SampleStatus.WAITING;
      }
   }

   private WorldgenTerrainSampler() {
   }

   /** @deprecated Prefer {@link #surfaceYSample}; this generates chunks via ServerLevel.getHeight. */
   @Deprecated
   public static int surfaceY(ServerLevel level, int x, int z) {
      OptionalInt y = WorldgenChunkAccess.surfaceY(level, x, z);
      return y.orElseGet(() -> level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x, z));
   }

   public static IntSample surfaceYSample(ServerLevel level, int x, int z) {
      OptionalInt y = WorldgenChunkAccess.surfaceY(level, x, z);
      return y.isPresent() ? IntSample.ready(y.getAsInt()) : IntSample.waiting();
   }

   public static BiomeSample biomeAtSample(ServerLevel level, int x, int z) {
      Optional<Holder<Biome>> biome = WorldgenChunkAccess.biomeAt(level, x, z);
      return biome.isPresent() ? BiomeSample.ready(biome.get()) : BiomeSample.waiting();
   }

   /** @deprecated Prefer {@link #biomeAtSample}. */
   @Deprecated
   public static Holder<Biome> biomeAt(ServerLevel level, int x, int z) {
      BiomeSample sample = biomeAtSample(level, x, z);
      if (!sample.isWaiting()) {
         return sample.biome();
      }
      int y = Math.min(level.getMaxBuildHeight() - 1, Math.max(level.getMinBuildHeight(), surfaceY(level, x, z)));
      return level.getBiome(new BlockPos(x, y, z));
   }

   public static MetricsSample sampleLoaded(ServerLevel level, BlockPos center, int radius, int step) {
      int sampleStep = Math.max(4, step);
      // Centre column is mandatory; corners of the AABB often sit outside the circular view-distance
      // disc, so we sample only loaded columns instead of waiting for a full square.
      if (!WorldgenChunkAccess.isColumnLoaded(level, center.getX(), center.getZ())) {
         return MetricsSample.waiting();
      }

      int minY = Integer.MAX_VALUE;
      int maxY = Integer.MIN_VALUE;
      int samples = 0;
      int expected = 0;
      int water = 0;
      int lava = 0;
      int buildable = 0;
      double slopeAccum = 0.0;
      int slopePairs = 0;
      int prevY = Integer.MIN_VALUE;

      for (int dx = -radius; dx <= radius; dx += sampleStep) {
         for (int dz = -radius; dz <= radius; dz += sampleStep) {
            expected++;
            int x = center.getX() + dx;
            int z = center.getZ() + dz;
            IntSample ySample = surfaceYSample(level, x, z);
            if (ySample.isWaiting()) {
               prevY = Integer.MIN_VALUE;
               continue;
            }
            int y = ySample.value();
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            samples++;
            BlockPos surface = new BlockPos(x, y, z);
            Optional<BlockState> stateOpt = WorldgenChunkAccess.blockState(level, surface);
            Optional<BlockState> belowOpt = WorldgenChunkAccess.blockState(level, surface.below());
            if (stateOpt.isEmpty() || belowOpt.isEmpty()) {
               prevY = Integer.MIN_VALUE;
               continue;
            }
            BlockState state = stateOpt.get();
            BlockState below = belowOpt.get();
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

      // Need a solid majority of the grid so sparse edge loads cannot invent a settlement.
      if (samples < Math.max(4, (expected + 1) / 2)) {
         return MetricsSample.waiting();
      }

      float waterFrac = (float)water / samples;
      float lavaFrac = (float)lava / samples;
      float buildableFrac = (float)buildable / samples;
      float avgSlope = slopePairs == 0 ? 0.0F : (float)(slopeAccum / slopePairs);
      int range = maxY - minY;
      return MetricsSample.ready(new TerrainMetrics(minY, maxY, range, avgSlope, waterFrac, lavaFrac, buildableFrac, samples));
   }

   /** @deprecated Prefer {@link #sampleLoaded}. */
   @Deprecated
   public static TerrainMetrics sample(ServerLevel level, BlockPos center, int radius, int step) {
      MetricsSample s = sampleLoaded(level, center, radius, step);
      if (!s.isWaiting()) {
         return s.metrics();
      }
      // Legacy fallback retained for non-worldgen callers; may generate chunks.
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

   public static ValiditySample biomeValidityLoaded(
      ServerLevel level, BlockPos center, int radius, List<TagKey<Biome>> requiredTags, int step
   ) {
      return biomeValidityLoaded(level, center, radius, requiredTags, List.of(), step);
   }

   public static ValiditySample biomeValidityLoaded(
      ServerLevel level,
      BlockPos center,
      int radius,
      List<TagKey<Biome>> requiredTags,
      List<net.minecraft.resources.ResourceKey<Biome>> requiredKeys,
      int step
   ) {
      boolean noTags = requiredTags == null || requiredTags.isEmpty();
      boolean noKeys = requiredKeys == null || requiredKeys.isEmpty();
      if (noTags && noKeys) {
         return ValiditySample.ready(1.0F);
      }
      int sampleStep = Math.max(8, step);
      if (!WorldgenChunkAccess.isColumnLoaded(level, center.getX(), center.getZ())) {
         return ValiditySample.waiting();
      }
      int expected = 0;
      int total = 0;
      int matched = 0;
      for (int dx = -radius; dx <= radius; dx += sampleStep) {
         for (int dz = -radius; dz <= radius; dz += sampleStep) {
            expected++;
            BiomeSample biome = biomeAtSample(level, center.getX() + dx, center.getZ() + dz);
            if (biome.isWaiting()) {
               continue;
            }
            total++;
            boolean hit = false;
            if (!noTags) {
               for (TagKey<Biome> tag : requiredTags) {
                  if (biome.biome().is(tag)) {
                     hit = true;
                     break;
                  }
               }
            }
            if (!hit && !noKeys) {
               for (net.minecraft.resources.ResourceKey<Biome> key : requiredKeys) {
                  if (biome.biome().is(key)) {
                     hit = true;
                     break;
                  }
               }
            }
            if (hit) {
               matched++;
            }
         }
      }
      if (total < Math.max(4, (expected + 1) / 2)) {
         return ValiditySample.waiting();
      }
      return ValiditySample.ready((float)matched / total);
   }

   /** @deprecated Prefer {@link #biomeValidityLoaded}. */
   @Deprecated
   public static float biomeValidity(ServerLevel level, BlockPos center, int radius, List<TagKey<Biome>> requiredTags, int step) {
      ValiditySample sample = biomeValidityLoaded(level, center, radius, requiredTags, step);
      if (!sample.isWaiting()) {
         return sample.validity();
      }
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
