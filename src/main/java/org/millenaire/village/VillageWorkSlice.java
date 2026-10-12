package org.millenaire.village;

import org.millenaire.perf.tick.ServerTickGovernor;
import org.millenaire.perf.tick.TickPressure;

/**
 * Ordered slices of {@link Village#tick} so heavy work progresses across ticks under budget.
 */
public enum VillageWorkSlice {
   BOOKKEEPING,
   INTEGRITY,
   GROWTH,
   ENVIRONMENT,
   MARKETS,
   PATHS,
   PANELS_MARVEL;

   private static final VillageWorkSlice[] VALUES = values();

   public static VillageWorkSlice forGameTime(long gameTime, int villageHash) {
      int idx = Math.floorMod((int)gameTime + villageHash, VALUES.length);
      return VALUES[idx];
   }

   /**
    * When the governor has time and pressure is HEALTHY, all slices may run.
    * Under SOFT/HARD, INTEGRITY/GROWTH only run as the rotating focus (even if hasTime).
    * When over budget, only BOOKKEEPING + the rotating focus slice run.
    */
   public static boolean allow(VillageWorkSlice slice, VillageWorkSlice focus, boolean hasTime) {
      if (!hasTime) {
         return slice == BOOKKEEPING || slice == focus;
      }
      TickPressure pressure = ServerTickGovernor.pressure();
      if (pressure == TickPressure.HEALTHY) {
         return true;
      }
      // Soft/Hard: defer expensive integrity/growth unless they are today's focus.
      if (slice == INTEGRITY || slice == GROWTH) {
         return slice == focus;
      }
      return true;
   }
}
