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
 * Bounded coarse catch-up for inactive settlements.
 * Never force-loads chunks. Physical NPC/block actions remain deferred until live activation.
 */
public final class OffscreenReconciler {
   private static final Logger LOGGER = LogUtils.getLogger();

   private OffscreenReconciler() {
   }

   public static void reconcile(ServerLevel level, CivilizationSavedData state) {
      if (!Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationOffscreenEnabled.get())) {
         return;
      }
      List<Faction> factions = new ArrayList<>(FactionSavedData.get(level).getFactionManager().getAllFactions());
      factions.sort(Comparator.comparing(f -> f.getId().asString()));
      int budget = Math.min(CivilizationConfig.MAX_FACTIONS_PER_STAGE, MillenaireServerConfig.SERVER.civilizationFactionBudget.getAsInt());
      TradeManager trade = EconomySavedData.get(level).getTradeManager();
      VillageSavedData villages = VillageSavedData.get(level);
      long now = level.getGameTime();
      int processed = 0;

      for (Faction faction : factions) {
         if (processed >= budget) {
            break;
         }
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
         // Coarse: refresh economic profile without loading chunks. Caps catch-up to configured days.
         long last = state.lastStageGameTime(org.millenaire.civilization.CivilizationStage.OFFSCREEN_RECONCILE);
         long maxCatchup = Math.min(
            CivilizationConfig.MAX_OFFSCREEN_CATCHUP_TICKS,
            MillenaireServerConfig.SERVER.civilizationCatchupDays.getAsInt() * TickConstants.DAY_LENGTH
         );
         long elapsed = last <= 0L ? TickConstants.DAY_LENGTH : Math.min(now - last, maxCatchup);
         if (elapsed < TickConstants.DAY_LENGTH / 2L && anyActive) {
            // Mixed active/inactive factions already get live ticks; skip tiny catch-up.
            processed++;
            continue;
         }
         try {
            trade.refreshProfile(level, faction, now);
            CivilizationMetrics.OFFSCREEN_CATCHUPS.incrementAndGet();
         } catch (Exception e) {
            LOGGER.debug("[Millenaire] Offscreen economy refresh failed for {}: {}", faction.getId(), e.toString());
         }
         processed++;
      }
   }
}
