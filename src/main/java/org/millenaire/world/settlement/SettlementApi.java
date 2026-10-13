package org.millenaire.world.settlement;

import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Public read-only settlement API. */
public final class SettlementApi {
   private SettlementApi() {
   }

   @Nullable
   public static Settlement getNearestSettlement(Level level, BlockPos pos) {
      return getNearestSettlement(level, pos, Double.MAX_VALUE);
   }

   @Nullable
   public static Settlement getNearestSettlement(Level level, BlockPos pos, double maxDist) {
      if (!(level instanceof ServerLevel serverLevel)) {
         return null;
      }
      return SettlementSavedData.get(serverLevel).getRegistry().getNearest(pos, maxDist);
   }

   @Nullable
   public static Settlement getSettlement(Level level, String id) {
      if (!(level instanceof ServerLevel serverLevel)) {
         return null;
      }
      return SettlementSavedData.get(serverLevel).getRegistry().get(id);
   }

   public static Collection<Settlement> getAll(Level level) {
      if (!(level instanceof ServerLevel serverLevel)) {
         return java.util.List.of();
      }
      return SettlementSavedData.get(serverLevel).getRegistry().getAll();
   }
}
