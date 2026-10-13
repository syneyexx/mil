package org.millenaire.world.road;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * Orchestrates discovery-driven graph rebuilds, async planning, and budgetted building.
 */
public final class RoadNetworkService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final RoadNetworkService INSTANCE = new RoadNetworkService();

   private RoutePlanner planner;
   private final RoadBuilder builder = new RoadBuilder();
   private boolean debugParticles;
   private boolean graphNeedsRebuild;

   private RoadNetworkService() {
   }

   public static RoadNetworkService get() {
      return INSTANCE;
   }

   public RoadBuilder getBuilder() {
      return this.builder;
   }

   public void setDebugParticles(boolean on) {
      this.debugParticles = on;
   }

   public boolean isDebugParticles() {
      return this.debugParticles;
   }

   public void onServerStarted(ServerLevel overworld) {
      shutdownPlanner();
      this.planner = new RoutePlanner(RoadNetworkConfig.plannerThreads(), 2);
      this.graphNeedsRebuild = true;
   }

   public void onServerStopping() {
      shutdownPlanner();
   }

   private void shutdownPlanner() {
      if (this.planner != null) {
         this.planner.shutdown();
         this.planner = null;
      }
   }

   public void onRegistryDirty(ServerLevel level) {
      this.graphNeedsRebuild = true;
   }

   public void onChunkLoad(ServerLevel level, ChunkPos pos) {
      if (!RoadNetworkConfig.enabled() || level.dimension() != Level.OVERWORLD) {
         return;
      }
      RoadNetworkSavedData data = RoadNetworkSavedData.get(level);
      long key = pos.toLong();
      for (Long edgeId : data.getChunkIndex().edgesForChunk(key)) {
         RoadEdge edge = data.getGraph().getEdge(edgeId);
         if (edge == null || edge.polyline() == null) {
            continue;
         }
         if (edge.state() == EdgeState.PLANNED || edge.state() == EdgeState.BUILDING || edge.state() == EdgeState.DONE) {
            if (!edge.isChunkBuilt(key)) {
               this.builder.enqueue(level, key, edgeId);
            }
         }
      }
   }

   public void tick(ServerLevel level) {
      if (!RoadNetworkConfig.enabled() || level.dimension() != Level.OVERWORLD) {
         return;
      }
      RoadNetworkSavedData data = RoadNetworkSavedData.get(level);

      if (this.graphNeedsRebuild || data.getRegistry().isDirty()) {
         rebuildGraph(level, data);
      }

      // Apply planner results
      if (this.planner != null) {
         for (RoutePlanner.PlanResult result : this.planner.drainResults()) {
            RoadEdge edge = data.getGraph().getEdge(result.edgeId());
            if (edge == null) {
               continue;
            }
            if (result.success() && result.polyline() != null) {
               edge.setPolyline(result.polyline(), result.segmentKinds());
               data.getChunkIndex().indexEdgeFromPolyline(edge.id(), result.polyline());
               data.setDirty();
               LOGGER.debug("[Roads] Planned edge {} ({} points)", edge.id(), result.polyline().length / 3);
            } else {
               long backoff = backoffTicks(edge.failCount());
               edge.recordFailure(level.getGameTime() + backoff);
               data.setDirty();
            }
         }

         // Submit pending edges
         submitPending(level, data);
      }

      this.builder.tick(level, data);

      if (this.debugParticles && level.getGameTime() % 10L == 0L) {
         debugShowNearby(level, data);
      }
   }

   private void rebuildGraph(ServerLevel level, RoadNetworkSavedData data) {
      data.getGraph()
         .rebuild(
            data.getRegistry().getAll(),
            RoadNetworkConfig.kNearest(),
            RoadNetworkConfig.maxEdgeLength(),
            RoadNetworkConfig.hardMaxEdgeLength(),
            RoadNetworkConfig.loopRatio()
         );
      data.getRegistry().clearDirty();
      this.graphNeedsRebuild = false;
      data.rebuildChunkIndex();
      data.setDirty();
      LOGGER.info("[Roads] Graph rebuilt: {} nodes, {} edges", data.getRegistry().size(), data.getGraph().size());
   }

   private void submitPending(ServerLevel level, RoadNetworkSavedData data) {
      if (this.planner == null || !this.planner.canSubmit()) {
         return;
      }
      long now = level.getGameTime();
      for (RoadEdge edge : data.getGraph().getEdges()) {
         if (!this.planner.canSubmit()) {
            break;
         }
         if (edge.state() == EdgeState.FAILED && now >= edge.nextRetryGameTime() && edge.failCount() < 4) {
            edge.resetForRetry();
         }
         if (edge.state() != EdgeState.PENDING) {
            continue;
         }
         RoadNode a = data.getRegistry().get(edge.nodeA());
         RoadNode b = data.getRegistry().get(edge.nodeB());
         if (a == null || b == null) {
            continue;
         }
         RoutePlanner.RoadPlanConfig cfg = new RoutePlanner.RoadPlanConfig();
         cfg.maxBridgeLength = RoadNetworkConfig.maxBridgeLength();
         cfg.tunnelThreshold = RoadNetworkConfig.tunnelThreshold();
         this.planner.submit(level, edge, a, b, data.getRegistry().getAll(), cfg);
      }
   }

   private static long backoffTicks(int failCount) {
      return switch (failCount) {
         case 0 -> 20L * 60;
         case 1 -> 20L * 60 * 5;
         case 2 -> 20L * 60 * 30;
         default -> Long.MAX_VALUE / 4;
      };
   }

   private void debugShowNearby(ServerLevel level, RoadNetworkSavedData data) {
      // Particle debug is best-effort; skip if no players nearby
      if (level.players().isEmpty()) {
         return;
      }
      BlockPos player = level.players().get(0).blockPosition();
      for (RoadEdge edge : data.getGraph().getEdges()) {
         int[] poly = edge.polyline();
         if (poly == null) {
            continue;
         }
         for (int i = 0; i < poly.length; i += 9) {
            int x = poly[i];
            int y = poly[i + 1] + 1;
            int z = poly[i + 2];
            if (Math.abs(x - player.getX()) > 128 || Math.abs(z - player.getZ()) > 128) {
               continue;
            }
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, x + 0.5, y, z + 0.5, 1, 0, 0, 0, 0);
         }
      }
   }

   public void rebuildAll(ServerLevel level) {
      RoadNetworkSavedData data = RoadNetworkSavedData.get(level);
      for (RoadEdge edge : data.getGraph().getEdges()) {
         edge.resetFully();
      }
      this.graphNeedsRebuild = true;
      data.setDirty();
   }

   public void rebuildEdge(ServerLevel level, long edgeId) {
      RoadNetworkSavedData data = RoadNetworkSavedData.get(level);
      RoadEdge edge = data.getGraph().getEdge(edgeId);
      if (edge != null) {
         edge.resetFully();
         data.setDirty();
      }
   }

   public List<String> infoLines(ServerLevel level) {
      RoadNetworkSavedData data = RoadNetworkSavedData.get(level);
      List<String> lines = new ArrayList<>();
      lines.add("Roads enabled=" + RoadNetworkConfig.enabled());
      lines.add("Nodes=" + data.getRegistry().size() + " Edges=" + data.getGraph().size());
      lines.add(
         "States: PENDING="
            + data.getGraph().countByState(EdgeState.PENDING)
            + " PLANNING="
            + data.getGraph().countByState(EdgeState.PLANNING)
            + " PLANNED="
            + data.getGraph().countByState(EdgeState.PLANNED)
            + " BUILDING="
            + data.getGraph().countByState(EdgeState.BUILDING)
            + " DONE="
            + data.getGraph().countByState(EdgeState.DONE)
            + " FAILED="
            + data.getGraph().countByState(EdgeState.FAILED)
      );
      lines.add("BuildQueue=" + this.builder.queueSize() + " lastTickMs=" + String.format("%.2f", this.builder.lastTickMillis()));
      lines.add("ChunkIndex=" + data.getChunkIndex().chunkCount() + " paused=" + this.builder.isPaused());
      return lines;
   }
}
