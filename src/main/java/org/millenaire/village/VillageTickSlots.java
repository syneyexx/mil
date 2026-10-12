package org.millenaire.village;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Hash-partitioned buckets so a periodic job touches {@code 1/interval} of items per tick
 * instead of scanning every item. Item {@code x} is due when
 * {@code floorMod(gameTime, interval) == floorMod(hash(x), interval)}.
 */
final class VillageTickSlots<T> {
   private final ArrayList<ArrayList<T>> slots = new ArrayList<>();
   private int interval = 1;

   int interval() {
      return this.interval;
   }

   void rebuild(Collection<T> items, ToIntFunction<T> hash, int interval) {
      this.interval = Math.max(1, interval);
      this.slots.clear();
      for (int i = 0; i < this.interval; i++) {
         this.slots.add(new ArrayList<>());
      }
      for (T item : items) {
         this.slots.get(Math.floorMod(hash.applyAsInt(item), this.interval)).add(item);
      }
   }

   List<T> due(long gameTime) {
      if (this.slots.isEmpty()) {
         return Collections.emptyList();
      }
      return this.slots.get((int)Math.floorMod(gameTime, (long)this.interval));
   }
}
