package org.millenaire.compat;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

/**
 * Tracks village sites that already received a Millenaire-placed waystone.
 */
public final class WaystonePlacementSavedData extends SavedData {
   private static final String DATA_NAME = "millenaire_village_waystones";
   private final Set<String> placedKeys = new HashSet<>();

   private WaystonePlacementSavedData() {
   }

   public static Factory<WaystonePlacementSavedData> factory() {
      return new Factory<>(WaystonePlacementSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static WaystonePlacementSavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      ServerLevel storage = overworld != null ? overworld : level;
      return storage.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public boolean has(String key) {
      return this.placedKeys.contains(key);
   }

   public void mark(String key) {
      if (this.placedKeys.add(key)) {
         this.setDirty();
      }
   }

   public void unmark(String key) {
      if (this.placedKeys.remove(key)) {
         this.setDirty();
      }
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      ListTag list = new ListTag();
      for (String key : this.placedKeys) {
         list.add(StringTag.valueOf(key));
      }
      tag.put("placed", list);
      return tag;
   }

   private static WaystonePlacementSavedData load(CompoundTag tag) {
      WaystonePlacementSavedData data = new WaystonePlacementSavedData();
      ListTag list = tag.getList("placed", Tag.TAG_STRING);
      for (int i = 0; i < list.size(); i++) {
         data.placedKeys.add(list.getString(i));
      }
      return data;
   }
}
