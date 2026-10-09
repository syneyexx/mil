package org.millenaire.worldgen.materialize;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

/**
 * Unique resume queue for unfinished chunk materialization work.
 * Items are keyed by planId+chunk so duplicate chunk-load events do not enqueue twice.
 */
public final class MaterializationResumeQueue {
   public record Item(UUID planId, long chunkPacked) {
      public Item {
         Objects.requireNonNull(planId, "planId");
      }

      public ChunkPos chunkPos() {
         return new ChunkPos(this.chunkPacked);
      }

      public CompoundTag save() {
         CompoundTag tag = new CompoundTag();
         tag.putUUID("plan", this.planId);
         tag.putLong("chunk", this.chunkPacked);
         return tag;
      }

      @Nullable
      public static Item load(CompoundTag tag) {
         try {
            return new Item(tag.getUUID("plan"), tag.getLong("chunk"));
         } catch (Exception e) {
            return null;
         }
      }
   }

   private final LinkedHashSet<Item> pending = new LinkedHashSet<>();
   private int roundRobinCursor;

   public boolean offer(UUID planId, long chunkPacked) {
      if (planId == null) {
         return false;
      }
      return this.pending.add(new Item(planId, chunkPacked));
   }

   public boolean remove(UUID planId, long chunkPacked) {
      return this.pending.remove(new Item(planId, chunkPacked));
   }

   public boolean contains(UUID planId, long chunkPacked) {
      return this.pending.contains(new Item(planId, chunkPacked));
   }

   public int size() {
      return this.pending.size();
   }

   public boolean isEmpty() {
      return this.pending.isEmpty();
   }

   public void clear() {
      this.pending.clear();
      this.roundRobinCursor = 0;
   }

   /**
    * Round-robin drain of up to {@code limit} unique items without removing them.
    * Caller removes completed items explicitly.
    */
   public List<Item> peekRoundRobin(int limit) {
      if (this.pending.isEmpty() || limit <= 0) {
         return List.of();
      }
      List<Item> all = new ArrayList<>(this.pending);
      int n = all.size();
      int start = Math.floorMod(this.roundRobinCursor, n);
      List<Item> out = new ArrayList<>(Math.min(limit, n));
      for (int i = 0; i < n && out.size() < limit; i++) {
         out.add(all.get((start + i) % n));
      }
      this.roundRobinCursor = start + out.size();
      return out;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putInt("cursor", this.roundRobinCursor);
      ListTag list = new ListTag();
      for (Item item : this.pending) {
         list.add(item.save());
      }
      tag.put("items", list);
      return tag;
   }

   public void load(CompoundTag tag) {
      this.clear();
      if (tag == null) {
         return;
      }
      this.roundRobinCursor = tag.getInt("cursor");
      ListTag list = tag.getList("items", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         Item item = Item.load(list.getCompound(i));
         if (item != null) {
            this.pending.add(item);
         }
      }
   }
}
