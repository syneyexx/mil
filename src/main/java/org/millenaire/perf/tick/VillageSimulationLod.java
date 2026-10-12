package org.millenaire.perf.tick;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.village.Village;

/**
 * Distance-based simulation fidelity for villages. Features stay enabled; Mid only
 * amortizes cadence so far-but-loaded villages cost less per tick.
 */
public enum VillageSimulationLod {
   NEAR,
   MID,
   FAR;

   public static boolean lodEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.villageLodEnabled.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   public static int midInterval() {
      try {
         return Math.max(1, MillenaireServerConfig.SERVER.villageLodMidInterval.getAsInt());
      } catch (IllegalStateException e) {
         return 2;
      }
   }

   /**
    * Resolve LOD for an activation-known village. Inactive villages are {@link #FAR}.
    */
   public static VillageSimulationLod resolve(ServerLevel level, Village village) {
      if (village == null || level == null) {
         return FAR;
      }
      if (!village.isActive()) {
         return FAR;
      }
      if (!lodEnabled()) {
         return NEAR;
      }
      double nearestSq = nearestPlayerDistSq(level, village);
      double nearRadius = keepNearRadius();
      if (nearestSq <= nearRadius * nearRadius) {
         return NEAR;
      }
      return MID;
   }

   public static double nearestPlayerDistSq(ServerLevel level, Village village) {
      if (village == null || village.getCenter() == null) {
         return Double.MAX_VALUE;
      }
      BlockPos c = village.getCenter();
      return LevelTickCache.nearestPlayerDistSqToBlock(level, c.getX(), c.getY(), c.getZ());
   }

   private static double keepNearRadius() {
      try {
         return MillenaireServerConfig.SERVER.keepActiveRadius.getAsInt();
      } catch (IllegalStateException e) {
         return 200.0;
      }
   }

   /**
    * Whether a Mid village should run its full {@code Village.tick} this game tick.
    * Near always; Far never (background only).
    */
   public boolean shouldRunFullTick(long gameTime, int villageHash) {
      if (this == NEAR) {
         return true;
      }
      if (this == FAR) {
         return false;
      }
      int interval = midInterval();
      int stagger = Math.floorMod(villageHash, interval);
      return Math.floorMod(gameTime, interval) == stagger;
   }

   /** Stable hash from village UUID bits. */
   public static int villageHash(Village village) {
      if (village == null || village.getId() == null) {
         return 0;
      }
      long mix = village.getId().uuid().getMostSignificantBits() ^ village.getId().uuid().getLeastSignificantBits();
      return (int)mix;
   }
}
