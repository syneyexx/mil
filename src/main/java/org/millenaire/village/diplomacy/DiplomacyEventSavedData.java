package org.millenaire.village.diplomacy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class DiplomacyEventSavedData extends SavedData {
   public static final String DATA_NAME = "millenaire_diplomacy_events";
   private final List<DiplomacyEvent> events = new ArrayList<>();
   private final Map<UUID, Long> lastEventDayByPlayer = new HashMap<>();

   private DiplomacyEventSavedData() {
   }

   public static Factory<DiplomacyEventSavedData> factory() {
      return new Factory<>(DiplomacyEventSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static DiplomacyEventSavedData get(ServerLevel level) {
      return level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public void add(DiplomacyEvent event) {
      this.events.add(event);
      this.lastEventDayByPlayer.put(event.playerId(), event.createdDay());
      this.setDirty();
   }

   @Nullable
   public DiplomacyEvent find(UUID eventId) {
      for (DiplomacyEvent e : this.events) {
         if (e.eventId().equals(eventId)) {
            return e;
         }
      }
      return null;
   }

   public void replace(DiplomacyEvent updated) {
      for (int i = 0; i < this.events.size(); i++) {
         if (this.events.get(i).eventId().equals(updated.eventId())) {
            this.events.set(i, updated);
            this.setDirty();
            return;
         }
      }
   }

   public List<DiplomacyEvent> pendingForPlayer(UUID playerId) {
      List<DiplomacyEvent> result = new ArrayList<>();
      for (DiplomacyEvent e : this.events) {
         if (e.pending() && e.playerId().equals(playerId)) {
            result.add(e);
         }
      }
      return result;
   }

   public long lastEventDay(UUID playerId) {
      return this.lastEventDayByPlayer.getOrDefault(playerId, -1L);
   }

   public void expireBefore(long day) {
      boolean changed = false;
      Iterator<DiplomacyEvent> it = this.events.iterator();
      while (it.hasNext()) {
         DiplomacyEvent e = it.next();
         if (e.pending() && e.expiryDay() < day) {
            it.remove();
            changed = true;
         } else if (!e.pending() && e.createdDay() < day - 30L) {
            it.remove();
            changed = true;
         }
      }
      if (changed) {
         this.setDirty();
      }
   }

   public CompoundTag save(CompoundTag tag, Provider registries) {
      ListTag list = new ListTag();
      for (DiplomacyEvent e : this.events) {
         list.add(e.save());
      }
      tag.put("events", list);
      CompoundTag last = new CompoundTag();
      for (Map.Entry<UUID, Long> e : this.lastEventDayByPlayer.entrySet()) {
         last.putLong(e.getKey().toString(), e.getValue());
      }
      tag.put("lastByPlayer", last);
      return tag;
   }

   private static DiplomacyEventSavedData load(CompoundTag tag) {
      DiplomacyEventSavedData data = new DiplomacyEventSavedData();
      if (tag.contains("events", Tag.TAG_LIST)) {
         ListTag list = tag.getList("events", Tag.TAG_COMPOUND);
         for (int i = 0; i < list.size(); i++) {
            data.events.add(DiplomacyEvent.load(list.getCompound(i)));
         }
      }
      if (tag.contains("lastByPlayer", Tag.TAG_COMPOUND)) {
         CompoundTag last = tag.getCompound("lastByPlayer");
         for (String key : last.getAllKeys()) {
            try {
               data.lastEventDayByPlayer.put(UUID.fromString(key), last.getLong(key));
            } catch (IllegalArgumentException ignored) {
            }
         }
      }
      return data;
   }
}
