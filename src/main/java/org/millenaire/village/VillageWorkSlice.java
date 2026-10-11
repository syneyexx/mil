package org.millenaire.village;

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
    * When the governor has time, all slices may run.
    * When over budget, only BOOKKEEPING + the rotating focus slice run (features continue next ticks).
    */
   public static boolean allow(VillageWorkSlice slice, VillageWorkSlice focus, boolean hasTime) {
      if (hasTime) {
         return true;
      }
      return slice == BOOKKEEPING || slice == focus;
   }
}
