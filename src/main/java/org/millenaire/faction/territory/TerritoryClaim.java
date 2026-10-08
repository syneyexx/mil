package org.millenaire.faction.territory;

import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.war.WarId;

public final class TerritoryClaim {
   private final ClaimId id;
   private final FactionId claimantFactionId;
   private final TerritoryId territoryId;
   private final ClaimType type;
   private int strength;
   @Nullable
   private final String reason;
   private final long createdGameTime;
   private long expirationGameTime;
   private boolean active = true;
   private long resolvedGameTime;
   @Nullable
   private WarId originWarId;

   public TerritoryClaim(
      ClaimId id,
      FactionId claimantFactionId,
      TerritoryId territoryId,
      ClaimType type,
      int strength,
      long createdGameTime,
      @Nullable String reason
   ) {
      this.id = Objects.requireNonNull(id, "id");
      this.claimantFactionId = Objects.requireNonNull(claimantFactionId, "claimantFactionId");
      this.territoryId = Objects.requireNonNull(territoryId, "territoryId");
      this.type = type == null ? ClaimType.HISTORICAL : type;
      this.strength = TerritoryConfig.clampClaimStrength(strength);
      this.createdGameTime = createdGameTime;
      this.reason = reason;
   }

   public ClaimId getId() {
      return this.id;
   }

   public FactionId getClaimantFactionId() {
      return this.claimantFactionId;
   }

   public TerritoryId getTerritoryId() {
      return this.territoryId;
   }

   public ClaimType getType() {
      return this.type;
   }

   public int getStrength() {
      return this.strength;
   }

   @Nullable
   public String getReason() {
      return this.reason;
   }

   public long getCreatedGameTime() {
      return this.createdGameTime;
   }

   public long getExpirationGameTime() {
      return this.expirationGameTime;
   }

   public boolean isActive() {
      return this.active;
   }

   public long getResolvedGameTime() {
      return this.resolvedGameTime;
   }

   @Nullable
   public WarId getOriginWarId() {
      return this.originWarId;
   }

   public boolean isExpired(long gameTime) {
      return this.expirationGameTime > 0L && gameTime >= this.expirationGameTime;
   }

   void setStrength(int strength) {
      this.strength = TerritoryConfig.clampClaimStrength(strength);
   }

   void setExpirationGameTime(long expirationGameTime) {
      this.expirationGameTime = Math.max(0L, expirationGameTime);
   }

   void setOriginWarId(@Nullable WarId warId) {
      this.originWarId = warId;
   }

   void resolve(long gameTime) {
      this.active = false;
      this.resolvedGameTime = gameTime;
   }

   void loadState(int strength, long expiration, boolean active, long resolved, @Nullable WarId originWarId) {
      this.strength = TerritoryConfig.clampClaimStrength(strength);
      this.expirationGameTime = Math.max(0L, expiration);
      this.active = active;
      this.resolvedGameTime = Math.max(0L, resolved);
      this.originWarId = originWarId;
   }
}
