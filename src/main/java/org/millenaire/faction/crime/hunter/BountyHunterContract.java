package org.millenaire.faction.crime.hunter;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Persistent hunter assignment for one player / culture bounty.
 */
public final class BountyHunterContract {
   private final UUID contractId;
   private final UUID targetPlayerId;
   private final ResourceLocation cultureId;
   private final String bountyKey;
   private BountyHunterPhase phase;
   private long createdGameTime;
   private long dispatchAtGameTime;
   private long nextRedispatchGameTime;
   @Nullable
   private UUID hunterEntityUuid;
   @Nullable
   private BlockPos lastKnownPos;
   @Nullable
   private ResourceLocation lastKnownDimension;
   private long lastSightingGameTime;
   private boolean settled;
   @Nullable
   private UUID settlementId;
   private int failureCount;

   public BountyHunterContract(
      UUID contractId,
      UUID targetPlayerId,
      ResourceLocation cultureId,
      String bountyKey,
      long createdGameTime,
      long dispatchAtGameTime
   ) {
      this.contractId = Objects.requireNonNull(contractId, "contractId");
      this.targetPlayerId = Objects.requireNonNull(targetPlayerId, "targetPlayerId");
      this.cultureId = Objects.requireNonNull(cultureId, "cultureId");
      this.bountyKey = Objects.requireNonNull(bountyKey, "bountyKey");
      this.phase = BountyHunterPhase.DISPATCH_PENDING;
      this.createdGameTime = createdGameTime;
      this.dispatchAtGameTime = dispatchAtGameTime;
   }

   public UUID contractId() {
      return this.contractId;
   }

   public UUID targetPlayerId() {
      return this.targetPlayerId;
   }

   public ResourceLocation cultureId() {
      return this.cultureId;
   }

   public String bountyKey() {
      return this.bountyKey;
   }

   public BountyHunterPhase phase() {
      return this.phase;
   }

   public void setPhase(BountyHunterPhase phase) {
      this.phase = phase != null ? phase : BountyHunterPhase.INACTIVE;
   }

   public long createdGameTime() {
      return this.createdGameTime;
   }

   public long dispatchAtGameTime() {
      return this.dispatchAtGameTime;
   }

   public void setDispatchAtGameTime(long dispatchAtGameTime) {
      this.dispatchAtGameTime = dispatchAtGameTime;
   }

   public long nextRedispatchGameTime() {
      return this.nextRedispatchGameTime;
   }

   public void setNextRedispatchGameTime(long nextRedispatchGameTime) {
      this.nextRedispatchGameTime = nextRedispatchGameTime;
   }

   @Nullable
   public UUID hunterEntityUuid() {
      return this.hunterEntityUuid;
   }

   public void setHunterEntityUuid(@Nullable UUID hunterEntityUuid) {
      this.hunterEntityUuid = hunterEntityUuid;
   }

   @Nullable
   public BlockPos lastKnownPos() {
      return this.lastKnownPos;
   }

   public void setLastKnownPos(@Nullable BlockPos lastKnownPos) {
      this.lastKnownPos = lastKnownPos;
   }

   @Nullable
   public ResourceLocation lastKnownDimension() {
      return this.lastKnownDimension;
   }

   public void setLastKnownDimension(@Nullable ResourceLocation lastKnownDimension) {
      this.lastKnownDimension = lastKnownDimension;
   }

   public long lastSightingGameTime() {
      return this.lastSightingGameTime;
   }

   public void setLastSightingGameTime(long lastSightingGameTime) {
      this.lastSightingGameTime = lastSightingGameTime;
   }

   public boolean settled() {
      return this.settled;
   }

   public void setSettled(boolean settled) {
      this.settled = settled;
   }

   @Nullable
   public UUID settlementId() {
      return this.settlementId;
   }

   public void setSettlementId(@Nullable UUID settlementId) {
      this.settlementId = settlementId;
   }

   public int failureCount() {
      return this.failureCount;
   }

   public void incrementFailureCount() {
      this.failureCount++;
   }

   public void setFailureCount(int failureCount) {
      this.failureCount = Math.max(0, failureCount);
   }

   public void setCreatedGameTime(long createdGameTime) {
      this.createdGameTime = createdGameTime;
   }
}
