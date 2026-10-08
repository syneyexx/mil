package org.millenaire.faction.conflict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;

public final class NegotiatedSettlement {
   private final ConflictId conflictId;
   private final FactionId originFactionId;
   private final ConflictResolution outcome;
   private final List<String> terms = new ArrayList<>();
   private final long settledGameTime;
   private int autonomyGranted;
   private boolean leadershipChanged;
   private boolean governmentReformed;
   private boolean territoryCeded;
   @Nullable
   private String detail;

   public NegotiatedSettlement(ConflictId conflictId, FactionId originFactionId, ConflictResolution outcome, long gameTime) {
      this.conflictId = Objects.requireNonNull(conflictId, "conflictId");
      this.originFactionId = Objects.requireNonNull(originFactionId, "originFactionId");
      this.outcome = outcome == null ? ConflictResolution.NEGOTIATED_SETTLEMENT : outcome;
      this.settledGameTime = gameTime;
   }

   public ConflictId getConflictId() {
      return this.conflictId;
   }

   public FactionId getOriginFactionId() {
      return this.originFactionId;
   }

   public ConflictResolution getOutcome() {
      return this.outcome;
   }

   public List<String> getTerms() {
      return Collections.unmodifiableList(this.terms);
   }

   public long getSettledGameTime() {
      return this.settledGameTime;
   }

   public int getAutonomyGranted() {
      return this.autonomyGranted;
   }

   public boolean isLeadershipChanged() {
      return this.leadershipChanged;
   }

   public boolean isGovernmentReformed() {
      return this.governmentReformed;
   }

   public boolean isTerritoryCeded() {
      return this.territoryCeded;
   }

   @Nullable
   public String getDetail() {
      return this.detail;
   }

   void addTerm(String term) {
      if (term != null && !term.isBlank()) {
         this.terms.add(term.trim());
      }
   }

   void setAutonomyGranted(int autonomy) {
      this.autonomyGranted = ConflictConfig.clamp(autonomy);
   }

   void setLeadershipChanged(boolean changed) {
      this.leadershipChanged = changed;
   }

   void setGovernmentReformed(boolean reformed) {
      this.governmentReformed = reformed;
   }

   void setTerritoryCeded(boolean ceded) {
      this.territoryCeded = ceded;
   }

   void setDetail(@Nullable String detail) {
      this.detail = detail;
   }

   void loadState(List<String> terms, int autonomy, boolean leadership, boolean reform, boolean territory, @Nullable String detail) {
      this.terms.clear();
      if (terms != null) {
         this.terms.addAll(terms);
      }

      this.autonomyGranted = ConflictConfig.clamp(autonomy);
      this.leadershipChanged = leadership;
      this.governmentReformed = reform;
      this.territoryCeded = territory;
      this.detail = detail;
   }
}
