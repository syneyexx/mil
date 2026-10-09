package org.millenaire.civilization;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.civilization.bridge.CivilizationEventBridge;
import org.millenaire.civilization.bridge.NeedsTradeBridge;
import org.millenaire.civilization.offscreen.OffscreenReconciler;
import org.millenaire.civilization.population.MigrationEvaluator;
import org.millenaire.civilization.population.MigrationTransactionService;
import org.millenaire.civilization.population.PopulationAggregator;
import org.millenaire.civilization.settlement.ColonizationService;
import org.millenaire.civilization.settlement.SettlementDevelopmentClassifier;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionLifecycle;
import org.millenaire.faction.FactionManager;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.conflict.ConflictSavedData;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.economy.EconomySavedData;
import org.millenaire.faction.military.MilitarySavedData;
import org.millenaire.faction.politics.PoliticsSavedData;
import org.millenaire.faction.relationship.RelationshipSavedData;
import org.millenaire.faction.territory.TerritorySavedData;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.living.events.LivingEventService;
import org.slf4j.Logger;

/**
 * Single authoritative civilization update coordinator.
 * Extends FactionLifecycle by imposing causal stage order, cadence, and per-tick quotas.
 * Does not replace individual managers — it schedules their existing tick APIs.
 */
public final class CivilizationOrchestrator {
   private static final Logger LOGGER = LogUtils.getLogger();

   private CivilizationOrchestrator() {
   }

