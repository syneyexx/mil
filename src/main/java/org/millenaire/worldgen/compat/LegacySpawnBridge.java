package org.millenaire.worldgen.compat;

import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.worldgen.WorldgenEngine;

/**
 * Compatibility helpers between Phase 4 worldgen and legacy/manual spawn paths.
 */
public final class LegacySpawnBridge {
   private LegacySpawnBridge() {
   }

   /**
    * Natural enqueue into VillageSpawnQueue should be skipped when the Phase 4 engine owns generation
    * and legacy fallback is disabled.
    */
   public static boolean suppressLegacyNaturalEnqueue() {
      if (!WorldgenEngine.isEnabled()) {
         return false;
      }
      try {
         return !Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenLegacyFallback.get());
      } catch (IllegalStateException e) {
         return false;
      }
   }

   /**
    * Manual /millenaire spawn* commands always use live-world VillageSpawner APIs.
    */
   public static boolean useLiveWorldPlacementForManualSpawn() {
      return true;
   }

   public static boolean legacyQueueStillAuthoritative() {
      try {
         return !Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenEngineEnabled.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }
}
