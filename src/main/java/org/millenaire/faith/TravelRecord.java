package org.millenaire.faith;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import org.millenaire.village.VillageId;

/**
 * Abstract NPC pilgrimage / travel outside loaded chunks. No physical multi-chunk pathing.
 */
public final class TravelRecord {
   public enum Status {
      PLANNED,
      IN_PROGRESS,
      COMPLETED,
      FAILED,
      EXPIRED
   }

   private final UUID id;
   private final UUID villagerRecordUuid;
   private final VillageId fromVillage;
   private final VillageId toVillage;
   private final long plannedDay;
   private long completeByDay;
   private Status status = Status.PLANNED;
   @Nullable
   private String purpose;

   public TravelRecord(UUID id, UUID villagerRecordUuid, VillageId from, VillageId to, long plannedDay) {
      this.id = id;
      this.villagerRecordUuid = villagerRecordUuid;
      this.fromVillage = from;
      this.toVillage = to;
      this.plannedDay = plannedDay;
      this.completeByDay = plannedDay + 7L;
   }

   public UUID id() {
      return this.id;
   }

   public UUID villagerRecordUuid() {
      return this.villagerRecordUuid;
   }

   public VillageId fromVillage() {
      return this.fromVillage;
   }

   public VillageId toVillage() {
      return this.toVillage;
   }

   public long plannedDay() {
      return this.plannedDay;
   }

   public long completeByDay() {
      return this.completeByDay;
   }

   public void setCompleteByDay(long completeByDay) {
      this.completeByDay = completeByDay;
   }

   public Status status() {
      return this.status;
   }

   public void setStatus(Status status) {
      this.status = status;
   }

   @Nullable
   public String purpose() {
      return this.purpose;
   }

   public void setPurpose(@Nullable String purpose) {
      this.purpose = purpose;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.id);
      tag.putUUID("villager", this.villagerRecordUuid);
      tag.putUUID("from", this.fromVillage.uuid());
      tag.putUUID("to", this.toVillage.uuid());
      tag.putLong("day", this.plannedDay);
      tag.putLong("by", this.completeByDay);
      tag.putString("status", this.status.name());
      if (this.purpose != null) {
         tag.putString("purpose", this.purpose);
      }
      return tag;
   }

   public static TravelRecord load(CompoundTag tag) {
      TravelRecord r = new TravelRecord(
         tag.getUUID("id"),
         tag.getUUID("villager"),
         new VillageId(tag.getUUID("from")),
         new VillageId(tag.getUUID("to")),
         tag.getLong("day")
      );
      r.completeByDay = tag.getLong("by");
      try {
         r.status = Status.valueOf(tag.getString("status"));
      } catch (IllegalArgumentException e) {
         r.status = Status.EXPIRED;
      }
      if (tag.contains("purpose")) {
         r.purpose = tag.getString("purpose");
      }
      return r;
   }
}
