package org.millenaire.civilization.settlement;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Lifecycle for autonomous founding: planned → materializing → activated → migrated / failed / cancelled.
 */
public final class ColonizationOperation {
   public enum Status {
      PLANNED,
      AWAITING_CHUNKS,
      MATERIALIZING,
      ACTIVATED,
      MIGRATED,
      FAILED,
      CANCELLED
   }

   private final UUID operationId;
   private final UUID parentVillageId;
   private final String factionId;
   private final UUID planId;
   private final BlockPos targetCenter;
   private final long createdAtGameTime;
   private Status status;
   private String reason = "";
   private boolean resourcesReserved;

   public ColonizationOperation(
      UUID operationId,
      UUID parentVillageId,
      String factionId,
      UUID planId,
      BlockPos targetCenter,
      long createdAtGameTime
   ) {
      this.operationId = operationId;
      this.parentVillageId = parentVillageId;
      this.factionId = factionId;
      this.planId = planId;
      this.targetCenter = targetCenter;
      this.createdAtGameTime = createdAtGameTime;
      this.status = Status.PLANNED;
   }

   public UUID operationId() {
      return this.operationId;
   }

   public UUID parentVillageId() {
      return this.parentVillageId;
   }

   public String factionId() {
      return this.factionId;
   }

   public UUID planId() {
      return this.planId;
   }

   public BlockPos targetCenter() {
      return this.targetCenter;
   }

   public long createdAtGameTime() {
      return this.createdAtGameTime;
   }

   public Status status() {
      return this.status;
   }

   public void setStatus(Status status) {
      this.status = status == null ? Status.FAILED : status;
   }

   public String reason() {
      return this.reason;
   }

   public void setReason(String reason) {
      this.reason = reason == null ? "" : reason;
   }

   public boolean resourcesReserved() {
      return this.resourcesReserved;
   }

   public void setResourcesReserved(boolean v) {
      this.resourcesReserved = v;
   }

   public boolean isTerminal() {
      return this.status == Status.MIGRATED || this.status == Status.FAILED || this.status == Status.CANCELLED;
   }

   public boolean isActive() {
      return !isTerminal();
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.operationId);
      tag.putUUID("parent", this.parentVillageId);
      tag.putString("faction", this.factionId);
      tag.putUUID("plan", this.planId);
      tag.putInt("x", this.targetCenter.getX());
      tag.putInt("y", this.targetCenter.getY());
      tag.putInt("z", this.targetCenter.getZ());
      tag.putLong("at", this.createdAtGameTime);
      tag.putString("status", this.status.name());
      tag.putString("reason", this.reason);
      tag.putBoolean("reserved", this.resourcesReserved);
      return tag;
   }

   @Nullable
   public static ColonizationOperation load(CompoundTag tag) {
      try {
         ColonizationOperation op = new ColonizationOperation(
            tag.getUUID("id"),
            tag.getUUID("parent"),
            tag.getString("faction"),
            tag.getUUID("plan"),
            new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
            tag.getLong("at")
         );
         try {
            op.status = Status.valueOf(tag.getString("status"));
         } catch (Exception ignored) {
         }
         op.reason = tag.getString("reason");
         op.resourcesReserved = tag.getBoolean("reserved");
         return op;
      } catch (Exception e) {
         return null;
      }
   }
}
