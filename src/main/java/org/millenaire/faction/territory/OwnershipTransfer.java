package org.millenaire.faction.territory;

import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.war.WarId;
import org.millenaire.village.VillageId;

/**
 * Immutable historical permanent ownership transfer. Never silently rewritten.
 */
public final class OwnershipTransfer {
   private final TransferId id;
   private final TerritoryId territoryId;
   private final VillageId settlementId;
   private final FactionId previousOwnerFactionId;
   private final FactionId newOwnerFactionId;
   private final ConquestType transferType;
   @Nullable
   private final WarId warId;
   @Nullable
   private final PeaceTreatyId peaceTreatyId;
   private final long transferredGameTime;
   @Nullable
   private final String reason;

   public OwnershipTransfer(
      TransferId id,
      TerritoryId territoryId,
      VillageId settlementId,
      FactionId previousOwnerFactionId,
      FactionId newOwnerFactionId,
      ConquestType transferType,
      long transferredGameTime,
      @Nullable WarId warId,
      @Nullable PeaceTreatyId peaceTreatyId,
      @Nullable String reason
   ) {
      this.id = Objects.requireNonNull(id, "id");
      this.territoryId = Objects.requireNonNull(territoryId, "territoryId");
      this.settlementId = Objects.requireNonNull(settlementId, "settlementId");
      this.previousOwnerFactionId = Objects.requireNonNull(previousOwnerFactionId, "previousOwnerFactionId");
      this.newOwnerFactionId = Objects.requireNonNull(newOwnerFactionId, "newOwnerFactionId");
      this.transferType = transferType == null ? ConquestType.TRANSFER : transferType;
      this.transferredGameTime = transferredGameTime;
      this.warId = warId;
      this.peaceTreatyId = peaceTreatyId;
      this.reason = reason;
   }

   public TransferId getId() {
      return this.id;
   }

   public TerritoryId getTerritoryId() {
      return this.territoryId;
   }

   public VillageId getSettlementId() {
      return this.settlementId;
   }

   public FactionId getPreviousOwnerFactionId() {
      return this.previousOwnerFactionId;
   }

   public FactionId getNewOwnerFactionId() {
      return this.newOwnerFactionId;
   }

   public ConquestType getTransferType() {
      return this.transferType;
   }

   @Nullable
   public WarId getWarId() {
      return this.warId;
   }

   @Nullable
   public PeaceTreatyId getPeaceTreatyId() {
      return this.peaceTreatyId;
   }

   public long getTransferredGameTime() {
      return this.transferredGameTime;
   }

   @Nullable
   public String getReason() {
      return this.reason;
   }
}
