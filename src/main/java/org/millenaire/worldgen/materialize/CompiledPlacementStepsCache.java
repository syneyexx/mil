package org.millenaire.worldgen.materialize;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import org.millenaire.building.PlacementStep;
import org.millenaire.config.MillenaireServerConfig;

/**
 * Bounded cache of immutable template-derived placement steps.
 * Steps are world-independent (no {@code isNoOpAirStep} filtering) so cursor indices stay
 * stable across ticks and save/load. Runtime world checks happen at step execution.
 */
public final class CompiledPlacementStepsCache {
   public record Key(ResourceLocation planId, Rotation rotation, int groundLevel) {
   }

   public record CompiledSteps(List<PlacementStep> steps, Map<Long, int[]> chunkIndexRanges) {
      public CompiledSteps {
         steps = List.copyOf(steps);
         chunkIndexRanges = Collections.unmodifiableMap(chunkIndexRanges);
      }
   }

   private static final LinkedHashMap<Key, CompiledSteps> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<Key, CompiledSteps> eldest) {
         int cap = 64;
         try {
            cap = MillenaireServerConfig.SERVER.worldgenPlacementStepCacheSize.getAsInt();
         } catch (Exception ignored) {
         }
         return size() > Math.max(8, cap);
      }
   };

   private CompiledPlacementStepsCache() {
   }

   private static long compileMisses;
   private static long compileNanos;
   private static long approxBytes;

   public static synchronized CompiledSteps getOrCompute(Key key, java.util.function.Supplier<List<PlacementStep>> compiler) {
      CompiledSteps hit = CACHE.get(key);
      if (hit != null) {
         return hit;
      }
      // Compile outside the critical section? Kept synchronized for cache coherency; track cost for telemetry.
      long start = System.nanoTime();
      List<PlacementStep> steps = compiler.get();
      Map<Long, int[]> byChunk = bucketByRelativeChunk(steps);
      CompiledSteps compiled = new CompiledSteps(steps, byChunk);
      CACHE.put(key, compiled);
      long took = Math.max(0L, System.nanoTime() - start);
      compileMisses++;
      compileNanos += took;
      // Rough footprint: steps * ~48 bytes + index arrays.
      approxBytes += (long)steps.size() * 48L;
      for (int[] idxs : byChunk.values()) {
         approxBytes += (long)idxs.length * 4L;
      }
      return compiled;
   }

   /** Cumulative compile-miss count (main-thread). */
   public static synchronized long compileMisses() {
      return compileMisses;
   }

   public static synchronized long compileNanos() {
      return compileNanos;
   }

   public static synchronized long approxCachedBytes() {
      return approxBytes;
   }

   /**
    * Bucket steps by the chunk relative to origin (0,0) before world offset.
    * Absolute chunk membership is resolved at execution using origin.
    */
   private static Map<Long, int[]> bucketByRelativeChunk(List<PlacementStep> steps) {
      // Store dense index lists per relative chunk of the relativePos.
      Map<Long, java.util.ArrayList<Integer>> temp = new LinkedHashMap<>();
      for (int i = 0; i < steps.size(); i++) {
         var pos = steps.get(i).relativePos();
         long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
         temp.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(i);
      }
      Map<Long, int[]> out = new LinkedHashMap<>();
      for (var e : temp.entrySet()) {
         List<Integer> idxs = e.getValue();
         int[] arr = new int[idxs.size()];
         for (int i = 0; i < idxs.size(); i++) {
            arr[i] = idxs.get(i);
         }
         out.put(e.getKey(), arr);
      }
      return out;
   }

   public static synchronized void invalidateAll() {
      CACHE.clear();
   }

   public static synchronized int size() {
      return CACHE.size();
   }
}
