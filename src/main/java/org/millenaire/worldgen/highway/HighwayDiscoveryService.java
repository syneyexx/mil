package org.millenaire.worldgen.highway;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.worldgen.materialize.MaterializationTickBudget;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;
import org.millenaire.worldgen.plan.SettlementPlanStatus;
import org.slf4j.Logger;

/**
 * Enqueue-on-chunk-load / tick-drain discovery of highway endpoints.
 * Never force-loads; never uses villager POIs.
 */
public final class HighwayDiscoveryService {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final TagKey<Structure> HIGHWAY_ENDPOINT = TagKey.create(
      Registries.STRUCTURE,
      ResourceLocation.fromNamespaceAndPath("millenaire", "highway_endpoint")
   );
   private static final int MAX_CHUNK_PROBES_PER_TICK = 4;
   private static final int MAX_MILLENAIRE_SCAN_PER_TICK = 16;
   private static final int DEDUP_RADIUS = 48;

   private HighwayDiscoveryService() {
   }

   public static boolean isEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.worldgenSandHighways.get());
      } catch (IllegalStateException e) {
         return false;
      }
   }

   public static void enqueueChunk(ServerLevel level, ChunkPos chunk) {
      if (!isEnabled() || level == null || chunk == null || level.dimension() != Level.OVERWORLD) {
         return;
      }
      HighwayNodeSavedData.get(level).enqueueChunk(chunk);
   }

   public static void onVillageActivated(ServerLevel level, Village village) {
      if (!isEnabled() || level == null || village == null || village.getCenter() == null) {
         return;
      }
      if (shouldSkipMillenaireVillage(village)) {
         return;
      }
      upsertMillenaireVillage(level, village, level.getGameTime());
   }

   public static void tick(ServerLevel level) {
      if (!isEnabled() || level == null || level.dimension() != Level.OVERWORLD) {
         return;
      }
      if (!MaterializationTickBudget.hasTimeAndOps()) {
         return;
      }
      HighwayNodeSavedData store = HighwayNodeSavedData.get(level);
      drainChunkProbes(level, store);
      scanMillenaireSources(level, store);
      refreshFactionHubs(level, store);
   }

   private static void drainChunkProbes(ServerLevel level, HighwayNodeSavedData store) {
      boolean vanilla = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.highwayDiscoverVanilla.get());
      boolean mod = Boolean.TRUE.equals(MillenaireServerConfig.SERVER.highwayDiscoverMod.get());
      if (!vanilla && !mod) {
         store.drainPendingChunks(MAX_CHUNK_PROBES_PER_TICK);
         return;
      }
      for (ChunkPos chunk : store.drainPendingChunks(MAX_CHUNK_PROBES_PER_TICK)) {
         if (!MaterializationTickBudget.hasTimeAndOps()) {
            store.enqueueChunk(chunk);
            return;
         }
         if (!level.hasChunk(chunk.x, chunk.z)) {
            continue;
         }
         MaterializationTickBudget.tryConsumeScan();
         if (vanilla) {
            probeVanillaVillage(level, store, chunk);
         }
         if (mod) {
            probeModEndpoints(level, store, chunk);
         }
      }
   }

   private static void probeVanillaVillage(ServerLevel level, HighwayNodeSavedData store, ChunkPos chunk) {
      StructureStart start = findVillageStart(level, chunk);
      if (start == null || !start.isValid()) {
         return;
      }
      BlockPos center = start.getBoundingBox().getCenter();
      if (store.hasPreferredNear(new HighwayNodeSavedData.BlockPosApprox(center.getX(), center.getZ()), DEDUP_RADIUS)) {
         return;
      }
      String key = "vanilla:" + SectionPos.blockToSectionCoord(center.getX()) + "," + SectionPos.blockToSectionCoord(center.getZ());
      int radius = Math.max(24, Math.max(start.getBoundingBox().getXSpan(), start.getBoundingBox().getZSpan()) / 2);
      HighwayNode node = new HighwayNode(key, HighwayNodeKind.VANILLA_VILLAGE, center, radius);
      node.setDiscoveredAtGameTime(level.getGameTime());
      if (store.upsert(node)) {
         LOGGER.debug("[Millenaire] Highway discovered vanilla village at {}", center.toShortString());
      }
   }

   private static StructureStart findVillageStart(ServerLevel level, ChunkPos chunk) {
      BlockPos probe = chunk.getMiddleBlockPosition(level.getSeaLevel());
      StructureStart start = level.structureManager().getStructureWithPieceAt(probe, StructureTags.VILLAGE);
      if (start != null && start.isValid()) {
         return start;
      }
      for (int dx = 0; dx <= 15; dx += 15) {
         for (int dz = 0; dz <= 15; dz += 15) {
            BlockPos corner = new BlockPos(chunk.getBlockX(dx), level.getSeaLevel(), chunk.getBlockZ(dz));
            start = level.structureManager().getStructureWithPieceAt(corner, StructureTags.VILLAGE);
            if (start != null && start.isValid()) {
               return start;
            }
         }
      }
      return null;
   }

   private static void probeModEndpoints(ServerLevel level, HighwayNodeSavedData store, ChunkPos chunk) {
      Optional<HolderSet.Named<Structure>> tag = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(HIGHWAY_ENDPOINT);
      if (tag.isEmpty()) {
         return;
      }
      BlockPos probe = chunk.getMiddleBlockPosition(level.getSeaLevel());
      List<BlockPos> probes = List.of(
         probe,
         new BlockPos(chunk.getBlockX(0), level.getSeaLevel(), chunk.getBlockZ(0)),
         new BlockPos(chunk.getBlockX(15), level.getSeaLevel(), chunk.getBlockZ(0)),
         new BlockPos(chunk.getBlockX(0), level.getSeaLevel(), chunk.getBlockZ(15)),
         new BlockPos(chunk.getBlockX(15), level.getSeaLevel(), chunk.getBlockZ(15))
      );
      for (Holder<Structure> holder : tag.get()) {
         Structure structure = holder.value();
         ResourceLocation id = holder.unwrapKey().map(k -> k.location()).orElse(null);
         for (BlockPos p : probes) {
            StructureStart start = level.structureManager().getStructureAt(p, structure);
            if (start == null || !start.isValid()) {
               continue;
            }
            BlockPos center = start.getBoundingBox().getCenter();
            if (store.hasPreferredNear(new HighwayNodeSavedData.BlockPosApprox(center.getX(), center.getZ()), DEDUP_RADIUS)) {
               return;
            }
            String structPart = id != null ? id.toString() : "unknown";
            long packed = (((long)center.getX()) << 32) ^ (center.getZ() & 0xffffffffL);
            String key = "mod:" + structPart + "@" + Long.toHexString(packed);
            int radius = Math.max(24, Math.max(start.getBoundingBox().getXSpan(), start.getBoundingBox().getZSpan()) / 2);
            HighwayNode node = new HighwayNode(key, HighwayNodeKind.MOD_SETTLEMENT, center, radius);
            node.setStructureId(id);
            node.setDiscoveredAtGameTime(level.getGameTime());
            if (store.upsert(node)) {
               LOGGER.debug("[Millenaire] Highway discovered mod settlement {} at {}", structPart, center.toShortString());
            }
            return;
         }
      }
   }

   private static void scanMillenaireSources(ServerLevel level, HighwayNodeSavedData store) {
      if (!Boolean.TRUE.equals(MillenaireServerConfig.SERVER.highwayDiscoverMillenaire.get())) {
         return;
      }
      List<Village> villages = new ArrayList<>(VillageSavedData.get(level).getVillageManager().getAllVillages());
      villages.sort(Comparator.comparing(v -> v.getId().uuid()));
      int cursor = store.millenaireScanCursor();
      if (cursor >= villages.size()) {
         cursor = 0;
      }
      int scanned = 0;
      while (scanned < MAX_MILLENAIRE_SCAN_PER_TICK && !villages.isEmpty() && MaterializationTickBudget.hasTimeAndOps()) {
         Village village = villages.get(cursor % villages.size());
         cursor++;
         scanned++;
         MaterializationTickBudget.tryConsumeScan();
         if (shouldSkipMillenaireVillage(village)) {
            continue;
         }
         upsertMillenaireVillage(level, village, level.getGameTime());
      }
      store.setMillenaireScanCursor(cursor);

      for (SettlementPlan plan : SettlementPlanSavedData.get(level).allPlans()) {
         if (!MaterializationTickBudget.hasTimeAndOps()) {
            break;
         }
         SettlementPlanStatus status = plan.status();
         if (status != SettlementPlanStatus.MATERIALIZING
            && status != SettlementPlanStatus.PHYSICAL_COMPLETE
            && status != SettlementPlanStatus.ACTIVATING
            && status != SettlementPlanStatus.ACTIVE) {
            continue;
         }
         if (plan.loneBuilding()) {
            continue;
         }
         UUID villageUuid = plan.villageUuid();
         if (villageUuid != null) {
            Village live = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(villageUuid));
            if (live != null) {
               continue;
            }
         }
         String key = "plan:" + plan.planId();
         HighwayNode node = new HighwayNode(key, HighwayNodeKind.MILLENAIRE_PLAN, plan.center(), plan.radius());
         node.setPlanId(plan.planId());
         node.setMillenaireVillageId(villageUuid);
         node.setDiscoveredAtGameTime(level.getGameTime());
         store.upsert(node);
         MaterializationTickBudget.tryConsumeScan();
      }
   }

   private static void upsertMillenaireVillage(ServerLevel level, Village village, long gameTime) {
      String key = "millenaire:" + village.getId().uuid();
      int radius = 90;
      if (village.getVillageTypeId() != null) {
         VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
         if (vt != null) {
            radius = vt.radius();
         }
      }
      HighwayNode node = new HighwayNode(key, HighwayNodeKind.MILLENAIRE, village.getCenter(), Math.max(32, radius));
      node.setMillenaireVillageId(village.getId().uuid());
      node.setDiscoveredAtGameTime(gameTime);
      // Merge plan-key into village key when present.
      HighwayNodeSavedData store = HighwayNodeSavedData.get(level);
      for (SettlementPlan plan : SettlementPlanSavedData.get(level).allPlans()) {
         if (village.getId().uuid().equals(plan.villageUuid())) {
            node.setPlanId(plan.planId());
            HighwayNode planNode = store.get("plan:" + plan.planId());
            if (planNode != null) {
               node.setLinkDegree(Math.max(node.linkDegree(), planNode.linkDegree()));
            }
            break;
         }
      }
      store.upsert(node);
   }

   private static boolean shouldSkipMillenaireVillage(Village village) {
      if (village.getCultureId() != null) {
         var culture = ModCultures.getCulture(village.getCultureId());
         if (culture != null && culture.excludeFromTrains()) {
            return true;
         }
      }
      if (village.getVillageTypeId() != null) {
         VillageType type = ModCultures.getVillageType(village.getVillageTypeId());
         if (type != null && type.loneBuilding()) {
            return !Boolean.TRUE.equals(MillenaireServerConfig.SERVER.highwayIncludeLoneBuildings.get());
         }
      }
      return false;
   }

   private static void refreshFactionHubs(ServerLevel level, HighwayNodeSavedData store) {
      Map<String, HighwayNode> bestByFaction = new HashMap<>();
      for (HighwayNode node : store.allNodes()) {
         if (node.kind() != HighwayNodeKind.MILLENAIRE || node.millenaireVillageId() == null) {
            continue;
         }
         Village village = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(node.millenaireVillageId()));
         if (village == null || village.getFactionId() == null) {
            continue;
         }
         Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(village.getFactionId());
         if (faction == null) {
            continue;
         }
         String fid = faction.getId().toString();
         HighwayNode best = bestByFaction.get(fid);
         if (best == null || node.radiusHint() > best.radiusHint()) {
            bestByFaction.put(fid, node);
         }
      }
      for (HighwayNode node : store.allNodes()) {
         boolean hub = bestByFaction.containsValue(node);
         if (node.factionHub() != hub) {
            node.setFactionHub(hub);
            store.bumpTopologyRevision();
            store.setDirty();
         }
      }
   }
}
