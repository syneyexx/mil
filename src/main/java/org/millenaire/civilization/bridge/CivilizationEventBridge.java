package org.millenaire.civilization.bridge;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.TickConstants;
import org.millenaire.civilization.CivilizationMetrics;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.diplomacy.AgreementStatus;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.diplomacy.DiplomaticAgreement;
import org.millenaire.faction.economy.EconomicBalanceLevel;
import org.millenaire.faction.economy.EconomySavedData;
import org.millenaire.faction.economy.FactionEconomicProfile;
import org.millenaire.faction.politics.FactionPoliticalState;
import org.millenaire.faction.politics.Government;
import org.millenaire.faction.politics.PoliticsManager;
import org.millenaire.faction.politics.PoliticsSavedData;
import org.millenaire.faction.war.War;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.faction.war.WarState;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.events.LivingEventSource;
import org.millenaire.living.events.LivingEventType;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * Publishes verified civilization outcomes into the Phase 3 LivingEventService exactly once.
 */
public final class CivilizationEventBridge {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final long DEDUPE_TTL = TickConstants.DAY_LENGTH;

   private CivilizationEventBridge() {
   }

   public static void ingestPending(ServerLevel level, CivilizationSavedData state) {
      // Reserved for inbound immutable snapshots from async workers.
   }

   public static void publishPoliticsChanges(ServerLevel level, CivilizationSavedData state) {
      PoliticsManager politics = PoliticsSavedData.get(level).getPoliticsManager();
      for (Faction faction : orderedFactions(level)) {
         if (!state.consumeEventBudget()) {
            return;
         }
         FactionPoliticalState political = politics.getState(faction.getId());
         if (political == null || political.getActiveGovernmentId() == null) {
            continue;
         }
         Government government = politics.getGovernment(political.getActiveGovernmentId());
         Object leader = government == null ? null : government.getCurrentLeaderId();
         String leaderMarker = "leader:" + faction.getId().asString();
         Village sample = sampleVillage(level, faction);
         if (sample == null) {
            continue;
         }
         if (state.updateStateMarker(leaderMarker, String.valueOf(leader))) {
            String dedupe = leaderMarker + ":" + String.valueOf(leader);
            emit(level, sample, LivingEventType.LEADERSHIP_CHANGED, "faction=" + faction.getId().asString(), dedupe);
         }
         String successionMarker = "succession:" + faction.getId().asString();
         String successionValue = political.isSuccessionPending() ? String.valueOf(political.getSuccessionStartedGameTime()) : "none";
         if (state.updateStateMarker(successionMarker, successionValue) && political.isSuccessionPending()) {
            emit(level, sample, LivingEventType.LEADERSHIP_CHANGED, "succession_pending", successionMarker + ":" + successionValue);
         }
      }
   }

   public static void publishDiplomacyChanges(ServerLevel level, CivilizationSavedData state) {
      for (DiplomaticAgreement agreement : DiplomacySavedData.get(level).getDiplomaticManager().getAllAgreements()) {
         if (!state.consumeEventBudget()) {
            return;
         }
         String marker = "treaty:" + agreement.getId().asString();
         String status = agreement.getStatus().name();
         Village sample = sampleVillageForFaction(level, agreement.getPrimaryFactionId());
         if (sample == null) {
            sample = sampleVillageForFaction(level, agreement.getSecondaryFactionId());
         }
         if (sample == null) {
            continue;
         }
         boolean recent = Math.abs(level.getGameTime() - agreement.getCreatedGameTime()) <= DEDUPE_TTL
            || Math.abs(level.getGameTime() - agreement.getLastStateChangeGameTime()) <= DEDUPE_TTL;
         if (state.updateStateMarker(marker, status, recent)) {
            LivingEventType type = agreement.getStatus() == AgreementStatus.ACTIVE
               ? LivingEventType.TREATY_SIGNED
               : LivingEventType.TREATY_ENDED;
            emit(level, sample, type, "agreement=" + agreement.getId().asString() + " status=" + status, marker + ":" + status);
         }
      }
   }

