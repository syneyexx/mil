package org.millenaire.worldgen.materialize;

import java.util.function.BiPredicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Narrow registry hook so placement guards can query realized road cells without a hard
 * dependency cycle on regional materializer internals.
 */
public final class RegisteredRoadIndex {
   @Nullable
   private static BiPredicate<ServerLevel, BlockPos> regionalLookup;
   @Nullable
   private static BiPredicate<ServerLevel, BlockPos> highwayLookup;

   private RegisteredRoadIndex() {
   }

   public static void bindRegionalLookup(BiPredicate<ServerLevel, BlockPos> lookup) {
      regionalLookup = lookup;
   }

   public static void bindHighwayLookup(BiPredicate<ServerLevel, BlockPos> lookup) {
      highwayLookup = lookup;
   }

   public static boolean isRegistered(ServerLevel level, BlockPos pos) {
      if (level == null || pos == null) {
         return false;
      }
      BiPredicate<ServerLevel, BlockPos> regional = regionalLookup;
      if (regional != null && regional.test(level, pos)) {
         return true;
      }
      BiPredicate<ServerLevel, BlockPos> highway = highwayLookup;
      return highway != null && highway.test(level, pos);
   }
}
