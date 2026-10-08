package org.millenaire.faction.economy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.village.VillageId;

/**
 * Persistent directional trade connection from exporter {@code source} to importer {@code target}.
 * Separate from Phase 2 political relationships.
 */
public final class TradeRoute {
   private final FactionId sourceFactionId;
   private final FactionId targetFactionId;
   private TradeRouteStatus status = TradeRouteStatus.INACTIVE;
   private TradeDisruptionReason disruptionReason = TradeDisruptionReason.NONE;
   private int activity;
   private int successfulTrades;
   private int failedTrades;
   private long cumulativeVolume;
   private long cumulativeValue;
   private long establishedGameTime;
   private long lastSuccessfulTradeGameTime;
   private long lastAttemptGameTime;
   private long lastActivityDecayGameTime;
   @Nullable
   private VillageId lastSourceSettlementId;
   @Nullable
   private VillageId lastTargetSettlementId;
   private final Map<EconomicResourceId, TradeResourceStats> resourceStats = new LinkedHashMap<>();
   private final Deque<TradeHistoryEntry> history = new ArrayDeque<>();

   public TradeRoute(FactionId sourceFactionId, FactionId targetFactionId, long establishedGameTime) {
      this.sourceFactionId = Objects.requireNonNull(sourceFactionId, "sourceFactionId");
      this.targetFactionId = Objects.requireNonNull(targetFactionId, "targetFactionId");
      if (sourceFactionId.equals(targetFactionId)) {
         throw new IllegalArgumentException("Self trade routes are not allowed: " + sourceFactionId);
      }

      this.establishedGameTime = establishedGameTime;
      this.lastActivityDecayGameTime = establishedGameTime;
   }

   public FactionId getSourceFactionId() {
      return this.sourceFactionId;
   }

   public FactionId getTargetFactionId() {
      return this.targetFactionId;
   }

   public TradeRouteKey getKey() {
      return TradeRouteKey.of(this.sourceFactionId, this.targetFactionId);
   }

   public TradeRouteStatus getStatus() {
      return this.status;
   }

   public TradeDisruptionReason getDisruptionReason() {
      return this.disruptionReason;
   }

   public int getActivity() {
      return this.activity;
   }

   public int getSuccessfulTrades() {
      return this.successfulTrades;
   }

   public int getFailedTrades() {
      return this.failedTrades;
   }

   public long getCumulativeVolume() {
      return this.cumulativeVolume;
   }

   public long getCumulativeValue() {
      return this.cumulativeValue;
   }

   public long getEstablishedGameTime() {
      return this.establishedGameTime;
   }

   public long getLastSuccessfulTradeGameTime() {
      return this.lastSuccessfulTradeGameTime;
   }

   public long getLastAttemptGameTime() {
      return this.lastAttemptGameTime;
   }

   public long getLastActivityDecayGameTime() {
      return this.lastActivityDecayGameTime;
   }

   @Nullable
   public VillageId getLastSourceSettlementId() {
      return this.lastSourceSettlementId;
   }

   @Nullable
   public VillageId getLastTargetSettlementId() {
      return this.lastTargetSettlementId;
   }

   public Map<EconomicResourceId, TradeResourceStats> getResourceStats() {
      return Collections.unmodifiableMap(this.resourceStats);
   }

   public List<TradeHistoryEntry> getHistory() {
      return Collections.unmodifiableList(new ArrayList<>(this.history));
   }

   public boolean isTradeable() {
      return this.status != TradeRouteStatus.DISRUPTED;
   }

   void setDisrupted(TradeDisruptionReason reason) {
      this.status = TradeRouteStatus.DISRUPTED;
      this.disruptionReason = reason == null ? TradeDisruptionReason.UNKNOWN : reason;
   }

   void clearDisruption() {
      this.disruptionReason = TradeDisruptionReason.NONE;
      this.refreshStatusFromActivity();
   }

   void recordSuccess(EconomicResourceId resourceId, int quantity, int value, long gameTime, @Nullable VillageId sourceSettlement, @Nullable VillageId targetSettlement) {
      this.successfulTrades++;
      this.cumulativeVolume += Math.max(0, quantity);
      this.cumulativeValue += Math.max(0, value);
      this.lastSuccessfulTradeGameTime = gameTime;
      this.lastAttemptGameTime = gameTime;
      this.lastSourceSettlementId = sourceSettlement;
      this.lastTargetSettlementId = targetSettlement;
      this.activity = EconomicConfig.clampActivity(this.activity + EconomicConfig.activityGainForValue(value));
      this.resourceStats.computeIfAbsent(resourceId, TradeResourceStats::new).recordSuccess(quantity, value, gameTime);
      this.appendHistory(new TradeHistoryEntry(gameTime, resourceId, quantity, value, true, TradeFailureReason.NONE, sourceSettlement, targetSettlement));
      this.refreshStatusFromActivity();
   }

