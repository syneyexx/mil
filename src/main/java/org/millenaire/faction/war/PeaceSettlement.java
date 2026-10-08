package org.millenaire.faction.war;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Diplomatic peace terms for Phase 6. Explicitly excludes territorial transfer.
 */
public final class PeaceSettlement {
   private final WarId warId;
   private final PeaceOutcome outcome;
   private final int finalWarScore;
   private final long settledGameTime;
   @Nullable
   private final String termsNote;
   @Nullable
   private final String reason;

   public PeaceSettlement(WarId warId, PeaceOutcome outcome, int finalWarScore, long settledGameTime, @Nullable String termsNote, @Nullable String reason) {
      this.warId = Objects.requireNonNull(warId, "warId");
      this.outcome = outcome == null ? PeaceOutcome.WHITE_PEACE : outcome;
      this.finalWarScore = WarConfig.clampWarScore(finalWarScore);
      this.settledGameTime = settledGameTime;
      this.termsNote = termsNote;
      this.reason = reason;
   }

   public WarId getWarId() {
      return this.warId;
   }

   public PeaceOutcome getOutcome() {
      return this.outcome;
   }

   public int getFinalWarScore() {
      return this.finalWarScore;
   }

   public long getSettledGameTime() {
      return this.settledGameTime;
   }

   @Nullable
   public String getTermsNote() {
      return this.termsNote;
   }

   @Nullable
   public String getReason() {
      return this.reason;
   }
}
