package org.millenaire.faction;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.diplomacy.DiplomaticManager;
import org.millenaire.faction.economy.EconomySavedData;
import org.millenaire.faction.economy.TradeManager;
import org.millenaire.faction.military.MilitaryManager;
import org.millenaire.faction.military.MilitarySavedData;
import org.millenaire.faction.relationship.RelationshipManager;
import org.millenaire.faction.relationship.RelationshipSavedData;
import org.millenaire.faction.conflict.ConflictSavedData;
import org.millenaire.faction.conflict.InternalConflictManager;
import org.millenaire.faction.politics.PoliticsManager;
import org.millenaire.faction.politics.PoliticsSavedData;
import org.millenaire.faction.territory.TerritoryManager;
import org.millenaire.faction.territory.TerritorySavedData;
import org.millenaire.faction.war.WarManager;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * World lifecycle entry points for factions, relationships, economy, diplomacy, military, warfare, territory, politics, and internal conflict.
 */
public final class FactionLifecycle {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final long[] LAST_STAGE_GAME_TIME = new long[FactionTickStage.values().length];

   private enum FactionTickStage {
      RELATIONSHIPS(20L),
      ECONOMY(40L),
      DIPLOMACY(40L),
      MILITARY(40L),
      WAR(40L),
      TERRITORY(60L),
      POLITICS(60L),
      CONFLICT(60L);

      private final long interval;

      FactionTickStage(long interval) {
         this.interval = interval;
      }
   }

   private FactionLifecycle() {
   }