   public static void publishWarChanges(ServerLevel level, CivilizationSavedData state) {
      for (War war : WarSavedData.get(level).getWarManager().getAllWars()) {
         if (!state.consumeEventBudget()) {
            return;
         }
         String marker = "war:" + war.getId();
         String warState = war.getState().name();
         Village sample = sampleVillageForFaction(level, war.getInitiatorFactionId());
         if (sample == null) {
            sample = sampleVillageForFaction(level, war.getPrimaryTargetFactionId());
         }
         if (sample == null) {
            continue;
         }
         boolean recent = Math.abs(level.getGameTime() - war.getStartedGameTime()) <= DEDUPE_TTL;
         if (state.updateStateMarker(marker, warState, recent)) {
            LivingEventType type = isPeaceful(war.getState()) ? LivingEventType.PEACE_SIGNED : LivingEventType.WAR_DECLARED;
            emit(level, sample, type, "war=" + war.getId() + " state=" + warState, marker + ":" + warState);
         }
      }
   }

   public static void flushHistory(ServerLevel level, CivilizationSavedData state) {
      for (String key : state.drainHistoryKeys()) {
         LOGGER.trace("[Millenaire] History flush {}", key);
      }

      var trade = EconomySavedData.get(level).getTradeManager();
      for (Faction faction : orderedFactions(level)) {
         if (!state.consumeEventBudget()) {
            return;
         }
         FactionEconomicProfile profile = trade.getProfile(faction.getId());
         if (profile == null) {
            continue;
         }
         Village sample = sampleVillage(level, faction);
         if (sample == null) {
            continue;
         }
         EconomicBalanceLevel bal = profile.getBalanceLevel();
         if (bal == EconomicBalanceLevel.UNDER_PRESSURE || bal == EconomicBalanceLevel.IMPORT_DEPENDENT) {
            String key = "shortage:" + faction.getId().asString() + ":" + bal.name() + ":" + (level.getGameTime() / TickConstants.DAY_LENGTH);
            if (state.tryMarkPublished(key, level.getGameTime(), DEDUPE_TTL)) {
               emit(level, sample, LivingEventType.MATERIAL_SHORTAGE_DETECTED, "balance=" + bal.name(), key);
            }
         } else if (bal == EconomicBalanceLevel.HEALTHY) {
            String key = "shortage_resolved:" + faction.getId().asString() + ":" + (level.getGameTime() / TickConstants.DAY_LENGTH);
            if (state.tryMarkPublished(key, level.getGameTime(), DEDUPE_TTL)) {
               emit(level, sample, LivingEventType.MATERIAL_SHORTAGE_RESOLVED, "balance=" + bal.name(), key);
            }
         }
      }
   }

   private static boolean isPeaceful(WarState state) {
      return switch (state) {
         case ENDED, PEACE_NEGOTIATION, CEASEFIRE -> true;
         default -> false;
      };
   }

   private static void emit(ServerLevel level, Village village, LivingEventType type, String details, String dedupeKey) {
      LivingEventService.emit(level, type, LivingEventSource.SYSTEM, village, null, null, details, "public", dedupeKey);
      CivilizationMetrics.EVENTS_PUBLISHED.incrementAndGet();
   }

   private static List<Faction> orderedFactions(ServerLevel level) {
      List<Faction> list = new ArrayList<>(FactionSavedData.get(level).getFactionManager().getAllFactions());
      list.sort(Comparator.comparing(f -> f.getId().asString()));
      return list;
   }

   private static Village sampleVillage(ServerLevel level, Faction faction) {
      VillageSavedData data = VillageSavedData.get(level);
      for (VillageId id : faction.getSettlementIds()) {
         Village v = data.getVillageManager().getVillage(id);
         if (v != null) {
            return v;
         }
      }
      return null;
   }

   private static Village sampleVillageForFaction(ServerLevel level, FactionId factionId) {
      if (factionId == null) {
         return null;
      }
      Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(factionId);
      return faction == null ? null : sampleVillage(level, faction);
   }
}
