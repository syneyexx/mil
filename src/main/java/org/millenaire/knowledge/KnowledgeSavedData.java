package org.millenaire.knowledge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class KnowledgeSavedData extends SavedData {
   public static final int SCHEMA_VERSION = 1;
   private static final String DATA_NAME = "millenaire_knowledge";

   private final Map<UUID, Map<String, Float>> progressByVillage = new HashMap<>();
   private final Map<UUID, Set<String>> unlockedLocal = new HashMap<>();
   private final Set<String> unlockedRegional = new HashSet<>();

   public static KnowledgeSavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         overworld = level;
      }
      return overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   private static Factory<KnowledgeSavedData> factory() {
      return new Factory<>(KnowledgeSavedData::new, KnowledgeSavedData::load, null);
   }

   public float addProgress(UUID villageId, String nodeId, float delta, float cap) {
      Map<String, Float> map = this.progressByVillage.computeIfAbsent(villageId, id -> new HashMap<>());
      float next = Math.min(cap, map.getOrDefault(nodeId, 0f) + Math.max(0f, delta));
      map.put(nodeId, next);
      return next;
   }

   public void decayProgress(UUID villageId, String nodeId, float amount) {
      Map<String, Float> map = this.progressByVillage.get(villageId);
      if (map == null) {
         return;
      }
      float cur = map.getOrDefault(nodeId, 0f);
      map.put(nodeId, Math.max(0f, cur - amount));
   }

   public float getProgress(UUID villageId, String nodeId) {
      Map<String, Float> map = this.progressByVillage.get(villageId);
      return map == null ? 0f : map.getOrDefault(nodeId, 0f);
   }

   public void unlock(UUID villageId, String nodeId, KnowledgeNode.KnowledgeScope scope) {
      if (scope == KnowledgeNode.KnowledgeScope.REGIONAL) {
         this.unlockedRegional.add(nodeId);
      } else {
         this.unlockedLocal.computeIfAbsent(villageId, id -> new HashSet<>()).add(nodeId);
      }
   }

   public boolean isUnlocked(UUID villageId, String nodeId) {
      return this.unlockedRegional.contains(nodeId)
         || this.unlockedLocal.getOrDefault(villageId, Set.of()).contains(nodeId);
   }

   public static KnowledgeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
      KnowledgeSavedData data = new KnowledgeSavedData();
      if (tag.contains("regional", Tag.TAG_LIST)) {
         ListTag list = tag.getList("regional", Tag.TAG_STRING);
         for (int i = 0; i < list.size(); i++) {
            data.unlockedRegional.add(list.getString(i));
         }
      }
      if (tag.contains("villages", Tag.TAG_LIST)) {
         ListTag villages = tag.getList("villages", Tag.TAG_COMPOUND);
         for (int i = 0; i < villages.size(); i++) {
            CompoundTag v = villages.getCompound(i);
            UUID id = v.getUUID("id");
            Set<String> unlocked = new HashSet<>();
            if (v.contains("unlocked", Tag.TAG_LIST)) {
               ListTag u = v.getList("unlocked", Tag.TAG_STRING);
               for (int j = 0; j < u.size(); j++) {
                  unlocked.add(u.getString(j));
               }
            }
            data.unlockedLocal.put(id, unlocked);
            Map<String, Float> prog = new HashMap<>();
            if (v.contains("progress", Tag.TAG_COMPOUND)) {
               CompoundTag p = v.getCompound("progress");
               for (String key : p.getAllKeys()) {
                  prog.put(key, p.getFloat(key));
               }
            }
            data.progressByVillage.put(id, prog);
         }
      }
      return data;
   }

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
      tag.putInt("version", SCHEMA_VERSION);
      ListTag regional = new ListTag();
      for (String id : this.unlockedRegional) {
         regional.add(StringTag.valueOf(id));
      }
      tag.put("regional", regional);
      ListTag villages = new ListTag();
      Set<UUID> all = new HashSet<>();
      all.addAll(this.unlockedLocal.keySet());
      all.addAll(this.progressByVillage.keySet());
      for (UUID id : all) {
         CompoundTag v = new CompoundTag();
         v.putUUID("id", id);
         ListTag u = new ListTag();
         for (String node : this.unlockedLocal.getOrDefault(id, Set.of())) {
            u.add(StringTag.valueOf(node));
         }
         v.put("unlocked", u);
         CompoundTag p = new CompoundTag();
         for (var e : this.progressByVillage.getOrDefault(id, Map.of()).entrySet()) {
            p.putFloat(e.getKey(), e.getValue());
         }
         v.put("progress", p);
         villages.add(v);
      }
      tag.put("villages", villages);
      return tag;
   }
}
