package org.millenaire.founding;

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

public final class FoundingSavedData extends SavedData {
   public static final int SCHEMA_VERSION = 1;
   private static final String DATA_NAME = "millenaire_founding";

   private final Map<UUID, FoundingOperation> operations = new LinkedHashMap<>();

   public static FoundingSavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         overworld = level;
      }
      return overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   private static Factory<FoundingSavedData> factory() {
      return new Factory<>(FoundingSavedData::new, FoundingSavedData::load, null);
   }

   public void put(FoundingOperation op) {
      this.operations.put(op.id(), op);
      this.setDirty();
   }

   public FoundingOperation get(UUID id) {
      return this.operations.get(id);
   }

   public List<FoundingOperation> openOps() {
      List<FoundingOperation> out = new ArrayList<>();
      for (FoundingOperation op : this.operations.values()) {
         if (op.isOpen()) {
            out.add(op);
         }
      }
      return out;
   }

   public int openCountForParent(UUID parentVillageId) {
      int n = 0;
      for (FoundingOperation op : this.operations.values()) {
         if (op.isOpen() && op.parentVillageId().equals(parentVillageId)) {
            n++;
         }
      }
      return n;
   }

   public static FoundingSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
      FoundingSavedData data = new FoundingSavedData();
      if (tag.contains("ops", Tag.TAG_LIST)) {
         ListTag list = tag.getList("ops", Tag.TAG_COMPOUND);
         for (int i = 0; i < list.size(); i++) {
            FoundingOperation op = FoundingOperation.load(list.getCompound(i), registries);
            data.operations.put(op.id(), op);
         }
      }
      return data;
   }

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
      tag.putInt("version", SCHEMA_VERSION);
      ListTag list = new ListTag();
      for (FoundingOperation op : this.operations.values()) {
         list.add(op.save(registries));
      }
      tag.put("ops", list);
      return tag;
   }
}
