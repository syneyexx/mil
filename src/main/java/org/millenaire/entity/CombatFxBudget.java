package org.millenaire.entity;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Soft client-side gate for combat projectile trail particles.
 * Keeps trails visible nearby while thinning them under density / distance pressure.
 */
public final class CombatFxBudget {
   private static final double NEAR_DIST_SQ = 32.0 * 32.0;
   private static final double FAR_DIST_SQ = 64.0 * 64.0;
   private static long lastGameTime = Long.MIN_VALUE;
   private static int spawnedThisTick;

   private CombatFxBudget() {
   }

   public static boolean allowTrailParticle(Entity projectile) {
      if (projectile == null || !projectile.level().isClientSide) {
         return true;
      }
      List<? extends Player> players = projectile.level().players();
      if (players.isEmpty()) {
         return false;
      }
      double distSq = players.get(0).distanceToSqr(projectile);
      long gameTime = projectile.level().getGameTime();
      if (gameTime != lastGameTime) {
         lastGameTime = gameTime;
         spawnedThisTick = 0;
      }
      if (distSq > FAR_DIST_SQ) {
         return false;
      }
      int cap = distSq <= NEAR_DIST_SQ ? 48 : 24;
      if (spawnedThisTick >= cap) {
         return false;
      }
      if (distSq > NEAR_DIST_SQ && (projectile.tickCount & 1) != 0) {
         return false;
      }
      spawnedThisTick++;
      return true;
   }
}
