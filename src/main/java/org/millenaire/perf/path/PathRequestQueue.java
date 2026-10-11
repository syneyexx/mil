package org.millenaire.perf.path;

import java.util.HashSet;
import java.util.Set;
import org.millenaire.config.MillenaireServerConfig;

/**
 * Global cap on new entity path searches started per server tick.
 * Rejects excess starts so sync A* cannot storm the main thread.
 * <p>
 * Resets when {@link #ensureTick(long)} sees a new gameTime — call from entity path
 * creation (entity loop runs before {@code ServerTickEvent.Post}).
 */
public final class PathRequestQueue {
   private static long tickGameTime = Long.MIN_VALUE;
   private static int startsThisTick;
   private static int rejectsThisTick;
   private static int coalescedThisTick;
   private static final Set<Integer> inFlight = new HashSet<>();

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
         return false;
      }
      inFlight.add(entityId);
      startsThisTick++;
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
         + ",max="
         + maxStartsPerTick()
         + '}';
   }

   public static void resetForTest() {
      tickGameTime = Long.MIN_VALUE;
      startsThisTick = 0;
      rejectsThisTick = 0;
      coalescedThisTick = 0;
      inFlight.clear();
   }
}
