package org.millenaire.faction.diplomacy;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.millenaire.faction.FactionId;
import org.slf4j.Logger;

/**
 * World-scoped persistence for diplomatic agreements and history.
 */
public final class DiplomacySavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int CURRENT_VERSION = 1;
   public static final String DATA_NAME = "millenaire_faction_diplomacy";

   private final DiplomaticManager diplomaticManager = new DiplomaticManager(this::setDirty);

   private DiplomacySavedData() {
   }

   public static Factory<DiplomacySavedData> factory() {
      return new Factory<>(DiplomacySavedData::new, (tag, registries) -> load(tag), null);
   }

   public static DiplomacySavedData get(ServerLevel level) {
      return (DiplomacySavedData)level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public DiplomaticManager getDiplomaticManager() {
      return this.diplomaticManager;
   }

   public CompoundTag save(CompoundTag root, Provider registries) {
      root.putInt("version", CURRENT_VERSION);
      root.putLong("last_expiration_check", this.diplomaticManager.getLastExpirationCheckGameTime());
      ListTag agreements = new ListTag();

      for (DiplomaticAgreement agreement : this.diplomaticManager.getAllAgreements()) {
         try {
            agreements.add(saveAgreement(agreement));
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize diplomatic agreement {}", agreement.getId(), e);
         }
      }

      root.put("agreements", agreements);
      ListTag history = new ListTag();

      for (DiplomaticHistoryEntry entry : this.diplomaticManager.getDiplomaticHistory()) {
         history.add(saveHistoryEntry(entry));
      }

      root.put("history", history);
      LOGGER.debug("[Millenaire] Saved {} diplomatic agreement(s)", agreements.size());
      return root;
   }

   private static CompoundTag saveAgreement(DiplomaticAgreement agreement) {
      CompoundTag tag = new CompoundTag();
      tag.putString("id", agreement.getId().asString());
      tag.putString("type", agreement.getType().name());
      tag.putString("primary", agreement.getPrimaryFactionId().asString());
      tag.putString("secondary", agreement.getSecondaryFactionId().asString());
      tag.putLong("created", agreement.getCreatedGameTime());
      tag.putLong("start", agreement.getStartGameTime());
      tag.putLong("expiration", agreement.getExpirationGameTime());
      tag.putString("status", agreement.getStatus().name());
      tag.putString("termination_reason", agreement.getTerminationReason().name());
      tag.putLong("terminated", agreement.getTerminatedGameTime());
      tag.putLong("last_state_change", agreement.getLastStateChangeGameTime());
      if (agreement.getReasonNote() != null) {
         tag.putString("reason_note", agreement.getReasonNote());
      }

      ListTag history = new ListTag();

      for (DiplomaticHistoryEntry entry : agreement.getHistory()) {
         history.add(saveHistoryEntry(entry));
      }

      tag.put("history", history);
      return tag;
   }

   private static CompoundTag saveHistoryEntry(DiplomaticHistoryEntry entry) {
      CompoundTag tag = new CompoundTag();
      tag.putLong("game_time", entry.getGameTime());
      tag.putString("event", entry.getEventType().name());
      tag.putString("faction_a", entry.getFactionA().asString());
      tag.putString("faction_b", entry.getFactionB().asString());
      if (entry.getAgreementId() != null) {
         tag.putString("agreement_id", entry.getAgreementId().asString());
      }

      if (entry.getAgreementType() != null) {
         tag.putString("agreement_type", entry.getAgreementType().name());
      }

      if (entry.getTerminationReason() != null) {
         tag.putString("termination_reason", entry.getTerminationReason().name());
      }

      return tag;
   }

   private static DiplomacySavedData load(CompoundTag root) {
      DiplomacySavedData data = new DiplomacySavedData();
      int version = root.contains("version") ? root.getInt("version") : 0;
      if (version > CURRENT_VERSION) {
         LOGGER.warn("[Millenaire] DiplomacySavedData version {} newer than supported {} — best-effort load", version, CURRENT_VERSION);
      } else {
         LOGGER.info("[Millenaire] Loading DiplomacySavedData version {}", version);
      }

      if (root.contains("last_expiration_check")) {
         data.diplomaticManager.setLastExpirationCheckGameTime(root.getLong("last_expiration_check"));
      }

      if (root.contains("agreements")) {
         ListTag agreements = root.getList("agreements", 10);

         for (int i = 0; i < agreements.size(); i++) {
            try {
               DiplomaticAgreement agreement = readAgreement(agreements.getCompound(i));
               if (agreement != null) {
                  data.diplomaticManager.loadAgreement(agreement);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed diplomatic agreement at index {}", i, e);
            }
         }
      }

      if (root.contains("history")) {
         ListTag historyList = root.getList("history", 10);
         List<DiplomaticHistoryEntry> history = new ArrayList<>();

         for (int i = 0; i < historyList.size(); i++) {
            DiplomaticHistoryEntry entry = readHistoryEntry(historyList.getCompound(i));
            if (entry != null) {
               history.add(entry);
            }
         }

         data.diplomaticManager.replaceGlobalHistory(history);
      }

      return data;
   }

   @Nullable
   private static DiplomaticAgreement readAgreement(CompoundTag tag) {
      AgreementId id = AgreementId.tryParse(tag.contains("id") ? tag.getString("id") : null);
      AgreementType type = AgreementType.tryParse(tag.contains("type") ? tag.getString("type") : null);
      FactionId primary = FactionId.tryParse(tag.contains("primary") ? tag.getString("primary") : null);
      FactionId secondary = FactionId.tryParse(tag.contains("secondary") ? tag.getString("secondary") : null);
      if (id == null || type == null || primary == null || secondary == null || primary.equals(secondary)) {
         LOGGER.error("[Millenaire] Invalid diplomatic agreement identity — skipped");
         return null;
      }

      DiplomaticAgreement agreement = new DiplomaticAgreement(
         id,
         type,
         primary,
         secondary,
         tag.contains("created") ? tag.getLong("created") : 0L,
         tag.contains("start") ? tag.getLong("start") : 0L,
         tag.contains("expiration") ? tag.getLong("expiration") : 0L
      );
      AgreementStatus status = AgreementStatus.tryParse(tag.contains("status") ? tag.getString("status") : null);
      AgreementTerminationReason termination = AgreementTerminationReason.tryParse(
         tag.contains("termination_reason") ? tag.getString("termination_reason") : null
      );
      agreement.loadTerminatedState(
         status == null ? AgreementStatus.ACTIVE : status,
         termination == null ? AgreementTerminationReason.NONE : termination,
         tag.contains("terminated") ? tag.getLong("terminated") : 0L,
         tag.contains("last_state_change") ? tag.getLong("last_state_change") : 0L,
         tag.contains("reason_note") ? tag.getString("reason_note") : null
      );
      if (tag.contains("history")) {
         ListTag historyList = tag.getList("history", 10);
         List<DiplomaticHistoryEntry> history = new ArrayList<>();

         for (int i = 0; i < historyList.size(); i++) {
            DiplomaticHistoryEntry entry = readHistoryEntry(historyList.getCompound(i));
            if (entry != null) {
               history.add(entry);
            }
         }

         agreement.replaceHistory(history);
      }

      return agreement;
   }

   @Nullable
   private static DiplomaticHistoryEntry readHistoryEntry(CompoundTag tag) {
      DiplomaticEventType eventType = DiplomaticEventType.tryParse(tag.contains("event") ? tag.getString("event") : null);
      FactionId factionA = FactionId.tryParse(tag.contains("faction_a") ? tag.getString("faction_a") : null);
      FactionId factionB = FactionId.tryParse(tag.contains("faction_b") ? tag.getString("faction_b") : null);
      if (eventType == null || factionA == null || factionB == null) {
         return null;
      }

      return new DiplomaticHistoryEntry(
         tag.contains("game_time") ? tag.getLong("game_time") : 0L,
         eventType,
         factionA,
         factionB,
         AgreementId.tryParse(tag.contains("agreement_id") ? tag.getString("agreement_id") : null),
         AgreementType.tryParse(tag.contains("agreement_type") ? tag.getString("agreement_type") : null),
         AgreementTerminationReason.tryParse(tag.contains("termination_reason") ? tag.getString("termination_reason") : null)
      );
   }
}
