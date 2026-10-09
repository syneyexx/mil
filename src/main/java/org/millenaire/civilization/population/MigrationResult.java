package org.millenaire.civilization.population;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Explicit migration outcome. Callers must not treat a non-null operation id as success.
 */
public final class MigrationResult {
   public enum Outcome {
      REJECTED,
      RESERVED,
      PENDING,
      COMPLETE,
      ALREADY_COMPLETE,
      FAILED,
      RECOVERY_REQUIRED
   }

   @Nullable
   private final UUID operationId;
   private final Outcome outcome;
   private final boolean completed;
   private final boolean retryable;
   private final String reasonKey;
   @Nullable
   private final UUID origin;
   @Nullable
   private final UUID destination;
   @Nullable
   private final UUID destinationHome;

   public MigrationResult(
      @Nullable UUID operationId,
      Outcome outcome,
      boolean completed,
      boolean retryable,
      String reasonKey,
      @Nullable UUID origin,
      @Nullable UUID destination,
      @Nullable UUID destinationHome
   ) {
      this.operationId = operationId;
      this.outcome = outcome == null ? Outcome.FAILED : outcome;
      this.completed = completed;
      this.retryable = retryable;
      this.reasonKey = reasonKey == null ? "" : reasonKey;
      this.origin = origin;
      this.destination = destination;
      this.destinationHome = destinationHome;
   }

   public static MigrationResult rejected(String reason) {
      return new MigrationResult(null, Outcome.REJECTED, false, true, reason, null, null, null);
   }

   public static MigrationResult rejected(String reason, @Nullable UUID origin, @Nullable UUID destination) {
      return new MigrationResult(null, Outcome.REJECTED, false, true, reason, origin, destination, null);
   }

   public static MigrationResult of(
      UUID operationId,
      Outcome outcome,
      boolean completed,
      boolean retryable,
      String reason,
      @Nullable UUID origin,
      @Nullable UUID destination,
      @Nullable UUID home
   ) {
      return new MigrationResult(operationId, outcome, completed, retryable, reason, origin, destination, home);
   }

   @Nullable
   public UUID operationId() {
      return this.operationId;
   }

   public Outcome outcome() {
      return this.outcome;
   }

   public boolean completed() {
      return this.completed;
   }

   public boolean retryable() {
      return this.retryable;
   }

   public String reasonKey() {
      return this.reasonKey;
   }

   @Nullable
   public UUID origin() {
      return this.origin;
   }

   @Nullable
   public UUID destination() {
      return this.destination;
   }

   @Nullable
   public UUID destinationHome() {
      return this.destinationHome;
   }
}
