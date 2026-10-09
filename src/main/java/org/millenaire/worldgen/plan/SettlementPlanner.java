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
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.NameLists;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.world.StructureAvoidance;
import org.millenaire.worldgen.WorldgenMetrics;
import org.millenaire.worldgen.content.VillageTypeWorldgenEntry;
import org.millenaire.worldgen.content.WorldgenContentSnapshot;
import org.millenaire.worldgen.terrain.WorldgenTerrainSampler;
import org.millenaire.worldgen.terrain.WorldgenTerrainSampler.TerrainMetrics;
import org.slf4j.Logger;

/**
 * Stage A — deterministic region candidate selection. No block writes.
 */
public final class SettlementPlanner {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final Rotation[] ROTATIONS = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};

   private SettlementPlanner() {
   }

   /**
    * Ensure a plan exists for the region containing {@code chunk}. Idempotent.
    */
   @Nullable
   public static SettlementPlan ensureRegionPlan(ServerLevel level, ChunkPos chunk) {
      MillenaireServerConfig cfg = MillenaireServerConfig.SERVER;
      if (!Boolean.TRUE.equals(cfg.worldgenEngineEnabled.get())) {
         return null;
      }
      if (!Boolean.TRUE.equals(cfg.generateVillages.get()) && !Boolean.TRUE.equals(cfg.generateLoneBuildings.get())) {
         return null;
      }

      int regionSize = cfg.worldgenRegionSizeChunks.getAsInt();
      SettlementRegionKey region = SettlementRegionKey.fromChunk(chunk, regionSize);
      SettlementPlanSavedData store = SettlementPlanSavedData.get(level);
      SettlementPlan existing = store.getByRegion(region);
      if (existing != null) {
         return existing;
      }

      long worldSeed = level.getSeed();
      WorldgenMetrics.CANDIDATES_EVALUATED.incrementAndGet();
      RandomSource posRng = WorldgenSeeds.stream(worldSeed, WorldgenSeeds.SALT_POSITION, region);
      // Stable jitter inside region (not at exact center)
      int size = Math.max(4, regionSize);
      int baseChunkX = region.regionX() * size;
      int baseChunkZ = region.regionZ() * size;
      int jitterX = posRng.nextInt(Math.max(1, size - 2)) + 1;
      int jitterZ = posRng.nextInt(Math.max(1, size - 2)) + 1;
      int centerChunkX = baseChunkX + jitterX;
      int centerChunkZ = baseChunkZ + jitterZ;
      int blockX = (centerChunkX << 4) + 8;
      int blockZ = (centerChunkZ << 4) + 8;
      int surfaceY = WorldgenTerrainSampler.surfaceY(level, blockX, blockZ);
      BlockPos center = new BlockPos(blockX, surfaceY, blockZ);

      // Density gate: not every region hosts a settlement
      double density = cfg.worldgenSettlementDensity.get();
      float densityRoll = posRng.nextFloat();
      boolean tryVillage = Boolean.TRUE.equals(cfg.generateVillages.get()) && densityRoll < density;
      boolean tryLone = Boolean.TRUE.equals(cfg.generateLoneBuildings.get())
         && posRng.nextFloat() < cfg.worldgenLoneBuildingDensity.get();

      if (!tryVillage && !tryLone) {
         SettlementPlan empty = rejectedPlan(worldSeed, region, center, SettlementFailureReason.DISABLED_BY_CONFIG, "density_gate");
         store.putPlan(empty);
         return empty;
      }

      int spawnProt = Math.max(cfg.spawnProtectionRadius.getAsInt(), cfg.worldgenProtectedSpawnRadius.getAsInt());
      BlockPos spawn = level.getSharedSpawnPos();
      long dx = (long)center.getX() - spawn.getX();
      long dz = (long)center.getZ() - spawn.getZ();
      if (dx * dx + dz * dz < (long)spawnProt * spawnProt) {
         SettlementPlan empty = rejectedPlan(worldSeed, region, center, SettlementFailureReason.DISABLED_BY_CONFIG, "spawn_protection");
         store.putPlan(empty);
         return empty;
      }

      int minSpacing = Math.max(cfg.minVillageDistance.getAsInt(), cfg.worldgenMinimumSettlementSpacing.getAsInt());
      if (store.hasActiveOrPlannedNear(new SettlementPlanSavedData.BlockPosApprox(center.getX(), center.getZ()), minSpacing)) {
         SettlementPlan empty = rejectedPlan(worldSeed, region, center, SettlementFailureReason.SETTLEMENT_RESERVATION_CONFLICT, "plan_spacing");
         store.putPlan(empty);
         return empty;
      }

      VillageSavedData villageData = VillageSavedData.get(level);
      VillageManager manager = villageData.getVillageManager();
      if (manager.isWithinMinDistance(center, minSpacing)) {
         SettlementPlan empty = rejectedPlan(worldSeed, region, center, SettlementFailureReason.SETTLEMENT_RESERVATION_CONFLICT, "existing_village");
         store.putPlan(empty);
         return empty;
      }

      // Neighbor arbitration: if adjacent regions claim overlapping centers, highest priority wins.
      // Priority is immutable hash of candidate identity — not generation order.
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
                     return empty;
                  }
               }
            }
         }
      }

      WorldgenContentSnapshot snap = WorldgenContentSnapshot.require();
      Holder<Biome> biome = WorldgenTerrainSampler.biomeAt(level, blockX, blockZ);
      List<VillageTypeWorldgenEntry> villageCandidates = new ArrayList<>();
      List<VillageTypeWorldgenEntry> loneCandidates = new ArrayList<>();
      if (tryVillage) {
         villageCandidates.addAll(snap.matchingVillages(biome::is));
         if (!Boolean.TRUE.equals(cfg.generateHamlets.get())) {
            villageCandidates.removeIf(e -> !e.hamlets().isEmpty());
         }
      }
      if (tryLone) {
         loneCandidates.addAll(snap.matchingLoneBuildings(biome::is));
      }

      // Prefer village over lone when both eligible; type stream is separate from position stream.
      RandomSource typeRng = WorldgenSeeds.stream(worldSeed, WorldgenSeeds.SALT_TYPE, region);
      VillageTypeWorldgenEntry selected = null;
      boolean lone = false;
      if (!villageCandidates.isEmpty()) {
         selected = snap.pickWeighted(villageCandidates, typeRng);
         lone = false;
      } else if (!loneCandidates.isEmpty()) {
         selected = snap.pickWeighted(loneCandidates, typeRng);
         lone = true;
      }
      if (selected == null) {
         SettlementPlan empty = rejectedPlan(worldSeed, region, center, SettlementFailureReason.NO_COMPATIBLE_BIOME, "no_candidates");
         store.putPlan(empty);
         return empty;
      }

      float validity = WorldgenTerrainSampler.biomeValidity(
         level, center, Math.min(selected.radius(), 64), selected.biomeTags(), 16
      );
      float minValidity = Math.max(0.2F, selected.minimumBiomeValidity());
      if (validity < minValidity) {
         SettlementPlan empty = rejectedPlan(
            worldSeed, region, center, SettlementFailureReason.NO_COMPATIBLE_BIOME, "validity=" + validity
         );
         store.putPlan(empty);
         return empty;
      }

      int maxSlope = cfg.worldgenMaximumTerrainSlope.getAsInt();
      TerrainMetrics metrics = WorldgenTerrainSampler.sample(level, center, Math.min(selected.radius(), 48), 8);
      if (!Boolean.TRUE.equals(cfg.worldgenAllowHillyTerrain.get()) && metrics.isSteep(maxSlope)) {
         SettlementPlan empty = rejectedPlan(worldSeed, region, center, SettlementFailureReason.STEEP_TERRAIN, metrics.toString());
         store.putPlan(empty);
         return empty;
      }
      if (metrics.tooWet(0.45F)) {
         SettlementPlan empty = rejectedPlan(worldSeed, region, center, SettlementFailureReason.WATER_CONFLICT, metrics.toString());
         store.putPlan(empty);
         return empty;
      }
      if (metrics.hasLava(0.05F)) {
         SettlementPlan empty = rejectedPlan(worldSeed, region, center, SettlementFailureReason.LAVA_CONFLICT, metrics.toString());
         store.putPlan(empty);
         return empty;
      }
      if (metrics.insufficientBuildable(0.35F)) {
         SettlementPlan empty = rejectedPlan(worldSeed, region, center, SettlementFailureReason.INSUFFICIENT_BUILDABLE_AREA, metrics.toString());
         store.putPlan(empty);
         return empty;
      }

      if (Boolean.TRUE.equals(cfg.worldgenPreserveVanillaStructures.get())
         && StructureAvoidance.hasConflict(level, center, selected.radius())) {
         SettlementPlan empty = rejectedPlan(worldSeed, region, center, SettlementFailureReason.VANILLA_STRUCTURE_CONFLICT, "structure_avoidance");
         store.putPlan(empty);
         return empty;
      }

      UUID planId = WorldgenSeeds.settlementUuid(worldSeed, region, selected.id().toString());
      UUID villageUuid = planId;
      RandomSource rotRng = WorldgenSeeds.stream(worldSeed, WorldgenSeeds.SALT_ROTATION, region);
      Rotation orientation = ROTATIONS[rotRng.nextInt(ROTATIONS.length)];
      RandomSource compRng = WorldgenSeeds.stream(worldSeed, WorldgenSeeds.SALT_COMPLETION, region);
      int completion = computeInitialCompletion(level, center, compRng);
      String name = pickName(selected, worldSeed, region);

      SettlementPlan plan = new SettlementPlan(
         planId,
         villageUuid,
         region,
         worldSeed,
         selected.cultureId(),
         selected.id(),
         center,
         orientation,
         selected.radius(),
         completion,
         lone,
         null,
         name
      );
      plan.reserveFootprintChunks();
      plan.trySetStatus(SettlementPlanStatus.PLANNED);
      store.putPlan(plan);
      WorldgenMetrics.PLANS_CREATED.incrementAndGet();

      if (Boolean.TRUE.equals(cfg.worldgenDetailedDiagnostics.get())) {
         LOGGER.info(
            "[Millenaire] Planned settlement {} type={} at {} region={},{} completion={}%",
            plan.planId(),
            selected.id(),
            center.toShortString(),
            region.regionX(),
            region.regionZ(),
            completion
         );
      }
      return plan;
   }

   private static int computeInitialCompletion(ServerLevel level, BlockPos center, RandomSource rng) {
      MillenaireServerConfig cfg = MillenaireServerConfig.SERVER;
      if (!Boolean.TRUE.equals(cfg.worldgenAllowPartialInitialCompletion.get())) {
         return 0;
      }
      BlockPos spawn = level.getSharedSpawnPos();
      double dist = Math.sqrt(center.distToCenterSqr(spawn.getX(), center.getY(), spawn.getZ()));
      int minD = cfg.completionMinDistance.getAsInt();
      int maxD = Math.max(minD + 1, cfg.completionMaxDistance.getAsInt());
      int maxPct = cfg.completionMaxPercentage.getAsInt();
      if (dist <= minD) {
         return 0;
      }
      double t = Math.min(1.0, (dist - minD) / (double)(maxD - minD));
      int base = (int)Math.round(t * maxPct);
      int jitter = rng.nextInt(Math.max(1, maxPct / 5 + 1));
      return Math.max(0, Math.min(maxPct, base + jitter - maxPct / 10));
   }

   private static String pickName(VillageTypeWorldgenEntry selected, long worldSeed, SettlementRegionKey region) {
      RandomSource nameRng = WorldgenSeeds.stream(worldSeed, WorldgenSeeds.SALT_NAME, region);
      String listKey = selected.nameList();
      if (listKey != null) {
         NameLists lists = ModCultures.getNameLists(selected.cultureId());
         if (lists != null) {
            List<String> names = lists.lists().get(listKey);
            if (names != null && !names.isEmpty()) {
               return names.get(nameRng.nextInt(names.size()));
            }
         }
      }
      return selected.id().getPath();
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
