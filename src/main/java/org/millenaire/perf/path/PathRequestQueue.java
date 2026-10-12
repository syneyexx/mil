package org.millenaire.perf.path;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.millenaire.config.MillenaireServerConfig;

/**
 * Global cap on new entity path searches started per server tick.
 * Rejects excess starts so sync A* cannot storm the main thread.
 * <p>
 * Rejected starts are not retained: callers (e.g. {@code MillPathNavigation}) return a null path and the nav
 * manager's own cooldown/backoff retries later, so a full queue never turns into unbounded sync A*.
 * Cumulative counters survive tick resets for diagnostics.
 * <p>
 * Resets when {@link #ensureTick(long)} sees a new gameTime — call from entity path
 * creation (entity loop runs before {@code ServerTickEvent.Post}).
 */
public final class PathRequestQueue {
   private static long tickGameTime = Long.MIN_VALUE;
   private static int startsThisTick;
   private static int rejectsThisTick;
   private static int coalescedThisTick;
   private static long totalStarts;
   private static long totalRejects;
   private static final IntOpenHashSet inFlight = new IntOpenHashSet();

   private PathRequestQueue() {
   }

   public static void ensureTick(long gameTime) {
      if (tickGameTime != gameTime) {
         tickGameTime = gameTime;
         startsThisTick = 0;
         rejectsThisTick = 0;
         coalescedThisTick = 0;
         inFlight.clear();
      }
   }

   public static int maxStartsPerTick() {
      try {
         return Math.max(1, MillenaireServerConfig.SERVER.pathMaxStartsPerTick.getAsInt());
      } catch (IllegalStateException e) {
         return 2;
      }
   }

   /**
    * Attempt to begin a path search for {@code entityId}.
    * @return true if the search may proceed synchronously this tick
    */
   public static boolean tryBeginSearch(int entityId, long gameTime) {
      ensureTick(gameTime);
      if (inFlight.contains(entityId)) {
         coalescedThisTick++;
         return false;
      }
      if (startsThisTick >= maxStartsPerTick()) {
         rejectsThisTick++;
         totalRejects++;
         return false;
      }
      inFlight.add(entityId);
      startsThisTick++;
      totalStarts++;
      return true;
   }

   public static void completeSearch(int entityId) {
      inFlight.remove(entityId);
   }

   public static int startsThisTick() {
      return startsThisTick;
   }

   public static int rejectsThisTick() {
      return rejectsThisTick;
   }

   public static long totalStarts() {
      return totalStarts;
   }

   public static long totalRejects() {
      return totalRejects;
   }

   public static int coalescedThisTick() {
      return coalescedThisTick;
   }

   public static int inFlightCount() {
      return inFlight.size();
   }

   public static String diagnostic() {
      return "pathQ={starts="
         + startsThisTick
         + ",rejects="
         + rejectsThisTick
         + ",coalesce="
         + coalescedThisTick
         + ",inFlight="
         + inFlight.size()
         + ",totalRejects="
         + totalRejects
         + ",max="
         + maxStartsPerTick()
         + '}';
   }

   public static void resetForTest() {
      tickGameTime = Long.MIN_VALUE;
      startsThisTick = 0;
      rejectsThisTick = 0;
      coalescedThisTick = 0;
      totalStarts = 0L;
      totalRejects = 0L;
      inFlight.clear();
   }
}
