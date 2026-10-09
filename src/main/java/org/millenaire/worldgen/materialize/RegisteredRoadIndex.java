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

   private RegisteredRoadIndex() {
   }

   public static void bindRegionalLookup(BiPredicate<ServerLevel, BlockPos> lookup) {
      regionalLookup = lookup;
   }

   public static boolean isRegistered(ServerLevel level, BlockPos pos) {
      BiPredicate<ServerLevel, BlockPos> lookup = regionalLookup;
      return lookup != null && level != null && pos != null && lookup.test(level, pos);
   }
}
