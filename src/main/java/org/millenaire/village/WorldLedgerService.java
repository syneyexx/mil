package org.millenaire.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.ReputationLabel;
import org.millenaire.culture.VillageType;
import org.millenaire.network.WorldLedgerPayload;

public final class WorldLedgerService {
   private WorldLedgerService() {
   }

   public static WorldLedgerPayload build(ServerLevel level, ServerPlayer player) {
      CultureDiplomacySimulator.ensureUpToDate(level);
      VillageManager vm = VillageSavedData.get(level).getVillageManager();
      PlayerCultureReputation cultureRep = PlayerCultureReputation.get(level);
      CultureStandingSavedData standingData = CultureStandingSavedData.get(level);
      ResourceLocation playerCulture = CultureWarService.playerCultureId(player, level);

      int villageCount = 0;
      int loneCount = 0;
      int activeRaids = 0;
      Map<ResourceLocation, Integer> villagesByCulture = new HashMap<>();
      Village owned = null;

      for (Village v : vm.getAllVillages()) {
         VillageType vt = ModCultures.getVillageType(v.getVillageTypeId());
         if (vt != null && vt.loneBuilding()) {
            loneCount++;
         } else {
            villageCount++;
            villagesByCulture.merge(v.getCultureId(), 1, Integer::sum);
         }
         if (v.getRaidStart() > 0L || v.isUnderAttack()) {
            activeRaids++;
         }
         if (owned == null && v.isPlayerControlled() && player.getUUID().equals(v.getOwnerUUID())) {
            owned = v;
         }
      }

      List<WorldLedgerPayload.CultureRow> cultures = new ArrayList<>();
      for (Entry<ResourceLocation, Culture> entry : ModCultures.getAllCultures().entrySet()) {
         ResourceLocation cultureId = entry.getKey();
         int repValue = cultureRep.get(player.getUUID(), cultureId);
         List<ReputationLabel> labels = ModCultures.getCultureReputationLabels(cultureId);
         if (labels == null) {
            labels = ModCultures.getReputationLabels(cultureId);
         }
         String repLabel = VillageReputation.getLabel(repValue, labels);
         if (repLabel == null) {
            repLabel = "reputation.neutral";
         }
         int languageScore = cultureRep.getLanguageKnowledge(player.getUUID(), cultureId);
         int bounty = org.millenaire.crime.PlayerBountySavedData.get(level).get(player.getUUID(), cultureId);
         boolean controlled = false;
         for (Village v : vm.getAllVillages()) {
            if (v.isPlayerControlled() && player.getUUID().equals(v.getOwnerUUID()) && cultureId.equals(v.getCultureId())) {
               controlled = true;
               break;
            }
         }
         cultures.add(
            new WorldLedgerPayload.CultureRow(
               cultureId.getPath(),
               resolveLedgerDisplayName(cultureId, owned),
               repValue,
               repLabel,
               languageScore,
               villagesByCulture.getOrDefault(cultureId, 0),
               controlled,
               bounty
            )
         );
      }
      cultures.sort(Comparator.comparingInt(WorldLedgerPayload.CultureRow::reputation).reversed());

      List<WorldLedgerPayload.WarRow> wars = new ArrayList<>();
      for (CultureStandingSavedData.CultureStandingEntry e : standingData.getAllStandings()) {
         wars.add(
            new WorldLedgerPayload.WarRow(
               e.cultureA().getPath(),
               e.cultureB().getPath(),
               resolveLedgerDisplayName(e.cultureA(), owned),
               resolveLedgerDisplayName(e.cultureB(), owned),
               e.standing(),
               e.atWar()
            )
         );
      }
      wars.sort(
         Comparator
            .comparing(WorldLedgerPayload.WarRow::atWar)
            .reversed()
            .thenComparingInt(WorldLedgerPayload.WarRow::standing)
      );
      int activeWarCount = (int)wars.stream().filter(WorldLedgerPayload.WarRow::atWar).count();

      List<WorldLedgerPayload.TradeRow> tradeRanks = new ArrayList<>();
      for (WorldLedgerPayload.CultureRow c : cultures) {
         ResourceLocation cultureId = ResourceLocation.fromNamespaceAndPath("millenaire", c.culturePath());
         int standingVs = 0;
         String hint = "same";
         if (playerCulture == null) {
            hint = "neutral";
            standingVs = 0;
         } else if (playerCulture.equals(cultureId)) {
            hint = "same";
            standingVs = 100;
         } else if (standingData.isAtWar(playerCulture, cultureId)) {
            hint = "war";
            standingVs = standingData.getStanding(playerCulture, cultureId);
         } else {
            standingVs = standingData.getStanding(playerCulture, cultureId);
            if (standingVs >= CultureStandingSavedData.ALLIED_THRESHOLD) {
               hint = "allied";
            } else if (standingVs >= 0) {
               hint = "neutral";
            } else {
               hint = "poor";
            }
         }
         int rankScore = c.reputation() + standingVs * 2 + c.languageScore() / 10 + c.villageCount() * 5;
         if ("war".equals(hint)) {
            rankScore -= 500;
         }
         tradeRanks.add(new WorldLedgerPayload.TradeRow(c.culturePath(), c.displayNameKey(), standingVs, hint, rankScore));
      }
      tradeRanks.sort(Comparator.comparingInt(WorldLedgerPayload.TradeRow::rankScore).reversed());

      boolean hasOwned = owned != null;
      CityStats cityStats = hasOwned ? CityStatsService.getOrRefresh(level, owned) : CityStats.EMPTY;
      return new WorldLedgerPayload(
         villageCount,
         loneCount,
         activeRaids,
         activeWarCount,
         playerCulture != null ? playerCulture.getPath() : null,
         hasOwned,
         hasOwned ? owned.getId().uuid().toString() : null,
         hasOwned && owned.getVillageName() != null ? owned.getVillageName() : null,
         hasOwned ? owned.getCultureId().getPath() : null,
         cityStats.population(),
         cityStats.maxResidentSlots(),
         cityStats.openJobSlots(),
         cityStats.openBeds(),
         cityStats.threatLabel(),
         cultures,
         wars,
         tradeRanks
      );
   }

   /**
    * Resolved label for ledger UI: player's owned village name for their culture,
    * otherwise culture display name, otherwise an i18n key fallback.
    */
   static String resolveLedgerDisplayName(ResourceLocation cultureId, @Nullable Village owned) {
      if (owned != null
         && cultureId.equals(owned.getCultureId())
         && owned.getVillageName() != null
         && !owned.getVillageName().isBlank()) {
         return owned.getVillageName();
      }
      Culture culture = ModCultures.getCulture(cultureId);
      if (culture != null && culture.displayName() != null && !culture.displayName().isBlank()) {
         return culture.displayName();
      }
      return "culture.millenaire." + cultureId.getPath();
   }
}
