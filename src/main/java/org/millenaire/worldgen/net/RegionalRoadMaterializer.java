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
import org.millenaire.worldgen.materialize.MaterializationTickBudget;
import org.millenaire.worldgen.plan.PlannedRoad;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;
import org.millenaire.worldgen.plan.SettlementPlanStatus;
import org.slf4j.Logger;

/**
 * Physical placement for regional connectors planned by {@link RegionalRoadPlanner}.
 * Never force-loads chunks; unfinished segments wait on the resume path.
 */
public final class RegionalRoadMaterializer {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_ROUTES_PER_TICK = 2;
   private static final int MAX_WAYPOINTS_SCAN = 48;

   public enum RouteStatus {
      PLANNED,
      PARTIAL,
      CONNECTED,
      BLOCKED
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

      RouteProgress(UUID routeId, UUID fromPlanId, UUID toPlanId, List<BlockPos> waypoints) {
         this.routeId = routeId;
         this.fromPlanId = fromPlanId;
         this.toPlanId = toPlanId;
         this.waypoints = new ArrayList<>(waypoints);
      }

      CompoundTag save() {
         CompoundTag tag = new CompoundTag();
         tag.putUUID("id", this.routeId);
         tag.putUUID("from", this.fromPlanId);
         tag.putUUID("to", this.toPlanId);
         tag.putString("status", this.status.name());
         tag.putString("block", this.blockReason);
         tag.putInt("cursor", this.cursor);
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
         ListTag done = tag.getList("doneChunks", Tag.TAG_COMPOUND);
         for (int i = 0; i < done.size(); i++) {
            progress.completedChunks.add(done.getCompound(i).getLong("c"));
         }
         return progress;
      }
   }

   private RegionalRoadMaterializer() {
   }

