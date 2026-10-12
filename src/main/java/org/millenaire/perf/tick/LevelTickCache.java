package org.millenaire.perf.tick;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Per-tick, per-level hot values shared across villages/villagers so each entity does not
 * re-walk {@code level.players()} or re-query day/night.
 */
public final class LevelTickCache {
   private static ResourceKey<Level> cachedDimension;
   private static long cachedGameTime = Long.MIN_VALUE;
   private static boolean night;
   private static long dayTimeInCycle;
   private static double[] playerXyz = new double[0];
   private static int playerCount;

   private LevelTickCache() {
   }

   public static void ensure(ServerLevel level) {
      if (level == null) {
         return;
      }
      long gameTime = level.getGameTime();
      ResourceKey<Level> dim = level.dimension();
      if (cachedGameTime == gameTime && dim.equals(cachedDimension)) {
         return;
      }
      cachedGameTime = gameTime;
      cachedDimension = dim;
      night = !level.isDay();
      dayTimeInCycle = level.getDayTime() % 24000L;
      List<ServerPlayer> players = level.players();
      playerCount = players.size();
      int need = playerCount * 3;
      if (playerXyz.length < need) {
         playerXyz = new double[Math.max(need, 24)];
      }
      for (int i = 0; i < playerCount; i++) {
         ServerPlayer p = players.get(i);
         int base = i * 3;
         playerXyz[base] = p.getX();
         playerXyz[base + 1] = p.getY();
         playerXyz[base + 2] = p.getZ();
      }
   }

   public static boolean isNight(ServerLevel level) {
      ensure(level);
      return night;
   }

   public static long dayTimeInCycle(ServerLevel level) {
      ensure(level);
      return dayTimeInCycle;
   }

   /** Squared horizontal+vertical distance from {@code entity} to nearest player, or {@link Double#MAX_VALUE}. */
   public static double nearestPlayerDistSq(ServerLevel level, Entity entity) {
      ensure(level);
      if (playerCount <= 0 || entity == null) {
         return Double.MAX_VALUE;
      }
      double ex = entity.getX();
      double ey = entity.getY();
      double ez = entity.getZ();
      double nearest = Double.MAX_VALUE;
      for (int i = 0; i < playerCount; i++) {
         int base = i * 3;
         double dx = playerXyz[base] - ex;
         double dy = playerXyz[base + 1] - ey;
         double dz = playerXyz[base + 2] - ez;
         double d = dx * dx + dy * dy + dz * dz;
         if (d < nearest) {
            nearest = d;
         }
      }
      return nearest;
   }

   /** Squared distance from block centre (x+0.5,y+0.5,z+0.5) to nearest player. */
   public static double nearestPlayerDistSqToBlock(ServerLevel level, int blockX, int blockY, int blockZ) {
      ensure(level);
      if (playerCount <= 0) {
         return Double.MAX_VALUE;
      }
      double ex = blockX + 0.5;
      double ey = blockY + 0.5;
      double ez = blockZ + 0.5;
      double nearest = Double.MAX_VALUE;
      for (int i = 0; i < playerCount; i++) {
         int base = i * 3;
         double dx = playerXyz[base] - ex;
         double dy = playerXyz[base + 1] - ey;
         double dz = playerXyz[base + 2] - ez;
         double d = dx * dx + dy * dy + dz * dz;
         if (d < nearest) {
            nearest = d;
         }
      }
      return nearest;
   }

   public static void clear() {
      cachedDimension = null;
      cachedGameTime = Long.MIN_VALUE;
      playerCount = 0;
   }

   /** Test helper. */
   public static void resetForTest() {
      clear();
   }

   @Nullable
   static ResourceKey<Level> cachedDimensionForTest() {
      return cachedDimension;
   }
}
