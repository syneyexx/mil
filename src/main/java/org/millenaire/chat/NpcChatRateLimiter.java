package org.millenaire.chat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcChatRateLimiter {
   static final int MAX_TRACKED = 10000;
   private final Map<UUID, Long> lastAcceptMs = new ConcurrentHashMap<>();
   private final long minIntervalMs;

   public NpcChatRateLimiter(long minIntervalMs) {
      if (minIntervalMs < 0L) {
         throw new IllegalArgumentException("minIntervalMs must be >= 0");
      }
      this.minIntervalMs = minIntervalMs;
   }

   public boolean acquire(UUID playerId) {
      return this.acquire(playerId, System.currentTimeMillis());
   }

   public boolean acquire(UUID playerId, long nowMs) {
      Long prev = this.lastAcceptMs.get(playerId);
      if (prev != null && nowMs - prev < this.minIntervalMs) {
         return false;
      }
      this.lastAcceptMs.put(playerId, nowMs);
      if (this.lastAcceptMs.size() > MAX_TRACKED) {
         long staleCutoff = nowMs - this.minIntervalMs;
         this.lastAcceptMs.entrySet().removeIf(e -> e.getValue() < staleCutoff);
      }
      return true;
   }
}
