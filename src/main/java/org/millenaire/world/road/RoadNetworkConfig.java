package org.millenaire.world.road;

import org.millenaire.config.MillenaireServerConfig;

/** Safe accessors for road config with defaults when the config spec is not loaded. */
public final class RoadNetworkConfig {
   private RoadNetworkConfig() {
   }

   public static boolean enabled() {
      try {
         return MillenaireServerConfig.SERVER.roadsEnabled.getAsBoolean();
      } catch (Throwable t) {
         return true;
      }
   }

   public static int roadWidth() {
      try {
         return MillenaireServerConfig.SERVER.roadsWidth.getAsInt();
      } catch (Throwable t) {
         return 3;
      }
   }

   public static int maxEdgeLength() {
      try {
         return MillenaireServerConfig.SERVER.roadsMaxEdgeLength.getAsInt();
      } catch (Throwable t) {
         return 1500;
      }
   }

   public static int hardMaxEdgeLength() {
      try {
         return MillenaireServerConfig.SERVER.roadsHardMaxEdgeLength.getAsInt();
      } catch (Throwable t) {
         return 4000;
      }
   }

   public static int kNearest() {
      try {
         return MillenaireServerConfig.SERVER.roadsKNearest.getAsInt();
      } catch (Throwable t) {
         return 3;
      }
   }

   public static double loopRatio() {
      try {
         return MillenaireServerConfig.SERVER.roadsLoopRatio.get();
      } catch (Throwable t) {
         return 0.15;
      }
   }

   public static double maxMillisPerTick() {
      try {
         return MillenaireServerConfig.SERVER.roadsMaxMillisPerTick.get();
      } catch (Throwable t) {
         return 1.0;
      }
   }

   public static int maxChunksPerTick() {
      try {
         return MillenaireServerConfig.SERVER.roadsMaxChunksPerTick.getAsInt();
      } catch (Throwable t) {
         return 1;
      }
   }

   public static int maxQueueSize() {
      try {
         return MillenaireServerConfig.SERVER.roadsMaxQueueSize.getAsInt();
      } catch (Throwable t) {
         return 2048;
      }
   }

   public static double msptPauseThreshold() {
      try {
         return MillenaireServerConfig.SERVER.roadsMsptPauseThreshold.get();
      } catch (Throwable t) {
         return 45.0;
      }
   }

   public static int tunnelThreshold() {
      try {
         return MillenaireServerConfig.SERVER.roadsTunnelThreshold.getAsInt();
      } catch (Throwable t) {
         return 12;
      }
   }

   public static int maxBridgeLength() {
      try {
         return MillenaireServerConfig.SERVER.roadsMaxBridgeLength.getAsInt();
      } catch (Throwable t) {
         return 48;
      }
   }

   public static boolean bridgesEnabled() {
      try {
         return MillenaireServerConfig.SERVER.roadsBridgesEnabled.getAsBoolean();
      } catch (Throwable t) {
         return true;
      }
   }

   public static boolean tunnelsEnabled() {
      try {
         return MillenaireServerConfig.SERVER.roadsTunnelsEnabled.getAsBoolean();
      } catch (Throwable t) {
         return true;
      }
   }

   public static int plannerThreads() {
      try {
         return MillenaireServerConfig.SERVER.roadsPlannerThreads.getAsInt();
      } catch (Throwable t) {
         return 1;
      }
   }
}
