package org.millenaire.worldgen.plan;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.perf.compute.ComputeOffload;
import org.millenaire.perf.compute.MillenaireComputePool;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.world.SiteValidator;
import org.millenaire.world.StructureAvoidance;
import org.millenaire.worldgen.WorldgenMetrics;
import org.millenaire.worldgen.content.VillageTypeWorldgenEntry;
import org.millenaire.worldgen.content.WorldgenContentSnapshot;
import org.millenaire.worldgen.terrain.WorldgenChunkAccess;
import org.millenaire.worldgen.terrain.WorldgenTerrainSampler;
import org.millenaire.worldgen.terrain.WorldgenTerrainSampler.BiomeSample;
import org.millenaire.worldgen.terrain.WorldgenTerrainSampler.IntSample;
import org.millenaire.worldgen.terrain.WorldgenTerrainSampler.MetricsSample;
import org.millenaire.worldgen.terrain.WorldgenTerrainSampler.TerrainMetrics;
import org.millenaire.worldgen.terrain.WorldgenTerrainSampler.ValiditySample;
import org.slf4j.Logger;

/**
 * Stage A — deterministic region candidate selection. No block writes.
 * Seed/config gates run before any world read. Missing chunks yield WAITING, not INVALID.
 */
public final class SettlementPlanner {
   private static final Logger LOGGER = LogUtils.getLogger();
   public enum PlanResult {
      /** Plan exists in store (accepted or rejected). */
      READY,
      /** Required columns are not loaded yet — retry later; nothing persisted as failure. */
      WAITING,
      /** Engine/config disabled. */
      DISABLED,
      /** Another thread/tick is already evaluating this region. */
      IN_FLIGHT
   }

   public record Outcome(PlanResult result, @Nullable SettlementPlan plan) {
      public static Outcome ready(SettlementPlan plan) {
         return new Outcome(PlanResult.READY, plan);
      }

      public static Outcome waiting() {
         return new Outcome(PlanResult.WAITING, null);
      }

      public static Outcome disabled() {
         return new Outcome(PlanResult.DISABLED, null);
      }

      public static Outcome inFlight() {
         return new Outcome(PlanResult.IN_FLIGHT, null);
      }
   }

   private SettlementPlanner() {
   }

   /**
    * Ensure a plan exists for the region containing {@code chunk}. Idempotent.
    * Prefer {@link #ensureRegionPlanOutcome} when WAITING must be distinguished from null.
    */
   @Nullable
   public static SettlementPlan ensureRegionPlan(ServerLevel level, ChunkPos chunk) {
      Outcome outcome = ensureRegionPlanOutcome(level, chunk);
      return outcome.plan();
   }

   public static Outcome ensureRegionPlanOutcome(ServerLevel level, ChunkPos chunk) {
      MillenaireServerConfig cfg = MillenaireServerConfig.SERVER;
      if (!Boolean.TRUE.equals(cfg.worldgenEngineEnabled.get())) {
         return Outcome.disabled();
      }
      if (!Boolean.TRUE.equals(cfg.generateVillages.get()) && !Boolean.TRUE.equals(cfg.generateLoneBuildings.get())) {
         return Outcome.disabled();
      }

      // Never plan/reject before CultureLoader has populated the content snapshot — early
      // start-region chunks otherwise permanently store no_candidates INVALID plans.
      if (!WorldgenContentSnapshot.isReady()) {
         SettlementRegionKey region = SettlementRegionKey.fromChunk(chunk, cfg.worldgenRegionSizeChunks.getAsInt());
         WorldgenPlanningQueue.get().markRegionWaiting(region.packed());
         WorldgenPlanningQueue.get().offerPlanRegion(region, chunk);
         return Outcome.waiting();
      }

      int regionSize = cfg.worldgenRegionSizeChunks.getAsInt();
      SettlementRegionKey region = SettlementRegionKey.fromChunk(chunk, regionSize);
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      SettlementPlan existing = store.getByRegion(region);
      if (existing != null) {
         WorldgenPlanningQueue.get().clearRegionWaiting(region.packed());
         return Outcome.ready(existing);
      }

      WorldgenPlanningQueue queue = WorldgenPlanningQueue.get();
      if (!queue.tryBeginRegion(region.packed())) {
         return Outcome.inFlight();
      }
      boolean keepInFlight = false;
      try {
         Outcome outcome = evaluateRegion(level, chunk, region, regionSize, store, cfg);
         keepInFlight = outcome.result() == PlanResult.IN_FLIGHT;
         return outcome;
      } finally {
         if (!keepInFlight) {
            queue.endRegion(region.packed());
         }
      }
   }

