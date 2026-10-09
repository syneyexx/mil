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
   private int schemaVersion = SCHEMA;

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

   public void markDirtyPlan() {
      this.setDirty();
   }

   public void reindexChunks(SettlementPlan plan) {
      long planIdBits = plan.planId().getMostSignificantBits() ^ plan.planId().getLeastSignificantBits();
      this.chunkIndex.values().forEach(list -> list.removeIf(id -> id.equals(plan.planId())));
      for (Long packed : plan.reservedChunks()) {
         this.chunkIndex.computeIfAbsent(packed, k -> new ArrayList<>()).add(plan.planId());
      }
      for (PlannedBuilding b : plan.buildings()) {
         int minCx = b.origin().getX() >> 4;
         int maxCx = (b.origin().getX() + Math.max(b.width(), 1)) >> 4;
         int minCz = b.origin().getZ() >> 4;
         int maxCz = (b.origin().getZ() + Math.max(b.depth(), 1)) >> 4;
         for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
               long key = ChunkPos.asLong(cx, cz);
               List<UUID> list = this.chunkIndex.computeIfAbsent(key, k -> new ArrayList<>());
               if (!list.contains(plan.planId())) {
                  list.add(plan.planId());
               }
            }
         }
      }
      // touch planIdBits to silence unused if optimizer complains in some builds
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
      ListTag list = new ListTag();
      for (SettlementPlan plan : this.plansById.values()) {
         list.add(plan.save());
      }
      root.put("plans", list);
      return root;
   }

   private static SettlementPlanSavedData load(CompoundTag tag) {
      SettlementPlanSavedData data = new SettlementPlanSavedData();
      data.schemaVersion = tag.contains("schema") ? tag.getInt("schema") : SCHEMA;
      ListTag list = tag.getList("plans", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         SettlementPlan plan = SettlementPlan.load(list.getCompound(i));
         if (plan != null) {
            data.plansById.put(plan.planId(), plan);
            data.regionIndex.put(plan.region().packed(), plan.planId());
            data.reindexChunks(plan);
         } else {
            LOGGER.warn("[Millenaire] Skipping corrupt worldgen plan entry #{}", i);
         }
      }
      return data;
   }

   public record BlockPosApprox(int x, int z) {
   }
}
