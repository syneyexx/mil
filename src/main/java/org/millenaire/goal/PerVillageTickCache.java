package org.millenaire.goal;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class PerVillageTickCache<T> {
   private static final long DEFAULT_EXPIRY_TICKS = 12000L;
   private static final long DEFAULT_CLEANUP_INTERVAL = 2400L;
   private final int refreshTicks;
   private final long expiryTicks;
   private final long cleanupInterval;
   private long lastCleanupTick = 0L;
   private final ConcurrentHashMap<UUID, PerVillageTickCache.Entry<T>> entries = new ConcurrentHashMap<>();
   private int hits;
   private int misses;

   public PerVillageTickCache(int refreshTicks) {
      this(refreshTicks, 12000L, 2400L);
   }

   public PerVillageTickCache(int refreshTicks, long expiryTicks, long cleanupInterval) {
      this.refreshTicks = refreshTicks;
      this.expiryTicks = expiryTicks;
      this.cleanupInterval = cleanupInterval;
   }

   public T get(UUID villageId, long currentTick, Supplier<T> scanner) {
      this.runCleanupIfNeeded(currentTick);
      PerVillageTickCache.Entry<T> entry = this.entries.get(villageId);
      if (entry == null || currentTick - entry.tick() >= this.refreshTicks || currentTick < entry.tick()) {
         this.misses++;
         entry = new PerVillageTickCache.Entry<>(currentTick, scanner.get());
         this.entries.put(villageId, entry);
      } else {
         this.hits++;
      }

      return entry.value();
   }

   public int hits() {
      return this.hits;
   }

   public int misses() {
      return this.misses;
   }

   public void resetMetrics() {
      this.hits = 0;
      this.misses = 0;
   }

   private void runCleanupIfNeeded(long currentTick) {
      if (currentTick - this.lastCleanupTick >= this.cleanupInterval) {
         this.cleanup(currentTick);
         this.lastCleanupTick = currentTick;
      }
   }

   public void cleanup(long currentTick) {
      this.entries.entrySet().removeIf(entry -> currentTick - entry.getValue().tick() > this.expiryTicks);
   }

   private record Entry<T>(long tick, T value) {
   }
}