   /**
    * Initializes faction, relationship, and economy world data. Idempotent for a loaded world.
    */
   public static void onWorldReady(ServerLevel overworld) {
      if (overworld == null) {
         LOGGER.error("[Millenaire] FactionLifecycle.onWorldReady called with null overworld");
         return;
      }

      try {
         FactionSavedData factionData = FactionSavedData.get(overworld);
         VillageSavedData villageData = VillageSavedData.get(overworld);
         factionData.getFactionManager().bootstrap(overworld, villageData.getVillageManager());
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Faction system initialization failed — villages remain playable but faction data may be incomplete until the next successful load: {}",
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }

      FactionManager factionManager = null;

      try {
         factionManager = FactionSavedData.get(overworld).getFactionManager();
         RelationshipSavedData relationshipData = RelationshipSavedData.get(overworld);
         int purged = relationshipData.getRelationshipManager().purgeOrphans(factionManager);
         if (purged > 0) {
            LOGGER.info("[Millenaire] Purged {} orphan faction relationship(s) on world ready", purged);
         }
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Relationship system initialization failed — factions remain usable but relationship data may be incomplete until the next successful load: {}",
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }

      try {
         if (factionManager == null) {
            factionManager = FactionSavedData.get(overworld).getFactionManager();
         }

         TradeManager tradeManager = EconomySavedData.get(overworld).getTradeManager();
         int purgedRoutes = tradeManager.purgeOrphans(factionManager);
         if (purgedRoutes > 0) {
            LOGGER.info("[Millenaire] Purged {} orphan trade route(s) on world ready", purgedRoutes);
         }

         tradeManager.refreshAllProfiles(overworld);
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Faction economy initialization failed — factions/relationships remain usable but economic data may be incomplete until the next successful load: {}",
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }

      try {
         if (factionManager == null) {
            factionManager = FactionSavedData.get(overworld).getFactionManager();
         }

         DiplomaticManager diplomaticManager = DiplomacySavedData.get(overworld).getDiplomaticManager();
         int purgedAgreements = diplomaticManager.purgeInvalid(factionManager);
         if (purgedAgreements > 0) {
            LOGGER.info("[Millenaire] Purged {} invalid diplomatic agreement(s) on world ready", purgedAgreements);
         }

         diplomaticManager.tick(overworld);
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Faction diplomacy initialization failed — factions/economy remain usable but diplomatic data may be incomplete until the next successful load: {}",
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }

      try {
         if (factionManager == null) {
            factionManager = FactionSavedData.get(overworld).getFactionManager();
         }

         MilitaryManager militaryManager = MilitarySavedData.get(overworld).getMilitaryManager();
         int purgedMilitary = militaryManager.purgeInvalid(factionManager);
         if (purgedMilitary > 0) {
            LOGGER.info("[Millenaire] Purged {} orphan military organization(s) on world ready", purgedMilitary);
         }

         militaryManager.ensureOrganizations(overworld);
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Faction military initialization failed — factions/diplomacy remain usable but military data may be incomplete until the next successful load: {}",
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }

      try {
         if (factionManager == null) {
            factionManager = FactionSavedData.get(overworld).getFactionManager();
         }

         WarManager warManager = WarSavedData.get(overworld).getWarManager();
         int purgedWars = warManager.purgeInvalid(factionManager);
         if (purgedWars > 0) {
            LOGGER.info("[Millenaire] Purged {} orphan war record(s) on world ready", purgedWars);
         }

         warManager.validateLoadedConsistency(factionManager, MilitarySavedData.get(overworld).getMilitaryManager());
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Faction warfare initialization failed — earlier faction systems remain usable but war data may be incomplete until the next successful load: {}",
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }

      try {
         if (factionManager == null) {
            factionManager = FactionSavedData.get(overworld).getFactionManager();
         }

         TerritoryManager territoryManager = TerritorySavedData.get(overworld).getTerritoryManager();
         territoryManager.ensureAllTerritories(overworld);
         int purgedTerritories = territoryManager.purgeInvalid(factionManager);
         if (purgedTerritories > 0) {
            LOGGER.info("[Millenaire] Repaired {} territory reference(s) on world ready", purgedTerritories);
         }

         territoryManager.validateLoadedConsistency(factionManager);
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Faction territory initialization failed — earlier faction systems remain usable but territory data may be incomplete until the next successful load: {}",
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }

      try {
         if (factionManager == null) {
            factionManager = FactionSavedData.get(overworld).getFactionManager();
         }

         PoliticsManager politicsManager = PoliticsSavedData.get(overworld).getPoliticsManager();
         politicsManager.ensureAllPolitics(overworld);
         int purgedPolitics = politicsManager.purgeInvalid(factionManager);
         if (purgedPolitics > 0) {
            LOGGER.info("[Millenaire] Purged {} orphan political state(s) on world ready", purgedPolitics);
         }

         politicsManager.validateLoadedConsistency(factionManager);
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Faction politics initialization failed — earlier faction systems remain usable but political data may be incomplete until the next successful load: {}",
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }

      try {
         if (factionManager == null) {
            factionManager = FactionSavedData.get(overworld).getFactionManager();
         }

         InternalConflictManager conflictManager = ConflictSavedData.get(overworld).getConflictManager();
         int purgedConflicts = conflictManager.purgeInvalid(factionManager);
         if (purgedConflicts > 0) {
            LOGGER.info("[Millenaire] Purged {} orphan internal conflict record(s) on world ready", purgedConflicts);
         }

         conflictManager.validateLoadedConsistency(factionManager);
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Faction internal conflict initialization failed — earlier faction systems remain usable but conflict data may be incomplete until the next successful load: {}",
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }
   }

   /**
    * Server-tick entry for relationship decay and periodic economy updates.
    */
   public static void onServerTick(ServerLevel overworld) {
      if (overworld == null) {
         return;
      }

      boolean staging = true;
      try {
         staging = Boolean.TRUE.equals(org.millenaire.config.MillenaireServerConfig.SERVER.factionLifecycleStagingEnabled.get());
      } catch (IllegalStateException ignored) {
      }

      long gameTime = overworld.getGameTime();
      runFactionStage(staging, gameTime, FactionTickStage.RELATIONSHIPS, () -> {
         RelationshipManager manager = RelationshipSavedData.get(overworld).getRelationshipManager();
         manager.tickDecay(overworld);
      }, "relationship decay");
      runFactionStage(staging, gameTime, FactionTickStage.ECONOMY, () -> {
         EconomySavedData.get(overworld).getTradeManager().tick(overworld);
      }, "economy");
      runFactionStage(staging, gameTime, FactionTickStage.DIPLOMACY, () -> {
         DiplomacySavedData.get(overworld).getDiplomaticManager().tick(overworld);
      }, "diplomacy");
      runFactionStage(staging, gameTime, FactionTickStage.MILITARY, () -> {
         MilitarySavedData.get(overworld).getMilitaryManager().tick(overworld);
      }, "military");
      runFactionStage(staging, gameTime, FactionTickStage.WAR, () -> {
         WarSavedData.get(overworld).getWarManager().tick(overworld);
      }, "warfare");
      runFactionStage(staging, gameTime, FactionTickStage.TERRITORY, () -> {
         TerritorySavedData.get(overworld).getTerritoryManager().tick(overworld);
      }, "territory");
      runFactionStage(staging, gameTime, FactionTickStage.POLITICS, () -> {
         PoliticsSavedData.get(overworld).getPoliticsManager().tick(overworld);
      }, "politics");
      runFactionStage(staging, gameTime, FactionTickStage.CONFLICT, () -> {
         ConflictSavedData.get(overworld).getConflictManager().tick(overworld);
      }, "internal conflict");
   }