   private static Outcome evaluateRegion(
      ServerLevel level,
      ChunkPos chunk,
      SettlementRegionKey region,
      int regionSize,
      SettlementPlanSavedData store,
      MillenaireServerConfig cfg
   ) {
      // Re-check after acquiring in-flight lock — another drain may have stored a plan.
      SettlementPlan raced = store.getByRegion(region);
      if (raced != null) {
         WorldgenPlanningQueue.get().clearRegionWaiting(region.packed());
         return Outcome.ready(raced);
      }

      long worldSeed = level.getSeed();
      WorldgenMetrics.CANDIDATES_EVALUATED.incrementAndGet();
      RandomSource posRng = WorldgenSeeds.stream(worldSeed, WorldgenSeeds.SALT_POSITION, region);
      // Stable jitter inside region (not at exact center). RNG order must stay identical.
      int size = Math.max(4, regionSize);
      int baseChunkX = region.regionX() * size;
      int baseChunkZ = region.regionZ() * size;
      int jitterX = posRng.nextInt(Math.max(1, size - 2)) + 1;
      int jitterZ = posRng.nextInt(Math.max(1, size - 2)) + 1;
      int centerChunkX = baseChunkX + jitterX;
      int centerChunkZ = baseChunkZ + jitterZ;
      int blockX = (centerChunkX << 4) + 8;
      int blockZ = (centerChunkZ << 4) + 8;
      int provisionalY = level.getSeaLevel();

      // Density gate BEFORE any world read (preserves posRng float order).
      double density = cfg.worldgenSettlementDensity.get();
      float densityRoll = posRng.nextFloat();
      boolean tryVillage = Boolean.TRUE.equals(cfg.generateVillages.get()) && densityRoll < density;
      boolean tryLone = Boolean.TRUE.equals(cfg.generateLoneBuildings.get())
         && posRng.nextFloat() < cfg.worldgenLoneBuildingDensity.get();

      if (!tryVillage && !tryLone) {
         BlockPos center = new BlockPos(blockX, provisionalY, blockZ);
         SettlementPlan empty = rejectedPlan(worldSeed, region, center, SettlementFailureReason.DISABLED_BY_CONFIG, "density_gate");
         store.putPlan(empty);
         WorldgenPlanningQueue.get().clearRegionWaiting(region.packed());
         return Outcome.ready(empty);
      }

      int spawnProt = Math.max(cfg.spawnProtectionRadius.getAsInt(), cfg.worldgenProtectedSpawnRadius.getAsInt());
      if (spawnProt > 0) {
         BlockPos spawn = level.getSharedSpawnPos();
         long dx = (long)blockX - spawn.getX();
         long dz = (long)blockZ - spawn.getZ();
         if (dx * dx + dz * dz < (long)spawnProt * spawnProt) {
            BlockPos center = new BlockPos(blockX, provisionalY, blockZ);
            SettlementPlan empty = rejectedPlan(worldSeed, region, center, SettlementFailureReason.DISABLED_BY_CONFIG, "spawn_protection");
            store.putPlan(empty);
            WorldgenPlanningQueue.get().clearRegionWaiting(region.packed());
            return Outcome.ready(empty);
         }
      }

      // World reads begin here — only already-loaded chunks.
      if (!WorldgenChunkAccess.isColumnLoaded(level, blockX, blockZ)) {
         WorldgenPlanningQueue.get().markRegionWaiting(region.packed());
         WorldgenPlanningQueue.get().offerPlanRegion(region, chunk);
         return Outcome.waiting();
      }

      IntSample surface = WorldgenTerrainSampler.surfaceYSample(level, blockX, blockZ);
      if (surface.isWaiting()) {
         WorldgenPlanningQueue.get().markRegionWaiting(region.packed());
         WorldgenPlanningQueue.get().offerPlanRegion(region, chunk);
         return Outcome.waiting();
      }
      BlockPos center = new BlockPos(blockX, surface.value(), blockZ);

      int minSpacing = Math.max(cfg.minVillageDistance.getAsInt(), cfg.worldgenMinimumSettlementSpacing.getAsInt());
      // Plan-spacing for full villages only. Lone KEY_ONLY placement near villages is handled below
      // via VillageManager.classifyLBPlacement (legacy VillageSpawnQueue semantics).
      @SuppressWarnings("unused")
      boolean planTooCloseForVillage = store.hasActiveOrPlannedNear(
         new SettlementPlanSavedData.BlockPosApprox(center.getX(), center.getZ()), minSpacing
      );

      VillageSavedData villageData = VillageSavedData.get(level);
      VillageManager manager = villageData.getVillageManager();

      long selfPriority = WorldgenSeeds.mix(worldSeed, WorldgenSeeds.SALT_ARBITER, region.packed(), center.asLong());
      for (int ox = -1; ox <= 1; ox++) {
         for (int oz = -1; oz <= 1; oz++) {
            if (ox == 0 && oz == 0) {
               continue;
            }
            SettlementRegionKey neighbor = new SettlementRegionKey(region.regionX() + ox, region.regionZ() + oz);
            SettlementPlan neighborPlan = store.getByRegion(neighbor);
            if (neighborPlan != null && !neighborPlan.status().isTerminalFailure()) {
               long ndx = (long)neighborPlan.center().getX() - center.getX();
               long ndz = (long)neighborPlan.center().getZ() - center.getZ();
               if (ndx * ndx + ndz * ndz < (long)minSpacing * minSpacing) {
                  long nPriority = WorldgenSeeds.mix(worldSeed, WorldgenSeeds.SALT_ARBITER, neighbor.packed(), neighborPlan.center().asLong());
                  if (Long.compareUnsigned(nPriority, selfPriority) > 0) {
                     SettlementPlan empty = rejectedPlan(
                        worldSeed, region, center, SettlementFailureReason.SETTLEMENT_RESERVATION_CONFLICT, "neighbor_arbiter"
                     );
                     store.putPlan(empty);
                     WorldgenPlanningQueue.get().clearRegionWaiting(region.packed());
                     return Outcome.ready(empty);
                  }
               }
            }
         }
      }

      WorldgenContentSnapshot snap = WorldgenContentSnapshot.require();
      if (!snap.hasNaturalContent()) {
         // Content claimed ready but snapshot is empty — wait rather than sticky-reject.
         WorldgenPlanningQueue.get().markRegionWaiting(region.packed());
         WorldgenPlanningQueue.get().offerPlanRegion(region, chunk);
         return Outcome.waiting();
      }
      BiomeSample biomeSample = WorldgenTerrainSampler.biomeAtSample(level, blockX, blockZ);
      if (biomeSample.isWaiting()) {
         WorldgenPlanningQueue.get().markRegionWaiting(region.packed());
         WorldgenPlanningQueue.get().offerPlanRegion(region, chunk);
         return Outcome.waiting();
      }
      Holder<Biome> biome = biomeSample.biome();
      List<VillageTypeWorldgenEntry> villageCandidates = new ArrayList<>();
      List<VillageTypeWorldgenEntry> loneCandidates = new ArrayList<>();
      boolean villageUsedFallback = false;
      boolean loneUsedFallback = false;
      if (tryVillage) {
         WorldgenContentSnapshot.BiomeMatchResult villageMatch = snap.matchingVillagesForBiome(biome);
         villageCandidates.addAll(villageMatch.entries());
         villageUsedFallback = villageMatch.usedFallback();
         if (!Boolean.TRUE.equals(cfg.generateHamlets.get())) {
            villageCandidates.removeIf(e -> !e.hamlets().isEmpty());
         }
      }
      VillageManager.LBPlacement lonePlacement = VillageManager.LBPlacement.FAR;
      if (tryLone) {
         lonePlacement = manager.classifyLBPlacement(
            center,
            cfg.minLoneBuildingDistance.getAsInt(),
            cfg.minVillageLoneBuildingDistance.getAsInt(),
            villageData.getLoneBuildingPositions()
         );
         if (lonePlacement != VillageManager.LBPlacement.BLOCKED) {
            WorldgenContentSnapshot.BiomeMatchResult loneMatch = snap.matchingLoneBuildingsForBiome(biome);
            loneCandidates.addAll(loneMatch.entries());
            loneUsedFallback = loneMatch.usedFallback();
            if (lonePlacement == VillageManager.LBPlacement.KEY_ONLY) {
               loneCandidates.removeIf(e -> !e.keyLoneBuilding());
            }
         }
      }

      RandomSource typeRng = WorldgenSeeds.stream(worldSeed, WorldgenSeeds.SALT_TYPE, region);
      VillageTypeWorldgenEntry selected = null;
      boolean lone = false;
      boolean usedFallback = false;
      boolean tooCloseForVillage = manager.isWithinMinDistance(center, minSpacing);
      if (!tooCloseForVillage && !villageCandidates.isEmpty()) {
         selected = snap.pickWeighted(villageCandidates, typeRng);
         lone = false;
         usedFallback = villageUsedFallback;
      } else if (!loneCandidates.isEmpty()) {
         selected = snap.pickWeighted(loneCandidates, typeRng);
         lone = true;
         usedFallback = loneUsedFallback;
      }
      if (selected == null) {
         SettlementFailureReason reason = lonePlacement == VillageManager.LBPlacement.BLOCKED && villageCandidates.isEmpty()
            ? SettlementFailureReason.SETTLEMENT_RESERVATION_CONFLICT
            : SettlementFailureReason.NO_COMPATIBLE_BIOME;
         SettlementPlan empty = rejectedPlan(worldSeed, region, center, reason, "no_candidates");
         store.putPlan(empty);
         WorldgenPlanningQueue.get().clearRegionWaiting(region.packed());
         return Outcome.ready(empty);
      }

      if (manager.overlapsExistingVillage(center, selected.radius())) {
         SettlementPlan empty = rejectedPlan(worldSeed, region, center, SettlementFailureReason.SETTLEMENT_RESERVATION_CONFLICT, "footprint_overlap");
         store.putPlan(empty);
         WorldgenPlanningQueue.get().clearRegionWaiting(region.packed());
         return Outcome.ready(empty);
      }

      int sampleRadius = Math.min(selected.radius(), 64);
      // Fallback culture picks have culture-level tags that won't match untagged mod biomes —
      // skip tag validity (treat as 1.0) and rely on terrain/site checks.
      boolean relaxValidity = usedFallback || org.millenaire.compat.ClimateBiomeFallback.isModpackBiome(biome);
      ValiditySample validitySample;
      if (usedFallback || !selected.hasBiomeConstraints()) {
         validitySample = ValiditySample.ready(1.0F);
      } else {
         validitySample = WorldgenTerrainSampler.biomeValidityLoaded(
            level, center, sampleRadius, selected.biomeTags(), selected.biomeKeys(), 16
         );
      }
      if (validitySample.isWaiting()) {
         WorldgenPlanningQueue.get().markRegionWaiting(region.packed());
         WorldgenPlanningQueue.get().offerPlanRegion(region, chunk);
         return Outcome.waiting();
      }
      float minValidity = Math.max(0.2F, selected.minimumBiomeValidity());
      if (relaxValidity && !usedFallback) {
         minValidity = Math.max(0.25F, selected.minimumBiomeValidity() * 0.55F);
      }
      if (validitySample.validity() < minValidity) {
         SettlementPlan empty = rejectedPlan(
            worldSeed, region, center, SettlementFailureReason.NO_COMPATIBLE_BIOME, "validity=" + validitySample.validity()
         );
         store.putPlan(empty);
         WorldgenPlanningQueue.get().clearRegionWaiting(region.packed());
         return Outcome.ready(empty);
      }

      int maxSlope = cfg.worldgenMaximumTerrainSlope.getAsInt();
      MetricsSample metricsSample = WorldgenTerrainSampler.sampleLoaded(level, center, Math.min(selected.radius(), 48), 8);
      if (metricsSample.isWaiting()) {
         WorldgenPlanningQueue.get().markRegionWaiting(region.packed());
         WorldgenPlanningQueue.get().offerPlanRegion(region, chunk);
         return Outcome.waiting();
      }
      TerrainMetrics metrics = metricsSample.metrics();
      if (!Boolean.TRUE.equals(cfg.worldgenAllowHillyTerrain.get()) && metrics.isSteep(maxSlope)) {
         SettlementPlan empty = rejectedPlan(worldSeed, region, center, SettlementFailureReason.STEEP_TERRAIN, metrics.toString());
         store.putPlan(empty);
         WorldgenPlanningQueue.get().clearRegionWaiting(region.packed());
         return Outcome.ready(empty);
      }
      if (metrics.tooWet(0.45F)) {
         SettlementPlan empty = rejectedPlan(worldSeed, region, center, SettlementFailureReason.WATER_CONFLICT, metrics.toString());
         store.putPlan(empty);
         WorldgenPlanningQueue.get().clearRegionWaiting(region.packed());
         return Outcome.ready(empty);
      }
      if (metrics.hasLava(0.05F)) {
         SettlementPlan empty = rejectedPlan(worldSeed, region, center, SettlementFailureReason.LAVA_CONFLICT, metrics.toString());
         store.putPlan(empty);
         WorldgenPlanningQueue.get().clearRegionWaiting(region.packed());
         return Outcome.ready(empty);
      }
      if (metrics.insufficientBuildable(0.35F)) {
         SettlementPlan empty = rejectedPlan(worldSeed, region, center, SettlementFailureReason.INSUFFICIENT_BUILDABLE_AREA, metrics.toString());
         store.putPlan(empty);
         WorldgenPlanningQueue.get().clearRegionWaiting(region.packed());
         return Outcome.ready(empty);
      }

      SiteValidator.LoadedVerdict siteVerdict = SiteValidator.validateLoaded(level, center, selected.radius(), lone);
      if (siteVerdict == SiteValidator.LoadedVerdict.WAITING) {
         WorldgenPlanningQueue.get().markRegionWaiting(region.packed());
         WorldgenPlanningQueue.get().offerPlanRegion(region, chunk);
         return Outcome.waiting();
      }
      if (siteVerdict == SiteValidator.LoadedVerdict.FAIL) {
         SettlementPlan empty = rejectedPlan(worldSeed, region, center, SettlementFailureReason.INVALID_TERRAIN, "site_validator");
         store.putPlan(empty);
         WorldgenPlanningQueue.get().clearRegionWaiting(region.packed());
         return Outcome.ready(empty);
      }

      if (Boolean.TRUE.equals(cfg.worldgenPreserveVanillaStructures.get())
         || Boolean.TRUE.equals(cfg.worldgenPreserveModdedStructures.get())) {
         StructureAvoidance.LoadedVerdict structureVerdict = StructureAvoidance.hasConflictLoaded(level, center, selected.radius());
         if (structureVerdict == StructureAvoidance.LoadedVerdict.WAITING) {
            WorldgenPlanningQueue.get().markRegionWaiting(region.packed());
            WorldgenPlanningQueue.get().offerPlanRegion(region, chunk);
            return Outcome.waiting();
         }
         if (structureVerdict == StructureAvoidance.LoadedVerdict.CONFLICT) {
            SettlementFailureReason reason = Boolean.TRUE.equals(cfg.worldgenPreserveModdedStructures.get())
               ? SettlementFailureReason.MODDED_STRUCTURE_CONFLICT
               : SettlementFailureReason.VANILLA_STRUCTURE_CONFLICT;
            SettlementPlan empty = rejectedPlan(worldSeed, region, center, reason, "structure_avoidance");
            store.putPlan(empty);
            WorldgenPlanningQueue.get().clearRegionWaiting(region.packed());
            return Outcome.ready(empty);
         }
      }

      RegionPlanFinalizeInput finalizeInput = RegionPlanFinalizeInput.fromSelected(
         worldSeed, region, center, selected, lone, level.getSharedSpawnPos()
      );
      if (ComputeOffload.worldgenPlan()) {
         boolean submitted = MillenaireComputePool.submit(() -> {
            SettlementPlan built = finalizeInput.buildPlan();
            return new RegionPlanComputeResult(built, region.packed(), true);
         });
         if (submitted) {
            return Outcome.inFlight();
         }
      }

      SettlementPlan plan = finalizeInput.buildPlan();
      store.putPlan(plan);
      WorldgenMetrics.PLANS_CREATED.incrementAndGet();
      WorldgenPlanningQueue.get().clearRegionWaiting(region.packed());

      if (Boolean.TRUE.equals(cfg.worldgenDetailedDiagnostics.get())) {
         LOGGER.info(
            "[Millenaire] Planned settlement {} type={} at {} region={},{} completion={}%",
            plan.planId(),
            selected.id(),
            center.toShortString(),
            region.regionX(),
            region.regionZ(),
            plan.initialCompletionPercent()
         );
      }
      return Outcome.ready(plan);
   }

