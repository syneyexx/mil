package org.millenaire.worldgen.prefill;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.perf.tick.ServerTickGovernor;
import org.millenaire.village.VillageChunkLoader;
import org.millenaire.worldgen.WorldgenEngine;
import org.millenaire.worldgen.content.WorldgenContentSnapshot;
import org.millenaire.worldgen.materialize.MaterializationTickBudget;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;
import org.millenaire.worldgen.plan.SettlementPlanStatus;
import org.millenaire.worldgen.plan.SettlementRegionKey;
import org.millenaire.worldgen.plan.WorldgenPlanningQueue;
import org.millenaire.worldgen.prefill.SpawnRingPrefillSavedData.Phase;
import org.millenaire.worldgen.starter.StarterSettlementSavedData;
import org.slf4j.Logger;

/**
 * Budgeted spawn-ring prefill: sync-generate region centre chunks, then kick Phase 4 until
 * ring plans are terminal. Temporary force-load tickets are released when each plan finishes.
 */
public final class SpawnRingPrefillService {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int MAX_DRAIN_TICKS = 20 * 60 * 15; // 15 minutes wall-ish at 20 tps
   private static final int CENTRE_CHUNK_RING = 1; // 3x3 around region centre
   private static final int CENTRE_CELL_SIDE = CENTRE_CHUNK_RING * 2 + 1;
   private static final int CENTRE_CELL_COUNT = CENTRE_CELL_SIDE * CENTRE_CELL_SIDE;
   /** Max new chunk loads/tickets per ensureFootprintLoaded call. */
   private static final int MAX_FOOTPRINT_CHUNKS_PER_CALL = 8;
   /** Cap on force-load tickets held across all prefill plans. */
   private static final int MAX_HELD_TICKETS = 256;

   /** planId -> chunks currently force-loaded for prefill. */
   private static final Map<UUID, Set<ChunkPos>> HELD_TICKETS = new HashMap<>();
   /** Cached ring-region packed keys for O(1) isRingPlan checks. */
   private static final LongOpenHashSet RING_REGION_SET = new LongOpenHashSet();
   private static long ringSetDataIdentity = Long.MIN_VALUE;
   /** Throttle ensureFootprintLoaded per plan (every 20 drain ticks). */
   private static final Map<UUID, Integer> LAST_FOOTPRINT_DRAIN_TICK = new HashMap<>();
   /** planId -> next index into the plan's ordered footprint chunk list. */
   private static final Map<UUID, Integer> FOOTPRINT_CURSOR = new HashMap<>();

   private SpawnRingPrefillService() {
   }

   public static void clearTransientState() {
      HELD_TICKETS.clear();
      RING_REGION_SET.clear();
      ringSetDataIdentity = Long.MIN_VALUE;
      LAST_FOOTPRINT_DRAIN_TICK.clear();
      FOOTPRINT_CURSOR.clear();
   }

   public static void onWorldReady(ServerLevel level) {
      tick(level);
   }

   public static void tick(ServerLevel level) {
      if (level == null || !level.dimension().equals(Level.OVERWORLD)) {
         return;
      }
      if (!WorldgenEngine.isEnabled()) {
         return;
      }
      boolean enabled;
      try {
         enabled = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenPrefillEnabled.get());
      } catch (IllegalStateException e) {
         return;
      }
      if (!enabled) {
         return;
      }
      if (!WorldgenContentSnapshot.isReady()) {
         return;
      }

      SpawnRingPrefillSavedData data = SpawnRingPrefillSavedData.get(level);
      if (data.isFinished()) {
         return;
      }

      if (data.phase() == Phase.IDLE) {
         if (!starterReady(level)) {
            return;
         }
         beginPrefill(level, data);
         return;
      }

      if (data.phase() == Phase.CHUNKING) {
         tickChunking(level, data);
         return;
      }

