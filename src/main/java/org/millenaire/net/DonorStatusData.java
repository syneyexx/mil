package org.millenaire.net;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

public final class DonorStatusData extends SavedData {
   private static final String DATA_NAME = "millenaire_donor_status";
   private final Set<UUID> donors = new HashSet<>();

   public static DonorStatusData get(ServerLevel level) {
      return (DonorStatusData)level.getServer()
         .overworld()
         .getDataStorage()
         .computeIfAbsent(new Factory<>(DonorStatusData::new, DonorStatusData::load), "millenaire_donor_status");
   }

   public boolean isDonor(UUID player) {
      return this.donors.contains(player);
   }

   public void setDonor(UUID player, boolean isDonor) {
      boolean changed = isDonor ? this.donors.add(player) : this.donors.remove(player);
      if (changed) {
         this.setDirty();
      }
   }

   public CompoundTag save(CompoundTag tag, Provider registries) {
      ListTag list = new ListTag();

      for (UUID id : this.donors) {
         list.add(StringTag.valueOf(id.toString()));
      }

      tag.put("donors", list);
      return tag;
   }

   public static DonorStatusData load(CompoundTag tag, Provider registries) {
      DonorStatusData data = new DonorStatusData();
      if (tag.contains("donors")) {
         ListTag list = tag.getList("donors", 8);

         for (int i = 0; i < list.size(); i++) {
            try {
               data.donors.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException var6) {
            }
         }
      }

      return data;
   }
}
