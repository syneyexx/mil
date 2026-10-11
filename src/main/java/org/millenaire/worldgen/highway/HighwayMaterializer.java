package org.millenaire.worldgen.highway;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.perf.compute.ComputeOffload;
import org.millenaire.perf.compute.MillenaireComputePool;
import org.millenaire.worldgen.materialize.BridgeColumnProgress;
import org.millenaire.worldgen.materialize.BridgePlacementHelper;
import org.millenaire.worldgen.materialize.MaterializationTickBudget;
import org.millenaire.worldgen.materialize.RegisteredRoadIndex;
import org.millenaire.worldgen.materialize.RoadPlacementGuard;
import org.millenaire.worldgen.net.RegionalRoadPlanner;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;
import org.slf4j.Logger;

/**
 * Places 4-wide sand highways with bridges and tunnels. Never force-loads chunks.
 */
public final class HighwayMaterializer {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_ROUTES_PER_TICK = 2;
   private static final int MAX_CELLS_SCAN = 64;
   private static boolean lookupBound;
   private static volatile boolean asyncPlanInFlight;
   private static volatile long asyncPlanGeneration;

   private HighwayMaterializer() {
   }

   public static void tick(ServerLevel level) {
      if (!HighwayDiscoveryService.isEnabled() || level == null) {
         return;
      }
      ensureLookupBound();
      HighwayNodeSavedData nodes = HighwayNodeSavedData.get(level);
      HighwayRouteSavedData routes = HighwayRouteSavedData.get(level);

      maybePlanLinks(level, nodes, routes);

      List<HighwayRoute> ordered = List.copyOf(routes.allRoutes());
      if (ordered.isEmpty()) {
         return;
      }
      int start = routes.roundRobinCursor() % ordered.size();
      int processed = 0;
      for (int i = 0; i < ordered.size() && processed < MAX_ROUTES_PER_TICK; i++) {
         if (!MaterializationTickBudget.hasTimeAndOps()) {
            break;
         }
         HighwayRoute route = ordered.get((start + i) % ordered.size());
         if (route.status() == HighwayRoute.Status.CONNECTED || route.status() == HighwayRoute.Status.FAILED) {
            continue;
         }
         advanceRoute(level, nodes, route);
         routes.setDirty();
         processed++;
      }
      routes.setRoundRobinCursor(start + processed);
   }

   private static void maybePlanLinks(ServerLevel level, HighwayNodeSavedData nodes, HighwayRouteSavedData routes) {
      if (ComputeOffload.highwayPlan()) {
         if (asyncPlanInFlight) {
            return;
         }
         HighwayPlanSnapshot snapshot = HighwayPlanSnapshot.capture(level, nodes, routes);
         long generation = ++asyncPlanGeneration;
         asyncPlanInFlight = true;
         boolean submitted = MillenaireComputePool.submit(() -> {
            List<HighwayRoute> planned = HighwayGraphPlanner.planFromSnapshot(snapshot);
            return new HighwayPlanComputeResult(planned, generation);
         });
         if (!submitted) {
            asyncPlanInFlight = false;
            List<HighwayRoute> planned = HighwayGraphPlanner.planNewLinks(level, nodes, routes);
            for (HighwayRoute route : planned) {
               routes.put(route);
               LOGGER.debug("[Millenaire] Highway planned {} -> {} ({} cells)", route.fromNodeKey(), route.toNodeKey(), route.waypoints().size());
            }
         }
         return;
      }
      List<HighwayRoute> planned = HighwayGraphPlanner.planNewLinks(level, nodes, routes);
      for (HighwayRoute route : planned) {
         routes.put(route);
         LOGGER.debug("[Millenaire] Highway planned {} -> {} ({} cells)", route.fromNodeKey(), route.toNodeKey(), route.waypoints().size());
      }
   }

   /** Called from {@link HighwayPlanComputeResult#apply}; returns false when stale. */
   public static boolean completeAsyncPlan(long generation) {
      if (generation != asyncPlanGeneration) {
         return false;
      }
      asyncPlanInFlight = false;
      return true;
   }

   public static void onServerStopping() {
      asyncPlanInFlight = false;
      asyncPlanGeneration++;
   }

   private static void ensureLookupBound() {
      if (!lookupBound) {
         RegisteredRoadIndex.bindHighwayLookup((lvl, pos) -> HighwayRouteSavedData.get(lvl).isRegisteredCell(pos));
         lookupBound = true;
      }
   }

