package org.millenaire.faith;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;

/** Min-heap of future gameTime deadlines for rituals and deferred religion work. */
public final class ReligionDeadlineQueue {
   public enum Kind {
      RITUAL_START,
      RITUAL_END,
      PLAN_VILLAGE,
      TRAVEL_COMPLETE,
      DEVOTION_DECAY
   }

   public record Entry(long gameTime, Kind kind, UUID targetId) implements Comparable<Entry> {
      @Override
      public int compareTo(Entry o) {
         int c = Long.compare(this.gameTime, o.gameTime);
         if (c != 0) {
            return c;
         }
         return this.kind.compareTo(o.kind);
      }
   }

   private final PriorityQueue<Entry> heap = new PriorityQueue<>();
   private static final int MAX_ENTRIES = 4096;

   public void schedule(long gameTime, Kind kind, UUID targetId) {
      if (this.heap.size() >= MAX_ENTRIES) {
         // Drop farthest-future PLAN entries first conceptually by rejecting new plans when full.
         if (kind == Kind.PLAN_VILLAGE) {
            return;
         }
         Entry farthest = null;
         for (Entry e : this.heap) {
            if (farthest == null || e.gameTime > farthest.gameTime) {
               farthest = e;
            }
         }
         if (farthest != null && farthest.gameTime > gameTime) {
            this.heap.remove(farthest);
         } else {
            return;
         }
      }
      this.heap.offer(new Entry(gameTime, kind, targetId));
   }

   public List<Entry> pollDue(long now, int max) {
      List<Entry> out = new ArrayList<>(Math.min(max, 8));
      while (out.size() < max && !this.heap.isEmpty() && this.heap.peek().gameTime <= now) {
         out.add(this.heap.poll());
      }
      return out;
   }

   public int size() {
      return this.heap.size();
   }

   public long oldestWaitMs(long nowGameTime) {
      Entry peek = this.heap.peek();
      if (peek == null) {
         return 0L;
      }
      return Math.max(0L, nowGameTime - peek.gameTime);
   }

   public void clear() {
      this.heap.clear();
   }
}
