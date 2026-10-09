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
import javax.annotation.Nullable;
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
      /** Last successfully placed surface — persists across ticks for slope checks. */
      public int lastPlacedX = Integer.MIN_VALUE;
      public int lastPlacedY = Integer.MIN_VALUE;
      public int lastPlacedZ = Integer.MIN_VALUE;

      RouteProgress(UUID routeId, UUID fromPlanId, UUID toPlanId, List<BlockPos> waypoints) {
         this.routeId = routeId;
         this.fromPlanId = fromPlanId;
         this.toPlanId = toPlanId;
         this.waypoints = new ArrayList<>(waypoints);
         this.continuousExpanded = true;
      }

      @Nullable
      public BlockPos lastPlacedSurface() {
         if (this.lastPlacedX == Integer.MIN_VALUE) {
            return null;
         }
         return new BlockPos(this.lastPlacedX, this.lastPlacedY, this.lastPlacedZ);
      }

      public void setLastPlacedSurface(BlockPos pos) {
         if (pos == null) {
            this.lastPlacedX = Integer.MIN_VALUE;
            this.lastPlacedY = Integer.MIN_VALUE;
            this.lastPlacedZ = Integer.MIN_VALUE;
            return;
         }
         this.lastPlacedX = pos.getX();
         this.lastPlacedY = pos.getY();
         this.lastPlacedZ = pos.getZ();
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
         if (this.lastPlacedX != Integer.MIN_VALUE) {
            tag.putInt("lastX", this.lastPlacedX);
            tag.putInt("lastY", this.lastPlacedY);
            tag.putInt("lastZ", this.lastPlacedZ);
         }
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
         if (tag.contains("lastX")) {
            progress.lastPlacedX = tag.getInt("lastX");
            progress.lastPlacedY = tag.getInt("lastY");
            progress.lastPlacedZ = tag.getInt("lastZ");
         }
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
      SettlementPlan fromPlan = store.getPlan(route.fromPlanId);
      int scanned = 0;
      boolean waitingChunk = false;
      boolean any = false;
      BlockPos previousSurface = route.lastPlacedSurface();

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
               BlockState at = level.getBlockState(surface);
               if (at.getFluidState().isEmpty()) {
                  // Attempt a continuous lateral detour before blocking.
                  List<BlockPos> detour = planContinuousDetour(level, previousSurface, surface, path, fromPlan);
                  if (detour.isEmpty()) {
                     route.status = RouteStatus.BLOCKED;
                     route.blockReason = "too_steep";
                     route.cursor = i;
                     return;
                  }
                  spliceDetour(route, i, detour);
                  surface = new BlockPos(detour.getFirst().getX(), walkableSurfaceY(level, detour.getFirst().getX(), detour.getFirst().getZ()), detour.getFirst().getZ());
               }
            }
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
            if (route.bridgeDeckY == Integer.MIN_VALUE) {
               route.bridgeDeckY = surface.getY();
            }
            BridgePlacementHelper.Outcome bridge = BridgePlacementHelper.placeBridge(
               level,
               new BlockPos(surface.getX(), route.bridgeDeckY, surface.getZ()),
               path,
               null,
               bridgeKey,
               true,
               fromPlan
            );
            if (!bridge.finished()) {
               route.status = bridge.budgetExhausted() ? RouteStatus.PARTIAL : RouteStatus.BLOCKED;
               route.blockReason = bridge.reason().isEmpty() ? "bridge_incomplete" : bridge.reason();
               route.cursor = i;
               route.bridgeSupportCursor += bridge.ops();
               return;
            }
            int deckY = route.bridgeDeckY != Integer.MIN_VALUE ? route.bridgeDeckY : surface.getY();
            surface = new BlockPos(surface.getX(), deckY, surface.getZ());
            route.bridgeSupportCursor = 0;
            route.bridgeDeckY = Integer.MIN_VALUE;
         } else {
            org.millenaire.worldgen.materialize.RoadPlacementGuard.Result permit =
               org.millenaire.worldgen.materialize.RoadPlacementGuard.evaluate(
                  level, surface, org.millenaire.worldgen.materialize.RoadPlacementGuard.Intent.PATH_SURFACE, fromPlan, path
               );
            if (!permit.allowed()) {
               List<BlockPos> detour = planContinuousDetour(level, previousSurface != null ? previousSurface : surface, surface, path, fromPlan);
               if (detour.isEmpty()) {
                  route.status = RouteStatus.BLOCKED;
                  route.blockReason = permit.reason().isEmpty() ? "obstacle_protected" : permit.reason();
                  route.cursor = i;
                  return;
               }
               spliceDetour(route, i, detour);
               // Restart this index on the spliced continuous segment next iteration.
               i--;
               continue;
            }
            if (!permit.alreadyPath()) {
               // Commit-time recheck.
               permit = org.millenaire.worldgen.materialize.RoadPlacementGuard.evaluate(
                  level, surface, org.millenaire.worldgen.materialize.RoadPlacementGuard.Intent.PATH_SURFACE, fromPlan, path
               );
               if (!permit.allowed()) {
                  route.status = RouteStatus.BLOCKED;
                  route.blockReason = permit.reason();
                  route.cursor = i;
                  return;
               }
               if (!permit.alreadyPath()) {
                  if (!MaterializationTickBudget.tryConsumeOp()) {
                     route.status = RouteStatus.PARTIAL;
                     route.blockReason = "budget_exhausted";
                     route.cursor = i;
                     return;
                  }
                  level.setBlock(surface, path, 2);
               }
            }
         }
         route.completedChunks.add(cp.toLong());
         route.cursor = i + 1;
         route.setLastPlacedSurface(surface);
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

   /**
    * Plan a short continuous lateral detour around an obstacle. Empty when no coherent path exists.
    * Never returns a single sideways hop that would break route continuity.
    */
   private static List<BlockPos> planContinuousDetour(
      ServerLevel level,
      BlockPos from,
      BlockPos blocked,
      BlockState path,
      @Nullable SettlementPlan owningPlan
   ) {
      List<BlockPos> best = List.of();
      for (int side : new int[]{1, -1, 2, -2, 3, -3}) {
         List<BlockPos> candidate = new ArrayList<>();
         int dx = Math.abs(blocked.getX() - from.getX()) >= Math.abs(blocked.getZ() - from.getZ()) ? 0 : side;
         int dz = dx == 0 ? 0 : 0;
         if (dx == 0) {
            dz = side;
         }
         BlockPos mid = new BlockPos(blocked.getX() + dx, blocked.getY(), blocked.getZ() + dz);
         BlockPos join = new BlockPos(blocked.getX() + dx, blocked.getY(), blocked.getZ() + (dz == 0 ? 0 : 0));
         // Corridor: from → mid → rejoined cell past obstacle in original direction.
         int dirX = Integer.signum(blocked.getX() - from.getX());
         int dirZ = Integer.signum(blocked.getZ() - from.getZ());
         BlockPos after = blocked.offset(dirX, 0, dirZ);
         List<BlockPos> corridor = List.of(mid, after.offset(dx, 0, dz), after);
         boolean ok = true;
         for (BlockPos cell : corridor) {
            if (!level.hasChunk(cell.getX() >> 4, cell.getZ() >> 4)) {
               ok = false;
               break;
            }
            int y = walkableSurfaceY(level, cell.getX(), cell.getZ());
            BlockPos at = new BlockPos(cell.getX(), y, cell.getZ());
            BlockState state = level.getBlockState(at);
            if (!state.getFluidState().isEmpty()) {
               ok = false;
               break;
            }
            org.millenaire.worldgen.materialize.RoadPlacementGuard.Result permit =
               org.millenaire.worldgen.materialize.RoadPlacementGuard.evaluate(
                  level, at, org.millenaire.worldgen.materialize.RoadPlacementGuard.Intent.PATH_SURFACE, owningPlan, path
               );
            if (!permit.allowed()) {
               ok = false;
               break;
            }
            candidate.add(cell);
         }
         if (ok && candidate.size() >= 2) {
            best = candidate;
            break;
         }
      }
      return best;
   }

   private static void spliceDetour(RouteProgress route, int atIndex, List<BlockPos> detour) {
      if (detour == null || detour.isEmpty() || atIndex < 0 || atIndex >= route.waypoints.size()) {
         return;
      }
      route.waypoints.remove(atIndex);
      route.waypoints.addAll(atIndex, detour);
   }

   /**
    * Bounded lookup of a regional route touching a village's settlement plan.
    * Presentation-only — does not force-load chunks or mutate routes.
    */
   @Nullable
   public static RouteProgress findProgressForVillage(ServerLevel level, org.millenaire.village.Village village) {
      if (level == null || village == null) {
         return null;
      }
      SettlementPlanSavedData plans = SettlementPlanSavedData.get(level);
      SettlementPlan own = null;
      for (SettlementPlan plan : plans.allPlans()) {
         if (village.getId().uuid().equals(plan.villageUuid())) {
            own = plan;
            break;
         }
      }
      if (own == null) {
         return null;
      }
      UUID planId = own.planId();
      RouteProgress best = null;
      for (RouteProgress route : RegionalRoadSavedData.get(level).routes().values()) {
         if (!planId.equals(route.fromPlanId) && !planId.equals(route.toPlanId)) {
            continue;
         }
         if (best == null
            || route.status == RouteStatus.BLOCKED
            || route.status == RouteStatus.WAITING_CHUNKS
            || route.status == RouteStatus.PARTIAL) {
            best = route;
            if (route.status == RouteStatus.BLOCKED || route.status == RouteStatus.WAITING_CHUNKS) {
               break;
            }
         }
      }
      return best;
   }

   public static String endpointLabel(ServerLevel level, RouteProgress route, UUID selfPlanId) {
      if (route == null) {
         return "";
      }
      UUID other = selfPlanId != null && selfPlanId.equals(route.fromPlanId) ? route.toPlanId : route.fromPlanId;
      SettlementPlan plan = SettlementPlanSavedData.get(level).getPlan(other);
      if (plan == null) {
         return "???";
      }
      if (plan.villageUuid() != null) {
         org.millenaire.village.Village v = org.millenaire.village.VillageSavedData.get(level)
            .getVillageManager()
            .getVillage(new org.millenaire.village.VillageId(plan.villageUuid()));
         if (v != null && v.getVillageName() != null && !v.getVillageName().isBlank()) {
            return v.getVillageName();
         }
      }
      return plan.center().toShortString();
   }

   private static boolean endpointsMissing(SettlementPlanSavedData store, RouteProgress route) {
      SettlementPlan from = store.getPlan(route.fromPlanId);
      SettlementPlan to = store.getPlan(route.toPlanId);
      return from == null || to == null
         || from.status().isTerminalFailure()
         || to.status().isTerminalFailure();
   }

   private static boolean isExistingPath(BlockState state) {
      // Conservative: registered path types only — loose cobble/planks elsewhere are not “a road”.
      return org.millenaire.worldgen.materialize.RoadPlacementGuard.isRegisteredPath(state);
   }

   private static BlockPos findSafeAdjacent(ServerLevel level, BlockPos surface, BlockState path) {
      for (BlockPos cand : List.of(surface.north(), surface.south(), surface.east(), surface.west())) {
         if (!level.hasChunk(cand.getX() >> 4, cand.getZ() >> 4)) {
            continue;
         }
         int y = walkableSurfaceY(level, cand.getX(), cand.getZ());
         BlockPos at = new BlockPos(cand.getX(), y, cand.getZ());
         org.millenaire.worldgen.materialize.RoadPlacementGuard.Result permit =
            org.millenaire.worldgen.materialize.RoadPlacementGuard.evaluate(
               level, at, org.millenaire.worldgen.materialize.RoadPlacementGuard.Intent.CONNECT_EXISTING_PATH, null, path
            );
         if (permit.allowed() && level.getBlockState(at).getFluidState().isEmpty()) {
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
