package org.millenaire.worldgen.net;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.worldgen.materialize.BridgePlacementHelper;
import org.millenaire.worldgen.materialize.MaterializationTickBudget;
import org.millenaire.worldgen.plan.PlannedRoad;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;
import org.millenaire.worldgen.plan.SettlementPlanStatus;
import org.slf4j.Logger;

/**
 * Physical placement for regional connectors planned by {@link RegionalRoadPlanner}.
 * Places continuous adjacent cells between settlements. Never force-loads chunks.
 * CONNECTED requires every necessary cell and bridge to finish — never a coarse waypoint sample alone.
 */
public final class RegionalRoadMaterializer {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_ROUTES_PER_TICK = 2;
   private static final int MAX_CELLS_SCAN = 96;
   private static final int MAX_PLAN_LINKS_PER_TICK = 4;
   private static final int MAX_PLAN_PAIR_SCANS = 24;

   public enum RouteStatus {
      PLANNED,
      PARTIAL,
      WAITING_CHUNKS,
      BLOCKED,
      CONNECTED,
      FAILED,
      RECOVERY_REQUIRED
   }

   public static final class RouteProgress {
      public final UUID routeId;
      public final UUID fromPlanId;
      public final UUID toPlanId;
      public final List<BlockPos> waypoints;
      public final Set<Long> completedChunks = new HashSet<>();
      public RouteStatus status = RouteStatus.PLANNED;
      public String blockReason = "";
      public int cursor;
      public int bridgeSupportCursor;
      public int bridgeDeckY = Integer.MIN_VALUE;
      public boolean continuousExpanded;
      public boolean coarseOnlyLegacy;

      RouteProgress(UUID routeId, UUID fromPlanId, UUID toPlanId, List<BlockPos> waypoints) {
         this.routeId = routeId;
         this.fromPlanId = fromPlanId;
         this.toPlanId = toPlanId;
         this.waypoints = new ArrayList<>(waypoints);
         this.continuousExpanded = true;
      }

      CompoundTag save() {
         CompoundTag tag = new CompoundTag();
         tag.putUUID("id", this.routeId);
         tag.putUUID("from", this.fromPlanId);
         tag.putUUID("to", this.toPlanId);
         tag.putString("status", this.status.name());
         tag.putString("block", this.blockReason);
         tag.putInt("cursor", this.cursor);
         tag.putInt("bridgeSupport", this.bridgeSupportCursor);
         tag.putInt("bridgeDeckY", this.bridgeDeckY);
         tag.putBoolean("continuous", this.continuousExpanded);
         tag.putBoolean("coarseLegacy", this.coarseOnlyLegacy);
         ListTag pts = new ListTag();
         for (BlockPos p : this.waypoints) {
            CompoundTag pt = new CompoundTag();
            pt.putInt("x", p.getX());
            pt.putInt("y", p.getY());
            pt.putInt("z", p.getZ());
            pts.add(pt);
         }
         tag.put("waypoints", pts);
         ListTag done = new ListTag();
         for (Long c : this.completedChunks) {
            CompoundTag ct = new CompoundTag();
            ct.putLong("c", c);
            done.add(ct);
         }
         tag.put("doneChunks", done);
         return tag;
      }

      static RouteProgress load(CompoundTag tag) {
         List<BlockPos> pts = new ArrayList<>();
         ListTag list = tag.getList("waypoints", Tag.TAG_COMPOUND);
         for (int i = 0; i < list.size(); i++) {
            CompoundTag pt = list.getCompound(i);
            pts.add(new BlockPos(pt.getInt("x"), pt.getInt("y"), pt.getInt("z")));
         }
         RouteProgress progress = new RouteProgress(tag.getUUID("id"), tag.getUUID("from"), tag.getUUID("to"), pts);
         try {
            progress.status = RouteStatus.valueOf(tag.getString("status"));
         } catch (Exception ignored) {
         }
         progress.blockReason = tag.getString("block");
         progress.cursor = tag.getInt("cursor");
         progress.bridgeSupportCursor = tag.getInt("bridgeSupport");
         progress.bridgeDeckY = tag.contains("bridgeDeckY") ? tag.getInt("bridgeDeckY") : Integer.MIN_VALUE;
         progress.continuousExpanded = tag.contains("continuous") && tag.getBoolean("continuous");
         progress.coarseOnlyLegacy = tag.getBoolean("coarseLegacy");
         ListTag done = tag.getList("doneChunks", Tag.TAG_COMPOUND);
         for (int i = 0; i < done.size(); i++) {
            progress.completedChunks.add(done.getCompound(i).getLong("c"));
         }
         return progress;
      }

