package org.millenaire.world.road;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Optional hook for claim mods / spawn protection. Default implementation checks spawn protection only.
 */
@FunctionalInterface
public interface ProtectedAreaProvider {
   boolean isProtected(ServerLevel level, BlockPos pos);

   static ProtectedAreaProvider spawnProtection() {
      return (level, pos) -> {
         BlockPos spawn = level.getSharedSpawnPos();
         int radius;
         try {
            radius = org.millenaire.config.MillenaireServerConfig.SERVER.spawnProtectionRadius.getAsInt();
         } catch (IllegalStateException e) {
            radius = 250;
         }
         if (radius <= 0) {
            return false;
         }
         int dx = pos.getX() - spawn.getX();
         int dz = pos.getZ() - spawn.getZ();
         return dx * dx + dz * dz <= radius * radius;
      };
   }
}
