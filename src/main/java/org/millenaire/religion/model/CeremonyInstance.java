package org.millenaire.religion.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.building.BuildingId;

public final class CeremonyInstance {
   private final UUID ceremonyId;
   private final UUID villageId;
   private final ResourceLocation ritualId;
   private final ResourceLocation traditionId;
   private CeremonyState state;
   @Nullable
   private BuildingId buildingId;
   @Nullable
   private UUID leaderVillagerId;
   private long plannedStartGameTime;
   private long gatherDeadlineGameTime;
   private long activeEndGameTime;
   private final Set<UUID> creditedParticipants = new HashSet<>();
   private final Set<UUID> arrivedParticipants = new HashSet<>();
   private final Map<String, Integer> consumedSupplies = new HashMap<>();
   private boolean suppliesConsumed;
   private boolean outcomeApplied;
   @Nullable
   private String interruptReason;
   @Nullable
   private String festivalLink;
   private long createdGameTime;
   private int schemaVersion = 1;

   public CeremonyInstance(
      UUID ceremonyId,
      UUID villageId,
      ResourceLocation ritualId,
      ResourceLocation traditionId,
      long createdGameTime
   ) {
      this.ceremonyId = ceremonyId;
      this.villageId = villageId;
      this.ritualId = ritualId;
      this.traditionId = traditionId;
      this.state = CeremonyState.PLANNED;
      this.createdGameTime = createdGameTime;
   }

   public UUID ceremonyId() {
      return this.ceremonyId;
   }

   public UUID villageId() {
      return this.villageId;
   }

   public ResourceLocation ritualId() {
      return this.ritualId;
   }

   public ResourceLocation traditionId() {
      return this.traditionId;
   }

   public CeremonyState state() {
      return this.state;
   }

   public void setState(CeremonyState state) {
      this.state = state == null ? CeremonyState.CANCELLED : state;
   }

   @Nullable
   public BuildingId buildingId() {
      return this.buildingId;
   }

   public void setBuildingId(@Nullable BuildingId buildingId) {
      this.buildingId = buildingId;
   }

   @Nullable
   public UUID leaderVillagerId() {
      return this.leaderVillagerId;
   }

   public void setLeaderVillagerId(@Nullable UUID leaderVillagerId) {
      this.leaderVillagerId = leaderVillagerId;
   }

   public long plannedStartGameTime() {
      return this.plannedStartGameTime;
   }

   public void setPlannedStartGameTime(long t) {
      this.plannedStartGameTime = t;
   }

   public long gatherDeadlineGameTime() {
      return this.gatherDeadlineGameTime;
   }

   public void setGatherDeadlineGameTime(long t) {
      this.gatherDeadlineGameTime = t;
   }

   public long activeEndGameTime() {
      return this.activeEndGameTime;
   }

   public void setActiveEndGameTime(long t) {
      this.activeEndGameTime = t;
   }

   public Set<UUID> creditedParticipants() {
      return this.creditedParticipants;
   }

   public Set<UUID> arrivedParticipants() {
      return this.arrivedParticipants;
   }

   public Map<String, Integer> consumedSupplies() {
      return this.consumedSupplies;
   }

   public boolean suppliesConsumed() {
      return this.suppliesConsumed;
   }

   public void setSuppliesConsumed(boolean v) {
      this.suppliesConsumed = v;
   }

   public boolean outcomeApplied() {
      return this.outcomeApplied;
   }

   public void setOutcomeApplied(boolean v) {
      this.outcomeApplied = v;
   }

   @Nullable
   public String interruptReason() {
      return this.interruptReason;
   }

   public void setInterruptReason(@Nullable String reason) {
      this.interruptReason = reason;
   }

   @Nullable
   public String festivalLink() {
      return this.festivalLink;
   }

   public void setFestivalLink(@Nullable String festivalLink) {
      this.festivalLink = festivalLink;
   }