   public static void tick(ServerLevel level, SettlementPlanSavedData store) {
      if (!Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenGenerateRegionalRoads.get())) {
         return;
      }
      RegionalRoadSavedData roads = RegionalRoadSavedData.get(level);
      ensurePlans(level, store, roads);
      int processed = 0;
      List<RouteProgress> ordered = new ArrayList<>(roads.routes().values());
      ordered.sort(Comparator.comparing(r -> r.routeId));
      int start = Math.floorMod(roads.roundRobinCursor(), Math.max(1, ordered.size()));
      for (int i = 0; i < ordered.size() && processed < MAX_ROUTES_PER_TICK; i++) {
         if (!MaterializationTickBudget.hasOps()) {
            break;
         }
         RouteProgress route = ordered.get((start + i) % ordered.size());
         if (route.status == RouteStatus.CONNECTED) {
            continue;
         }
         advanceRoute(level, route);
         processed++;
         roads.setRoundRobinCursor(start + i + 1);
      }
      roads.setDirty();
   }

   private static void ensurePlans(ServerLevel level, SettlementPlanSavedData store, RegionalRoadSavedData roads) {
      List<SettlementPlan> active = new ArrayList<>();
      for (SettlementPlan plan : store.allPlans()) {
         if (plan.status() == SettlementPlanStatus.ACTIVE
            || plan.status() == SettlementPlanStatus.PHYSICAL_COMPLETE
            || plan.status() == SettlementPlanStatus.MATERIALIZING) {
            active.add(plan);
         }
      }
      for (SettlementPlan from : active) {
         List<PlannedRoad> links = RegionalRoadPlanner.planLinks(from, active);
         for (PlannedRoad link : links) {
            if (roads.routes().containsKey(link.roadId())) {
               continue;
            }
            roads.routes().put(link.roadId(), new RouteProgress(link.roadId(), link.fromBuildingId(), link.toBuildingId(), link.waypoints()));
            if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenDetailedDiagnostics.get())) {
               LOGGER.debug("[Millenaire] Planned regional road {}", link.roadId());
            }
         }
      }
   }

   private static void advanceRoute(ServerLevel level, RouteProgress route) {
      boolean bridges = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenGenerateBridges.get());
      BlockState path = Blocks.DIRT_PATH.defaultBlockState();
      int scanned = 0;
      boolean waitingChunk = false;
      boolean any = false;
      for (int i = route.cursor; i < route.waypoints.size() && scanned < MAX_WAYPOINTS_SCAN; i++) {
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
         long packed = cp.toLong();
         if (route.completedChunks.contains(packed) && i > 0) {
            // still place this waypoint; completion is per-waypoint via cursor
         }
         int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, p.getX(), p.getZ()) - 1;
         BlockPos surface = new BlockPos(p.getX(), surfaceY, p.getZ());
         BlockState cur = level.getBlockState(surface);
         if (!cur.getFluidState().isEmpty()) {
            if (!bridges) {
               route.status = RouteStatus.BLOCKED;
               route.blockReason = "water_no_bridges";
               route.cursor = i;
               return;
            }
            placeBridge(level, surface, path);
         } else if (!cur.is(Blocks.BEDROCK) && MaterializationTickBudget.tryConsumeOp()) {
            level.setBlock(surface, path, 2);
         }
         route.completedChunks.add(packed);
         route.cursor = i + 1;
         any = true;
      }
      if (route.cursor >= route.waypoints.size()) {
         route.status = RouteStatus.CONNECTED;
         route.blockReason = "";
      } else if (waitingChunk) {
         route.status = RouteStatus.PARTIAL;
         route.blockReason = "chunk_not_loaded";
      } else if (any) {
         route.status = RouteStatus.PARTIAL;
         route.blockReason = "";
      }
   }

   private static void placeBridge(ServerLevel level, BlockPos surface, BlockState deck) {
      int y = surface.getY();
      while (y > level.getMinBuildHeight() + 1) {
         BlockPos support = new BlockPos(surface.getX(), y, surface.getZ());
         BlockState state = level.getBlockState(support);
         if (state.canOcclude() && state.getFluidState().isEmpty()) {
            break;
         }
         if (!MaterializationTickBudget.tryConsumeOp()) {
            return;
         }
         level.setBlock(support, Blocks.STONE.defaultBlockState(), 2);
         y--;
      }
      if (MaterializationTickBudget.tryConsumeOp()) {
         level.setBlock(surface, Blocks.OAK_PLANKS.defaultBlockState(), 2);
         // deck preference ignored underwater — planks are the deterministic bridge deck
      }
   }

   /**
    * Saved progress for regional roads — separate from settlement plans so existing worlds stay compatible.
    */
   public static final class RegionalRoadSavedData extends net.minecraft.world.level.saveddata.SavedData {
      private static final String DATA_NAME = "millenaire_regional_roads";
      public static final int SCHEMA = 1;
      private final Map<UUID, RouteProgress> routes = new HashMap<>();
      private int roundRobinCursor;
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

      @Override
      public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
         tag.putInt("schema", this.schema);
         tag.putInt("cursor", this.roundRobinCursor);
         ListTag list = new ListTag();
         for (RouteProgress route : this.routes.values()) {
            list.add(route.save());
         }
         tag.put("routes", list);
         return tag;
      }

      private static RegionalRoadSavedData load(CompoundTag tag) {
         RegionalRoadSavedData data = new RegionalRoadSavedData();
         data.schema = tag.contains("schema") ? tag.getInt("schema") : SCHEMA;
         data.roundRobinCursor = tag.getInt("cursor");
         ListTag list = tag.getList("routes", Tag.TAG_COMPOUND);
         for (int i = 0; i < list.size(); i++) {
            try {
               RouteProgress route = RouteProgress.load(list.getCompound(i));
               data.routes.put(route.routeId, route);
            } catch (Exception e) {
               LOGGER.warn("[Millenaire] Skipping corrupt regional road entry #{}", i);
            }
         }
         // Cap retention of completed routes
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
