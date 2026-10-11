package org.millenaire.religion.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.building.BuildingId;

public final class VillageFaithCommunity {
   private final UUID villageId;
   private final LinkedHashSet<ResourceLocation> traditionIds = new LinkedHashSet<>();
   @Nullable
   private ResourceLocation hostTraditionId;
   private ReligionPolicy policy = ReligionPolicy.OPEN;
   private final Set<BuildingId> designatedShrines = new LinkedHashSet<>();
   private final Set<BuildingId> consecratedBuildings = new LinkedHashSet<>();
   @Nullable
   private UUID activeCeremonyId;
   private long lastCeremonyDay = -1L;
   private long lastMemorialDay = -1L;
   private long lastCatchupDay = -1L;
   private final List<String> history = new ArrayList<>();
   private static final int MAX_HISTORY = 32;

   public VillageFaithCommunity(UUID villageId) {
      this.villageId = villageId;
   }

   public UUID villageId() {
      return this.villageId;
   }

   public Set<ResourceLocation> traditionIds() {
      return this.traditionIds;
   }

   public void addTradition(ResourceLocation id) {
      if (id != null) {
         this.traditionIds.add(id);
         if (this.hostTraditionId == null) {
            this.hostTraditionId = id;
         }
      }
   }

   @Nullable
   public ResourceLocation hostTraditionId() {
      return this.hostTraditionId;
   }

   public void setHostTraditionId(@Nullable ResourceLocation hostTraditionId) {
      this.hostTraditionId = hostTraditionId;
   }

   public ReligionPolicy policy() {
      return this.policy;
   }

   public void setPolicy(ReligionPolicy policy) {
      this.policy = policy == null ? ReligionPolicy.OPEN : policy;
   }

   public Set<BuildingId> designatedShrines() {
      return this.designatedShrines;
   }

   public Set<BuildingId> consecratedBuildings() {
      return this.consecratedBuildings;
   }

   @Nullable
   public UUID activeCeremonyId() {
      return this.activeCeremonyId;
   }

   public void setActiveCeremonyId(@Nullable UUID activeCeremonyId) {
      this.activeCeremonyId = activeCeremonyId;
   }

   public long lastCeremonyDay() {
      return this.lastCeremonyDay;
   }

   public void setLastCeremonyDay(long day) {
      this.lastCeremonyDay = day;
   }

   public long lastMemorialDay() {
      return this.lastMemorialDay;
   }

   public void setLastMemorialDay(long day) {
      this.lastMemorialDay = day;
   }

   public long lastCatchupDay() {
      return this.lastCatchupDay;
   }

   public void setLastCatchupDay(long day) {
      this.lastCatchupDay = day;
   }

   public void addHistory(String entry) {
      if (entry == null || entry.isEmpty()) {
         return;
      }
      this.history.add(0, entry);
      while (this.history.size() > MAX_HISTORY) {
         this.history.remove(this.history.size() - 1);
      }
   }

   public List<String> history() {
      return List.copyOf(this.history);
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("village", this.villageId);
      ListTag traditions = new ListTag();
      for (ResourceLocation id : this.traditionIds) {
         traditions.add(StringTag.valueOf(id.toString()));
      }
      tag.put("traditions", traditions);
      if (this.hostTraditionId != null) {
         tag.putString("host", this.hostTraditionId.toString());
      }
      tag.putString("policy", this.policy.id());
      ListTag shrines = new ListTag();
      for (BuildingId b : this.designatedShrines) {
         CompoundTag bt = new CompoundTag();
         bt.putUUID("id", b.uuid());
         shrines.add(bt);
      }
      tag.put("shrines", shrines);
      ListTag consecrated = new ListTag();
      for (BuildingId b : this.consecratedBuildings) {
         CompoundTag bt = new CompoundTag();
         bt.putUUID("id", b.uuid());
         consecrated.add(bt);
      }
      tag.put("consecrated", consecrated);
      if (this.activeCeremonyId != null) {
         tag.putUUID("activeCeremony", this.activeCeremonyId);
      }
      tag.putLong("lastCeremonyDay", this.lastCeremonyDay);
      tag.putLong("lastMemorialDay", this.lastMemorialDay);
      tag.putLong("lastCatchupDay", this.lastCatchupDay);
      ListTag hist = new ListTag();
      for (String h : this.history) {
         CompoundTag e = new CompoundTag();
         e.putString("t", h);
         hist.add(e);
      }
      tag.put("history", hist);
      return tag;
   }

   public static VillageFaithCommunity load(CompoundTag tag) {
      VillageFaithCommunity c = new VillageFaithCommunity(tag.getUUID("village"));
      ListTag traditions = tag.getList("traditions", 8);
      for (int i = 0; i < traditions.size(); i++) {
         try {
            c.traditionIds.add(ResourceLocation.parse(traditions.getString(i)));
         } catch (Exception ignored) {
         }
      }
      if (tag.contains("host")) {
         try {
            c.hostTraditionId = ResourceLocation.parse(tag.getString("host"));
         } catch (Exception ignored) {
         }
      }
      c.policy = ReligionPolicy.fromId(tag.getString("policy"));
      ListTag shrines = tag.getList("shrines", 10);
      for (int i = 0; i < shrines.size(); i++) {
         try {
            CompoundTag bt = shrines.getCompound(i);
            if (bt.hasUUID("id")) {
               c.designatedShrines.add(new BuildingId(bt.getUUID("id")));
            }
         } catch (Exception ignored) {
         }
      }
      ListTag consecrated = tag.getList("consecrated", 10);
      for (int i = 0; i < consecrated.size(); i++) {
         try {
            CompoundTag bt = consecrated.getCompound(i);
            if (bt.hasUUID("id")) {
               c.consecratedBuildings.add(new BuildingId(bt.getUUID("id")));
            }
         } catch (Exception ignored) {
         }
      }
      if (tag.hasUUID("activeCeremony")) {
         c.activeCeremonyId = tag.getUUID("activeCeremony");
      }
      c.lastCeremonyDay = tag.getLong("lastCeremonyDay");
      c.lastMemorialDay = tag.getLong("lastMemorialDay");
      c.lastCatchupDay = tag.getLong("lastCatchupDay");
      ListTag hist = tag.getList("history", 10);
      for (int i = 0; i < hist.size(); i++) {
         String t = hist.getCompound(i).getString("t");
         if (!t.isEmpty()) {
            c.history.add(t);
         }
      }
      return c;
   }
}
