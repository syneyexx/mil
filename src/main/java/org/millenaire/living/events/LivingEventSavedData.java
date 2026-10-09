package org.millenaire.living.events;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.slf4j.Logger;

public final class LivingEventSavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String DATA_NAME = "millenaire_living_events";
   public static final int CURRENT_VERSION = 1;

   private final Map<UUID, LivingEventJournal> journals = new HashMap<>();
   private int defaultMaxEvents = 128;

   private LivingEventSavedData() {
   }

   public static Factory<LivingEventSavedData> factory() {
      return new Factory<>(LivingEventSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static LivingEventSavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      ServerLevel storage = overworld != null ? overworld : level;
      return storage.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public void setDefaultMaxEvents(int max) {
      this.defaultMaxEvents = Math.max(16, max);
      for (LivingEventJournal journal : this.journals.values()) {
         journal.setMaxEvents(this.defaultMaxEvents);
      }
      this.setDirty();
   }

   public LivingEventJournal journalFor(UUID villageId) {
      return this.journals.computeIfAbsent(villageId, id -> new LivingEventJournal(id, this.defaultMaxEvents));
   }

   @Nullable
   public LivingEventJournal getJournal(UUID villageId) {
      return this.journals.get(villageId);
   }

   public Map<UUID, LivingEventJournal> journals() {
      return this.journals;
   }

   public void removeVillage(UUID villageId) {
      if (this.journals.remove(villageId) != null) {
         this.setDirty();
      }
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      tag.putInt("version", CURRENT_VERSION);
      tag.putInt("defaultMax", this.defaultMaxEvents);
      ListTag list = new ListTag();
      for (LivingEventJournal journal : this.journals.values()) {
         list.add(journal.save());
      }
      tag.put("journals", list);
      return tag;
   }

   private static LivingEventSavedData load(CompoundTag tag) {
      LivingEventSavedData data = new LivingEventSavedData();
      int version = tag.contains("version") ? tag.getInt("version") : 0;
      if (version > CURRENT_VERSION) {
         LOGGER.warn("[Millenaire] LivingEventSavedData version {} newer than supported {}", version, CURRENT_VERSION);
      }
      data.defaultMaxEvents = tag.contains("defaultMax") ? Math.max(16, tag.getInt("defaultMax")) : 128;
      ListTag list = tag.getList("journals", 10);
      for (int i = 0; i < list.size(); i++) {
         try {
            LivingEventJournal journal = LivingEventJournal.load(list.getCompound(i), data.defaultMaxEvents);
            data.journals.put(journal.villageId(), journal);
         } catch (Exception e) {
            LOGGER.warn("[Millenaire] Skipping corrupt living event journal entry {}", i);
         }
      }
      return data;
   }
}
