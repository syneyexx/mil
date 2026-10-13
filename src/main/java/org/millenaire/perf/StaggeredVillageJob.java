package org.millenaire.perf;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.village.Village;

/**
 * Phase 0 tick contract: run expensive per-village work on a staggered cadence
 * keyed by village UUID so load is spread across ticks.
 */
public final class StaggeredVillageJob {
   private StaggeredVillageJob() {
   }

   public static int slot(UUID villageUuid, int periodTicks) {
      if (periodTicks <= 1) {
         return 0;
      }
      int hash = villageUuid.hashCode();
      int mod = hash % periodTicks;
      return mod < 0 ? mod + periodTicks : mod;
   }

   public static boolean shouldRun(ServerLevel level, Village village, int periodTicks) {
      if (periodTicks <= 1) {
         return true;
      }
      long gameTime = level.getGameTime();
      return gameTime % periodTicks == slot(village.getId().uuid(), periodTicks);
   }

   public static boolean shouldRunActive(ServerLevel level, Village village, int periodTicks) {
      return village.isActive() && shouldRun(level, village, periodTicks);
   }
}
