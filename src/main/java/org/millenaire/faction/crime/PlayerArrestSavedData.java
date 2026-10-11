package org.millenaire.faction.crime;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

/** Tracks active soft-death arrests per player. */
public final class PlayerArrestSavedData extends SavedData {
   private static final String DATA_NAME = "millenaire_player_arrest";
   private final Map<UUID, ArrestRecord> records = new HashMap<>();

   private PlayerArrestSavedData() {
   }

   public static Factory<PlayerArrestSavedData> factory() {
      return new Factory<>(PlayerArrestSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static PlayerArrestSavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      ServerLevel storage = overworld != null ? overworld : level;
      return storage.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   @Nullable
   public ArrestRecord get(UUID playerId) {
      return this.records.get(playerId);
   }

   public void put(ArrestRecord record) {
      if (record != null) {
         this.records.put(record.playerId(), record);
         this.setDirty();
      }
   }

   public void clear(UUID playerId) {
      if (this.records.remove(playerId) != null) {
         this.setDirty();
      }
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      ListTag list = new ListTag();
      for (ArrestRecord record : this.records.values()) {
         CompoundTag entry = new CompoundTag();
         entry.putUUID("player", record.playerId());
         entry.putString("culture", record.cultureId().toString());
         entry.putString("village", record.villageUuid().toString());
         entry.putLong("until", record.arrestedUntilGameTime());
         if (record.evidenceChestPos() != null) {
            entry.putLong("chest", record.evidenceChestPos().asLong());
         }
         if (record.jailPos() != null) {
            entry.putLong("jail", record.jailPos().asLong());
         }
         list.add(entry);
      }
      tag.put("records", list);
      return tag;
   }

   private static PlayerArrestSavedData load(CompoundTag tag) {
      PlayerArrestSavedData data = new PlayerArrestSavedData();
      ListTag list = tag.getList("records", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         CompoundTag entry = list.getCompound(i);
         UUID playerId = entry.getUUID("player");
         ResourceLocation culture = ResourceLocation.tryParse(entry.getString("culture"));
         UUID village = entry.getUUID("village");
         long until = entry.getLong("until");
         BlockPos chest = entry.contains("chest") ? BlockPos.of(entry.getLong("chest")) : null;
         BlockPos jail = entry.contains("jail") ? BlockPos.of(entry.getLong("jail")) : null;
         if (culture != null && village != null) {
            data.records.put(playerId, new ArrestRecord(playerId, culture, village, until, chest, jail));
         }
      }
      return data;
   }

   public record ArrestRecord(
      UUID playerId,
      ResourceLocation cultureId,
      UUID villageUuid,
      long arrestedUntilGameTime,
      @Nullable BlockPos evidenceChestPos,
      @Nullable BlockPos jailPos
   ) {
   }
}
