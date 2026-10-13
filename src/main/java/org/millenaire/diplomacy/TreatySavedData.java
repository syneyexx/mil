package org.millenaire.diplomacy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

public final class TreatySavedData extends SavedData {
   public static final int SCHEMA_VERSION = 1;
   private static final String DATA_NAME = "millenaire_treaties";

   private final Map<UUID, Treaty> treaties = new LinkedHashMap<>();

   public static TreatySavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         overworld = level;
      }
      return overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   private static Factory<TreatySavedData> factory() {
      return new Factory<>(TreatySavedData::new, TreatySavedData::load, null);
   }

   public void put(Treaty treaty) {
      this.treaties.put(treaty.id(), treaty);
      this.setDirty();
   }

   public List<Treaty> all() {
      return List.copyOf(this.treaties.values());
   }

   public List<Treaty> forVillage(UUID villageId) {
      List<Treaty> out = new ArrayList<>();
      for (Treaty t : this.treaties.values()) {
         if (t.active() && t.involves(villageId)) {
            out.add(t);
         }
      }
      return out;
   }

   public Treaty findActive(UUID a, UUID b, TreatyType type) {
      for (Treaty t : this.treaties.values()) {
         if (t.active() && t.type() == type && t.isBetween(a, b)) {
            return t;
         }
      }
      return null;
   }

   public static TreatySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
      TreatySavedData data = new TreatySavedData();
      if (tag.contains("treaties", Tag.TAG_LIST)) {
         ListTag list = tag.getList("treaties", Tag.TAG_COMPOUND);
         for (int i = 0; i < list.size(); i++) {
            Treaty t = Treaty.load(list.getCompound(i), registries);
            data.treaties.put(t.id(), t);
         }
      }
      return data;
   }

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
      tag.putInt("version", SCHEMA_VERSION);
      ListTag list = new ListTag();
      for (Treaty t : this.treaties.values()) {
         list.add(t.save(registries));
      }
      tag.put("treaties", list);
      return tag;
   }
}
