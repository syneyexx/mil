package org.millenaire.living.requests;

import com.mojang.logging.LogUtils;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.slf4j.Logger;

public final class VillageRequestSavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String DATA_NAME = "millenaire_living_requests";
   public static final int CURRENT_VERSION = 1;

   private final Map<String, VillageRequest> requests = new HashMap<>();

   private VillageRequestSavedData() {
   }

   public static Factory<VillageRequestSavedData> factory() {
      return new Factory<>(VillageRequestSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static VillageRequestSavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      ServerLevel storage = overworld != null ? overworld : level;
      return storage.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public void put(VillageRequest request) {
      this.requests.put(request.requestId(), request);
      this.setDirty();
   }

   @Nullable
   public VillageRequest get(String id) {
      return this.requests.get(id);
   }

   public Map<String, VillageRequest> all() {
      return this.requests;
   }

   public List<VillageRequest> forPlayer(UUID playerId) {
      List<VillageRequest> out = new ArrayList<>();
      for (VillageRequest r : this.requests.values()) {
         if (r.playerId().equals(playerId)) {
            out.add(r);
         }
      }
      return out;
   }

   public List<VillageRequest> forVillage(UUID villageId) {
      List<VillageRequest> out = new ArrayList<>();
      for (VillageRequest r : this.requests.values()) {
         if (r.villageId().equals(villageId)) {
            out.add(r);
         }
      }
      return out;
   }

   public List<VillageRequest> activeForNeed(UUID villageId, @Nullable String linkedNeedOrProjectId) {
      List<VillageRequest> out = new ArrayList<>();
      if (linkedNeedOrProjectId == null) {
         return out;
      }
      for (VillageRequest r : this.requests.values()) {
         if (r.villageId().equals(villageId)
            && linkedNeedOrProjectId.equals(r.linkedNeedOrProjectId())
            && VillageRequestState.activeStates().contains(r.state())) {
            out.add(r);
         }
      }
      return out;
   }

   public void remove(String id) {
      if (this.requests.remove(id) != null) {
         this.setDirty();
      }
   }

   public void pruneTerminalOlderThan(long minGameTime, int maxTotal) {
      Iterator<Map.Entry<String, VillageRequest>> it = this.requests.entrySet().iterator();
      while (it.hasNext()) {
         VillageRequest r = it.next().getValue();
         if (r.state().isTerminal() && r.lastUpdateGameTime() < minGameTime) {
            it.remove();
         }
      }
      if (this.requests.size() > maxTotal) {
         List<VillageRequest> terminal = new ArrayList<>();
         for (VillageRequest r : this.requests.values()) {
            if (r.state().isTerminal()) {
               terminal.add(r);
            }
         }
         terminal.sort((a, b) -> Long.compare(a.lastUpdateGameTime(), b.lastUpdateGameTime()));
         int remove = this.requests.size() - maxTotal;
         for (int i = 0; i < remove && i < terminal.size(); i++) {
            this.requests.remove(terminal.get(i).requestId());
         }
      }
      this.setDirty();
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      tag.putInt("version", CURRENT_VERSION);
      ListTag list = new ListTag();
      for (VillageRequest request : this.requests.values()) {
         list.add(request.save());
      }
      tag.put("requests", list);
      return tag;
   }

   private static VillageRequestSavedData load(CompoundTag tag) {
      VillageRequestSavedData data = new VillageRequestSavedData();
      int version = tag.contains("version") ? tag.getInt("version") : 0;
      if (version > CURRENT_VERSION) {
         LOGGER.warn("[Millenaire] VillageRequestSavedData version {} newer than supported {}", version, CURRENT_VERSION);
      }
      ListTag list = tag.getList("requests", 10);
      for (int i = 0; i < list.size(); i++) {
         VillageRequest request = VillageRequest.load(list.getCompound(i));
         if (request != null) {
            data.requests.put(request.requestId(), request);
         } else {
            LOGGER.warn("[Millenaire] Skipping corrupt village request {}", i);
         }
      }
      return data;
   }
}
