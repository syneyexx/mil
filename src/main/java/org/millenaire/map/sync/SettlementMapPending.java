package org.millenaire.map.sync;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pending settlement-map changes awaiting a coalesced push. Pure data holder so retry/merge rules are unit-testable.
 */
final class SettlementMapPending {
   private final Set<UUID> dirtyIds = new LinkedHashSet<>();
   private final Set<UUID> removedIds = new LinkedHashSet<>();
   private boolean full;

   boolean isEmpty() {
      return !this.full && this.dirtyIds.isEmpty() && this.removedIds.isEmpty();
   }

   boolean isFull() {
      return this.full;
   }

   void markFull() {
      this.full = true;
   }

   void markDirty(UUID id) {
      if (id != null) {
         this.dirtyIds.add(id);
      }
   }

   void markRemoved(UUID id) {
      if (id != null) {
         this.removedIds.add(id);
      }
   }

   void merge(SettlementMapPending other) {
      if (other == null) {
         return;
      }
      this.full |= other.full;
      this.dirtyIds.addAll(other.dirtyIds);
      this.removedIds.addAll(other.removedIds);
   }

   SettlementMapPending copy() {
      SettlementMapPending c = new SettlementMapPending();
      c.merge(this);
      return c;
   }

   void clear() {
      this.full = false;
      this.dirtyIds.clear();
      this.removedIds.clear();
   }

   List<UUID> dirtyIds() {
      return new ArrayList<>(this.dirtyIds);
   }

   List<UUID> removedIds() {
      return new ArrayList<>(this.removedIds);
   }

   static <T> List<List<T>> chunk(List<T> items, int maxPerChunk) {
      List<List<T>> out = new ArrayList<>();
      int size = Math.max(1, maxPerChunk);
      for (int from = 0; from < items.size(); from += size) {
         out.add(items.subList(from, Math.min(from + size, items.size())));
      }
      return out;
   }
}
