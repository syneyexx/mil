package org.millenaire.faith;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import org.millenaire.building.BuildingId;
import org.millenaire.village.VillageId;

/** Local chapter of an order within one village. At most one per (orderInstance, village). */
public final class Chapter {
   public enum Stage {
      SEEDED,
      SHRINE_READY,
      ACTIVE,
      EXPOSED,
      ORPHANED,
      DISBANDED
   }

   private final UUID id;
   private final UUID orderInstanceId;
   private final VillageId villageId;
   @Nullable
   private BuildingId shrineBuildingId;
   private Stage stage = Stage.SEEDED;
   private int cohesion = 50;
   private int secrecy = 70;
   private int tensionContribution;
   private long lastRitualGameTime = -1L;

   public Chapter(UUID id, UUID orderInstanceId, VillageId villageId) {
      this.id = id;
      this.orderInstanceId = orderInstanceId;
      this.villageId = villageId;
   }

   public UUID id() {
      return this.id;
   }

   public UUID orderInstanceId() {
      return this.orderInstanceId;
   }

   public VillageId villageId() {
      return this.villageId;
   }

   @Nullable
   public BuildingId shrineBuildingId() {
      return this.shrineBuildingId;
   }

   public void setShrineBuildingId(@Nullable BuildingId shrineBuildingId) {
      this.shrineBuildingId = shrineBuildingId;
   }

   public Stage stage() {
      return this.stage;
   }

   public void setStage(Stage stage) {
      this.stage = stage;
   }

   public int cohesion() {
      return this.cohesion;
   }

   public void setCohesion(int cohesion) {
      this.cohesion = clamp(cohesion);
   }

   public void addCohesion(int delta) {
      setCohesion(this.cohesion + delta);
   }

   public int secrecy() {
      return this.secrecy;
   }

   public void setSecrecy(int secrecy) {
      this.secrecy = clamp(secrecy);
   }

   public void addSecrecy(int delta) {
      setSecrecy(this.secrecy + delta);
   }

   public int tensionContribution() {
      return this.tensionContribution;
   }

   public void setTensionContribution(int tensionContribution) {
      this.tensionContribution = Math.max(0, Math.min(100, tensionContribution));
   }

   public long lastRitualGameTime() {
      return this.lastRitualGameTime;
   }

   public void setLastRitualGameTime(long lastRitualGameTime) {
      this.lastRitualGameTime = lastRitualGameTime;
   }

   private static int clamp(int v) {
      return Math.max(0, Math.min(100, v));
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.id);
      tag.putUUID("order", this.orderInstanceId);
      tag.putUUID("village", this.villageId.uuid());
      if (this.shrineBuildingId != null) {
         tag.putUUID("shrine", this.shrineBuildingId.uuid());
      }
      tag.putString("stage", this.stage.name());
      tag.putInt("cohesion", this.cohesion);
      tag.putInt("secrecy", this.secrecy);
      tag.putInt("tension", this.tensionContribution);
      tag.putLong("last_ritual", this.lastRitualGameTime);
      return tag;
   }

   public static Chapter load(CompoundTag tag) {
      Chapter chapter = new Chapter(
         tag.getUUID("id"),
         tag.getUUID("order"),
         new VillageId(tag.getUUID("village"))
      );
      if (tag.hasUUID("shrine")) {
         chapter.shrineBuildingId = new BuildingId(tag.getUUID("shrine"));
      }
      try {
         chapter.stage = Stage.valueOf(tag.getString("stage"));
      } catch (IllegalArgumentException e) {
         chapter.stage = Stage.SEEDED;
      }
      chapter.cohesion = tag.contains("cohesion") ? tag.getInt("cohesion") : 50;
      chapter.secrecy = tag.contains("secrecy") ? tag.getInt("secrecy") : 70;
      chapter.tensionContribution = tag.getInt("tension");
      chapter.lastRitualGameTime = tag.contains("last_ritual") ? tag.getLong("last_ritual") : -1L;
      return chapter;
   }
}
