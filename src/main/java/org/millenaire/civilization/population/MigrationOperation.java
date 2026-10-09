package org.millenaire.civilization.population;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/**
 * Idempotent migration transaction record.
 * Normal transitions are one-way toward terminal states.
 * Proven physical reconciliation uses {@link #reconcileStatus(Status)} — a separate, limited path.
 */
public final class MigrationOperation {
   public enum Status {
      RESERVED,
      REMOVED_FROM_ORIGIN,
      ADDED_TO_DESTINATION,
      COMPLETE,
      FAILED,
      ROLLED_BACK
   }

   private final UUID operationId;
   private final UUID villagerId;
   private final UUID originVillageId;
   private final UUID destinationVillageId;
   private final long createdAtGameTime;
   private Status status;
   private String failureReason;
   @Nullable
   private UUID destinationBuildingId;
   private boolean needsRecovery;
   private int recoveryAttempts;

   public MigrationOperation(
      UUID operationId,
      UUID villagerId,
      UUID originVillageId,
      UUID destinationVillageId,
      long createdAtGameTime
   ) {
      this.operationId = operationId;
      this.villagerId = villagerId;
      this.originVillageId = originVillageId;
      this.destinationVillageId = destinationVillageId;
      this.createdAtGameTime = createdAtGameTime;
      this.status = Status.RESERVED;
      this.failureReason = "";
   }

   public UUID operationId() {
      return this.operationId;
   }

   public UUID villagerId() {
      return this.villagerId;
   }

   public UUID originVillageId() {
      return this.originVillageId;
   }

   public UUID destinationVillageId() {
      return this.destinationVillageId;
   }

   public long createdAtGameTime() {
      return this.createdAtGameTime;
   }

   public Status status() {
      return this.status;
   }

   public String failureReason() {
      return this.failureReason;
   }

   @Nullable
   public UUID destinationBuildingId() {
      return this.destinationBuildingId;
   }

   public void setDestinationBuilding(@Nullable UUID buildingId) {
      this.destinationBuildingId = buildingId;
   }

   public boolean needsRecovery() {
      return this.needsRecovery;
   }

   public void markNeedsRecovery(boolean v) {
      this.needsRecovery = v;
   }

   public int recoveryAttempts() {
      return this.recoveryAttempts;
   }

   public void incrementRecoveryAttempts() {
      this.recoveryAttempts++;
   }

   public boolean isTerminal() {
      return this.status == Status.COMPLETE || this.status == Status.FAILED || this.status == Status.ROLLED_BACK;
   }

   public boolean isActive() {
      return !isTerminal() || this.needsRecovery;
   }

   /**
    * Strict normal transition. Does not allow REMOVED_FROM_ORIGIN → COMPLETE or FAILED → COMPLETE.
    */
   public boolean trySetStatus(Status next) {
      if (next == null || next == this.status) {
         return false;
      }
      boolean ok = switch (this.status) {
         case RESERVED -> next == Status.REMOVED_FROM_ORIGIN || next == Status.FAILED || next == Status.ROLLED_BACK || next == Status.COMPLETE;
         case REMOVED_FROM_ORIGIN -> next == Status.ADDED_TO_DESTINATION || next == Status.FAILED || next == Status.ROLLED_BACK;
         case ADDED_TO_DESTINATION -> next == Status.COMPLETE || next == Status.FAILED;
         case COMPLETE, FAILED, ROLLED_BACK -> false;
      };
      if (ok) {
         this.status = next;
      }
      return ok;
   }

   /**
    * Limited recovery transition after physical state has been verified.
    * Allowed: any non-COMPLETE → COMPLETE when destination ownership is proven;
    * any non-terminal → FAILED when origin-only / unresolvable evidence demands it.
    * Does not invent villagers or clear needsRecovery by itself.
    */
   public boolean reconcileStatus(Status next) {
      if (next == null || next == this.status) {
         return next == this.status;
      }
      if (next == Status.COMPLETE) {
         if (this.status == Status.COMPLETE) {
            return true;
         }
         // Proven completion may jump from mid-flight or FAILED+needsRecovery.
         if (this.status == Status.FAILED && !this.needsRecovery) {
            return false;
         }
         this.status = Status.COMPLETE;
         this.failureReason = "";
         return true;
      }
      if (next == Status.FAILED || next == Status.ROLLED_BACK) {
         if (this.status == Status.COMPLETE) {
            return false;
         }
         this.status = next;
         return true;
      }
      return trySetStatus(next);
   }

   public void fail(String reason) {
      this.failureReason = reason == null ? "" : reason;
      if (this.status != Status.COMPLETE) {
         this.status = Status.FAILED;
      }
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.operationId);
      tag.putUUID("villager", this.villagerId);
      tag.putUUID("origin", this.originVillageId);
      tag.putUUID("dest", this.destinationVillageId);
      tag.putLong("at", this.createdAtGameTime);
      tag.putString("status", this.status.name());
      tag.putString("fail", this.failureReason);
      tag.putBoolean("needsRecovery", this.needsRecovery);
      tag.putInt("recoveryAttempts", this.recoveryAttempts);
      if (this.destinationBuildingId != null) {
         tag.putUUID("destBuilding", this.destinationBuildingId);
      }
      return tag;
   }

   @Nullable
   public static MigrationOperation load(CompoundTag tag) {
      try {
         MigrationOperation op = new MigrationOperation(
            tag.getUUID("id"),
            tag.getUUID("villager"),
            tag.getUUID("origin"),
            tag.getUUID("dest"),
            tag.getLong("at")
         );
         try {
            op.status = Status.valueOf(tag.getString("status"));
         } catch (Exception ignored) {
         }
         op.failureReason = tag.getString("fail");
         op.needsRecovery = tag.getBoolean("needsRecovery");
         op.recoveryAttempts = tag.getInt("recoveryAttempts");
         if (tag.hasUUID("destBuilding")) {
            op.destinationBuildingId = tag.getUUID("destBuilding");
         }
         return op;
      } catch (Exception e) {
         return null;
      }
   }
}