   public static boolean isEnabled() {
      try {
         return Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationEvolutionEnabled.get());
      } catch (IllegalStateException e) {
         return true;
      }
   }

   /**
    * Server-tick entry. When disabled, callers should fall back to {@link FactionLifecycle#onServerTick}.
    */
   public static void onServerTick(ServerLevel overworld) {
      if (overworld == null || !isEnabled()) {
         return;
      }
      CivilizationMetrics.ORCHESTRATOR_TICKS.incrementAndGet();
      long gameTime = overworld.getGameTime();
      CivilizationSavedData state = CivilizationSavedData.get(overworld);
      int eventsBudget = Math.min(
         CivilizationConfig.MAX_EVENTS_PUBLISHED_PER_TICK,
         MillenaireServerConfig.SERVER.civilizationEventPublishBudget.getAsInt()
      );
      state.resetTickBudgets(eventsBudget);

      LivingEventService.beginTick(gameTime);

      runIfDue(overworld, state, CivilizationStage.EVENT_INGEST, gameTime, CivilizationConfig.STAGE_EVENT_INGEST_INTERVAL, () -> {
         CivilizationEventBridge.ingestPending(overworld, state);
      });

      runIfDue(overworld, state, CivilizationStage.MEMBERSHIP_RECONCILE, gameTime, CivilizationConfig.STAGE_MEMBERSHIP_INTERVAL, () -> {
         reconcileMemberships(overworld, state);
      });

      // Relationship decay is lightweight and safe every orchestrator pass (manager has its own internal cadence).
      safe(overworld, "relationships", () -> RelationshipSavedData.get(overworld).getRelationshipManager().tickDecay(overworld));

      runIfDue(overworld, state, CivilizationStage.ECONOMY, gameTime, CivilizationConfig.STAGE_ECONOMY_INTERVAL, () -> {
         EconomySavedData.get(overworld).getTradeManager().tick(overworld);
         NeedsTradeBridge.syncNeedsToEconomy(overworld, state);
      });

      runIfDue(overworld, state, CivilizationStage.POLITICS, gameTime, CivilizationConfig.STAGE_POLITICS_INTERVAL, () -> {
         PoliticsSavedData.get(overworld).getPoliticsManager().tick(overworld);
         CivilizationEventBridge.publishPoliticsChanges(overworld, state);
      });

      runIfDue(overworld, state, CivilizationStage.DIPLOMACY, gameTime, CivilizationConfig.STAGE_DIPLOMACY_INTERVAL, () -> {
         DiplomacySavedData.get(overworld).getDiplomaticManager().tick(overworld);
         CivilizationEventBridge.publishDiplomacyChanges(overworld, state);
      });

      runIfDue(overworld, state, CivilizationStage.MILITARY, gameTime, CivilizationConfig.STAGE_MILITARY_INTERVAL, () -> {
         MilitarySavedData.get(overworld).getMilitaryManager().tick(overworld);
      });

      runIfDue(overworld, state, CivilizationStage.WAR, gameTime, CivilizationConfig.STAGE_WAR_INTERVAL, () -> {
         WarSavedData.get(overworld).getWarManager().tick(overworld);
         TerritorySavedData.get(overworld).getTerritoryManager().tick(overworld);
         ConflictSavedData.get(overworld).getConflictManager().tick(overworld);
         CivilizationEventBridge.publishWarChanges(overworld, state);
      });

      runIfDue(overworld, state, CivilizationStage.DEMOGRAPHICS, gameTime, CivilizationConfig.STAGE_DEMOGRAPHICS_INTERVAL, () -> {
         PopulationAggregator.refreshDirtyFactions(overworld, state);
         MigrationTransactionService.recoverPending(overworld, state);
         MigrationEvaluator.evaluate(overworld, state);
      });

      runIfDue(overworld, state, CivilizationStage.DEVELOPMENT, gameTime, CivilizationConfig.STAGE_DEVELOPMENT_INTERVAL, () -> {
         SettlementDevelopmentClassifier.refresh(overworld, state);
         ColonizationService.tick(overworld, state);
      });

      runIfDue(overworld, state, CivilizationStage.HISTORY_PUBLISH, gameTime, CivilizationConfig.STAGE_HISTORY_INTERVAL, () -> {
         CivilizationEventBridge.flushHistory(overworld, state);
      });

      runIfDueBeforeWrite(overworld, state, CivilizationStage.OFFSCREEN_RECONCILE, gameTime, CivilizationConfig.STAGE_OFFSCREEN_INTERVAL, previous -> {
         OffscreenReconciler.reconcile(overworld, state, previous);
      });

      try {
         if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationMetricsLogging.get()) && gameTime % 1200L == 0L) {
            LOGGER.info("[Millenaire] Civilization metrics {}", CivilizationMetrics.snapshot());
         }
      } catch (IllegalStateException ignored) {
      }

      state.setDirty();
   }

   private static void runIfDue(
      ServerLevel level,
      CivilizationSavedData state,
      CivilizationStage stage,
      long gameTime,
      long interval,
      Runnable action
   ) {
      long last = state.lastStageGameTime(stage);
      // Stagger by stage ordinal so stages do not align on the same tick.
      long staggered = interval + stage.ordinal() * 7L;
      if (last > 0L && gameTime - last < staggered) {
         return;
      }
      state.setLastStageGameTime(stage, gameTime);
      CivilizationMetrics.STAGES_RUN.incrementAndGet();
      safe(level, stage.name(), action);
   }

   /**
    * Like {@link #runIfDue} but passes the previous stage timestamp before overwriting it.
    * Used by offscreen reconcile so elapsed time is computed correctly.
    */
   private static void runIfDueBeforeWrite(
      ServerLevel level,
      CivilizationSavedData state,
      CivilizationStage stage,
      long gameTime,
      long interval,
      java.util.function.LongConsumer action
   ) {
      long last = state.lastStageGameTime(stage);
      long staggered = interval + stage.ordinal() * 7L;
      if (last > 0L && gameTime - last < staggered) {
         return;
      }
      // Record attempt time only after reading previous; success time is set inside the reconciler.
      state.setLastStageGameTime(stage, gameTime);
      CivilizationMetrics.STAGES_RUN.incrementAndGet();
      try {
         action.accept(last);
      } catch (Exception e) {
         CivilizationMetrics.STAGE_FAILURES.incrementAndGet();
         LOGGER.error(
            "[Millenaire] Civilization stage '{}' failed: {}",
            stage.name(),
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }
   }

   private static void safe(ServerLevel level, String label, Runnable action) {
      try {
         action.run();
      } catch (Exception e) {
         CivilizationMetrics.STAGE_FAILURES.incrementAndGet();
         LOGGER.error(
            "[Millenaire] Civilization stage '{}' failed: {}",
            label,
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            e
         );
      }
   }

   private static void reconcileMemberships(ServerLevel level, CivilizationSavedData state) {
      FactionManager factions = FactionSavedData.get(level).getFactionManager();
      List<Faction> ordered = new ArrayList<>(factions.getAllFactions());
      ordered.sort(Comparator.comparing(f -> f.getId().asString()));
      int budget = Math.min(CivilizationConfig.MAX_FACTIONS_PER_STAGE, MillenaireServerConfig.SERVER.civilizationFactionBudget.getAsInt());
      int start = state.membershipCursor() % Math.max(1, ordered.size());
      int processed = 0;
      for (int i = 0; i < ordered.size() && processed < budget; i++) {
         Faction faction = ordered.get((start + i) % ordered.size());
         factions.reconcileSettlements(level, faction);
         try {
            PoliticsSavedData.get(level).getPoliticsManager().ensurePolitics(level, faction.getId());
         } catch (Exception e) {
            LOGGER.debug("[Millenaire] Politics ensure failed for {}: {}", faction.getId(), e.toString());
         }
         processed++;
      }
      state.setMembershipCursor(start + processed);
   }
}
