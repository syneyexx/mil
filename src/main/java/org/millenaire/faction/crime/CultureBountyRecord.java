package org.millenaire.faction.crime;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * Mutable bounty ledger for one player against one culture.
 */
public final class CultureBountyRecord {
   private final UUID playerId;
   private final ResourceLocation cultureId;
   private int bountyScore;
   private long lastCrimeGameTime;
   private long lastDecayGameTime;
   private int successfulBountyRaids;
   private int pendingWaves;
   private long nextWaveGameTime;
   private boolean warDeclared;
   private long lastTheftGameTime;
   private long lastAssaultGameTime;

   public CultureBountyRecord(UUID playerId, ResourceLocation cultureId) {
      this.playerId = Objects.requireNonNull(playerId, "playerId");
      this.cultureId = Objects.requireNonNull(cultureId, "cultureId");
   }

   public UUID playerId() {
      return this.playerId;
   }

   public ResourceLocation cultureId() {
      return this.cultureId;
   }

   public int bountyScore() {
      return this.bountyScore;
   }

   public void setBountyScore(int bountyScore) {
      this.bountyScore = Math.max(0, bountyScore);
   }

   public void addBounty(int amount) {
      if (amount > 0) {
         this.bountyScore = Math.min(2000, this.bountyScore + amount);
      }
   }

   public WantedTier tier() {
      return WantedTier.fromScore(this.bountyScore);
   }

   public long lastCrimeGameTime() {
      return this.lastCrimeGameTime;
   }

   public void setLastCrimeGameTime(long lastCrimeGameTime) {
      this.lastCrimeGameTime = lastCrimeGameTime;
   }

   public long lastDecayGameTime() {
      return this.lastDecayGameTime;
   }

   public void setLastDecayGameTime(long lastDecayGameTime) {
      this.lastDecayGameTime = lastDecayGameTime;
   }

   public int successfulBountyRaids() {
      return this.successfulBountyRaids;
   }

   public void incrementSuccessfulBountyRaids() {
      this.successfulBountyRaids++;
   }

   public void setSuccessfulBountyRaids(int value) {
      this.successfulBountyRaids = Math.max(0, value);
   }

   public int pendingWaves() {
      return this.pendingWaves;
   }

   public void setPendingWaves(int pendingWaves) {
      this.pendingWaves = Math.max(0, pendingWaves);
   }

   public long nextWaveGameTime() {
      return this.nextWaveGameTime;
   }

   public void setNextWaveGameTime(long nextWaveGameTime) {
      this.nextWaveGameTime = nextWaveGameTime;
   }

   public boolean warDeclared() {
      return this.warDeclared;
   }

   public void setWarDeclared(boolean warDeclared) {
      this.warDeclared = warDeclared;
   }

   public long lastTheftGameTime() {
      return this.lastTheftGameTime;
   }

   public void setLastTheftGameTime(long lastTheftGameTime) {
      this.lastTheftGameTime = lastTheftGameTime;
   }

   public long lastAssaultGameTime() {
      return this.lastAssaultGameTime;
   }

   public void setLastAssaultGameTime(long lastAssaultGameTime) {
      this.lastAssaultGameTime = lastAssaultGameTime;
   }

   public void clearCampaign() {
      this.pendingWaves = 0;
      this.nextWaveGameTime = 0L;
      this.successfulBountyRaids = 0;
      this.warDeclared = false;
   }

   public void reduceBounty(int amount) {
      if (amount > 0) {
         this.bountyScore = Math.max(0, this.bountyScore - amount);
         if (this.bountyScore < WantedTier.WANTED.minScore()) {
            this.clearCampaign();
         }
      }
   }
}
