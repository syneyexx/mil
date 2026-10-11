package org.millenaire.faction.crime.hunter;

import java.util.Collection;
import java.util.Collections;
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

/** Persistent player custody from bounty-hunter arrests. */
public final class BountyHunterCustodySavedData extends SavedData {
   private static final String DATA_NAME = "millenaire_bounty_hunter_custody";
   private final Map<UUID, CustodyRecord> records = new HashMap<>();

   private BountyHunterCustodySavedData() {
   }

   public static Factory<BountyHunterCustodySavedData> factory() {
      return new Factory<>(BountyHunterCustodySavedData::new, (tag, registries) -> load(tag), null);
   }

   public static BountyHunterCustodySavedData get(ServerLevel level) {
      ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
      ServerLevel storage = overworld != null ? overworld : level;
      return storage.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   @Nullable
   public CustodyRecord get(UUID playerId) {
      return this.records.get(playerId);
   }

   public Collection<CustodyRecord> all() {
      return Collections.unmodifiableCollection(this.records.values());
   }

   public void put(CustodyRecord record) {
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
      for (CustodyRecord r : this.records.values()) {
         CompoundTag entry = new CompoundTag();
         entry.putUUID("player", r.playerId());
         entry.putUUID("capture", r.captureId());
         entry.putUUID("contract", r.contractId());
         entry.putString("culture", r.cultureId().toString());
         if (r.hunterUuid() != null) {
            entry.putUUID("hunter", r.hunterUuid());
         }
         entry.putLong("capturedAt", r.capturedAtGameTime());
         entry.putLong("releaseAt", r.releaseAtGameTime());
         if (r.jailPos() != null) {
            entry.putLong("jail", r.jailPos().asLong());
         }
         if (r.releasePos() != null) {
            entry.putLong("release", r.releasePos().asLong());
         }
         if (r.jailVillageUuid() != null) {
            entry.putUUID("village", r.jailVillageUuid());
         }
         entry.putBoolean("settled", r.settled());
         list.add(entry);
      }
      tag.put("records", list);
      return tag;
   }

   private static BountyHunterCustodySavedData load(CompoundTag tag) {
      BountyHunterCustodySavedData data = new BountyHunterCustodySavedData();
      ListTag list = tag.getList("records", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         CompoundTag entry = list.getCompound(i);
         ResourceLocation culture = ResourceLocation.tryParse(entry.getString("culture"));
         if (culture == null || !entry.hasUUID("player") || !entry.hasUUID("capture") || !entry.hasUUID("contract")) {
            continue;
         }
         data.records.put(
            entry.getUUID("player"),
            new CustodyRecord(
               entry.getUUID("player"),
               entry.getUUID("capture"),
               entry.getUUID("contract"),
               culture,
               entry.hasUUID("hunter") ? entry.getUUID("hunter") : null,
               entry.getLong("capturedAt"),
               entry.getLong("releaseAt"),
               entry.contains("jail") ? BlockPos.of(entry.getLong("jail")) : null,
               entry.contains("release") ? BlockPos.of(entry.getLong("release")) : null,
               entry.hasUUID("village") ? entry.getUUID("village") : null,
               entry.getBoolean("settled")
            )
         );
      }
      return data;
   }

   public record CustodyRecord(
      UUID playerId,
      UUID captureId,
      UUID contractId,
      ResourceLocation cultureId,
      @Nullable UUID hunterUuid,
      long capturedAtGameTime,
      long releaseAtGameTime,
      @Nullable BlockPos jailPos,
      @Nullable BlockPos releasePos,
      @Nullable UUID jailVillageUuid,
      boolean settled
   ) {
   }
}
