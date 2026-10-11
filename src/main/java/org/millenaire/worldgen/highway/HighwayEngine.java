package org.millenaire.worldgen.highway;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.millenaire.worldgen.highway.travel.RoadJourneyScheduler;

/**
 * Orchestrates sand highway discovery, planning, materialization, and travelers.
 */
public final class HighwayEngine {
   private HighwayEngine() {
   }

   public static boolean isEnabled() {
      return HighwayDiscoveryService.isEnabled();
   }

   public static void enqueueChunk(ServerLevel level, ChunkPos chunk) {
      HighwayDiscoveryService.enqueueChunk(level, chunk);
   }

   public static void tick(ServerLevel overworld) {
      if (!isEnabled() || overworld == null || overworld.dimension() != Level.OVERWORLD) {
         return;
      }
      HighwayDiscoveryService.tick(overworld);
      HighwayMaterializer.tick(overworld);
      RoadJourneyScheduler.tick(overworld);
   }
}
