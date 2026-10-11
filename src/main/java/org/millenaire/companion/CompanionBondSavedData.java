package org.millenaire.companion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
public class CompanionBondSavedData extends SavedData {
   private static final String DATA_NAME = "millenaire_companions";

   private final Map<UUID, CompanionBond> bondsByPlayer = new HashMap<>();

   public static CompanionBondSavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
      ServerLevel store = overworld != null ? overworld : level;
      return store.getDataStorage().computeIfAbsent(new Factory<>(CompanionBondSavedData::new, CompanionBondSavedData::load), DATA_NAME);
   }

   public CompanionBondSavedData() {
   }

   public static CompanionBondSavedData load(CompoundTag tag, Provider provider) {
      CompanionBondSavedData data = new CompanionBondSavedData();
      ListTag list = tag.getList("bonds", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         CompanionBond bond = CompanionBond.load(list.getCompound(i));
         data.bondsByPlayer.put(bond.playerId(), bond);
      }
      return data;
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider provider) {
      ListTag list = new ListTag();
      for (CompanionBond bond : this.bondsByPlayer.values()) {
         list.add(bond.save());
      }
      tag.put("bonds", list);
      return tag;
   }

   @Nullable
   public CompanionBond get(UUID playerId) {
      return this.bondsByPlayer.get(playerId);
   }

   public CompanionBond getOrCreate(UUID playerId) {
      return this.bondsByPlayer.computeIfAbsent(playerId, CompanionBond::new);
   }

   public void put(CompanionBond bond) {
      this.bondsByPlayer.put(bond.playerId(), bond);
      this.setDirty();
   }

   public void remove(UUID playerId) {
      if (this.bondsByPlayer.remove(playerId) != null) {
         this.setDirty();
      }
   }

   public Map<UUID, CompanionBond> all() {
      return this.bondsByPlayer;
   }

   /** Touch dirty after mutating a bond in place. */
   public void markChanged() {
      this.setDirty();
   }
}
