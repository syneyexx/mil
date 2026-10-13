package org.millenaire.logistics;

import java.util.LinkedHashMap;
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

/**
 * World-level logistics store (schema v1): reservation ledger + delivery contracts.
 */
public final class LogisticsSavedData extends SavedData {
   public static final int SCHEMA_VERSION = 1;
   private static final String DATA_NAME = "millenaire_logistics";

   private final ReservationLedger ledger = new ReservationLedger();
   private final Map<UUID, DeliveryContract> contracts = new LinkedHashMap<>();

   public LogisticsSavedData() {
   }

   private static Factory<LogisticsSavedData> factory() {
      return new Factory<>(LogisticsSavedData::new, (tag, regs) -> load(tag, regs), null);
   }

   public static LogisticsSavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      if (overworld == null) {
         overworld = level;
      }
      return overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public ReservationLedger ledger() {
      return this.ledger;
   }

   public Map<UUID, DeliveryContract> contracts() {
      return this.contracts;
   }

   public void putContract(DeliveryContract contract) {
      this.contracts.put(contract.id(), contract);
      this.setDirty();
   }

   public DeliveryContract getContract(UUID id) {
      return this.contracts.get(id);
   }

   public static LogisticsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
      LogisticsSavedData data = new LogisticsSavedData();
      if (tag.contains("ledger", Tag.TAG_COMPOUND)) {
         data.ledger.load(tag.getCompound("ledger"), registries);
      }
      if (tag.contains("contracts", Tag.TAG_LIST)) {
         ListTag list = tag.getList("contracts", Tag.TAG_COMPOUND);
         for (int i = 0; i < list.size(); i++) {
            DeliveryContract c = DeliveryContract.load(list.getCompound(i), registries);
            data.contracts.put(c.id(), c);
         }
      }
      return data;
   }

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
      tag.putInt("version", SCHEMA_VERSION);
      tag.put("ledger", this.ledger.save(registries));
      ListTag list = new ListTag();
      for (DeliveryContract c : this.contracts.values()) {
         list.add(c.save(registries));
      }
      tag.put("contracts", list);
      return tag;
   }
}