   void recordFailure(
      EconomicResourceId resourceId,
      int quantity,
      int value,
      long gameTime,
      TradeFailureReason reason,
      @Nullable VillageId sourceSettlement,
      @Nullable VillageId targetSettlement
   ) {
      this.failedTrades++;
      this.lastAttemptGameTime = gameTime;
      this.appendHistory(
         new TradeHistoryEntry(gameTime, resourceId, quantity, value, false, reason == null ? TradeFailureReason.UNKNOWN : reason, sourceSettlement, targetSettlement)
      );
   }

   boolean decayActivity(long gameTime) {
      if (this.activity <= 0) {
         this.lastActivityDecayGameTime = gameTime;
         this.refreshStatusFromActivity();
         return false;
      }

      long elapsed = gameTime - this.lastActivityDecayGameTime;
      if (elapsed < EconomicConfig.TRADE_ACTIVITY_DECAY_INTERVAL_TICKS) {
         return false;
      }

      int steps = (int)(elapsed / EconomicConfig.TRADE_ACTIVITY_DECAY_INTERVAL_TICKS);
      steps = Math.min(steps, EconomicConfig.MAX_ACTIVITY_DECAY_STEPS_PER_PASS);
      int previous = this.activity;

      for (int i = 0; i < steps && this.activity > 0; i++) {
         this.activity = Math.max(0, this.activity - EconomicConfig.TRADE_ACTIVITY_DECAY_AMOUNT);
      }

      this.lastActivityDecayGameTime = this.lastActivityDecayGameTime + (long)steps * EconomicConfig.TRADE_ACTIVITY_DECAY_INTERVAL_TICKS;
      if (this.lastActivityDecayGameTime > gameTime) {
         this.lastActivityDecayGameTime = gameTime;
      }

      this.refreshStatusFromActivity();
      return this.activity != previous;
   }

   private void refreshStatusFromActivity() {
      if (this.status == TradeRouteStatus.DISRUPTED) {
         return;
      }

      this.status = this.activity > 0 ? TradeRouteStatus.ACTIVE : TradeRouteStatus.INACTIVE;
   }

   private void appendHistory(TradeHistoryEntry entry) {
      this.history.addLast(entry);

      while (this.history.size() > EconomicConfig.MAX_TRADE_HISTORY_ENTRIES) {
         this.history.removeFirst();
      }
   }

   void loadState(
      TradeRouteStatus status,
      TradeDisruptionReason disruptionReason,
      int activity,
      int successfulTrades,
      int failedTrades,
      long cumulativeVolume,
      long cumulativeValue,
      long establishedGameTime,
      long lastSuccessfulTradeGameTime,
      long lastAttemptGameTime,
      long lastActivityDecayGameTime,
      @Nullable VillageId lastSourceSettlementId,
      @Nullable VillageId lastTargetSettlementId
   ) {
      this.status = status == null ? TradeRouteStatus.INACTIVE : status;
      this.disruptionReason = disruptionReason == null ? TradeDisruptionReason.NONE : disruptionReason;
      this.activity = EconomicConfig.clampActivity(activity);
      this.successfulTrades = Math.max(0, successfulTrades);
      this.failedTrades = Math.max(0, failedTrades);
      this.cumulativeVolume = Math.max(0L, cumulativeVolume);
      this.cumulativeValue = Math.max(0L, cumulativeValue);
      this.establishedGameTime = Math.max(0L, establishedGameTime);
      this.lastSuccessfulTradeGameTime = Math.max(0L, lastSuccessfulTradeGameTime);
      this.lastAttemptGameTime = Math.max(0L, lastAttemptGameTime);
      this.lastActivityDecayGameTime = Math.max(0L, lastActivityDecayGameTime);
      this.lastSourceSettlementId = lastSourceSettlementId;
      this.lastTargetSettlementId = lastTargetSettlementId;
   }

   void replaceResourceStats(Map<EconomicResourceId, TradeResourceStats> stats) {
      this.resourceStats.clear();
      if (stats != null) {
         this.resourceStats.putAll(stats);
      }
   }

   void replaceHistory(List<TradeHistoryEntry> entries) {
      this.history.clear();
      if (entries == null) {
         return;
      }

      for (TradeHistoryEntry entry : entries) {
         if (entry != null) {
            this.history.addLast(entry);
         }
      }

      while (this.history.size() > EconomicConfig.MAX_TRADE_HISTORY_ENTRIES) {
         this.history.removeFirst();
      }
   }
}
