package org.millenaire.faction.politics;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;

public final class PoliticalDecision {
   private final DecisionId id;
   private final FactionId factionId;
   private final DecisionType type;
   @Nullable
   private final LeaderId proposerLeaderId;
   @Nullable
   private final PoliticalGroupId proposerGroupId;
   private final Set<PoliticalGroupId> affectedGroupIds = new LinkedHashSet<>();
   private int politicalCost;
   private int approvalSupport;
   private final long decidedGameTime;
   private boolean successful;
   @Nullable
   private String requirementsNote;
   @Nullable
   private String effectsNote;
   @Nullable
   private String outcome;

   public PoliticalDecision(
      DecisionId id,
      FactionId factionId,
      DecisionType type,
      long gameTime,
      @Nullable LeaderId proposerLeaderId,
      @Nullable PoliticalGroupId proposerGroupId
   ) {
      this.id = Objects.requireNonNull(id, "id");
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.type = Objects.requireNonNull(type, "type");
      this.decidedGameTime = gameTime;
      this.proposerLeaderId = proposerLeaderId;
      this.proposerGroupId = proposerGroupId;
   }

   public DecisionId getId() {
      return this.id;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   public DecisionType getType() {
      return this.type;
   }

   @Nullable
   public LeaderId getProposerLeaderId() {
      return this.proposerLeaderId;
   }

   @Nullable
   public PoliticalGroupId getProposerGroupId() {
      return this.proposerGroupId;
   }

   public Set<PoliticalGroupId> getAffectedGroupIds() {
      return Collections.unmodifiableSet(this.affectedGroupIds);
   }

   public int getPoliticalCost() {
      return this.politicalCost;
   }

   public int getApprovalSupport() {
      return this.approvalSupport;
   }

   public long getDecidedGameTime() {
      return this.decidedGameTime;
   }

   public boolean isSuccessful() {
      return this.successful;
   }

   @Nullable
   public String getRequirementsNote() {
      return this.requirementsNote;
   }

   @Nullable
   public String getEffectsNote() {
      return this.effectsNote;
   }

   @Nullable
   public String getOutcome() {
      return this.outcome;
   }

   void addAffectedGroup(PoliticalGroupId groupId) {
      if (groupId != null) {
         this.affectedGroupIds.add(groupId);
      }
   }

   void finalizeOutcome(boolean successful, int politicalCost, int approvalSupport, @Nullable String requirements, @Nullable String effects, @Nullable String outcome) {
      this.successful = successful;
      this.politicalCost = PoliticsConfig.clampNonNegative(politicalCost);
      this.approvalSupport = PoliticsConfig.clamp(approvalSupport);
      this.requirementsNote = requirements;
      this.effectsNote = effects;
      this.outcome = outcome;
   }

   void loadState(
      Set<PoliticalGroupId> affected,
      int politicalCost,
      int approvalSupport,
      boolean successful,
      @Nullable String requirements,
      @Nullable String effects,
      @Nullable String outcome
   ) {
      this.affectedGroupIds.clear();
      if (affected != null) {
         this.affectedGroupIds.addAll(affected);
      }

      this.politicalCost = PoliticsConfig.clampNonNegative(politicalCost);
      this.approvalSupport = PoliticsConfig.clamp(approvalSupport);
      this.successful = successful;
      this.requirementsNote = requirements;
      this.effectsNote = effects;
      this.outcome = outcome;
   }
}
