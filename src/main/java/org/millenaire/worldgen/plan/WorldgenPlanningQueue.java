package org.millenaire.worldgen.plan;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;

/**
 * In-memory, per-server planning work queue. Chunk-load handlers only offer compact keys;
 * the server tick drains a bounded amount of work. Cleared on server stop / world change.
 *
 * <p>PLAN_REGION is keyed by region only (not by trigger chunk) so exploring a region cannot
 * enqueue hundreds of duplicate waiting plan jobs.
 */
public final class WorldgenPlanningQueue {
   public enum Kind {
      LAYOUT,
      MATERIALIZE_TOUCH,
      PLAN_REGION
   }

   public record Item(Kind kind, long regionPacked, long chunkPacked) {
      public ChunkPos chunkPos() {
         return new ChunkPos(this.chunkPacked);
      }

      public SettlementRegionKey regionKey() {
         return SettlementRegionKey.fromPacked(this.regionPacked);
      }
   }

   private static final WorldgenPlanningQueue INSTANCE = new WorldgenPlanningQueue();

   private final Map<Kind, LinkedHashSet<Item>> pending = new EnumMap<>(Kind.class);
   private final Set<Long> regionsInFlight = new LinkedHashSet<>();
   private final Set<Long> regionsWaitingChunks = new LinkedHashSet<>();
   private final Set<Long> layoutsInFlight = new LinkedHashSet<>();
   /** Latest trigger chunk per region for PLAN_REGION jobs. */
   private final Map<Long, Long> planTriggerChunks = new java.util.HashMap<>();

   private WorldgenPlanningQueue() {
      for (Kind kind : Kind.values()) {
         this.pending.put(kind, new LinkedHashSet<>());
      }
   }

   public static WorldgenPlanningQueue get() {
      return INSTANCE;
   }

   public boolean offerPlanRegion(SettlementRegionKey region, ChunkPos triggerChunk) {
      if (region == null || triggerChunk == null) {
         return false;
      }
      long packed = region.packed();
      this.planTriggerChunks.put(packed, triggerChunk.toLong());
      // Do not mark waiting here — only SettlementPlanner does when chunks are actually missing.
      Item item = new Item(Kind.PLAN_REGION, packed, 0L);
      return this.pending.get(Kind.PLAN_REGION).add(item);
   }

   public boolean offerLayout(SettlementRegionKey region, ChunkPos triggerChunk) {
      if (region == null) {
         return false;
      }
      long chunkPacked = triggerChunk == null ? 0L : triggerChunk.toLong();
      return this.pending.get(Kind.LAYOUT).add(new Item(Kind.LAYOUT, region.packed(), chunkPacked));
   }

   public boolean offerMaterializeTouch(SettlementRegionKey region, ChunkPos chunk) {
      if (region == null || chunk == null) {
         return false;
      }
      return this.pending.get(Kind.MATERIALIZE_TOUCH).add(new Item(Kind.MATERIALIZE_TOUCH, region.packed(), chunk.toLong()));
   }

   public ChunkPos planTriggerChunk(long regionPacked) {
      Long packed = this.planTriggerChunks.get(regionPacked);
      return packed == null ? new ChunkPos(0, 0) : new ChunkPos(packed);
   }

   public boolean tryBeginRegion(long regionPacked) {
      return this.regionsInFlight.add(regionPacked);
   }

   public void endRegion(long regionPacked) {
      this.regionsInFlight.remove(regionPacked);
   }

   public boolean isRegionInFlight(long regionPacked) {
      return this.regionsInFlight.contains(regionPacked);
   }

   public void markRegionWaiting(long regionPacked) {
      this.regionsWaitingChunks.add(regionPacked);
   }

   public void clearRegionWaiting(long regionPacked) {
      this.regionsWaitingChunks.remove(regionPacked);
   }

   public boolean isRegionWaiting(long regionPacked) {
      return this.regionsWaitingChunks.contains(regionPacked);
   }

   public boolean tryBeginLayout(long regionPacked) {
      return this.layoutsInFlight.add(regionPacked);
   }

   public void endLayout(long regionPacked) {
      this.layoutsInFlight.remove(regionPacked);
   }

   public boolean isLayoutInFlight(long regionPacked) {
      return this.layoutsInFlight.contains(regionPacked);
   }

   /**
    * Drain in priority order: layout → materialize → plan. Avoids waiting plan jobs starving
    * settlement construction.
    */
   public List<Item> drainPriority(int layoutLimit, int materializeLimit, int planLimit) {
      List<Item> out = new ArrayList<>();
      take(Kind.LAYOUT, layoutLimit, out);
      take(Kind.MATERIALIZE_TOUCH, materializeLimit, out);
      take(Kind.PLAN_REGION, planLimit, out);
      return out;
   }

   private void take(Kind kind, int limit, List<Item> out) {
      if (limit <= 0) {
         return;
      }
      LinkedHashSet<Item> set = this.pending.get(kind);
      int n = 0;
      for (Item item : new ArrayList<>(set)) {
         if (n >= limit) {
            break;
         }
         out.add(item);
         n++;
      }
   }

   public boolean remove(Item item) {
      if (item == null) {
         return false;
      }
      boolean removed = this.pending.get(item.kind()).remove(item);
      if (removed && item.kind() == Kind.PLAN_REGION) {
         // Keep trigger chunk map until region resolves; cleared on clearRegionWaiting path by engine if needed.
      }
      return removed;
   }

   /** Move a waiting plan job to the end so other regions get a turn. */
   public void deferPlanRegion(Item item) {
      if (item == null || item.kind() != Kind.PLAN_REGION) {
         return;
      }
      LinkedHashSet<Item> set = this.pending.get(Kind.PLAN_REGION);
      if (set.remove(item)) {
         set.add(item);
      }
   }

   public int size() {
      int n = 0;
      for (LinkedHashSet<Item> set : this.pending.values()) {
         n += set.size();
      }
      return n;
   }

   public void clear() {
      for (LinkedHashSet<Item> set : this.pending.values()) {
         set.clear();
      }
      this.regionsInFlight.clear();
      this.regionsWaitingChunks.clear();
      this.layoutsInFlight.clear();
      this.planTriggerChunks.clear();
   }
}
