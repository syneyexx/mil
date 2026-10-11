package org.millenaire.faction.crime;

import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.slf4j.Logger;

/**
 * World-scoped persistence for culture bounty ledgers.
 */
public final class CultureBountySavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int CURRENT_VERSION = 1;
   public static final String DATA_NAME = "millenaire_culture_bounty";

   private final CultureBountyManager manager = new CultureBountyManager(this::setDirty);

   private CultureBountySavedData() {
   }

   public static Factory<CultureBountySavedData> factory() {
      return new Factory<>(CultureBountySavedData::new, (tag, registries) -> load(tag), null);
   }

   public static CultureBountySavedData get(ServerLevel level) {
      // Keep bounty ledger on the overworld so cross-chunk / hunter systems share one authority.
      ServerLevel overworld = level.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
      ServerLevel storage = overworld != null ? overworld : level;
      return storage.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public CultureBountyManager getManager() {
      return this.manager;
   }

   @Override
   public CompoundTag save(CompoundTag root, Provider registries) {
      root.putInt("version", CURRENT_VERSION);
      ListTag list = new ListTag();
      for (CultureBountyRecord record : this.manager.getAllRecords()) {
         if (record.bountyScore() <= 0
            && record.pendingWaves() <= 0
            && !record.warDeclared()
            && record.successfulBountyRaids() <= 0) {
            continue;
         }
         CompoundTag tag = new CompoundTag();
         tag.putUUID("player", record.playerId());
         tag.putString("culture", record.cultureId().toString());
         tag.putInt("score", record.bountyScore());
         tag.putLong("last_crime", record.lastCrimeGameTime());
         tag.putLong("last_decay", record.lastDecayGameTime());
         tag.putInt("successful_raids", record.successfulBountyRaids());
         tag.putInt("pending_waves", record.pendingWaves());
         tag.putLong("next_wave", record.nextWaveGameTime());
         tag.putBoolean("war_declared", record.warDeclared());
         tag.putLong("last_theft", record.lastTheftGameTime());
         tag.putLong("last_assault", record.lastAssaultGameTime());
         list.add(tag);
      }
      root.put("records", list);
      return root;
   }

   private static CultureBountySavedData load(CompoundTag root) {
      CultureBountySavedData data = new CultureBountySavedData();
      int version = root.contains("version") ? root.getInt("version") : 0;
      if (version > CURRENT_VERSION) {
         LOGGER.warn("[Millenaire] CultureBountySavedData version {} newer than supported {}", version, CURRENT_VERSION);
      }
      ListTag list = root.getList("records", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
         CompoundTag tag = list.getCompound(i);
         try {
            UUID player = tag.getUUID("player");
            ResourceLocation culture = ResourceLocation.parse(tag.getString("culture"));
            CultureBountyRecord record = new CultureBountyRecord(player, culture);
            record.setBountyScore(tag.getInt("score"));
            record.setLastCrimeGameTime(tag.getLong("last_crime"));
            record.setLastDecayGameTime(tag.getLong("last_decay"));
            record.setSuccessfulBountyRaids(tag.getInt("successful_raids"));
            record.setPendingWaves(tag.getInt("pending_waves"));
            record.setNextWaveGameTime(tag.getLong("next_wave"));
            record.setWarDeclared(tag.getBoolean("war_declared"));
            record.setLastTheftGameTime(tag.getLong("last_theft"));
            record.setLastAssaultGameTime(tag.getLong("last_assault"));
            data.manager.putLoaded(record);
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to load culture bounty record index {}", i, e);
         }
      }
      return data;
   }
}
