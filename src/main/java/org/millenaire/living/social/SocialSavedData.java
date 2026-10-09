package org.millenaire.living.social;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Iterator;
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

public final class SocialSavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String DATA_NAME = "millenaire_living_social";
   public static final int CURRENT_VERSION = 1;

   private final Map<UUID, NpcSocialProfile> profiles = new HashMap<>();

   private SocialSavedData() {
   }

   public static Factory<SocialSavedData> factory() {
      return new Factory<>(SocialSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static SocialSavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      ServerLevel storage = overworld != null ? overworld : level;
      return storage.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public NpcSocialProfile getOrCreate(UUID npcId) {
      return this.profiles.computeIfAbsent(npcId, NpcSocialProfile::new);
   }

   @Nullable
   public NpcSocialProfile get(UUID npcId) {
      return this.profiles.get(npcId);
   }

   public Map<UUID, NpcSocialProfile> profiles() {
      return this.profiles;
   }

   public void remove(UUID npcId) {
      if (this.profiles.remove(npcId) != null) {
         this.setDirty();
      }
   }

   public void pruneOrphans(java.util.Set<UUID> livingNpcIds, int maxProfiles) {
      Iterator<Map.Entry<UUID, NpcSocialProfile>> it = this.profiles.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<UUID, NpcSocialProfile> e = it.next();
         if (!livingNpcIds.contains(e.getKey()) && e.getValue().relationships().isEmpty() && e.getValue().knowledge().isEmpty()) {
            it.remove();
         }
      }
      if (this.profiles.size() > maxProfiles) {
         // Drop least recently socialized non-family-heavy profiles.
         java.util.List<Map.Entry<UUID, NpcSocialProfile>> list = new java.util.ArrayList<>(this.profiles.entrySet());
         list.sort(java.util.Comparator.comparingLong(a -> a.getValue().lastSocialInteractionGameTime()));
         int remove = this.profiles.size() - maxProfiles;
         for (int i = 0; i < remove && i < list.size(); i++) {
            this.profiles.remove(list.get(i).getKey());
         }
      }
      this.setDirty();
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      tag.putInt("version", CURRENT_VERSION);
      ListTag list = new ListTag();
      for (NpcSocialProfile profile : this.profiles.values()) {
         list.add(profile.save());
      }
      tag.put("profiles", list);
      return tag;
   }

   private static SocialSavedData load(CompoundTag tag) {
      SocialSavedData data = new SocialSavedData();
      int version = tag.contains("version") ? tag.getInt("version") : 0;
      if (version > CURRENT_VERSION) {
         LOGGER.warn("[Millenaire] SocialSavedData version {} newer than supported {}", version, CURRENT_VERSION);
      }
      ListTag list = tag.getList("profiles", 10);
      for (int i = 0; i < list.size(); i++) {
         try {
            NpcSocialProfile profile = NpcSocialProfile.load(list.getCompound(i));
            data.profiles.put(profile.npcId(), profile);
         } catch (Exception e) {
            LOGGER.warn("[Millenaire] Skipping corrupt social profile {}", i);
         }
      }
      return data;
   }
}
