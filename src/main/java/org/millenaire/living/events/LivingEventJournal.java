package org.millenaire.living.events;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Bounded per-village event history with dedupe-key tracking.
 */
public final class LivingEventJournal {
   private final UUID villageId;
   private final ArrayDeque<LivingEvent> events = new ArrayDeque<>();
   private final Map<String, Long> recentDedupeKeys = new HashMap<>();
   private int maxEvents;

   public LivingEventJournal(UUID villageId, int maxEvents) {
      this.villageId = villageId;
      this.maxEvents = Math.max(16, maxEvents);
   }

   public UUID villageId() {
      return this.villageId;
   }

   public void setMaxEvents(int maxEvents) {
      this.maxEvents = Math.max(16, maxEvents);
      this.trim();
   }

   public boolean hasDedupe(String key, long now, long ttlTicks) {
      if (key == null || key.isBlank()) {
         return false;
      }
      Long seen = this.recentDedupeKeys.get(key);
      if (seen == null) {
         return false;
      }
      if (now - seen > ttlTicks) {
         this.recentDedupeKeys.remove(key);
         return false;
      }
      return true;
   }

   public boolean tryAdd(LivingEvent event, long dedupeTtlTicks) {
      if (event == null) {
         return false;
      }
      String key = event.dedupeKey();
      if (!key.isBlank() && this.hasDedupe(key, event.gameTime(), dedupeTtlTicks)) {
         return false;
      }
      this.events.addLast(event);
      if (!key.isBlank()) {
         this.recentDedupeKeys.put(key, event.gameTime());
      }
      this.trim();
      return true;
   }

   public List<LivingEvent> recent(int limit) {
      if (this.events.isEmpty() || limit <= 0) {
         return List.of();
      }
      List<LivingEvent> all = new ArrayList<>(this.events);
      int from = Math.max(0, all.size() - limit);
      return Collections.unmodifiableList(all.subList(from, all.size()));
   }

   public List<LivingEvent> all() {
      return List.copyOf(this.events);
   }

   public void pruneOlderThan(long minGameTime) {
      while (!this.events.isEmpty() && this.events.peekFirst().gameTime() < minGameTime) {
         this.events.removeFirst();
      }
      this.recentDedupeKeys.entrySet().removeIf(e -> e.getValue() < minGameTime);
   }

   private void trim() {
      while (this.events.size() > this.maxEvents) {
         this.events.removeFirst();
      }
      if (this.recentDedupeKeys.size() > this.maxEvents * 2) {
         List<Map.Entry<String, Long>> entries = new ArrayList<>(this.recentDedupeKeys.entrySet());
         entries.sort(Map.Entry.comparingByValue());
         int remove = this.recentDedupeKeys.size() - this.maxEvents;
         for (int i = 0; i < remove; i++) {
            this.recentDedupeKeys.remove(entries.get(i).getKey());
         }
      }
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("village", this.villageId);
      tag.putInt("max", this.maxEvents);
      ListTag list = new ListTag();
      for (LivingEvent event : this.events) {
         list.add(event.save());
      }
      tag.put("events", list);
      CompoundTag dedupe = new CompoundTag();
      for (Map.Entry<String, Long> e : this.recentDedupeKeys.entrySet()) {
         dedupe.putLong(e.getKey(), e.getValue());
      }
      tag.put("dedupe", dedupe);
      return tag;
   }

   public static LivingEventJournal load(CompoundTag tag, int defaultMax) {
      UUID village = tag.getUUID("village");
      int max = tag.contains("max") ? tag.getInt("max") : defaultMax;
      LivingEventJournal journal = new LivingEventJournal(village, max);
      ListTag list = tag.getList("events", 10);
      for (int i = 0; i < list.size(); i++) {
         LivingEvent event = LivingEvent.load(list.getCompound(i));
         if (event != null) {
            journal.events.addLast(event);
         }
      }
      CompoundTag dedupe = tag.getCompound("dedupe");
      for (String key : dedupe.getAllKeys()) {
         journal.recentDedupeKeys.put(key, dedupe.getLong(key));
      }
      journal.trim();
      return journal;
   }
}