      if (data.phase() == Phase.DRAINING) {
         tickDraining(level, data);
      }
   }

   public static String statusLine(ServerLevel level) {
      if (level == null) {
         return "prefill: no level";
      }
      SpawnRingPrefillSavedData data = SpawnRingPrefillSavedData.get(level);
      boolean enabled;
      try {
         enabled = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenPrefillEnabled.get());
      } catch (IllegalStateException e) {
         enabled = false;
      }
      int open = countOpenRingPlans(level, data);
      return "prefill enabled="
         + enabled
         + " phase="
         + data.phase()
         + " regions="
         + data.regionIndex()
         + "/"
         + data.regionCount()
         + " radius="
         + data.radiusBlocks()
         + " drainTicks="
         + data.drainTicks()
         + " openPlans="
         + open
         + " heldTickets="
         + HELD_TICKETS.size()
         + (data.spawnAnchor() != null ? " spawn=" + data.spawnAnchor().toShortString() : "");
   }

   public static void retry(ServerLevel level) {
      if (level == null) {
         return;
      }
      releaseAllTickets(level);
      SpawnRingPrefillSavedData.get(level).prepareRetry();
      LOGGER.info("[Millenaire] Spawn-ring prefill reset — will restart on next tick");
   }

   private static boolean starterReady(ServerLevel level) {
      boolean wait;
      try {
         wait = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenPrefillWaitForStarter.get());
      } catch (IllegalStateException e) {
         wait = true;
      }
      if (!wait) {
         return true;
      }
      boolean starterEnabled;
      try {
         starterEnabled = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.starterSettlementEnabled.get());
      } catch (IllegalStateException e) {
         starterEnabled = true;
      }
      if (!starterEnabled) {
         return true;
      }
      StarterSettlementSavedData starter = StarterSettlementSavedData.get(level);
      return starter.isPlaced() || starter.hasGaveUp();
   }

   private static void beginPrefill(ServerLevel level, SpawnRingPrefillSavedData data) {
      int radius;
      int regionSize;
      try {
         radius = MillenaireServerConfig.SERVER.worldgenPrefillRadiusBlocks.getAsInt();
         regionSize = MillenaireServerConfig.SERVER.worldgenRegionSizeChunks.getAsInt();
      } catch (IllegalStateException e) {
         return;
      }
      BlockPos spawn = level.getSharedSpawnPos();
      List<Long> regions = enumerateRegions(spawn, radius, regionSize);
      data.setSpawnAnchor(spawn);
      data.setRadiusBlocks(radius);
      data.setRegions(regions);
      data.setPhase(Phase.CHUNKING);
      LOGGER.info(
         "[Millenaire] Spawn-ring prefill starting: {} regions within {} blocks of {}",
         regions.size(),
         radius,
         spawn.toShortString()
      );
   }

   static List<Long> enumerateRegions(BlockPos spawn, int radius, int regionSizeChunks) {
      int size = Math.max(4, regionSizeChunks);
      SettlementRegionKey spawnRegion = SettlementRegionKey.fromBlock(spawn.getX(), spawn.getZ(), size);
      int regionBlockSize = size * 16;
      int regionRadius = Math.max(1, (radius / Math.max(1, regionBlockSize)) + 1);
      long radiusSq = (long)radius * radius;
      List<Long> packed = new ArrayList<>();
      for (int drx = -regionRadius; drx <= regionRadius; drx++) {
         for (int drz = -regionRadius; drz <= regionRadius; drz++) {
            SettlementRegionKey key = new SettlementRegionKey(spawnRegion.regionX() + drx, spawnRegion.regionZ() + drz);
            int cx = key.centerChunkX(size);
            int cz = key.centerChunkZ(size);
            int bx = (cx << 4) + 8;
            int bz = (cz << 4) + 8;
            long dx = (long)bx - spawn.getX();
            long dz = (long)bz - spawn.getZ();
            if (dx * dx + dz * dz <= radiusSq) {
               packed.add(key.packed());
            }
         }
      }
      Collections.sort(packed);
      return packed;
   }

   private static void tickChunking(ServerLevel level, SpawnRingPrefillSavedData data) {
      int budget;
      int regionSize;
      try {
         budget = MillenaireServerConfig.SERVER.worldgenPrefillChunksPerTick.getAsInt();
         regionSize = MillenaireServerConfig.SERVER.worldgenRegionSizeChunks.getAsInt();
      } catch (IllegalStateException e) {
         return;
      }
      int generated = 0;
      while (data.regionIndex() < data.regionCount()) {
         if (!ServerTickGovernor.hasTime() || !MaterializationTickBudget.hasTime()) {
            return;
         }
         long packed = data.regionPacked().get(data.regionIndex());
         SettlementRegionKey region = SettlementRegionKey.fromPacked(packed);
         int cx = region.centerChunkX(regionSize);
         int cz = region.centerChunkZ(regionSize);
         int cell = Math.max(0, Math.min(CENTRE_CELL_COUNT - 1, data.regionCellOffset()));
         while (cell < CENTRE_CELL_COUNT) {
            int chunkX = cx + (cell / CENTRE_CELL_SIDE) - CENTRE_CHUNK_RING;
            int chunkZ = cz + (cell % CENTRE_CELL_SIDE) - CENTRE_CHUNK_RING;
            // Chunks already available cost nothing; only sync generation/loads spend the budget.
            if (!level.hasChunk(chunkX, chunkZ)) {
               if (generated >= budget || !ServerTickGovernor.hasTime() || !MaterializationTickBudget.hasTime()) {
                  data.setRegionCellOffset(cell);
                  return;
               }
               level.getChunk(chunkX, chunkZ);
               generated++;
            }
            cell++;
         }
         WorldgenPlanningQueue.get().offerPlanRegion(region, new ChunkPos(cx, cz));
         data.incrementRegionIndex();
         if (generated >= budget) {
            break;
         }
      }
      if (data.regionIndex() >= data.regionCount()) {
         data.setPhase(Phase.DRAINING);
         LOGGER.info("[Millenaire] Spawn-ring prefill chunking complete ({} regions) — draining Phase 4", data.regionCount());
      }
   }

   private static void tickDraining(ServerLevel level, SpawnRingPrefillSavedData data) {
      data.incrementDrainTicks();
      if (data.drainTicks() > MAX_DRAIN_TICKS) {
         releaseAllTickets(level);
         data.setPhase(Phase.GAVE_UP);
         LOGGER.warn(
            "[Millenaire] Spawn-ring prefill gave up after {} drain ticks ({} open plans remain)",
            data.drainTicks(),
            countOpenRingPlans(level, data)
         );
         return;
      }

      int kicksBudget;
      try {
         kicksBudget = MillenaireServerConfig.SERVER.worldgenPrefillPlanKicksPerTick.getAsInt();
      } catch (IllegalStateException e) {
         kicksBudget = 4;
      }

      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      ensureRingSet(data);
      int kicks = 0;
      int open = 0;
      int drainTick = data.drainTicks();
      for (SettlementPlan plan : store.plansView()) {
         if (!isRingPlan(plan, data)) {
            continue;
         }
         if (plan.activated() || plan.status() == SettlementPlanStatus.ACTIVE || plan.status().isTerminalFailure()) {
            releasePlanTickets(level, plan);
            LAST_FOOTPRINT_DRAIN_TICK.remove(plan.planId());
            continue;
         }
         open++;
         Integer last = LAST_FOOTPRINT_DRAIN_TICK.get(plan.planId());
         if (last == null || drainTick - last >= 20) {
            ensureFootprintLoaded(level, plan);
            LAST_FOOTPRINT_DRAIN_TICK.put(plan.planId(), drainTick);
         }
         if (kicks < kicksBudget) {
            WorldgenEngine.kickPlanExecution(level, plan);
            kicks++;
         }
      }

      // Also ensure every enumerated region has been offered at least once (sticky WAITING).
      int regionSize;
      try {
         regionSize = MillenaireServerConfig.SERVER.worldgenRegionSizeChunks.getAsInt();
      } catch (IllegalStateException e) {
         regionSize = 24;
      }
      int offers = 0;
      for (Long packed : data.regionPacked()) {
         if (offers >= kicksBudget) {
            break;
         }
         SettlementRegionKey region = SettlementRegionKey.fromPacked(packed);
         if (store.getByRegion(region) == null) {
            int cx = region.centerChunkX(regionSize);
            int cz = region.centerChunkZ(regionSize);
            level.getChunk(cx, cz);
            WorldgenPlanningQueue.get().offerPlanRegion(region, new ChunkPos(cx, cz));
            offers++;
            open++;
         }
      }

      if (open == 0 && allRegionsTerminalOrPresent(level, data)) {
         releaseAllTickets(level);
         data.setPhase(Phase.DONE);
         LOGGER.info(
            "[Millenaire] Spawn-ring prefill done after {} drain ticks ({} regions)",
            data.drainTicks(),
            data.regionCount()
         );
      }
   }

   private static boolean allRegionsTerminalOrPresent(ServerLevel level, SpawnRingPrefillSavedData data) {
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      for (Long packed : data.regionPacked()) {
         SettlementPlan plan = store.getByRegion(SettlementRegionKey.fromPacked(packed));
         if (plan == null) {
            return false;
         }
         if (!plan.activated()
            && plan.status() != SettlementPlanStatus.ACTIVE
            && !plan.status().isTerminalFailure()) {
            return false;
         }
      }
      return true;
   }

   private static int countOpenRingPlans(ServerLevel level, SpawnRingPrefillSavedData data) {
      if (data.regionCount() == 0) {
         return 0;
      }
      int open = 0;
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      for (Long packed : data.regionPacked()) {
         SettlementPlan plan = store.getByRegion(SettlementRegionKey.fromPacked(packed));
         if (plan == null
            || (!plan.activated()
               && plan.status() != SettlementPlanStatus.ACTIVE
               && !plan.status().isTerminalFailure())) {
            open++;
         }
      }
      return open;
   }

   private static void ensureRingSet(SpawnRingPrefillSavedData data) {
      // regionCount + first packed as a cheap identity; rebuild when the list changes.
      long identity = ((long)data.regionCount() << 32)
         ^ (data.regionCount() > 0 ? data.regionPacked().get(0) : 0L);
      if (identity == ringSetDataIdentity && RING_REGION_SET.size() == data.regionCount()) {
         return;
      }
      RING_REGION_SET.clear();
      for (Long packed : data.regionPacked()) {
         if (packed != null) {
            RING_REGION_SET.add(packed.longValue());
         }
      }
      ringSetDataIdentity = identity;
   }

   private static boolean isRingPlan(SettlementPlan plan, SpawnRingPrefillSavedData data) {
      if (plan == null || plan.region() == null || data.regionCount() == 0) {
         return false;
      }
      ensureRingSet(data);
      return RING_REGION_SET.contains(plan.region().packed());
   }

   private static void ensureFootprintLoaded(ServerLevel level, SettlementPlan plan) {
      List<ChunkPos> chunks = footprintChunks(plan);
      if (chunks.isEmpty()) {
         return;
      }
      UUID planId = plan.planId();
      Set<ChunkPos> held = HELD_TICKETS.computeIfAbsent(planId, id -> new HashSet<>());
      int heldTotal = heldTicketCount();
      int index = Math.max(0, FOOTPRINT_CURSOR.getOrDefault(planId, 0));
      if (index >= chunks.size()) {
         index = 0;
      }
      Set<ChunkPos> toAdd = new HashSet<>();
      while (index < chunks.size() && toAdd.size() < MAX_FOOTPRINT_CHUNKS_PER_CALL) {
         ChunkPos cp = chunks.get(index);
         if (held.contains(cp)) {
            index++;
            continue;
         }
         if (heldTotal + toAdd.size() >= MAX_HELD_TICKETS) {
            break;
         }
         level.getChunk(cp.x, cp.z);
         toAdd.add(cp);
         index++;
      }
      FOOTPRINT_CURSOR.put(planId, index >= chunks.size() ? 0 : index);
      if (!toAdd.isEmpty()) {
         BlockPos anchor = plan.center() != null ? plan.center() : level.getSharedSpawnPos();
         VillageChunkLoader.forceVillageChunks(level, anchor, toAdd);
         held.addAll(toAdd);
      }
   }

   /** Footprint chunks ordered nearest-centre first so the cursor loads the most important chunks early. */
   private static List<ChunkPos> footprintChunks(SettlementPlan plan) {
      Set<ChunkPos> chunks = new HashSet<>();
      Set<Long> reserved = plan.reservedChunks();
      if (reserved != null && !reserved.isEmpty()) {
         for (Long packed : reserved) {
            if (packed != null) {
               chunks.add(new ChunkPos(ChunkPos.getX(packed), ChunkPos.getZ(packed)));
            }
         }
      }
      ChunkPos centre = plan.center() != null ? new ChunkPos(plan.center()) : null;
      if (chunks.isEmpty() && centre != null) {
         for (int dx = -CENTRE_CHUNK_RING; dx <= CENTRE_CHUNK_RING; dx++) {
            for (int dz = -CENTRE_CHUNK_RING; dz <= CENTRE_CHUNK_RING; dz++) {
               chunks.add(new ChunkPos(centre.x + dx, centre.z + dz));
            }
         }
      }
      List<ChunkPos> ordered = new ArrayList<>(chunks);
      ordered.sort((a, b) -> {
         if (centre != null) {
            int cmp = Long.compare(distSq(a, centre), distSq(b, centre));
            if (cmp != 0) {
               return cmp;
            }
         }
         return Long.compare(a.toLong(), b.toLong());
      });
      return ordered;
   }

   private static long distSq(ChunkPos a, ChunkPos b) {
      long dx = (long)a.x - b.x;
      long dz = (long)a.z - b.z;
      return dx * dx + dz * dz;
   }

   private static int heldTicketCount() {
      int total = 0;
      for (Set<ChunkPos> set : HELD_TICKETS.values()) {
         total += set.size();
      }
      return total;
   }

   private static void releasePlanTickets(ServerLevel level, SettlementPlan plan) {
      if (plan == null || plan.planId() == null) {
         return;
      }
      FOOTPRINT_CURSOR.remove(plan.planId());
      Set<ChunkPos> held = HELD_TICKETS.remove(plan.planId());
      if (held == null || held.isEmpty()) {
         return;
      }
      BlockPos anchor = plan.center() != null ? plan.center() : level.getSharedSpawnPos();
      VillageChunkLoader.releaseVillageChunks(level, anchor, held);
   }

   private static void releaseAllTickets(ServerLevel level) {
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      for (Map.Entry<UUID, Set<ChunkPos>> entry : new ArrayList<>(HELD_TICKETS.entrySet())) {
         SettlementPlan plan = store.getPlan(entry.getKey());
         BlockPos anchor = plan != null && plan.center() != null ? plan.center() : level.getSharedSpawnPos();
         VillageChunkLoader.releaseVillageChunks(level, anchor, entry.getValue());
      }
      HELD_TICKETS.clear();
      FOOTPRINT_CURSOR.clear();
   }
}
