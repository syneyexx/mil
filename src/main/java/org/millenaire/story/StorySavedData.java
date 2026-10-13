package org.millenaire.story;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class StorySavedData extends SavedData {
   public static final int SCHEMA_VERSION = 1;
   private static final String DATA_NAME = "millenaire_stories";
   private static final int HISTORY_CAP = 64;

   private final List<StorylineInstance> active = new ArrayList<>();
   private final List<StorylineInstance> history = new ArrayList<>();
   private final Map<UUID, Long> lastStoryByVillage = new HashMap<>();

   public static StorySavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         overworld = level;
      }
      return overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   private static Factory<StorySavedData> factory() {
      return new Factory<>(StorySavedData::new, StorySavedData::load, null);
   }

   public int activeCount() {
      int n = 0;
      for (StorylineInstance s : this.active) {
         if (!s.finished()) {
            n++;
         }
      }
      return n;
   }

   public int countForVillage(UUID villageId) {
      int n = 0;
      for (StorylineInstance s : this.active) {
         if (!s.finished() && s.villageId().equals(villageId)) {
            n++;
         }
      }
      return n;
   }

   public long lastStoryGameTime(UUID villageId) {
      return this.lastStoryByVillage.getOrDefault(villageId, -1L);
   }

   public boolean recentlyTold(UUID villageId, String kind, long gameTime) {
      for (StorylineInstance s : this.history) {
         if (s.villageId().equals(villageId) && s.kind().equals(kind) && gameTime - s.startedGameTime() < 72000L) {
            return true;
         }
      }
      for (StorylineInstance s : this.active) {
         if (s.villageId().equals(villageId) && s.kind().equals(kind)) {
            return true;
         }
      }
      return false;
   }

   public void add(StorylineInstance inst) {
      this.active.add(inst);
      this.lastStoryByVillage.put(inst.villageId(), inst.startedGameTime());
      this.setDirty();
   }

   public void pruneFinished(long gameTime) {
      Iterator<StorylineInstance> it = this.active.iterator();
      while (it.hasNext()) {
         StorylineInstance s = it.next();
         if (s.finished() || gameTime >= s.expiresGameTime()) {
            if (!s.finished()) {
               s.finish("expired");
            }
            this.history.add(s);
            it.remove();
            this.setDirty();
         }
      }
      while (this.history.size() > HISTORY_CAP) {
         this.history.removeFirst();
         this.setDirty();
      }
   }

   public List<StorylineInstance> activeFor(UUID villageId) {
      List<StorylineInstance> out = new ArrayList<>();
      for (StorylineInstance s : this.active) {
         if (!s.finished() && s.villageId().equals(villageId)) {
            out.add(s);
         }
      }
      return out;
   }

   public static StorySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
      StorySavedData data = new StorySavedData();
      if (tag.contains("active", Tag.TAG_LIST)) {
         ListTag list = tag.getList("active", Tag.TAG_COMPOUND);
         for (int i = 0; i < list.size(); i++) {
            data.active.add(StorylineInstance.load(list.getCompound(i), registries));
         }
      }
      if (tag.contains("history", Tag.TAG_LIST)) {
         ListTag list = tag.getList("history", Tag.TAG_COMPOUND);
         for (int i = 0; i < list.size(); i++) {
            data.history.add(StorylineInstance.load(list.getCompound(i), registries));
         }
      }
      if (tag.contains("last", Tag.TAG_LIST)) {
         ListTag list = tag.getList("last", Tag.TAG_COMPOUND);
         for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            data.lastStoryByVillage.put(e.getUUID("v"), e.getLong("t"));
         }
      }
      return data;
   }

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
      tag.putInt("version", SCHEMA_VERSION);
      ListTag activeTag = new ListTag();
      for (StorylineInstance s : this.active) {
         activeTag.add(s.save(registries));
      }
      tag.put("active", activeTag);
      ListTag histTag = new ListTag();
      for (StorylineInstance s : this.history) {
         histTag.add(s.save(registries));
      }
      tag.put("history", histTag);
      ListTag last = new ListTag();
      for (var e : this.lastStoryByVillage.entrySet()) {
         CompoundTag c = new CompoundTag();
         c.putUUID("v", e.getKey());
         c.putLong("t", e.getValue());
         last.add(c);
      }
      tag.put("last", last);
      return tag;
   }
}
