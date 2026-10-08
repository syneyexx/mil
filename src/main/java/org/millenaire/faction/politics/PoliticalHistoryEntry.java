package org.millenaire.faction.politics;

import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;

public final class PoliticalHistoryEntry {
   private final long gameTime;
   private final PoliticalEventType eventType;
   private final FactionId factionId;
   @Nullable
   private final GovernmentId governmentId;
   @Nullable
   private final LeaderId leaderId;
   @Nullable
   private final PoliticalGroupId groupId;
   @Nullable
   private final CoalitionId coalitionId;
   @Nullable
   private final CrisisId crisisId;
   @Nullable
   private final DecisionId decisionId;
   private final int quantity;
   @Nullable
   private final String detail;

   public PoliticalHistoryEntry(
      long gameTime,
      PoliticalEventType eventType,
      FactionId factionId,
      @Nullable GovernmentId governmentId,
      @Nullable LeaderId leaderId,
      @Nullable PoliticalGroupId groupId,
      @Nullable CoalitionId coalitionId,
      @Nullable CrisisId crisisId,
      @Nullable DecisionId decisionId,
      int quantity,
      @Nullable String detail
   ) {
      this.gameTime = gameTime;
      this.eventType = Objects.requireNonNull(eventType, "eventType");
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.governmentId = governmentId;
      this.leaderId = leaderId;
      this.groupId = groupId;
      this.coalitionId = coalitionId;
      this.crisisId = crisisId;
      this.decisionId = decisionId;
      this.quantity = Math.max(0, quantity);
      this.detail = detail;
   }

   public long getGameTime() {
      return this.gameTime;
   }

   public PoliticalEventType getEventType() {
      return this.eventType;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   @Nullable
   public GovernmentId getGovernmentId() {
      return this.governmentId;
   }

   @Nullable
   public LeaderId getLeaderId() {
      return this.leaderId;
   }

   @Nullable
   public PoliticalGroupId getGroupId() {
      return this.groupId;
   }

   @Nullable
   public CoalitionId getCoalitionId() {
      return this.coalitionId;
   }

   @Nullable
   public CrisisId getCrisisId() {
      return this.crisisId;
   }

   @Nullable
   public DecisionId getDecisionId() {
      return this.decisionId;
   }

   public int getQuantity() {
      return this.quantity;
   }

   @Nullable
   public String getDetail() {
      return this.detail;
   }
}
