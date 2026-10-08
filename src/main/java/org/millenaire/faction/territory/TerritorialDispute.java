package org.millenaire.faction.territory;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.war.WarId;

public final class TerritorialDispute {
   private final DisputeId id;
   private final TerritoryId territoryId;
   private final Set<FactionId> claimantFactionIds = new LinkedHashSet<>();
   private final String reason;
   private final long createdGameTime;
   private DisputeStatus status = DisputeStatus.OPEN;
   @Nullable
   private WarId relatedWarId;
   @Nullable
   private PeaceTreatyId relatedPeaceTreatyId;
   private long resolvedGameTime;
   @Nullable
   private String resolutionReason;

   public TerritorialDispute(DisputeId id, TerritoryId territoryId, String reason, long createdGameTime) {
      this.id = Objects.requireNonNull(id, "id");
      this.territoryId = Objects.requireNonNull(territoryId, "territoryId");
      this.reason = reason == null ? "unspecified" : reason;
      this.createdGameTime = createdGameTime;
   }

   public DisputeId getId() {
      return this.id;
   }

   public TerritoryId getTerritoryId() {
      return this.territoryId;
   }

   public Set<FactionId> getClaimantFactionIds() {
      return Collections.unmodifiableSet(this.claimantFactionIds);
   }

   public String getReason() {
      return this.reason;
   }

   public long getCreatedGameTime() {
      return this.createdGameTime;
   }

   public DisputeStatus getStatus() {
      return this.status;
   }

   @Nullable
   public WarId getRelatedWarId() {
      return this.relatedWarId;
   }

   @Nullable
   public PeaceTreatyId getRelatedPeaceTreatyId() {
      return this.relatedPeaceTreatyId;
   }

   public long getResolvedGameTime() {
      return this.resolvedGameTime;
   }

   @Nullable
   public String getResolutionReason() {
      return this.resolutionReason;
   }

   boolean addClaimant(FactionId factionId) {
      return factionId != null && this.claimantFactionIds.add(factionId);
   }

   void setRelatedWarId(@Nullable WarId warId) {
      this.relatedWarId = warId;
   }

   void resolve(long gameTime, @Nullable PeaceTreatyId treatyId, @Nullable String reason) {
      this.status = DisputeStatus.RESOLVED;
      this.resolvedGameTime = gameTime;
      this.relatedPeaceTreatyId = treatyId;
      this.resolutionReason = reason;
   }

   void invalidate(long gameTime, @Nullable String reason) {
      this.status = DisputeStatus.INVALIDATED;
      this.resolvedGameTime = gameTime;
      this.resolutionReason = reason;
   }

   void loadState(
      Set<FactionId> claimants,
      DisputeStatus status,
      @Nullable WarId warId,
      @Nullable PeaceTreatyId treatyId,
      long resolved,
      @Nullable String resolutionReason
   ) {
      this.claimantFactionIds.clear();
      if (claimants != null) {
         this.claimantFactionIds.addAll(claimants);
      }

      this.status = status == null ? DisputeStatus.OPEN : status;
      this.relatedWarId = warId;
      this.relatedPeaceTreatyId = treatyId;
      this.resolvedGameTime = Math.max(0L, resolved);
      this.resolutionReason = resolutionReason;
   }
}
