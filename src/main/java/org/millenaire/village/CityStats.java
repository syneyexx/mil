package org.millenaire.village;

/**
 * Cached city-management summary for controlled villages (Phase 0/1).
 * Recomputed on dirty events or staggered failsafe — never every tick.
 */
public record CityStats(
   int population,
   int maxResidentSlots,
   int openJobSlots,
   int openBeds,
   int buildings,
   int threatLevel,
   String threatLabel,
   long computedAtGameTime
) {
   public static final CityStats EMPTY = new CityStats(0, 0, 0, 0, 0, 0, "none", -1L);

   public boolean isStale(long gameTime, long maxAgeTicks) {
      return this.computedAtGameTime < 0L || gameTime - this.computedAtGameTime > maxAgeTicks;
   }
}
