package org.millenaire.faction.conflict;

import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.politics.PoliticalGroupId;
import org.millenaire.faction.territory.TerritoryId;
import org.millenaire.village.VillageId;

public final class Unrest {
   private final UnrestId id;
   private final FactionId factionId;
   private final UnrestCause cause;
   private final UnrestScope scope;
   private int severity;
   private int escalationPressure;
   @Nullable
   private PoliticalGroupId politicalGroupId;
   @Nullable
   private VillageId settlementId;
   @Nullable
   private TerritoryId territoryId;
   private final long startedGameTime;
   private long lastUpdatedGameTime;
   private boolean active = true;
   private long resolvedGameTime;
   @Nullable
   private String detail;

   public Unrest(UnrestId id, FactionId factionId, UnrestCause cause, UnrestScope scope, int severity, long gameTime) {
      this.id = Objects.requireNonNull(id, "id");
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.cause = cause == null ? UnrestCause.LOW_STABILITY : cause;
      this.scope = scope == null ? UnrestScope.FACTION : scope;
      this.severity = ConflictConfig.clamp(severity);
      this.escalationPressure = this.severity;
      this.startedGameTime = gameTime;
      this.lastUpdatedGameTime = gameTime;
   }

   public UnrestId getId() {
      return this.id;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   public UnrestCause getCause() {
      return this.cause;
   }

   public UnrestScope getScope() {
      return this.scope;
   }

   public int getSeverity() {
      return this.severity;
   }

   public int getEscalationPressure() {
      return this.escalationPressure;
   }

   @Nullable
   public PoliticalGroupId getPoliticalGroupId() {
      return this.politicalGroupId;
   }

   @Nullable
   public VillageId getSettlementId() {
      return this.settlementId;
   }

   @Nullable
   public TerritoryId getTerritoryId() {
      return this.territoryId;
   }

   public long getStartedGameTime() {
      return this.startedGameTime;
   }

   public long getLastUpdatedGameTime() {
      return this.lastUpdatedGameTime;
   }

   public boolean isActive() {
      return this.active;
   }

   public long getResolvedGameTime() {
      return this.resolvedGameTime;
   }

   @Nullable
   public String getDetail() {
      return this.detail;
   }

   void setSeverity(int severity, long gameTime) {
      this.severity = ConflictConfig.clamp(severity);
      this.lastUpdatedGameTime = gameTime;
   }

   void setEscalationPressure(int pressure, long gameTime) {
      this.escalationPressure = ConflictConfig.clamp(pressure);
      this.lastUpdatedGameTime = gameTime;
   }

   void setPoliticalGroupId(@Nullable PoliticalGroupId groupId) {
      this.politicalGroupId = groupId;
   }

   void setSettlementId(@Nullable VillageId settlementId) {
      this.settlementId = settlementId;
   }

   void setTerritoryId(@Nullable TerritoryId territoryId) {
      this.territoryId = territoryId;
   }

   void setDetail(@Nullable String detail) {
      this.detail = detail;
   }

   void resolve(long gameTime) {
      this.active = false;
      this.resolvedGameTime = gameTime;
      this.lastUpdatedGameTime = gameTime;
   }

   void loadState(
      int severity,
      int escalationPressure,
      @Nullable PoliticalGroupId groupId,
      @Nullable VillageId settlementId,
      @Nullable TerritoryId territoryId,
      long lastUpdated,
      boolean active,
      long resolved,
      @Nullable String detail
   ) {
      this.severity = ConflictConfig.clamp(severity);
      this.escalationPressure = ConflictConfig.clamp(escalationPressure);
      this.politicalGroupId = groupId;
      this.settlementId = settlementId;
      this.territoryId = territoryId;
      this.lastUpdatedGameTime = Math.max(0L, lastUpdated);
      this.active = active;
      this.resolvedGameTime = Math.max(0L, resolved);
      this.detail = detail;
   }
}
