package org.millenaire.world.settlement;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class SettlementSavedData extends SavedData {
   public static final String DATA_NAME = "millenaire_settlements";
   public static final int CURRENT_VERSION = 1;

   private final SettlementRegistry registry = new SettlementRegistry();

   private SettlementSavedData() {
   }

   public static Factory<SettlementSavedData> factory() {
      return new Factory<>(SettlementSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static SettlementSavedData get(ServerLevel level) {
      return level.getServer().overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public SettlementRegistry getRegistry() {
      return this.registry;
   }

   private static SettlementSavedData load(CompoundTag tag) {
      SettlementSavedData data = new SettlementSavedData();
      if (tag.contains("registry")) {
         data.registry.load(tag.getCompound("registry"));
      }
      return data;
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      tag.putInt("version", CURRENT_VERSION);
      tag.put("registry", this.registry.save());
      return tag;
   }

   public void markChanged() {
      this.setDirty();
   }
}
