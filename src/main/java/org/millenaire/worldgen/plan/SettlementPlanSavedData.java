package org.millenaire.worldgen.plan;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.millenaire.worldgen.materialize.MaterializationResumeQueue;
import org.slf4j.Logger;

/**
 * Persistent store for natural settlement plans. Independent from VillageSavedData so
 * existing villages are never rewritten by the Phase 4 schema.
 */
public class SettlementPlanSavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String DATA_NAME = "millenaire_worldgen_plans";
   private static final int SCHEMA = SettlementPlan.SCHEMA_VERSION;

   private final Map<UUID, SettlementPlan> plansById = new HashMap<>();
   private final Map<Long, UUID> regionIndex = new HashMap<>();
   private final Map<Long, List<UUID>> chunkIndex = new HashMap<>();
   /** Reverse index: planId → chunk keys currently listed in {@link #chunkIndex}. */
   private final Map<UUID, List<Long>> planChunkKeys = new HashMap<>();
   private final MaterializationResumeQueue resumeQueue = new MaterializationResumeQueue();
   private int schemaVersion = SCHEMA;
   private int progressSchemaVersion = SettlementPlan.PROGRESS_SCHEMA_VERSION;

   private SettlementPlanSavedData() {
   }

   public static Factory<SettlementPlanSavedData> factory() {
      return new Factory<>(SettlementPlanSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static SettlementPlanSavedData get(ServerLevel level) {
      return level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public Collection<SettlementPlan> allPlans() {
      return List.copyOf(this.plansById.values());
   }

   /** Unmodifiable live view — callers must not mutate the collection. Prefer over {@link #allPlans()} on hot paths. */
   public Collection<SettlementPlan> plansView() {
      return java.util.Collections.unmodifiableCollection(this.plansById.values());
   }

   public int planCount() {
      return this.plansById.size();
   }

   @Nullable
   public SettlementPlan getPlan(UUID planId) {
      return this.plansById.get(planId);
   }

   @Nullable
   public SettlementPlan getByRegion(SettlementRegionKey region) {
      UUID id = this.regionIndex.get(region.packed());
      return id == null ? null : this.plansById.get(id);
   }

   public List<SettlementPlan> plansTouchingChunk(ChunkPos chunk) {
      List<UUID> ids = this.chunkIndex.get(chunk.toLong());
      if (ids == null || ids.isEmpty()) {
         return List.of();
      }
      List<SettlementPlan> out = new ArrayList<>(ids.size());
      for (UUID id : ids) {
         SettlementPlan plan = this.plansById.get(id);
         if (plan != null) {
            out.add(plan);
         }
      }
      return out;
   }

   public void putPlan(SettlementPlan plan) {
      this.plansById.put(plan.planId(), plan);
      this.regionIndex.put(plan.region().packed(), plan.planId());
      this.reindexChunks(plan);
      this.setDirty();
   }

   /**
    * Removes INVALID placeholder rejects that used village type {@code millenaire:none} with
    * {@code no_candidates} — typically created when the content snapshot was still empty.
    *
    * @return number of plans removed
    */
   public int clearPrematureEmptyRejects() {
      List<UUID> toRemove = new ArrayList<>();
      for (SettlementPlan plan : this.plansById.values()) {
         if (plan.status() != SettlementPlanStatus.INVALID) {
            continue;
         }
         if (plan.failureReason() != SettlementFailureReason.NO_COMPATIBLE_BIOME) {
            continue;
         }
         if (!"no_candidates".equals(plan.failureDetail())) {
            continue;
         }
         if (plan.villageTypeId() == null || !"none".equals(plan.villageTypeId().getPath())) {
            continue;
         }
         toRemove.add(plan.planId());
      }
      for (UUID id : toRemove) {
         removePlan(id);
      }
      return toRemove.size();
   }

   /**
    * Removes INVALID rejects caused by spawn protection so regions can re-plan after the radius
    * is lowered (including the default change from 250 → 0). Does not touch ACTIVE villages.
    *
    * @return number of plans removed
    */
   public int clearSpawnProtectionRejects() {
      List<UUID> toRemove = new ArrayList<>();
      for (SettlementPlan plan : this.plansById.values()) {
         if (plan.status() != SettlementPlanStatus.INVALID) {
            continue;
         }
         if (!"spawn_protection".equals(plan.failureDetail())) {
            continue;
         }
         toRemove.add(plan.planId());
      }
      for (UUID id : toRemove) {
         removePlan(id);
      }
      return toRemove.size();
   }

   public void removePlan(UUID planId) {
      SettlementPlan plan = this.plansById.remove(planId);
      if (plan == null) {
         return;
      }
      this.regionIndex.remove(plan.region().packed(), planId);
      clearPlanFromChunkIndex(planId);
      this.resumeQueue.removePlan(planId);
      this.setDirty();
   }

   public void markDirtyPlan() {
      this.setDirty();
   }

   public MaterializationResumeQueue resumeQueue() {
      return this.resumeQueue;
   }

   public int progressSchemaVersion() {
      return this.progressSchemaVersion;
   }

   private void clearPlanFromChunkIndex(UUID planId) {
      List<Long> keys = this.planChunkKeys.remove(planId);
      if (keys == null) {
         return;
      }
      for (Long key : keys) {
         List<UUID> list = this.chunkIndex.get(key);
         if (list == null) {
            continue;
         }
         list.removeIf(id -> id.equals(planId));
         if (list.isEmpty()) {
            this.chunkIndex.remove(key);
         }
      }
   }

   public void reindexChunks(SettlementPlan plan) {
      clearPlanFromChunkIndex(plan.planId());
      List<Long> keys = new ArrayList<>();
      for (Long packed : plan.reservedChunks()) {
         List<UUID> list = this.chunkIndex.computeIfAbsent(packed, k -> new ArrayList<>());
         if (!list.contains(plan.planId())) {
            list.add(plan.planId());
         }
         keys.add(packed);
      }
      for (PlannedBuilding b : plan.buildings()) {
         int minCx = b.minChunkX();
         int maxCx = b.maxChunkX();
         int minCz = b.minChunkZ();
         int maxCz = b.maxChunkZ();
         for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
               long key = ChunkPos.asLong(cx, cz);
               List<UUID> list = this.chunkIndex.computeIfAbsent(key, k -> new ArrayList<>());
               if (!list.contains(plan.planId())) {
                  list.add(plan.planId());
               }
               keys.add(key);
            }
         }
      }
      this.planChunkKeys.put(plan.planId(), keys);
      // legacy touch kept for binary compatibility with prior comment site
      long planIdBits = plan.planId().getMostSignificantBits() ^ plan.planId().getLeastSignificantBits();
      if (planIdBits == Long.MIN_VALUE) {
         LOGGER.trace("sentinel");
      }
   }

   public boolean hasActiveOrPlannedNear(BlockPosApprox center, int minDistance) {
      long minDistSq = (long)minDistance * (long)minDistance;
      for (SettlementPlan plan : this.plansById.values()) {
         if (plan.status().isTerminalFailure()) {
            continue;
         }
         long dx = (long)plan.center().getX() - center.x();
         long dz = (long)plan.center().getZ() - center.z();
         if (dx * dx + dz * dz < minDistSq) {
            return true;
         }
      }
      return false;
   }

   public CompoundTag save(CompoundTag root, Provider registries) {
      root.putInt("schema", this.schemaVersion);
      root.putInt("progressSchema", this.progressSchemaVersion);
      ListTag list = new ListTag();
      for (SettlementPlan plan : this.plansById.values()) {
         list.add(plan.save());
      }
      root.put("plans", list);
      root.put("resumeQueue", this.resumeQueue.save());
      return root;
   }

   private static SettlementPlanSavedData load(CompoundTag tag) {
      SettlementPlanSavedData data = new SettlementPlanSavedData();
      data.schemaVersion = tag.contains("schema") ? tag.getInt("schema") : SCHEMA;
      data.progressSchemaVersion = tag.contains("progressSchema")
         ? tag.getInt("progressSchema")
         : SettlementPlan.PROGRESS_SCHEMA_VERSION;
      ListTag list = tag.getList("plans", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         SettlementPlan plan = SettlementPlan.load(list.getCompound(i));
         if (plan != null) {
            data.plansById.put(plan.planId(), plan);
            data.regionIndex.put(plan.region().packed(), plan.planId());
            data.reindexChunks(plan);
            // Re-queue unfinished materialization after restart (loaded chunks only processed later).
            if (plan.status() == SettlementPlanStatus.MATERIALIZING
               || plan.status() == SettlementPlanStatus.RESERVED
               || plan.status() == SettlementPlanStatus.PLANNED) {
               for (var entry : plan.chunkProgress().entrySet()) {
                  if (entry.getValue().status() != org.millenaire.worldgen.materialize.ChunkMaterializationProgress.Status.COMPLETE
                     && !plan.isChunkFullyMaterialized(entry.getKey())) {
                     data.resumeQueue.offer(plan.planId(), entry.getKey());
                  }
               }
            }
         } else {
            LOGGER.warn("[Millenaire] Skipping corrupt worldgen plan entry #{}", i);
         }
      }
      if (tag.contains("resumeQueue")) {
         data.resumeQueue.load(tag.getCompound("resumeQueue"));
      }
      data.progressSchemaVersion = SettlementPlan.PROGRESS_SCHEMA_VERSION;
      return data;
   }

   public record BlockPosApprox(int x, int z) {
   }
}