   private static void runFactionStage(
      boolean staging,
      long gameTime,
      FactionTickStage stage,
      Runnable action,
      String label
   ) {
      if (staging) {
         long last = LAST_STAGE_GAME_TIME[stage.ordinal()];
         long staggered = stage.interval + stage.ordinal() * 7L;
         if (last > 0L && gameTime - last < staggered) {
            return;
         }
         if (!org.millenaire.perf.tick.ServerTickGovernor.hasTime()) {
            org.millenaire.perf.tick.ServerTickGovernor.noteDefer(org.millenaire.perf.tick.TickPhase.CIV_FACTION);
            return;
         }
         LAST_STAGE_GAME_TIME[stage.ordinal()] = gameTime;
      } else if (!org.millenaire.perf.tick.ServerTickGovernor.hasTime()) {
         org.millenaire.perf.tick.ServerTickGovernor.noteDefer(org.millenaire.perf.tick.TickPhase.CIV_FACTION);
         return;
      }
      try {
         action.run();
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Faction {} tick failed: {}",
            label,
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }
   }

   /**
    * Assigns a faction to a newly registered village. Safe to call from spawn paths.
    */
   public static void onVillageRegistered(ServerLevel level, Village village) {
      if (level == null || village == null) {
         return;
      }

      try {
         FactionSavedData.get(level).getFactionManager().ensureVillageFaction(level, village);
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Failed to associate village {} with a faction: {}",
            village.getId().uuid(),
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }

      try {
         TerritorySavedData.get(level).getTerritoryManager().ensureTerritory(level, village);
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Failed to register territory for village {}: {}",
            village.getId().uuid(),
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }

      try {
         if (village.getFactionId() != null) {
            PoliticsSavedData.get(level).getPoliticsManager().ensurePolitics(level, village.getFactionId());
         }
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Failed to ensure politics for village {}: {}",
            village.getId().uuid(),
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }
   }

   /**
    * Detaches a village from its faction before the village is removed from the world.
    */
   public static void onVillageRemoved(ServerLevel level, Village village) {
      if (level == null || village == null) {
         return;
      }

      FactionId factionId = village.getFactionId();
      org.millenaire.village.VillageId settlementId = village.getId();

      try {
         FactionSavedData.get(level).getFactionManager().unassignVillage(village);
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Failed to detach village {} from its faction during removal: {}",
            village.getId().uuid(),
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }

      if (factionId != null) {
         try {
            MilitarySavedData.get(level).getMilitaryManager().handleSettlementRemoved(level, factionId, settlementId, true);
         } catch (Exception e) {
            LOGGER.error(
               "[Millenaire] Failed to clean military references for settlement {}: {}",
               settlementId.uuid(),
               e.getClass().getSimpleName() + ": " + e.getMessage(),
               e
            );
         }
      }

      try {
         WarSavedData.get(level).getWarManager().handleSettlementRemoved(level, settlementId);
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Failed to clean war references for settlement {}: {}",
            settlementId.uuid(),
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }

      try {
         TerritorySavedData.get(level).getTerritoryManager().handleSettlementRemoved(level, settlementId);
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Failed to clean territory references for settlement {}: {}",
            settlementId.uuid(),
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }

      try {
         PoliticsSavedData.get(level).getPoliticsManager().handleSettlementRemoved(level, settlementId);
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Failed to clean politics references for settlement {}: {}",
            settlementId.uuid(),
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }

      try {
         ConflictSavedData.get(level).getConflictManager().handleSettlementRemoved(level, settlementId);
      } catch (Exception e) {
         LOGGER.error(
            "[Millenaire] Failed to clean conflict references for settlement {}: {}",
            settlementId.uuid(),
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }
   }
}
