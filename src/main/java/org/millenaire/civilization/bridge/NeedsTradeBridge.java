package org.millenaire.civilization.bridge;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.millenaire.civilization.CivilizationConfig;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.civilization.economy.SettlementTradeExecutor;
import org.millenaire.civilization.interaction.InteractionConfig;
import org.millenaire.civilization.interaction.TradeCorridorService;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.FactionType;
import org.millenaire.faction.TradePolicy;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.diplomacy.DiplomaticManager;
import org.millenaire.faction.economy.EconomicResourceId;
import org.millenaire.faction.economy.EconomySavedData;
import org.millenaire.faction.economy.FactionEconomicProfile;
import org.millenaire.faction.economy.ResourceBalance;
import org.millenaire.faction.economy.TradeManager;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.living.LivingVillagesService;
import org.millenaire.living.needs.VillageNeed;
import org.millenaire.living.needs.VillageNeedsSnapshot;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * Connects Living Villages needs / faction deficits to conserved settlement-level transfers.
 * Faction aggregates find candidates; concrete villages are selected by local stock and need.
 */
public final class NeedsTradeBridge {
   private static final Logger LOGGER = LogUtils.getLogger();

   private NeedsTradeBridge() {
   }

   public static void syncNeedsToEconomy(ServerLevel level, CivilizationSavedData state) {
      // Outstanding compensation always runs — independent of new auto-trade proposals.
      SettlementTradeExecutor.processOutstanding(level, state);
      if (!Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationAutoTradeEnabled.get())) {
         return;
      }
      TradeManager trade = EconomySavedData.get(level).getTradeManager();
      List<Faction> factions = new ArrayList<>(FactionSavedData.get(level).getFactionManager().getAllFactions());
      if (factions.isEmpty()) {
         return;
      }
      factions.sort(Comparator.comparing(f -> f.getId().asString()));
      int maxOps = Math.min(CivilizationConfig.MAX_TRADE_OPS_PER_CYCLE, MillenaireServerConfig.SERVER.civilizationTradeOpsBudget.getAsInt());
      int maxScans = Math.max(maxOps * 8, 16);
      int ops = 0;
      int scans = 0;
      int n = factions.size();
      int start = Math.floorMod(state.tradeCursor(), n);

