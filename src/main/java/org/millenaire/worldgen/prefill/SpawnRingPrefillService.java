package org.millenaire.worldgen.prefill;

import com.mojang.logging.LogUtils;
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
import org.millenaire.village.VillageChunkLoader;
import org.millenaire.worldgen.WorldgenEngine;
import org.millenaire.worldgen.content.WorldgenContentSnapshot;
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

   /** planId -> chunks currently force-loaded for prefill. */
   private static final Map<UUID, Set<ChunkPos>> HELD_TICKETS = new HashMap<>();

   private SpawnRingPrefillService() {
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
      while (data.regionIndex() < data.regionCount() && generated < budget) {
         long packed = data.regionPacked().get(data.regionIndex());
         SettlementRegionKey region = SettlementRegionKey.fromPacked(packed);
         int cx = region.centerChunkX(regionSize);
         int cz = region.centerChunkZ(regionSize);
         for (int dx = -CENTRE_CHUNK_RING; dx <= CENTRE_CHUNK_RING && generated < budget; dx++) {
            for (int dz = -CENTRE_CHUNK_RING; dz <= CENTRE_CHUNK_RING && generated < budget; dz++) {
               level.getChunk(cx + dx, cz + dz);
               generated++;
            }
         }
         WorldgenPlanningQueue.get().offerPlanRegion(region, new ChunkPos(cx, cz));
         data.incrementRegionIndex();
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
      int kicks = 0;
      int open = 0;
      for (SettlementPlan plan : store.plansView()) {
         if (!isRingPlan(plan, data)) {
            continue;
         }
         if (plan.activated() || plan.status() == SettlementPlanStatus.ACTIVE || plan.status().isTerminalFailure()) {
            releasePlanTickets(level, plan);
            continue;
         }
         open++;
         ensureFootprintLoaded(level, plan);
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

   private static boolean isRingPlan(SettlementPlan plan, SpawnRingPrefillSavedData data) {
      if (plan == null || plan.region() == null || data.regionCount() == 0) {
         return false;
      }
      long packed = plan.region().packed();
      return data.regionPacked().contains(packed);
   }

   private static void ensureFootprintLoaded(ServerLevel level, SettlementPlan plan) {
      Set<ChunkPos> chunks = new HashSet<>();
      Set<Long> reserved = plan.reservedChunks();
      if (reserved != null && !reserved.isEmpty()) {
         for (Long packed : reserved) {
            if (packed != null) {
               chunks.add(new ChunkPos(ChunkPos.getX(packed), ChunkPos.getZ(packed)));
            }
         }
      }
      if (chunks.isEmpty() && plan.center() != null) {
         ChunkPos centre = new ChunkPos(plan.center());
         for (int dx = -CENTRE_CHUNK_RING; dx <= CENTRE_CHUNK_RING; dx++) {
            for (int dz = -CENTRE_CHUNK_RING; dz <= CENTRE_CHUNK_RING; dz++) {
               chunks.add(new ChunkPos(centre.x + dx, centre.z + dz));
            }
         }
      }
      BlockPos anchor = plan.center() != null ? plan.center() : level.getSharedSpawnPos();
      for (ChunkPos cp : chunks) {
         level.getChunk(cp.x, cp.z);
      }
      Set<ChunkPos> held = HELD_TICKETS.computeIfAbsent(plan.planId(), id -> new HashSet<>());
      Set<ChunkPos> toAdd = new HashSet<>();
      for (ChunkPos cp : chunks) {
         if (!held.contains(cp)) {
            toAdd.add(cp);
         }
      }
      if (!toAdd.isEmpty()) {
         VillageChunkLoader.forceVillageChunks(level, anchor, toAdd);
         held.addAll(toAdd);
      }
   }

   private static void releasePlanTickets(ServerLevel level, SettlementPlan plan) {
      if (plan == null || plan.planId() == null) {
         return;
      }
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
   }
}