   public long createdGameTime() {
      return this.createdGameTime;
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putInt("schema", this.schemaVersion);
      tag.putUUID("id", this.ceremonyId);
      tag.putUUID("village", this.villageId);
      tag.putString("ritual", this.ritualId.toString());
      tag.putString("tradition", this.traditionId.toString());
      tag.putString("state", this.state.id());
      if (this.buildingId != null) {
         tag.putUUID("building", this.buildingId.uuid());
      }
      if (this.leaderVillagerId != null) {
         tag.putUUID("leader", this.leaderVillagerId);
      }
      tag.putLong("plannedStart", this.plannedStartGameTime);
      tag.putLong("gatherDeadline", this.gatherDeadlineGameTime);
      tag.putLong("activeEnd", this.activeEndGameTime);
      tag.putBoolean("suppliesConsumed", this.suppliesConsumed);
      tag.putBoolean("outcomeApplied", this.outcomeApplied);
      if (this.interruptReason != null) {
         tag.putString("interrupt", this.interruptReason);
      }
      if (this.festivalLink != null) {
         tag.putString("festival", this.festivalLink);
      }
      tag.putLong("created", this.createdGameTime);
      ListTag arrived = new ListTag();
      for (UUID u : this.arrivedParticipants) {
         arrived.add(StringTag.valueOf(u.toString()));
      }
      tag.put("arrived", arrived);
      ListTag credited = new ListTag();
      for (UUID u : this.creditedParticipants) {
         credited.add(StringTag.valueOf(u.toString()));
      }
      tag.put("credited", credited);
      CompoundTag supplies = new CompoundTag();
      for (Map.Entry<String, Integer> e : this.consumedSupplies.entrySet()) {
         supplies.putInt(e.getKey(), e.getValue());
      }
      tag.put("consumed", supplies);
      return tag;
   }

   public static CeremonyInstance load(CompoundTag tag) {
      UUID id = tag.getUUID("id");
      UUID village = tag.getUUID("village");
      ResourceLocation ritual = ResourceLocation.parse(tag.getString("ritual"));
      ResourceLocation tradition = ResourceLocation.parse(tag.getString("tradition"));
      CeremonyInstance c = new CeremonyInstance(id, village, ritual, tradition, tag.getLong("created"));
      c.schemaVersion = tag.contains("schema") ? tag.getInt("schema") : 1;
      CeremonyState st = CeremonyState.fromId(tag.getString("state"));
      c.state = st != null ? st : CeremonyState.CANCELLED;
      if (tag.hasUUID("building")) {
         c.buildingId = new BuildingId(tag.getUUID("building"));
      }
      if (tag.hasUUID("leader")) {
         c.leaderVillagerId = tag.getUUID("leader");
      }
      c.plannedStartGameTime = tag.getLong("plannedStart");
      c.gatherDeadlineGameTime = tag.getLong("gatherDeadline");
      c.activeEndGameTime = tag.getLong("activeEnd");
      c.suppliesConsumed = tag.getBoolean("suppliesConsumed");
      c.outcomeApplied = tag.getBoolean("outcomeApplied");
      if (tag.contains("interrupt")) {
         c.interruptReason = tag.getString("interrupt");
      }
      if (tag.contains("festival")) {
         c.festivalLink = tag.getString("festival");
      }
      ListTag arrived = tag.getList("arrived", 8);
      for (int i = 0; i < arrived.size(); i++) {
         try {
            c.arrivedParticipants.add(UUID.fromString(arrived.getString(i)));
         } catch (Exception ignored) {
         }
      }
      ListTag credited = tag.getList("credited", 8);
      for (int i = 0; i < credited.size(); i++) {
         try {
            c.creditedParticipants.add(UUID.fromString(credited.getString(i)));
         } catch (Exception ignored) {
         }
      }
      CompoundTag supplies = tag.getCompound("consumed");
      for (String key : supplies.getAllKeys()) {
         c.consumedSupplies.put(key, supplies.getInt(key));
      }
      return c;
   }
}
