package org.millenaire.civilization.bridge;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.civilization.CivilizationConfig;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.civilization.economy.SettlementTradeExecutor;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.economy.EconomySavedData;
import org.millenaire.faction.economy.FactionEconomicProfile;
import org.millenaire.faction.economy.ResourceBalance;
import org.millenaire.faction.economy.TradeManager;
import org.millenaire.living.LivingVillagesService;
import org.millenaire.living.needs.VillageNeedsSnapshot;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * Connects Phase 3 village needs / faction economic deficits to conserved trade attempts.
 */
public final class NeedsTradeBridge {
   private static final Logger LOGGER = LogUtils.getLogger();

   private NeedsTradeBridge() {
   }

   public static void syncNeedsToEconomy(ServerLevel level, CivilizationSavedData state) {
      if (!Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationAutoTradeEnabled.get())) {
         return;
      }
      TradeManager trade = EconomySavedData.get(level).getTradeManager();
      List<Faction> factions = new ArrayList<>(FactionSavedData.get(level).getFactionManager().getAllFactions());
      factions.sort(Comparator.comparing(f -> f.getId().asString()));
      int ops = 0;
      int maxOps = Math.min(CivilizationConfig.MAX_TRADE_OPS_PER_CYCLE, MillenaireServerConfig.SERVER.civilizationTradeOpsBudget.getAsInt());

      for (Faction importer : factions) {
         if (ops >= maxOps) {
            break;
         }
         FactionEconomicProfile profile = trade.getProfile(importer.getId());
         if (profile == null) {
            continue;
         }
         for (ResourceBalance balance : profile.getLastBalances().values()) {
            if (ops >= maxOps) {
               break;
            }
            if (!balance.hasDeficit()) {
               continue;
            }
            Faction exporter = findExporter(level, trade, importer.getId(), balance);
            if (exporter == null) {
               continue;
            }
            Village importerVillage = firstVillage(level, importer);
            Village exporterVillage = firstVillage(level, exporter);
            if (importerVillage == null || exporterVillage == null) {
               continue;
            }
            // Refresh needs so trade targets real shortages when Living Villages is enabled.
            try {
               if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.livingVillagesEnabled.get())) {
                  VillageNeedsSnapshot snap = LivingVillagesService.get().needsEngine().evaluate(level, importerVillage, false);
                  if (snap != null && snap.isEmpty() && !balance.hasDeficit()) {
                     continue;
                  }
               }
            } catch (Exception e) {
               LOGGER.debug("[Millenaire] Needs evaluation skipped: {}", e.toString());
            }

            boolean ok = SettlementTradeExecutor.tryTransferDeficitItem(
               level, exporterVillage, importerVillage, balance.resourceId(), Math.min(8, balance.deficitAmount())
            );
            if (ok) {
               ops++;
            }
         }
      }
   }

   private static Faction findExporter(ServerLevel level, TradeManager trade, FactionId importerId, ResourceBalance needed) {
      Faction best = null;
      int bestSurplus = 0;
      for (Faction candidate : FactionSavedData.get(level).getFactionManager().getAllFactions()) {
         if (candidate.getId().equals(importerId)) {
            continue;
         }
         if (trade.getRoute(candidate.getId(), importerId) == null && trade.getRoute(importerId, candidate.getId()) == null) {
            // Still allow opportunistic transfer when diplomacy permits — executor checks treaties.
         }
         FactionEconomicProfile profile = trade.getProfile(candidate.getId());
         if (profile == null) {
            continue;
         }
         ResourceBalance theirs = profile.getLastBalances().get(needed.resourceId());
         if (theirs != null && theirs.hasSurplus() && theirs.surplusAmount() > bestSurplus) {
            bestSurplus = theirs.surplusAmount();
            best = candidate;
         }
      }
      return best;
   }

   private static Village firstVillage(ServerLevel level, Faction faction) {
      VillageSavedData data = VillageSavedData.get(level);
      for (VillageId id : faction.getSettlementIds()) {
         Village v = data.getVillageManager().getVillage(id);
         if (v != null) {
            return v;
         }
      }
      return null;
   }
}