      for (int fi = 0; fi < n && ops < maxOps && scans < maxScans; fi++) {
         Faction importerFaction = factions.get((start + fi) % n);
         if (importerFaction.getType() == FactionType.PLAYER && !importerFaction.getTradePolicy().allowsImport()) {
            continue;
         }
         FactionEconomicProfile profile = trade.getProfile(importerFaction.getId());
         List<Village> importerVillages = villagesOf(level, importerFaction);
         if (importerVillages.isEmpty()) {
            continue;
         }

         // Prefer concrete Living Villages needs when available.
         for (Village importerVillage : importerVillages) {
            if (ops >= maxOps || scans >= maxScans) {
               break;
            }
            scans++;
            List<NeedItem> needs = localNeeds(level, importerVillage, profile);
            for (NeedItem need : needs) {
               if (ops >= maxOps || scans >= maxScans) {
                  break;
               }
               scans++;
               Village exporterVillage = findBestExporterVillage(level, trade, importerFaction.getId(), importerVillage, need);
               if (exporterVillage == null) {
                  continue;
               }
               boolean ok = SettlementTradeExecutor.tryTransferDeficitItem(
                  level, exporterVillage, importerVillage, need.resourceId(), need.amount()
               );
               if (ok) {
                  ops++;
                  if (exporterVillage.getFactionId() != null
                     && importerVillage.getFactionId() != null
                     && !exporterVillage.getFactionId().equals(importerVillage.getFactionId())) {
                     TradeCorridorService.recordSuccess(level, exporterVillage, importerVillage);
                  }
               }
            }
         }
      }
      state.setTradeCursor(start + Math.max(1, Math.min(n, scans)));
   }

   private static List<NeedItem> localNeeds(ServerLevel level, Village village, FactionEconomicProfile profile) {
      List<NeedItem> out = new ArrayList<>();
      try {
         if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.livingVillagesEnabled.get())) {
            VillageNeedsSnapshot snap = LivingVillagesService.get().needsEngine().evaluate(level, village, false);
            if (snap != null) {
               for (VillageNeed need : snap.needs()) {
                  if (need.shortage().isEmpty()) {
                     continue;
                  }
                  need.shortage().forEach((itemId, qty) -> {
                     if (qty != null && qty > 0) {
                        out.add(new NeedItem(new EconomicResourceId(itemId), Math.min(8, qty)));
                     }
                  });
               }
            }
         }
      } catch (Exception e) {
         LOGGER.debug("[Millenaire] Needs evaluation skipped: {}", e.toString());
      }
      if (out.isEmpty() && profile != null) {
         for (ResourceBalance balance : profile.getLastBalances().values()) {
            if (balance.hasDeficit()) {
               out.add(new NeedItem(balance.resourceId(), Math.min(8, balance.deficitAmount())));
            }
         }
      }
      out.sort(Comparator.comparing(n -> n.resourceId().id().toString()));
      return out;
   }

   private static Village findBestExporterVillage(
      ServerLevel level,
      TradeManager trade,
      FactionId importerFactionId,
      Village importerVillage,
      NeedItem need
   ) {
      Item item = resolveItem(need.resourceId());
      if (item == null) {
         return null;
      }
      DiplomaticManager diplomacy = DiplomacySavedData.get(level).getDiplomaticManager();
      List<Candidate> candidates = new ArrayList<>();

      for (Faction candidateFaction : FactionSavedData.get(level).getFactionManager().getAllFactions()) {
         boolean sameFaction = candidateFaction.getId().equals(importerFactionId);
         if (sameFaction) {
            if (!Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationIntraFactionLogistics.get())) {
               continue;
            }
         } else {
            if (!diplomacy.canTrade(candidateFaction.getId(), importerFactionId)) {
               continue;
            }
            if (WarSavedData.get(level).getWarManager().areAtWar(candidateFaction.getId(), importerFactionId)) {
               continue;
            }
         }
         if (candidateFaction.getType() == FactionType.PLAYER && !candidateFaction.getTradePolicy().allowsExport()) {
            continue;
         }
         for (Village exporter : villagesOf(level, candidateFaction)) {
            if (exporter.getId().equals(importerVillage.getId())) {
               continue;
            }
            int available = SettlementTradeExecutor.countExportable(level, exporter, item);
            if (available <= 0) {
               continue;
            }
            long distSq = distSq(exporter, importerVillage);
            // Prefer same-faction logistics slightly, then surplus, then proximity.
            int score = available * 10 - (int)Math.min(Integer.MAX_VALUE / 4, distSq / 256);
            if (sameFaction) {
               score += 1000;
            } else if (TradeCorridorService.hasRecognizedCorridor(level, candidateFaction.getId(), importerFactionId)) {
               score += InteractionConfig.CORRIDOR_EXPORTER_SCORE_BONUS;
            }
            candidates.add(new Candidate(exporter, available, score, sameFaction));
         }
      }
      if (candidates.isEmpty()) {
         return null;
      }
      candidates.sort(Comparator.comparingInt(Candidate::score).reversed().thenComparing(c -> c.village().getId().uuid()));
      for (Candidate candidate : candidates) {
         // Try next candidate when best is not executable (executor will also check).
         if (candidate.available() > 0 && candidate.village().getTownhall() != null) {
            return candidate.village();
         }
      }
      return null;
   }

   private static Item resolveItem(EconomicResourceId resourceId) {
      if (resourceId == null) {
         return null;
      }
      return BuiltInRegistries.ITEM.getHolder(resourceId.id()).map(Holder.Reference::value).orElse(null);
   }

   private static List<Village> villagesOf(ServerLevel level, Faction faction) {
      VillageSavedData data = VillageSavedData.get(level);
      List<Village> out = new ArrayList<>();
      for (VillageId id : faction.getSettlementIds()) {
         Village v = data.getVillageManager().getVillage(id);
         if (v != null) {
            out.add(v);
         }
      }
      out.sort(Comparator.comparing(v -> v.getId().uuid()));
      return out;
   }

   private static long distSq(Village a, Village b) {
      long dx = (long)a.getCenter().getX() - b.getCenter().getX();
      long dz = (long)a.getCenter().getZ() - b.getCenter().getZ();
      return dx * dx + dz * dz;
   }

   private record NeedItem(EconomicResourceId resourceId, int amount) {
   }

   private record Candidate(Village village, int available, int score, boolean sameFaction) {
   }
}
