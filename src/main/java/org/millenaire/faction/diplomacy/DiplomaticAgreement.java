package org.millenaire.faction.diplomacy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;

/**
 * Persistent formal diplomatic agreement.
 * <p>
 * For bilateral cooperative types, {@code primaryFactionId} and {@code secondaryFactionId} are
 * unordered participants. For {@link AgreementType#EMBARGO}, primary is the imposer and secondary
 * is the target.
 */
public final class DiplomaticAgreement {
   private final AgreementId id;
   private final AgreementType type;
   private final FactionId primaryFactionId;
   private final FactionId secondaryFactionId;
   private final long createdGameTime;
   private final long startGameTime;
   private final long expirationGameTime;
   private AgreementStatus status;
   private AgreementTerminationReason terminationReason = AgreementTerminationReason.NONE;
   private long terminatedGameTime;
   private long lastStateChangeGameTime;
   @Nullable
   private String reasonNote;
   private final Deque<DiplomaticHistoryEntry> history = new ArrayDeque<>();

   public DiplomaticAgreement(
      AgreementId id,
      AgreementType type,
      FactionId primaryFactionId,
      FactionId secondaryFactionId,
      long createdGameTime,
      long startGameTime,
      long expirationGameTime
   ) {
      this.id = Objects.requireNonNull(id, "id");
      this.type = Objects.requireNonNull(type, "type");
      this.primaryFactionId = Objects.requireNonNull(primaryFactionId, "primaryFactionId");
      this.secondaryFactionId = Objects.requireNonNull(secondaryFactionId, "secondaryFactionId");
      if (primaryFactionId.equals(secondaryFactionId)) {
         throw new IllegalArgumentException("Agreement cannot be with self: " + primaryFactionId);
      }

      this.createdGameTime = createdGameTime;
      this.startGameTime = startGameTime;
      this.expirationGameTime = Math.max(0L, expirationGameTime);
      this.status = AgreementStatus.ACTIVE;
      this.lastStateChangeGameTime = createdGameTime;
   }

   public AgreementId getId() {
      return this.id;
   }

   public AgreementType getType() {
      return this.type;
   }

   public FactionId getPrimaryFactionId() {
      return this.primaryFactionId;
   }

   public FactionId getSecondaryFactionId() {
      return this.secondaryFactionId;
   }

   /** Embargo imposer / first participant. */
   public FactionId getFactionA() {
      return this.primaryFactionId;
   }

   /** Embargo target / second participant. */
   public FactionId getFactionB() {
      return this.secondaryFactionId;
   }

   public long getCreatedGameTime() {
      return this.createdGameTime;
   }

   public long getStartGameTime() {
      return this.startGameTime;
   }

   /** Absolute game time when the agreement expires, or {@code 0} for permanent. */
   public long getExpirationGameTime() {
      return this.expirationGameTime;
   }

   public boolean isPermanent() {
      return this.expirationGameTime <= 0L;
   }

   public boolean isExpiredAt(long gameTime) {
      return !this.isPermanent() && gameTime >= this.expirationGameTime;
   }

   public AgreementStatus getStatus() {
      return this.status;
   }

   public boolean isActive() {
      return this.status == AgreementStatus.ACTIVE;
   }

   public AgreementTerminationReason getTerminationReason() {
      return this.terminationReason;
   }

   public long getTerminatedGameTime() {
      return this.terminatedGameTime;
   }

   public long getLastStateChangeGameTime() {
      return this.lastStateChangeGameTime;
   }

   @Nullable
   public String getReasonNote() {
      return this.reasonNote;
   }

   public List<DiplomaticHistoryEntry> getHistory() {
      return Collections.unmodifiableList(new ArrayList<>(this.history));
   }

   public boolean involves(FactionId factionId) {
      return factionId != null && (this.primaryFactionId.equals(factionId) || this.secondaryFactionId.equals(factionId));
   }

   public FactionId otherParticipant(FactionId factionId) {
      if (this.primaryFactionId.equals(factionId)) {
         return this.secondaryFactionId;
      }

      if (this.secondaryFactionId.equals(factionId)) {
         return this.primaryFactionId;
      }

      throw new IllegalArgumentException("Faction " + factionId + " is not a participant of " + this.id);
   }

   void setReasonNote(@Nullable String reasonNote) {
      this.reasonNote = reasonNote;
   }

   void terminate(long gameTime, AgreementTerminationReason reason) {
      this.status = AgreementStatus.TERMINATED;
      this.terminationReason = reason == null ? AgreementTerminationReason.EXPLICIT : reason;
      this.terminatedGameTime = gameTime;
      this.lastStateChangeGameTime = gameTime;
   }

   void recordHistory(DiplomaticHistoryEntry entry) {
      Objects.requireNonNull(entry, "entry");
      this.history.addLast(entry);

      while (this.history.size() > DiplomaticConfig.MAX_AGREEMENT_HISTORY_ENTRIES) {
         this.history.removeFirst();
      }
   }

   void replaceHistory(List<DiplomaticHistoryEntry> entries) {
      this.history.clear();
      if (entries == null) {
         return;
      }

      for (DiplomaticHistoryEntry entry : entries) {
         if (entry != null) {
            this.history.addLast(entry);
         }
      }

      while (this.history.size() > DiplomaticConfig.MAX_AGREEMENT_HISTORY_ENTRIES) {
         this.history.removeFirst();
      }
   }

   void loadTerminatedState(AgreementStatus status, AgreementTerminationReason reason, long terminatedGameTime, long lastStateChangeGameTime, @Nullable String reasonNote) {
      this.status = status == null ? AgreementStatus.ACTIVE : status;
      this.terminationReason = reason == null ? AgreementTerminationReason.NONE : reason;
      this.terminatedGameTime = Math.max(0L, terminatedGameTime);
      this.lastStateChangeGameTime = Math.max(0L, lastStateChangeGameTime);
      this.reasonNote = reasonNote;
   }
}
