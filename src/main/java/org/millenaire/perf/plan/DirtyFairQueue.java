package org.millenaire.perf.plan;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import javax.annotation.Nullable;

/**
 * Concurrent dirty-id set with budgeted drain (no full {@code Set.copyOf} every tick).
 * Tracks oldest enqueue game-time for starvation diagnostics.
 */
public final class DirtyFairQueue<T> {
   private final Set<T> dirty = ConcurrentHashMap.newKeySet();
   private final ConcurrentHashMap<T, Long> enqueuedAt = new ConcurrentHashMap<>();
   private volatile long oldestGameTime = -1L;

   public void mark(@Nullable T id, long gameTime) {
      if (id == null) {
         return;
      }
      if (this.dirty.add(id)) {
         this.enqueuedAt.put(id, gameTime);
         long oldest = this.oldestGameTime;
         if (oldest < 0L || gameTime < oldest) {
            this.oldestGameTime = gameTime;
         }
      }
   }

   public void mark(@Nullable T id) {
      mark(id, 0L);
   }

   public boolean contains(@Nullable T id) {
      return id != null && this.dirty.contains(id);
   }

   public void clear(@Nullable T id) {
      if (id == null) {
         return;
      }
      this.dirty.remove(id);
      this.enqueuedAt.remove(id);
      if (this.dirty.isEmpty()) {
         this.oldestGameTime = -1L;
      }
   }

   public void clearAll() {
      this.dirty.clear();
      this.enqueuedAt.clear();
      this.oldestGameTime = -1L;
   }

   /**
    * Drain up to {@code max} dirty ids without copying the whole set.
    * Does not remove entries — caller clears after successful submit or drop.
    */
   public List<T> pollLimited(int max) {
      List<T> out = new ArrayList<>(Math.min(Math.max(0, max), 8));
      if (max <= 0 || this.dirty.isEmpty()) {
         return out;
      }
      Iterator<T> it = this.dirty.iterator();
      while (it.hasNext() && out.size() < max) {
         out.add(it.next());
      }
      return out;
   }

   public int size() {
      return this.dirty.size();
   }

   public boolean isEmpty() {
      return this.dirty.isEmpty();
   }

   public long oldestGameTime() {
      return this.oldestGameTime;
   }

   public long ageOf(@Nullable T id, LongSupplier nowGameTime) {
      if (id == null) {
         return -1L;
      }
      Long at = this.enqueuedAt.get(id);
      if (at == null) {
         return -1L;
      }
      return Math.max(0L, nowGameTime.getAsLong() - at);
   }
}
