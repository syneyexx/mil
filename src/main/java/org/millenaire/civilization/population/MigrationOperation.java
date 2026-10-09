package org.millenaire.civilization.population;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/**
 * Idempotent migration transaction record. Status transitions are one-way toward terminal states.
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

   public boolean trySetStatus(Status next) {
      if (next == null || next == this.status) {
         return false;
      }
      boolean ok = switch (this.status) {
         case RESERVED -> next == Status.REMOVED_FROM_ORIGIN || next == Status.FAILED || next == Status.ROLLED_BACK;
         case REMOVED_FROM_ORIGIN -> next == Status.ADDED_TO_DESTINATION || next == Status.FAILED || next == Status.ROLLED_BACK;
         case ADDED_TO_DESTINATION -> next == Status.COMPLETE || next == Status.FAILED;
         case COMPLETE, FAILED, ROLLED_BACK -> false;
      };
      if (ok) {
         this.status = next;
      }
      return ok;
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
         return op;
      } catch (Exception e) {
         return null;
      }
   }
}
