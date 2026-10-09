package org.millenaire.living.actions;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/**
 * Server-owned action proposal. Model text never creates these.
 */
public final class NpcActionProposal {
   public enum State {
      PENDING,
      ACCEPTED,
      DECLINED,
      EXECUTED,
      EXPIRED,
      REJECTED
   }

   private final String proposalId;
   private final String sessionId;
   private final UUID playerId;
   private final UUID npcId;
   private final UUID villageId;
   private final NpcActionType actionType;
   @Nullable
   private final String targetId;
   private final CompoundTag validatedParameters;
   private final long createdGameTime;
   private final long expiryGameTime;
   private final int stateVersion;
   private State state;

   public NpcActionProposal(
      String proposalId,
      String sessionId,
      UUID playerId,
      UUID npcId,
      UUID villageId,
      NpcActionType actionType,
      @Nullable String targetId,
      CompoundTag validatedParameters,
      long createdGameTime,
      long expiryGameTime,
      int stateVersion
   ) {
      this.proposalId = proposalId;
      this.sessionId = sessionId;
      this.playerId = playerId;
      this.npcId = npcId;
      this.villageId = villageId;
      this.actionType = actionType;
      this.targetId = targetId;
      this.validatedParameters = validatedParameters == null ? new CompoundTag() : validatedParameters.copy();
      this.createdGameTime = createdGameTime;
      this.expiryGameTime = expiryGameTime;
      this.stateVersion = stateVersion;
      this.state = State.PENDING;
   }

   public String proposalId() {
      return this.proposalId;
   }

   public String sessionId() {
      return this.sessionId;
   }

   public UUID playerId() {
      return this.playerId;
   }

   public UUID npcId() {
      return this.npcId;
   }

   public UUID villageId() {
      return this.villageId;
   }

   public NpcActionType actionType() {
      return this.actionType;
   }

   @Nullable
   public String targetId() {
      return this.targetId;
   }

   public CompoundTag validatedParameters() {
      return this.validatedParameters.copy();
   }

   public long createdGameTime() {
      return this.createdGameTime;
   }

   public long expiryGameTime() {
      return this.expiryGameTime;
   }

   public int stateVersion() {
      return this.stateVersion;
   }

   public State state() {
      return this.state;
   }

   public void setState(State state) {
      this.state = state;
   }

   public boolean isExpired(long now) {
      return now > this.expiryGameTime || this.state == State.EXPIRED;
   }
}