   private static SettlementPlan rejectedPlan(
      long worldSeed, SettlementRegionKey region, BlockPos center, SettlementFailureReason reason, String detail
   ) {
      UUID id = WorldgenSeeds.settlementUuid(worldSeed, region, "rejected:" + reason.name());
      SettlementPlan plan = new SettlementPlan(
         id,
         id,
         region,
         worldSeed,
         ResourceLocation.fromNamespaceAndPath("millenaire", "none"),
         ResourceLocation.fromNamespaceAndPath("millenaire", "none"),
         center,
         Rotation.NONE,
         16,
         0,
         false,
         null,
         ""
      );
      plan.markInvalid(reason, detail);
      WorldgenMetrics.PLANS_REJECTED.incrementAndGet();
      WorldgenMetrics.recordReject(reason, detail);
      if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenDetailedDiagnostics.get())) {
         LOGGER.info(
            "[Millenaire] Rejected region {},{} at {} reason={} detail={}",
            region.regionX(),
            region.regionZ(),
            center.toShortString(),
            reason,
            detail
         );
      }
      return plan;
   }

   public static List<SettlementPlan> plansNear(ServerLevel level, BlockPos pos, int radius) {
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      List<SettlementPlan> out = new ArrayList<>();
      long r2 = (long)radius * radius;
      for (SettlementPlan plan : store.allPlans()) {
         long dx = (long)plan.center().getX() - pos.getX();
         long dz = (long)plan.center().getZ() - pos.getZ();
         if (dx * dx + dz * dz <= r2) {
            out.add(plan);
         }
      }
      out.sort(Comparator.comparingLong(p -> {
         long dx = (long)p.center().getX() - pos.getX();
         long dz = (long)p.center().getZ() - pos.getZ();
         return dx * dx + dz * dz;
      }));
      return out;
   }
}
