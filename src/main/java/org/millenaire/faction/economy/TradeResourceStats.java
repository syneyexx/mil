package org.millenaire.faction.economy;

import java.util.Objects;

/** Cumulative per-resource statistics on a directional trade route. */
public final class TradeResourceStats {
   private final EconomicResourceId resourceId;
   private long volume;
   private long value;
   private int successCount;
   private long lastTradeGameTime;

   public TradeResourceStats(EconomicResourceId resourceId) {
      this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
   }

   public EconomicResourceId getResourceId() {
      return this.resourceId;
   }

   public long getVolume() {
      return this.volume;
   }

   public long getValue() {
      return this.value;
   }

   public int getSuccessCount() {
      return this.successCount;
   }

   public long getLastTradeGameTime() {
      return this.lastTradeGameTime;
   }

   void recordSuccess(int quantity, int tradeValue, long gameTime) {
      this.volume += Math.max(0, quantity);
      this.value += Math.max(0, tradeValue);
      this.successCount++;
      this.lastTradeGameTime = gameTime;
   }

   void load(long volume, long value, int successCount, long lastTradeGameTime) {
      this.volume = Math.max(0L, volume);
      this.value = Math.max(0L, value);
      this.successCount = Math.max(0, successCount);
      this.lastTradeGameTime = Math.max(0L, lastTradeGameTime);
   }
}
