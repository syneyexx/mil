package org.millenaire.faction.territory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.war.PeaceOutcome;
import org.millenaire.faction.war.WarId;

/**
 * Finalized historical peace treaty. Immutable after creation — never silently rewritten.
 */
public final class PeaceTreaty {
   private final PeaceTreatyId id;
   private final WarId warId;
   private final Set<FactionId> participantFactionIds = new LinkedHashSet<>();
   private final PeaceOutcome outcome;
   private final List<PeaceTerm> terms = new ArrayList<>();
   private final List<TransferId> transferIds = new ArrayList<>();
   private final long settledGameTime;
   private final int finalWarScore;
   @Nullable
   private final String reason;
   private final boolean finalized = true;

   public PeaceTreaty(
      PeaceTreatyId id,
      WarId warId,
      PeaceOutcome outcome,
      long settledGameTime,
      int finalWarScore,
      @Nullable String reason
   ) {
      this.id = Objects.requireNonNull(id, "id");
      this.warId = Objects.requireNonNull(warId, "warId");
      this.outcome = outcome == null ? PeaceOutcome.WHITE_PEACE : outcome;
      this.settledGameTime = settledGameTime;
      this.finalWarScore = finalWarScore;
      this.reason = reason;
   }

   public PeaceTreatyId getId() {
      return this.id;
   }

   public WarId getWarId() {
      return this.warId;
   }

   public Set<FactionId> getParticipantFactionIds() {
      return Collections.unmodifiableSet(this.participantFactionIds);
   }

   public PeaceOutcome getOutcome() {
      return this.outcome;
   }

   public List<PeaceTerm> getTerms() {
      return Collections.unmodifiableList(this.terms);
   }

   public List<TransferId> getTransferIds() {
      return Collections.unmodifiableList(this.transferIds);
   }

   public long getSettledGameTime() {
      return this.settledGameTime;
   }

   public int getFinalWarScore() {
      return this.finalWarScore;
   }

   @Nullable
   public String getReason() {
      return this.reason;
   }

   public boolean isFinalized() {
      return this.finalized;
   }

   void addParticipant(FactionId factionId) {
      if (factionId != null) {
         this.participantFactionIds.add(factionId);
      }
   }

   void addTerm(PeaceTerm term) {
      if (term != null) {
         this.terms.add(term);
      }
   }

   void addTransferId(TransferId transferId) {
      if (transferId != null) {
         this.transferIds.add(transferId);
      }
   }

   void loadParticipants(Set<FactionId> participants) {
      this.participantFactionIds.clear();
      if (participants != null) {
         this.participantFactionIds.addAll(participants);
      }
   }

   void loadTerms(List<PeaceTerm> terms) {
      this.terms.clear();
      if (terms != null) {
         this.terms.addAll(terms);
      }
   }

   void loadTransferIds(List<TransferId> transferIds) {
      this.transferIds.clear();
      if (transferIds != null) {
         this.transferIds.addAll(transferIds);
      }
   }
}
