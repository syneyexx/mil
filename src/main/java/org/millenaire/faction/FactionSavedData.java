package org.millenaire.faction;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.millenaire.village.VillageId;
import org.slf4j.Logger;

/**
 * Authoritative world persistence for factions.
 * <p>
 * Stored via Minecraft dimension {@link net.minecraft.world.level.storage.DimensionDataStorage}
 * under {@code millenaire_factions}, matching other Millénaire {@link SavedData} types.
 * Settlement → faction links are also persisted on each {@link org.millenaire.village.Village};
 * {@link FactionManager#bootstrap} reconciles both sides after load.
 */
public final class FactionSavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int CURRENT_VERSION = 1;
   public static final String DATA_NAME = "millenaire_factions";

   private final FactionManager factionManager = new FactionManager(this::setDirty);

   private FactionSavedData() {
   }

   public static Factory<FactionSavedData> factory() {
      return new Factory<>(FactionSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static FactionSavedData get(ServerLevel level) {
      return (FactionSavedData)level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public FactionManager getFactionManager() {
      return this.factionManager;
   }

   public CompoundTag save(CompoundTag root, Provider registries) {
      root.putInt("version", CURRENT_VERSION);
      ListTag factionsList = new ListTag();

      for (Faction faction : this.factionManager.getAllFactions()) {
         try {
            factionsList.add(saveFaction(faction));
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize faction {} — faction omitted from this save", faction.getId(), e);
         }
      }

      root.put("factions", factionsList);
      return root;
   }

   private static CompoundTag saveFaction(Faction faction) {
      CompoundTag tag = new CompoundTag();
      tag.putString("id", faction.getId().asString());
      tag.putString("display_name", faction.getDisplayName());
      tag.putString("type", faction.getType().name());
      tag.putLong("created_at", faction.getCreatedAtGameTime());
      if (faction.getCultureId() != null) {
         tag.putString("culture", faction.getCultureId().toString());
      }
      if (faction.getSovereignOwnerUuid() != null) {
         tag.putUUID("sovereign_owner", faction.getSovereignOwnerUuid());
      }
      tag.putString("immigration_policy", faction.getImmigrationPolicy().name());
      tag.putString("trade_policy", faction.getTradePolicy().name());
      if (faction.getBannerNbt() != null) {
         tag.putString("banner_nbt", faction.getBannerNbt());
      }

      ListTag settlements = new ListTag();

      for (VillageId settlementId : faction.getSettlementIds()) {
         CompoundTag settlementTag = new CompoundTag();
         settlementTag.putUUID("village_id", settlementId.uuid());
         settlements.add(settlementTag);
      }

      tag.put("settlements", settlements);
      return tag;
   }

   private static FactionSavedData load(CompoundTag root) {
      FactionSavedData data = new FactionSavedData();
      int version = root.contains("version") ? root.getInt("version") : 0;
      if (version > CURRENT_VERSION) {
         LOGGER.warn(
            "[Millenaire] FactionSavedData version {} is newer than supported {} — loading best-effort",
            version,
            CURRENT_VERSION
         );
      } else {
         LOGGER.info("[Millenaire] Loading FactionSavedData version {}", version);
      }

      if (!root.contains("factions")) {
         return data;
      }

      ListTag factionsList = root.getList("factions", 10);

      for (int i = 0; i < factionsList.size(); i++) {
         CompoundTag factionTag = factionsList.getCompound(i);

         try {
            Faction faction = readFaction(factionTag);
            if (faction != null) {
               data.factionManager.loadFaction(faction);
            }
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Skipping malformed persisted faction at index {}", i, e);
         }
      }

      return data;
   }

   @Nullable
   private static Faction readFaction(CompoundTag tag) {
      FactionId factionId = FactionId.tryParse(tag.contains("id") ? tag.getString("id") : null);
      if (factionId == null) {
         LOGGER.error("[Millenaire] Persisted faction missing or has invalid id — skipped");
         return null;
      }

      FactionType type = FactionType.tryParse(tag.contains("type") ? tag.getString("type") : null);
      if (type == null) {
         LOGGER.error("[Millenaire] Persisted faction {} has missing or unknown type — skipped", factionId);
         return null;
      }

      if (!tag.contains("display_name")) {
         LOGGER.error("[Millenaire] Persisted faction {} missing display_name — skipped", factionId);
         return null;
      }

      String displayName = tag.getString("display_name");
      if (displayName == null || displayName.isBlank()) {
         LOGGER.error("[Millenaire] Persisted faction {} has blank display_name — skipped", factionId);
         return null;
      }

      ResourceLocation cultureId = null;
      if (tag.contains("culture")) {
         try {
            cultureId = ResourceLocation.parse(tag.getString("culture"));
         } catch (RuntimeException e) {
            LOGGER.error("[Millenaire] Persisted faction {} has invalid culture id '{}' — skipped", factionId, tag.getString("culture"));
            return null;
         }
      }

      if (type == FactionType.CULTURE && cultureId == null) {
         LOGGER.error("[Millenaire] Persisted CULTURE faction {} missing culture — skipped", factionId);
         return null;
      }

      long createdAt = tag.contains("created_at") ? tag.getLong("created_at") : 0L;
      Faction faction = new Faction(factionId, type, displayName, cultureId, createdAt);
      if (tag.hasUUID("sovereign_owner")) {
         faction.setSovereignOwnerUuid(tag.getUUID("sovereign_owner"));
      }
      faction.setImmigrationPolicy(ImmigrationPolicy.tryParse(tag.contains("immigration_policy") ? tag.getString("immigration_policy") : null));
      faction.setTradePolicy(TradePolicy.tryParse(tag.contains("trade_policy") ? tag.getString("trade_policy") : null));
      if (tag.contains("banner_nbt")) {
         faction.setBannerNbt(tag.getString("banner_nbt"));
      }
      Set<VillageId> settlements = new LinkedHashSet<>();

      if (tag.contains("settlements")) {
         ListTag settlementsList = tag.getList("settlements", 10);

         for (int i = 0; i < settlementsList.size(); i++) {
            CompoundTag settlementTag = settlementsList.getCompound(i);

            try {
               if (!settlementTag.hasUUID("village_id")) {
                  LOGGER.warn("[Millenaire] Faction {} settlement entry {} missing village_id — ignored", factionId, i);
                  continue;
               }

               settlements.add(new VillageId(settlementTag.getUUID("village_id")));
            } catch (Exception e) {
               LOGGER.warn("[Millenaire] Faction {} settlement entry {} malformed — ignored", factionId, i, e);
            }
         }
      }

      faction.replaceSettlements(settlements);
      return faction;
   }
}