      void ensureContinuousPath() {
         if (this.continuousExpanded || this.waypoints.size() < 2) {
            this.continuousExpanded = true;
            return;
         }
         // Detect coarse-only spacing (~32) and expand without destroying progress cursor mapping.
         long maxStep = 0;
         for (int i = 1; i < this.waypoints.size(); i++) {
            BlockPos a = this.waypoints.get(i - 1);
            BlockPos b = this.waypoints.get(i);
            maxStep = Math.max(maxStep, Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ())));
         }
         if (maxStep <= 1) {
            this.continuousExpanded = true;
            return;
         }
         BlockPos resumeAt = this.cursor > 0 && this.cursor <= this.waypoints.size()
            ? this.waypoints.get(Math.min(this.cursor, this.waypoints.size()) - 1)
            : this.waypoints.getFirst();
         List<BlockPos> expanded = RegionalRoadPlanner.expandContinuous(this.waypoints);
         this.waypoints.clear();
         this.waypoints.addAll(expanded);
         this.continuousExpanded = true;
         this.coarseOnlyLegacy = true;
         if (this.status == RouteStatus.CONNECTED) {
            this.status = RouteStatus.RECOVERY_REQUIRED;
            this.blockReason = "coarse_only_connected_unverified";
         }
         // Remap cursor to nearest expanded cell at/after previous position.
         int mapped = 0;
         for (int i = 0; i < this.waypoints.size(); i++) {
            BlockPos p = this.waypoints.get(i);
            if (p.getX() == resumeAt.getX() && p.getZ() == resumeAt.getZ()) {
               mapped = i;
               break;
            }
            if (Math.abs(p.getX() - resumeAt.getX()) + Math.abs(p.getZ() - resumeAt.getZ())
               < Math.abs(this.waypoints.get(mapped).getX() - resumeAt.getX())
                  + Math.abs(this.waypoints.get(mapped).getZ() - resumeAt.getZ())) {
               mapped = i;
            }
         }
         // For recovery, do not rebuild already-placed cells — resume forward only.
         this.cursor = mapped;
      }
   }

   private RegionalRoadMaterializer() {
   }

   public static void tick(ServerLevel level, SettlementPlanSavedData store) {
      if (!Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenGenerateRegionalRoads.get())) {
         return;
      }
      RegionalRoadSavedData roads = RegionalRoadSavedData.get(level);
      boolean dirty = ensurePlans(level, store, roads);
      int processed = 0;
      List<RouteProgress> ordered = new ArrayList<>(roads.routes().values());
      ordered.sort(Comparator.comparing(r -> r.routeId));
      if (ordered.isEmpty()) {
         if (dirty) {
            roads.setDirty();
         }
         return;
      }
      int start = Math.floorMod(roads.roundRobinCursor(), ordered.size());
      for (int i = 0; i < ordered.size() && processed < MAX_ROUTES_PER_TICK; i++) {
         if (!MaterializationTickBudget.hasOps()) {
            break;
         }
         RouteProgress route = ordered.get((start + i) % ordered.size());
         if (route.status == RouteStatus.CONNECTED || route.status == RouteStatus.FAILED) {
            continue;
         }
         if (route.status == RouteStatus.RECOVERY_REQUIRED) {
            route.ensureContinuousPath();
            route.status = RouteStatus.PARTIAL;
            dirty = true;
         }
         route.ensureContinuousPath();
         RouteStatus before = route.status;
         int beforeCursor = route.cursor;
         advanceRoute(level, store, route);
         if (route.status != before || route.cursor != beforeCursor) {
            dirty = true;
         }
         processed++;
         roads.setRoundRobinCursor(start + i + 1);
      }
      if (dirty || processed > 0) {
         roads.setDirty();
      }
   }

   private static boolean ensurePlans(ServerLevel level, SettlementPlanSavedData store, RegionalRoadSavedData roads) {
      List<SettlementPlan> active = new ArrayList<>();
      for (SettlementPlan plan : store.allPlans()) {
         if (plan.status() == SettlementPlanStatus.ACTIVE
            || plan.status() == SettlementPlanStatus.PHYSICAL_COMPLETE
            || plan.status() == SettlementPlanStatus.MATERIALIZING) {
            active.add(plan);
         }
      }
      active.sort(Comparator.comparing(SettlementPlan::planId));
      if (active.size() < 2) {
         return false;
      }
      int start = Math.floorMod(roads.planScanCursor(), active.size());
      int scanned = 0;
      int created = 0;
      boolean dirty = false;
      for (int i = 0; i < active.size() && scanned < MAX_PLAN_PAIR_SCANS && created < MAX_PLAN_LINKS_PER_TICK; i++) {
         scanned++;
         SettlementPlan from = active.get((start + i) % active.size());
         List<PlannedRoad> links = RegionalRoadPlanner.planLinks(from, active);
         for (PlannedRoad link : links) {
            if (roads.routes().containsKey(link.roadId())) {
               continue;
            }
            // Dedup by endpoint pair if an older schema used a different id mix.
            if (hasRouteBetween(roads, link.fromBuildingId(), link.toBuildingId())) {
               continue;
            }
            RouteProgress progress = new RouteProgress(link.roadId(), link.fromBuildingId(), link.toBuildingId(), link.waypoints());
            roads.routes().put(link.roadId(), progress);
            created++;
            dirty = true;
            if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenDetailedDiagnostics.get())) {
               LOGGER.debug("[Millenaire] Planned regional road {} cells={}", link.roadId(), link.waypoints().size());
            }
            if (created >= MAX_PLAN_LINKS_PER_TICK) {
               break;
            }
         }
      }
      roads.setPlanScanCursor(start + Math.max(1, scanned));
      return dirty;
   }

   private static boolean hasRouteBetween(RegionalRoadSavedData roads, UUID a, UUID b) {
      for (RouteProgress r : roads.routes().values()) {
         if ((r.fromPlanId.equals(a) && r.toPlanId.equals(b)) || (r.fromPlanId.equals(b) && r.toPlanId.equals(a))) {
            return true;
         }
      }
      return false;
   }

   private static void advanceRoute(ServerLevel level, SettlementPlanSavedData store, RouteProgress route) {
      if (endpointsMissing(store, route)) {
         route.status = RouteStatus.FAILED;
         route.blockReason = "endpoint_removed";
         return;
      }
      boolean bridges = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenGenerateBridges.get());
      BlockState path = Blocks.DIRT_PATH.defaultBlockState();
      int scanned = 0;
      boolean waitingChunk = false;
      boolean any = false;
      BlockPos previousSurface = null;

      for (int i = route.cursor; i < route.waypoints.size() && scanned < MAX_CELLS_SCAN; i++) {
         if (!MaterializationTickBudget.hasOps()) {
            route.status = RouteStatus.PARTIAL;
            route.blockReason = "budget_exhausted";
            return;
         }
         BlockPos p = route.waypoints.get(i);
         ChunkPos cp = new ChunkPos(p);
         scanned++;
         if (!level.hasChunk(cp.x, cp.z)) {
            waitingChunk = true;
            route.cursor = i;
            break;
         }
         int surfaceY = walkableSurfaceY(level, p.getX(), p.getZ());
         BlockPos surface = new BlockPos(p.getX(), surfaceY, p.getZ());
         if (previousSurface != null) {
            int rise = Math.abs(surface.getY() - previousSurface.getY());
            if (rise > RegionalRoadPlanner.maxStepRise() + 1) {
               // Prefer bridge only over fluid; otherwise block steep cliffs.
               BlockState at = level.getBlockState(surface);
               if (at.getFluidState().isEmpty()) {
                  route.status = RouteStatus.BLOCKED;
                  route.blockReason = "too_steep";
                  route.cursor = i;
                  return;
               }
            }
         }

         // Skip overwriting buildings / containers in loaded chunks.
         if (!BridgePlacementHelper.isSafeToOverwrite(level, surface)
            && !isExistingPath(level.getBlockState(surface))) {
            // Try adjacent rim cell within ±1 before blocking.
            BlockPos alt = findSafeAdjacent(level, surface, path);
            if (alt == null) {
               route.status = RouteStatus.BLOCKED;
               route.blockReason = "obstacle_protected";
               route.cursor = i;
               return;
            }
            surface = alt;
         }

         BlockState cur = level.getBlockState(surface);
         if (!cur.getFluidState().isEmpty()) {
            if (!bridges) {
               route.status = RouteStatus.BLOCKED;
               route.blockReason = "water_no_bridges";
               route.cursor = i;
               return;
            }
            UUID bridgeKey = BridgePlacementHelper.columnKey(route.routeId, surface);
            // Persist deck height on the route so retries stay stable even without chunk progress.
            if (route.bridgeDeckY == Integer.MIN_VALUE) {
               route.bridgeDeckY = surface.getY();
            }
            BridgePlacementHelper.Outcome bridge = BridgePlacementHelper.placeBridge(
               level,
               new BlockPos(surface.getX(), route.bridgeDeckY, surface.getZ()),
               path,
               null,
               bridgeKey,
               true
            );
            if (!bridge.finished()) {
               route.status = bridge.budgetExhausted() ? RouteStatus.PARTIAL : RouteStatus.BLOCKED;
               route.blockReason = bridge.reason().isEmpty() ? "bridge_incomplete" : bridge.reason();
               route.cursor = i;
               route.bridgeSupportCursor += bridge.ops();
               return;
            }
            route.bridgeSupportCursor = 0;
            route.bridgeDeckY = Integer.MIN_VALUE;
         } else if (!cur.is(Blocks.BEDROCK) && !isExistingPath(cur)) {
            if (!MaterializationTickBudget.tryConsumeOp()) {
               route.status = RouteStatus.PARTIAL;
               route.blockReason = "budget_exhausted";
               route.cursor = i;
               return;
            }
            level.setBlock(surface, path, 2);
         }
         route.completedChunks.add(cp.toLong());
         route.cursor = i + 1;
         previousSurface = surface;
         any = true;
      }

      if (route.cursor >= route.waypoints.size()) {
         route.status = RouteStatus.CONNECTED;
         route.blockReason = "";
         route.bridgeSupportCursor = 0;
         route.bridgeDeckY = Integer.MIN_VALUE;
      } else if (waitingChunk) {
         route.status = RouteStatus.WAITING_CHUNKS;
         route.blockReason = "chunk_not_loaded";
      } else if (any) {
         route.status = RouteStatus.PARTIAL;
         if (route.blockReason.isEmpty()) {
            route.blockReason = "";
         }
      }
   }

   private static boolean endpointsMissing(SettlementPlanSavedData store, RouteProgress route) {
      SettlementPlan from = store.getPlan(route.fromPlanId);
      SettlementPlan to = store.getPlan(route.toPlanId);
      return from == null || to == null
         || from.status().isTerminalFailure()
         || to.status().isTerminalFailure();
   }

   private static boolean isExistingPath(BlockState state) {
      return state.is(Blocks.DIRT_PATH)
         || state.is(Blocks.GRAVEL)
         || state.is(Blocks.COBBLESTONE)
         || state.is(Blocks.OAK_PLANKS)
         || state.is(Blocks.STONE_BRICKS)
         || state.is(Blocks.SANDSTONE)
         || state.is(Blocks.PACKED_MUD);
   }

   private static BlockPos findSafeAdjacent(ServerLevel level, BlockPos surface, BlockState path) {
      for (BlockPos cand : List.of(surface.north(), surface.south(), surface.east(), surface.west())) {
         if (!level.hasChunk(cand.getX() >> 4, cand.getZ() >> 4)) {
            continue;
         }
         int y = walkableSurfaceY(level, cand.getX(), cand.getZ());
         BlockPos at = new BlockPos(cand.getX(), y, cand.getZ());
         BlockState s = level.getBlockState(at);
         if (isExistingPath(s) || (BridgePlacementHelper.isSafeToOverwrite(level, at) && s.getFluidState().isEmpty())) {
            return at;
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
         if (state.canOcclude() || isExistingPath(state)) {
            return y;
         }
      }
      return Math.max(min, top);
   }

   /**
    * Saved progress for regional roads — separate from settlement plans so existing worlds stay compatible.
    */
   public static final class RegionalRoadSavedData extends net.minecraft.world.level.saveddata.SavedData {
      private static final String DATA_NAME = "millenaire_regional_roads";
      public static final int SCHEMA = 2;
      private final Map<UUID, RouteProgress> routes = new HashMap<>();
      private int roundRobinCursor;
      private int planScanCursor;
      private int schema = SCHEMA;

      public static RegionalRoadSavedData get(ServerLevel level) {
         return level.getDataStorage().computeIfAbsent(
            new Factory<>(RegionalRoadSavedData::new, (tag, regs) -> load(tag), null),
            DATA_NAME
         );
      }

      public Map<UUID, RouteProgress> routes() {
         return this.routes;
      }

      public int roundRobinCursor() {
         return this.roundRobinCursor;
      }

      public void setRoundRobinCursor(int cursor) {
         this.roundRobinCursor = Math.max(0, cursor);
      }

      public int planScanCursor() {
         return this.planScanCursor;
      }

      public void setPlanScanCursor(int cursor) {
         this.planScanCursor = Math.max(0, cursor);
      }

      @Override
      public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
         tag.putInt("schema", SCHEMA);
         tag.putInt("cursor", this.roundRobinCursor);
         tag.putInt("planScan", this.planScanCursor);
         ListTag list = new ListTag();
         for (RouteProgress route : this.routes.values()) {
            list.add(route.save());
         }
         tag.put("routes", list);
         return tag;
      }

      private static RegionalRoadSavedData load(CompoundTag tag) {
         RegionalRoadSavedData data = new RegionalRoadSavedData();
         int loadedSchema = tag.contains("schema") ? tag.getInt("schema") : 1;
         data.schema = SCHEMA;
         data.roundRobinCursor = tag.getInt("cursor");
         data.planScanCursor = tag.getInt("planScan");
         ListTag list = tag.getList("routes", Tag.TAG_COMPOUND);
         for (int i = 0; i < list.size(); i++) {
            try {
               RouteProgress route = RouteProgress.load(list.getCompound(i));
               if (loadedSchema < 2) {
                  // Old CONNECTED may only have coarse waypoints — force recovery expansion.
                  if (route.status == RouteStatus.CONNECTED || !route.continuousExpanded) {
                     route.coarseOnlyLegacy = true;
                     route.continuousExpanded = false;
                     if (route.status == RouteStatus.CONNECTED) {
                        route.status = RouteStatus.RECOVERY_REQUIRED;
                        route.blockReason = "schema1_coarse_connected";
                     }
                  }
               }
               data.routes.put(route.routeId, route);
            } catch (Exception e) {
               LOGGER.warn("[Millenaire] Skipping corrupt regional road entry #{}", i);
            }
         }
         // Cap retention of completed routes; keep blocked/recovery.
         if (data.routes.size() > 512) {
            List<UUID> completed = new ArrayList<>();
            for (RouteProgress r : data.routes.values()) {
               if (r.status == RouteStatus.CONNECTED) {
                  completed.add(r.routeId);
               }
            }
            completed.sort(Comparator.naturalOrder());
            for (int i = 0; i < completed.size() - 256; i++) {
               data.routes.remove(completed.get(i));
            }
         }
         return data;
      }
   }
}