   private static void advanceRoute(ServerLevel level, HighwayNodeSavedData nodes, HighwayRoute route) {
      BlockState path = HighwayPathMaterial.resolve();
      boolean bridges = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenGenerateBridges.get());
      boolean tunnels = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.highwayTunnels.get());
      int width = MillenaireServerConfig.SERVER.highwayWidth.getAsInt();
      SettlementPlan owningPlan = owningPlan(level, nodes, route);
      BlockPos previous = route.lastPlacedSurface();
      boolean waitingChunk = false;
      int scanned = 0;

      for (int i = route.cursor(); i < route.waypoints().size() && scanned < MAX_CELLS_SCAN; i++) {
         if (!MaterializationTickBudget.hasTimeAndOps()) {
            route.setStatus(HighwayRoute.Status.PARTIAL);
            route.setBlockReason("budget_exhausted");
            return;
         }
         BlockPos p = route.waypoints().get(i);
         scanned++;
         ChunkPos cp = new ChunkPos(p);
         if (!level.hasChunk(cp.x, cp.z)) {
            waitingChunk = true;
            route.setCursor(i);
            break;
         }

         int surfaceY = walkableSurfaceY(level, p.getX(), p.getZ());
         BlockPos surface = new BlockPos(p.getX(), surfaceY, p.getZ());
         int fwdX = 0;
         int fwdZ = 0;
         if (previous != null) {
            fwdX = Integer.signum(surface.getX() - previous.getX());
            fwdZ = Integer.signum(surface.getZ() - previous.getZ());
         } else if (i + 1 < route.waypoints().size()) {
            BlockPos next = route.waypoints().get(i + 1);
            fwdX = Integer.signum(next.getX() - p.getX());
            fwdZ = Integer.signum(next.getZ() - p.getZ());
         }
         if (fwdX == 0 && fwdZ == 0) {
            fwdX = 1;
         }
         int latX = Math.abs(fwdX) >= Math.abs(fwdZ) ? 0 : 1;
         int latZ = Math.abs(fwdX) >= Math.abs(fwdZ) ? 1 : 0;

         boolean steep = previous != null
            && Math.abs(surface.getY() - previous.getY()) > RegionalRoadPlanner.maxStepRise() + 1;

         BlockState cur = level.getBlockState(surface);
         if (!cur.getFluidState().isEmpty()) {
            if (!bridges) {
               route.setStatus(HighwayRoute.Status.BLOCKED);
               route.setBlockReason("water_no_bridges");
               route.setCursor(i);
               return;
            }
            if (!placeBandBridge(level, route, surface, latX, latZ, width, path, owningPlan)) {
               return;
            }
            int deckY = route.bridgeDeckY() != Integer.MIN_VALUE ? route.bridgeDeckY() : surface.getY();
            surface = new BlockPos(surface.getX(), deckY, surface.getZ());
            route.setBridgeSupportCursor(0);
            route.setBridgeDeckY(Integer.MIN_VALUE);
         } else if (steep && tunnels && shouldTunnel(level, previous, surface)) {
            int grade = route.gradeY() != Integer.MIN_VALUE
               ? route.gradeY()
               : (previous != null ? previous.getY() : surface.getY());
            route.setGradeY(grade);
            BlockPos floor = new BlockPos(surface.getX(), grade, surface.getZ());
            TunnelPlacementHelper.Outcome tunnel = TunnelPlacementHelper.placeTunnelColumn(
               level, floor, latX, latZ, width, path, route.tunnelCursor(), owningPlan
            );
            route.setTunnelCursor(tunnel.carveCursor());
            if (!tunnel.finished()) {
               route.setStatus(tunnel.budgetExhausted() ? HighwayRoute.Status.PARTIAL : HighwayRoute.Status.BLOCKED);
               route.setBlockReason(tunnel.reason().isEmpty() ? "tunnel_incomplete" : tunnel.reason());
               route.setCursor(i);
               return;
            }
            route.setTunnelCursor(0);
            surface = floor;
            markBandCells(route, surface, latX, latZ, width);
         } else if (steep && !tunnels) {
            route.setStatus(HighwayRoute.Status.BLOCKED);
            route.setBlockReason("too_steep");
            route.setCursor(i);
            return;
         } else {
            if (!placeBandSurface(level, route, surface, latX, latZ, width, path, owningPlan)) {
               return;
            }
         }

         route.setCursor(i + 1);
         route.setLastPlacedSurface(surface);
         previous = surface;
         route.setStatus(HighwayRoute.Status.PARTIAL);
         route.setBlockReason("");
      }

      if (route.cursor() >= route.waypoints().size()) {
         route.setStatus(HighwayRoute.Status.CONNECTED);
         route.setBlockReason("");
         route.setBridgeSupportCursor(0);
         route.setBridgeDeckY(Integer.MIN_VALUE);
         route.setTunnelCursor(0);
         route.setGradeY(Integer.MIN_VALUE);
      } else if (waitingChunk) {
         route.setStatus(HighwayRoute.Status.WAITING_CHUNKS);
         route.setBlockReason("chunk_not_loaded");
      }
   }

   private static boolean shouldTunnel(ServerLevel level, BlockPos previous, BlockPos surface) {
      if (previous == null) {
         return false;
      }
      int midX = (previous.getX() + surface.getX()) / 2;
      int midZ = (previous.getZ() + surface.getZ()) / 2;
      if (!level.hasChunk(midX >> 4, midZ >> 4)) {
         return true;
      }
      int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, midX, midZ);
      int grade = previous.getY();
      return top > grade + 4;
   }

   private static boolean placeBandSurface(
      ServerLevel level,
      HighwayRoute route,
      BlockPos center,
      int latX,
      int latZ,
      int width,
      BlockState path,
      SettlementPlan owningPlan
   ) {
      for (int offset : TunnelPlacementHelper.laneOffsets(width)) {
         BlockPos at = new BlockPos(center.getX() + latX * offset, center.getY(), center.getZ() + latZ * offset);
         int y = walkableSurfaceY(level, at.getX(), at.getZ());
         at = new BlockPos(at.getX(), y, at.getZ());
         if (!level.hasChunk(at.getX() >> 4, at.getZ() >> 4)) {
            route.setStatus(HighwayRoute.Status.WAITING_CHUNKS);
            route.setBlockReason("chunk_not_loaded");
            return false;
         }
         RoadPlacementGuard.Result permit =
            RoadPlacementGuard.evaluate(level, at, RoadPlacementGuard.Intent.PATH_SURFACE, owningPlan, path);
         if (permit.deferred()) {
            route.setStatus("chunk_not_loaded".equals(permit.reason())
               ? HighwayRoute.Status.WAITING_CHUNKS
               : HighwayRoute.Status.PARTIAL);
            route.setBlockReason(permit.reason());
            return false;
         }
         if (!permit.allowed()) {
            // Centreline must place; sides may skip protected cells.
            if (offset == 0) {
               route.setStatus(HighwayRoute.Status.BLOCKED);
               route.setBlockReason(permit.reason().isEmpty() ? "obstacle_protected" : permit.reason());
               return false;
            }
            continue;
         }
         if (!permit.alreadyPath()) {
            if (!MaterializationTickBudget.tryConsumeOp()) {
               route.setStatus(HighwayRoute.Status.PARTIAL);
               route.setBlockReason("budget_exhausted");
               return false;
            }
            level.setBlock(at, path, 2);
         }
         route.completedCells().add(HighwayRoute.packCell(at.getX(), at.getZ()));
      }
      return true;
   }

   private static boolean placeBandBridge(
      ServerLevel level,
      HighwayRoute route,
      BlockPos center,
      int latX,
      int latZ,
      int width,
      BlockState path,
      SettlementPlan owningPlan
   ) {
      int deckY = route.bridgeDeckY();
      for (int offset : TunnelPlacementHelper.laneOffsets(width)) {
         BlockPos col = new BlockPos(center.getX() + latX * offset, center.getY(), center.getZ() + latZ * offset);
         UUID bridgeKey = BridgePlacementHelper.columnKey(route.routeId(), col);
         BridgeColumnProgress.SingleColumn column =
            new BridgeColumnProgress.SingleColumn(route.bridgeSupportCursor(), deckY);
         BridgePlacementHelper.Outcome bridge = BridgePlacementHelper.placeBridge(
            level,
            new BlockPos(col.getX(), deckY != Integer.MIN_VALUE ? deckY : col.getY(), col.getZ()),
            path,
            column,
            bridgeKey,
            true,
            owningPlan
         );
         route.setBridgeSupportCursor(bridge.supportCursor());
         if (bridge.deckY() != Integer.MIN_VALUE) {
            deckY = bridge.deckY();
            route.setBridgeDeckY(deckY);
         }
         if (!bridge.finished()) {
            route.setStatus(bridge.budgetExhausted() ? HighwayRoute.Status.PARTIAL : HighwayRoute.Status.BLOCKED);
            route.setBlockReason(bridge.reason().isEmpty() ? "bridge_incomplete" : bridge.reason());
            return false;
         }
         int y = bridge.deckY() != Integer.MIN_VALUE ? bridge.deckY() : col.getY();
         route.completedCells().add(HighwayRoute.packCell(col.getX(), col.getZ()));
         route.setLastPlacedSurface(new BlockPos(col.getX(), y, col.getZ()));
      }
      return true;
   }

   private static void markBandCells(HighwayRoute route, BlockPos center, int latX, int latZ, int width) {
      for (int offset : TunnelPlacementHelper.laneOffsets(width)) {
         route.completedCells().add(HighwayRoute.packCell(center.getX() + latX * offset, center.getZ() + latZ * offset));
      }
   }

   private static SettlementPlan owningPlan(ServerLevel level, HighwayNodeSavedData nodes, HighwayRoute route) {
      for (String key : List.of(route.fromNodeKey(), route.toNodeKey())) {
         HighwayNode node = nodes.get(key);
         if (node != null && node.planId() != null) {
            SettlementPlan plan = SettlementPlanSavedData.get(level).getPlan(node.planId());
            if (plan != null) {
               return plan;
            }
         }
      }
      return null;
   }

   private static int walkableSurfaceY(ServerLevel level, int x, int z) {
      int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
      int min = level.getMinBuildHeight() + 1;
      for (int y = top; y >= min; y--) {
         BlockState state = level.getBlockState(new BlockPos(x, y, z));
         if (state.isAir()) {
            continue;
         }
         if (!state.getFluidState().isEmpty()) {
            return y;
         }
         if (state.canOcclude() || RoadPlacementGuard.isRegisteredPath(state)) {
            return y;
         }
      }
      return Math.max(min, top);
   }
}
