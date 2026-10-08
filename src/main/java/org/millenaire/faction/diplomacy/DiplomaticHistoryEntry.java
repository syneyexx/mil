package org.millenaire.faction.diplomacy;

import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;

/** One bounded diplomatic history record. */
public final class DiplomaticHistoryEntry {
   private final long gameTime;
   private final DiplomaticEventType eventType;
   private final FactionId factionA;
   private final FactionId factionB;
   @Nullable
   private final AgreementId agreementId;
   @Nullable
   private final AgreementType agreementType;
   @Nullable
   private final AgreementTerminationReason terminationReason;

   public DiplomaticHistoryEntry(
      long gameTime,
      DiplomaticEventType eventType,
      FactionId factionA,
      FactionId factionB,
      @Nullable AgreementId agreementId,
      @Nullable AgreementType agreementType,
      @Nullable AgreementTerminationReason terminationReason
   ) {
      this.gameTime = gameTime;
      this.eventType = Objects.requireNonNull(eventType, "eventType");
      this.factionA = Objects.requireNonNull(factionA, "factionA");
      this.factionB = Objects.requireNonNull(factionB, "factionB");
      this.agreementId = agreementId;
      this.agreementType = agreementType;
      this.terminationReason = terminationReason;
   }

   public long getGameTime() {
      return this.gameTime;
   }

   public DiplomaticEventType getEventType() {
      return this.eventType;
   }

   public FactionId getFactionA() {
      return this.factionA;
   }

   public FactionId getFactionB() {
      return this.factionB;
   }

   @Nullable
   public AgreementId getAgreementId() {
      return this.agreementId;
   }

   @Nullable
   public AgreementType getAgreementType() {
      return this.agreementType;
   }

   @Nullable
   public AgreementTerminationReason getTerminationReason() {
      return this.terminationReason;
   }
}
