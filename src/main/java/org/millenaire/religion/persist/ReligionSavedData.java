package org.millenaire.religion.persist;

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
import org.millenaire.religion.model.CeremonyInstance;
import org.millenaire.religion.model.NpcFaithProfile;
import org.millenaire.religion.model.VillageFaithCommunity;
import org.slf4j.Logger;

public final class ReligionSavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String DATA_NAME = "millenaire_religion";
   public static final int CURRENT_VERSION = 1;

   private final Map<UUID, VillageFaithCommunity> communities = new HashMap<>();
   private final Map<UUID, NpcFaithProfile> profiles = new HashMap<>();
   private final Map<UUID, CeremonyInstance> ceremonies = new HashMap<>();

   private ReligionSavedData() {
   }

   public static Factory<ReligionSavedData> factory() {
      return new Factory<>(ReligionSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static ReligionSavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      ServerLevel storage = overworld != null ? overworld : level;
      return storage.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public VillageFaithCommunity communityFor(UUID villageId) {
      return this.communities.computeIfAbsent(villageId, VillageFaithCommunity::new);
   }

   @Nullable
   public VillageFaithCommunity getCommunity(UUID villageId) {
      return this.communities.get(villageId);
   }

   public NpcFaithProfile profileFor(UUID villagerId) {
      return this.profiles.computeIfAbsent(villagerId, NpcFaithProfile::new);
   }

   @Nullable
   public NpcFaithProfile getProfile(UUID villagerId) {
      return this.profiles.get(villagerId);
   }

   public Map<UUID, NpcFaithProfile> profiles() {
      return this.profiles;
   }

   public Map<UUID, CeremonyInstance> ceremonies() {
      return this.ceremonies;
   }

   public void putCeremony(CeremonyInstance ceremony) {
      if (ceremony != null) {
         this.ceremonies.put(ceremony.ceremonyId(), ceremony);
         this.setDirty();
      }
   }

   @Nullable
   public CeremonyInstance getCeremony(UUID id) {
      return id == null ? null : this.ceremonies.get(id);
   }

   public void removeVillage(UUID villageId) {
      if (this.communities.remove(villageId) != null) {
         this.setDirty();
      }
      Iterator<Map.Entry<UUID, CeremonyInstance>> it = this.ceremonies.entrySet().iterator();
      while (it.hasNext()) {
         if (it.next().getValue().villageId().equals(villageId)) {
            it.remove();
            this.setDirty();
         }
      }
   }

   public void pruneCeremonies(int maxTracked, long now) {
      if (this.ceremonies.size() <= maxTracked) {
         return;
      }
      // Drop oldest terminal ceremonies first.
      this.ceremonies.entrySet().stream()
         .filter(e -> e.getValue().state().isTerminal())
         .sorted((a, b) -> Long.compare(a.getValue().createdGameTime(), b.getValue().createdGameTime()))
         .limit(Math.max(0, this.ceremonies.size() - maxTracked))
         .map(Map.Entry::getKey)
         .toList()
         .forEach(id -> {
            this.ceremonies.remove(id);
            this.setDirty();
         });
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      tag.putInt("version", CURRENT_VERSION);
      ListTag communitiesTag = new ListTag();
      for (VillageFaithCommunity c : this.communities.values()) {
         communitiesTag.add(c.save());
      }
      tag.put("communities", communitiesTag);
      ListTag profilesTag = new ListTag();
      for (NpcFaithProfile p : this.profiles.values()) {
         profilesTag.add(p.save());
      }
      tag.put("profiles", profilesTag);
      ListTag ceremoniesTag = new ListTag();
      for (CeremonyInstance c : this.ceremonies.values()) {
         ceremoniesTag.add(c.save());
      }
      tag.put("ceremonies", ceremoniesTag);
      return tag;
   }

   private static ReligionSavedData load(CompoundTag tag) {
      ReligionSavedData data = new ReligionSavedData();
      int version = tag.contains("version") ? tag.getInt("version") : 0;
      if (version > CURRENT_VERSION) {
         LOGGER.warn("[Religion] SavedData version {} newer than supported {}", version, CURRENT_VERSION);
      }
      ListTag communitiesTag = tag.getList("communities", 10);
      for (int i = 0; i < communitiesTag.size(); i++) {
         try {
            VillageFaithCommunity c = VillageFaithCommunity.load(communitiesTag.getCompound(i));
            data.communities.put(c.villageId(), c);
         } catch (Exception e) {
            LOGGER.warn("[Religion] Skipping corrupt community entry {}", i);
         }
      }
      ListTag profilesTag = tag.getList("profiles", 10);
      for (int i = 0; i < profilesTag.size(); i++) {
         try {
            NpcFaithProfile p = NpcFaithProfile.load(profilesTag.getCompound(i));
            data.profiles.put(p.villagerId(), p);
         } catch (Exception e) {
            LOGGER.warn("[Religion] Skipping corrupt faith profile entry {}", i);
         }
      }
      ListTag ceremoniesTag = tag.getList("ceremonies", 10);
      for (int i = 0; i < ceremoniesTag.size(); i++) {
         try {
            CeremonyInstance c = CeremonyInstance.load(ceremoniesTag.getCompound(i));
            data.ceremonies.put(c.ceremonyId(), c);
         } catch (Exception e) {
            LOGGER.warn("[Religion] Skipping corrupt ceremony entry {}", i);
         }
      }
      return data;
   }
}
