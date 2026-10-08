package org.millenaire.faction.economy;

import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.village.VillageId;

/** One bounded trade history record on a directional route. */
public final class TradeHistoryEntry {
   private final long gameTime;
   private final EconomicResourceId resourceId;
   private final int quantity;
   private final int value;
   private final boolean successful;
   private final TradeFailureReason failureReason;
   @Nullable
   private final VillageId sourceSettlementId;
   @Nullable
   private final VillageId targetSettlementId;

   public TradeHistoryEntry(
      long gameTime,
      EconomicResourceId resourceId,
      int quantity,
      int value,
      boolean successful,
      TradeFailureReason failureReason,
      @Nullable VillageId sourceSettlementId,
      @Nullable VillageId targetSettlementId
   ) {
      this.gameTime = gameTime;
      this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
      this.quantity = Math.max(0, quantity);
      this.value = Math.max(0, value);
      this.successful = successful;
      this.failureReason = failureReason == null ? TradeFailureReason.NONE : failureReason;
      this.sourceSettlementId = sourceSettlementId;
      this.targetSettlementId = targetSettlementId;
   }

   public long getGameTime() {
      return this.gameTime;
   }

   public EconomicResourceId getResourceId() {
      return this.resourceId;
   }

   public int getQuantity() {
      return this.quantity;
   }

   public int getValue() {
      return this.value;
   }

   public boolean isSuccessful() {
      return this.successful;
   }

   public TradeFailureReason getFailureReason() {
      return this.failureReason;
   }

   @Nullable
   public VillageId getSourceSettlementId() {
      return this.sourceSettlementId;
   }

   @Nullable
   public VillageId getTargetSettlementId() {
      return this.targetSettlementId;
   }
}
