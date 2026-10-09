package org.millenaire.civilization.offscreen;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.TickConstants;
import org.millenaire.civilization.CivilizationConfig;
import org.millenaire.civilization.CivilizationMetrics;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.economy.EconomySavedData;
import org.millenaire.faction.economy.TradeManager;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * Bounded coarse profile refresh for inactive settlements.
 * Does NOT invent free goods, force-load chunks, or simulate combat/block placement.
 * Uses per-faction last-success timestamps so elapsed time is meaningful after restarts.
 * When only a profile refresh runs, that is exactly what this class does — not a full catch-up sim.
 */
public final class OffscreenReconciler {
   private static final Logger LOGGER = LogUtils.getLogger();

   private OffscreenReconciler() {
   }

   /**
    * @param previousAttemptGameTime last scheduled attempt time BEFORE this pass overwrote stage clocks
    */
   public static void reconcile(ServerLevel level, CivilizationSavedData state, long previousAttemptGameTime) {
      if (!Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationOffscreenEnabled.get())) {
         return;
      }
      long now = level.getGameTime();
      state.setLastOffscreenAttemptGameTime(now);

      List<Faction> factions = new ArrayList<>(FactionSavedData.get(level).getFactionManager().getAllFactions());
      if (factions.isEmpty()) {
         return;
      }
      factions.sort(Comparator.comparing(f -> f.getId().asString()));
      int budget = Math.min(CivilizationConfig.MAX_FACTIONS_PER_STAGE, MillenaireServerConfig.SERVER.civilizationFactionBudget.getAsInt());
      int scanBudget = budget * 4;
      TradeManager trade = EconomySavedData.get(level).getTradeManager();
      VillageSavedData villages = VillageSavedData.get(level);
      int n = factions.size();
      int start = Math.floorMod(state.offscreenCursor(), n);
      int scanned = 0;
      int processed = 0;
      boolean anySuccess = false;

      long maxCatchup = Math.min(
         CivilizationConfig.MAX_OFFSCREEN_CATCHUP_TICKS,
         MillenaireServerConfig.SERVER.civilizationCatchupDays.getAsInt() * TickConstants.DAY_LENGTH
      );

      for (int i = 0; i < n && scanned < scanBudget && processed < budget; i++) {
         scanned++;
         Faction faction = factions.get((start + i) % n);
         boolean anyActive = false;
         boolean anyInactive = false;
         for (VillageId id : faction.getSettlementIds()) {
            Village village = villages.getVillageManager().getVillage(id);
            if (village == null) {
               continue;
            }
            if (village.isActive()) {
               anyActive = true;
            } else {
               anyInactive = true;
            }
         }
         if (!anyInactive) {
            continue;
         }

         String factionKey = faction.getId().asString();
         long lastSuccess = state.lastFactionOffscreenGameTime(factionKey);
         if (lastSuccess <= 0L) {
            lastSuccess = previousAttemptGameTime > 0L ? previousAttemptGameTime : state.lastOffscreenSuccessGameTime();
         }
         long rawElapsed = lastSuccess <= 0L ? TickConstants.DAY_LENGTH : now - lastSuccess;
         if (rawElapsed < 0L) {
            // Clock went backwards — reset without double-applying.
            state.setLastFactionOffscreenGameTime(factionKey, now);
            processed++;
            continue;
         }
         long elapsed = Math.min(rawElapsed, maxCatchup);
         if (elapsed < TickConstants.DAY_LENGTH / 2L && anyActive) {
            // Mixed active/inactive factions already get live ticks; skip tiny profile refresh.
            processed++;
            continue;
         }

         try {
            // Profile refresh only — no free goods materialization, no inventory mutation, no combat.
            trade.refreshProfile(level, faction, now);
            state.setLastFactionOffscreenGameTime(factionKey, now);
            state.setLastFactionEconomyGameTime(factionKey, now);
            CivilizationMetrics.OFFSCREEN_CATCHUPS.incrementAndGet();
            anySuccess = true;
            if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationMetricsLogging.get())) {
               LOGGER.debug(
                  "[Millenaire] Offscreen profile refresh faction={} elapsedTicks={} (capped={})",
                  factionKey,
                  rawElapsed,
                  elapsed
               );
            }
         } catch (Exception e) {
            LOGGER.debug("[Millenaire] Offscreen economy profile refresh failed for {}: {}", faction.getId(), e.toString());
         }
         processed++;
         state.setOffscreenCursor(start + i + 1);
      }

      if (anySuccess) {
         state.setLastOffscreenSuccessGameTime(now);
      }
   }
}
