package org.millenaire.civilization.presentation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.civilization.economy.SettlementTradeOperation;
import org.millenaire.civilization.interaction.CallToArmsOffer;
import org.millenaire.civilization.interaction.EnvoyMission;
import org.millenaire.civilization.interaction.FamineAlertService;
import org.millenaire.civilization.interaction.InteractionSavedData;
import org.millenaire.civilization.interaction.TheaterStory;
import org.millenaire.civilization.interaction.TradeCorridorRecord;
import org.millenaire.civilization.interaction.TradeCorridorService;
import org.millenaire.civilization.population.MigrationOperation;
import org.millenaire.civilization.population.PopulationAggregator;
import org.millenaire.civilization.population.PopulationSnapshot;
import org.millenaire.civilization.settlement.ColonizationOperation;
import org.millenaire.civilization.settlement.ColonizationService;
import org.millenaire.civilization.settlement.PlayerSettlementFoundingService;
import org.millenaire.culture.CulturalTemperament;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionQueries;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.FactionType;
import org.millenaire.faction.diplomacy.AgreementStatus;
import org.millenaire.faction.diplomacy.AgreementType;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.diplomacy.DiplomaticAgreement;
import org.millenaire.faction.economy.EconomySavedData;
import org.millenaire.faction.economy.FactionEconomicProfile;
import org.millenaire.faction.military.MilitaryAlertLevel;
import org.millenaire.faction.military.MilitaryOrganization;
import org.millenaire.faction.military.MilitaryQueries;
import org.millenaire.faction.relationship.RelationshipCategory;
import org.millenaire.faction.relationship.RelationshipQueries;
import org.millenaire.faction.war.War;
import org.millenaire.faction.war.WarGoal;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;

/**
 * Builds a full-world civilization command-center snapshot.
 * Never force-loads chunks. Caps network-facing lists while keeping all cultures/factions.
 */
public final class WorldStatusPresentationService {
   private static final int MAX_RANKS = 20;
   private static final int MAX_WARS = 32;
   private static final int MAX_THREATS = 32;
   private static final int MAX_ACTIVITY = 64;
   private static final int MAX_STANDINGS = 48;
   private static final int MAX_OPPONENTS = 6;
   private static final int MAX_DIPLOMACY_ACTIONS = 40;

   private WorldStatusPresentationService() {
   }

   public static WorldStatusSnapshot build(
      ServerLevel level,
      ServerPlayer viewer,
      int tabIndex,
      @Nullable String selectedFactionId
   ) {
      long now = level.getGameTime();
      Map<ResourceLocation, Culture> allCultures = ModCultures.getAllCultures();
      List<Faction> allFactions = new ArrayList<>(FactionQueries.getAllFactions(level));
      allFactions.sort(Comparator.comparing(f -> f.getDisplayName() == null ? "" : f.getDisplayName(), String.CASE_INSENSITIVE_ORDER));

      Faction viewerFaction = findViewerSovereignFaction(level, viewer);
      String viewerFactionId = viewerFaction == null ? "" : viewerFaction.getId().asString();
      String resolvedSelected = selectedFactionId == null || selectedFactionId.isBlank()
         ? viewerFactionId
         : selectedFactionId.trim();
      FactionId selectedId = FactionId.tryParse(resolvedSelected);
      if (selectedId != null && FactionSavedData.get(level).getFactionManager().getFaction(selectedId) == null) {
         selectedId = viewerFaction == null ? null : viewerFaction.getId();
         resolvedSelected = selectedId == null ? "" : selectedId.asString();
      }

      Map<FactionId, FactionMetrics> metrics = new LinkedHashMap<>();
      for (Faction faction : allFactions) {
         metrics.put(faction.getId(), computeMetrics(level, faction, viewer, now));
      }

      List<WorldStatusSnapshot.FactionLine> factionLines = new ArrayList<>();
      List<WorldStatusSnapshot.VillageLine> villageLines = new ArrayList<>();
      Map<String, CultureAgg> cultureAggs = new LinkedHashMap<>();
      for (Entry<ResourceLocation, Culture> entry : allCultures.entrySet()) {
         cultureAggs.put(entry.getKey().toString(), new CultureAgg(entry.getKey()));
      }

      for (Faction faction : allFactions) {
         FactionMetrics m = metrics.get(faction.getId());
         String cultureId = faction.getCultureId() == null ? "" : faction.getCultureId().toString();
         String cultureKey = cultureNameKey(faction.getCultureId());
         CultureAgg agg = cultureAggs.computeIfAbsent(cultureId.isEmpty() ? "_none" : cultureId, id -> {
            ResourceLocation loc = faction.getCultureId();
            return new CultureAgg(loc);
         });
         agg.factionCount++;
         agg.settlementCount += m.settlementCount;
         agg.residents += m.residents;
         if (m.atWar) {
            agg.anyAtWar = true;
         }

         factionLines.add(new WorldStatusSnapshot.FactionLine(
            faction.getId().asString(),
            safeName(faction.getDisplayName()),
            cultureId,
            cultureKey,
            faction.getType().name().toLowerCase(Locale.ROOT),
            m.leaderName,
            m.settlementCount,
            m.residents,
            m.housingCapacity,
            m.housingPressure,
            m.economyLabelKey,
            m.tradeScore,
            m.militaryPower,
            m.militaryAlertCode,
            m.atWar,
            m.activeTreaties,
            m.activeColonizations,
            m.activeMigrations,
            List.copyOf(m.warOpponentNames),
            m.viewerOwned,
            m.viewerSovereign
         ));

         for (VillageId vid : faction.getSettlementIds()) {
            Village village = VillageSavedData.get(level).getVillageManager().getVillage(vid);
            if (village == null) {
               continue;
            }
            PopulationSnapshot pop = PopulationAggregator.aggregateVillage(village, now);
            BlockPos center = village.getCenter();
            boolean controlled = viewer != null && village.isControlledBy(viewer.getUUID());
            boolean sovereign = viewer != null && PlayerSettlementFoundingService.isSovereignPlayerSettlement(level, village)
               && village.isControlledBy(viewer.getUUID());
            villageLines.add(new WorldStatusSnapshot.VillageLine(
               vid.uuid().toString(),
               villageName(village),
               faction.getId().asString(),
               safeName(faction.getDisplayName()),
               cultureId,
               cultureKey,
               center.getX(),
               center.getY(),
               center.getZ(),
               pop.residents(),
               controlled,
               sovereign
            ));
         }
      }

      List<WorldStatusSnapshot.CultureLine> cultureLines = new ArrayList<>();
      for (CultureAgg agg : cultureAggs.values()) {
         if ("_none".equals(agg.cultureIdString) && agg.factionCount == 0) {
            continue;
         }
         CulturalTemperament temperament = temperamentOf(agg.cultureId);
         cultureLines.add(new WorldStatusSnapshot.CultureLine(
            agg.cultureIdString,
            cultureNameKey(agg.cultureId),
            agg.factionCount,
            agg.settlementCount,
            agg.residents,
            agg.anyAtWar,
            temperament.primaryLabel(),
            temperament.secondaryLabel()
         ));
      }
      cultureLines.sort(Comparator.comparing(WorldStatusSnapshot.CultureLine::cultureNameKey));

      List<WorldStatusSnapshot.RankLine> tradeRanks = buildRanks(factionLines, Comparator
         .comparingLong(WorldStatusSnapshot.FactionLine::tradeScore).reversed()
         .thenComparing(WorldStatusSnapshot.FactionLine::displayName, String.CASE_INSENSITIVE_ORDER),
         f -> f.tradeScore());
      List<WorldStatusSnapshot.RankLine> militaryRanks = buildRanks(factionLines, Comparator
         .comparingInt(WorldStatusSnapshot.FactionLine::militaryPower).reversed()
         .thenComparing(WorldStatusSnapshot.FactionLine::displayName, String.CASE_INSENSITIVE_ORDER),
         f -> f.militaryPower());
      List<WorldStatusSnapshot.RankLine> populationRanks = buildRanks(factionLines, Comparator
         .comparingInt(WorldStatusSnapshot.FactionLine::residents).reversed()
         .thenComparing(WorldStatusSnapshot.FactionLine::displayName, String.CASE_INSENSITIVE_ORDER),
         f -> f.residents());

      List<WorldStatusSnapshot.WarLine> wars = buildWars(level, viewerFaction);
      List<WorldStatusSnapshot.ThreatLine> threats = buildThreats(level, viewer, viewerFaction, metrics);
      List<WorldStatusSnapshot.StandingLine> standings = buildStandings(level, selectedId == null ? (viewerFaction == null ? null : viewerFaction.getId()) : selectedId);
      List<WorldStatusSnapshot.PlayerSettlementLine> playerSettlements = buildPlayerSettlements(level, viewer);
      String actorVillageId = playerSettlements.isEmpty() ? "" : playerSettlements.get(0).villageId();
      List<WorldStatusSnapshot.DiplomacyActionLine> diplomacyActions =
         buildDiplomacyActions(level, viewer, actorVillageId, selectedId, viewerFaction);
      List<WorldStatusSnapshot.ActivityLine> activity = buildActivity(level, metrics, now);
      List<WorldStatusSnapshot.TheaterLine> theater = buildTheater(level, now);
      List<WorldStatusSnapshot.CorridorLine> corridors = buildCorridors(level);

      WorldStatusSnapshot.RankLine topTrade = tradeRanks.isEmpty() ? null : tradeRanks.get(0);
      WorldStatusSnapshot.RankLine topMil = militaryRanks.isEmpty() ? null : militaryRanks.get(0);
      WorldStatusSnapshot.RankLine topPop = populationRanks.isEmpty() ? null : populationRanks.get(0);

      WorldStatusSnapshot.Overview overview = new WorldStatusSnapshot.Overview(
         topTrade == null ? "" : topTrade.factionId(),
         topTrade == null ? "" : topTrade.displayName(),
         topTrade == null ? 0L : topTrade.score(),
         topMil == null ? "" : topMil.factionId(),
         topMil == null ? "" : topMil.displayName(),
         topMil == null ? 0 : (int) topMil.score(),
         topPop == null ? "" : topPop.factionId(),
         topPop == null ? "" : topPop.displayName(),
         topPop == null ? 0 : (int) topPop.score(),
         wars.size(),
         threats.size(),
         viewerFactionId,
         viewerFaction == null ? "" : safeName(viewerFaction.getDisplayName()),
         viewerFaction != null && WarSavedData.get(level).getWarManager().isAtWar(viewerFaction.getId()),
         !playerSettlements.isEmpty()
      );

      return new WorldStatusSnapshot(
         Math.max(0, tabIndex),
         resolvedSelected == null ? "" : resolvedSelected,
         overview,
         List.copyOf(cultureLines),
         List.copyOf(factionLines),
         List.copyOf(villageLines),
         List.copyOf(tradeRanks),
         List.copyOf(militaryRanks),
         List.copyOf(populationRanks),
         List.copyOf(wars),
         List.copyOf(threats),
         List.copyOf(standings),
         List.copyOf(playerSettlements),
         List.copyOf(diplomacyActions),
         List.copyOf(activity),
         List.copyOf(theater),
         List.copyOf(corridors),
         actorVillageId,
         now
      );
   }

   @Nullable
   private static Faction findViewerSovereignFaction(ServerLevel level, @Nullable ServerPlayer viewer) {
      if (viewer == null) {
         return null;
      }
      for (Faction faction : FactionQueries.getAllFactions(level)) {
         if (faction.getType() == FactionType.PLAYER && faction.isSovereignOwnedBy(viewer.getUUID())) {
            return faction;
         }
      }
      return null;
   }

   private static FactionMetrics computeMetrics(ServerLevel level, Faction faction, @Nullable ServerPlayer viewer, long now) {
      CivilizationFactionSnapshot snap = CivilizationPresentationService.factionSnapshot(level, faction.getId());
      FactionEconomicProfile profile = EconomySavedData.get(level).getTradeManager().getProfile(faction.getId());
      long tradeScore = profile == null ? 0L : profile.getCumulativeExportValue();
      MilitaryOrganization org = MilitaryQueries.organization(level, faction.getId());
      int power = org == null ? 0 : org.getCachedMilitaryPower();
      MilitaryAlertLevel alert = org == null ? MilitaryAlertLevel.NORMAL : org.getAlertLevel();
      String alertCode = alert == null ? "normal" : alert.name().toLowerCase(Locale.ROOT);

      List<String> opponents = new ArrayList<>();
      for (War war : WarSavedData.get(level).getWarManager().getAllWars()) {
         if (war == null || !war.getState().isOngoing() || !war.isParticipant(faction.getId())) {
            continue;
         }
         FactionId other = faction.getId().equals(war.getInitiatorFactionId())
            ? war.getPrimaryTargetFactionId()
            : war.getInitiatorFactionId();
         String name = CivilizationPresentationService.factionDisplayName(level, other);
         if (name == null || name.isBlank()) {
            name = other == null ? "?" : other.asString();
         }
         if (opponents.size() < MAX_OPPONENTS && !opponents.contains(name)) {
            opponents.add(name);
         }
      }

      boolean viewerOwned = viewer != null && faction.isSovereignOwnedBy(viewer.getUUID());
      boolean viewerSovereign = viewerOwned && faction.getType() == FactionType.PLAYER;

      return new FactionMetrics(
         snap.leaderName(),
         snap.settlementCount(),
         snap.residents(),
         snap.housingCapacity(),
         snap.housingPressure(),
         snap.economyLabelKey(),
         tradeScore,
         power,
         alertCode,
         snap.atWar(),
         snap.activeTreaties(),
         snap.activeColonizations(),
         snap.activeMigrations(),
         opponents,
         viewerOwned,
         viewerSovereign
      );
   }

   private static List<WorldStatusSnapshot.RankLine> buildRanks(
      List<WorldStatusSnapshot.FactionLine> factions,
      Comparator<WorldStatusSnapshot.FactionLine> order,
      java.util.function.ToLongFunction<WorldStatusSnapshot.FactionLine> scoreFn
   ) {
      List<WorldStatusSnapshot.FactionLine> sorted = new ArrayList<>(factions);
      sorted.sort(order);
      List<WorldStatusSnapshot.RankLine> ranks = new ArrayList<>();
      int limit = Math.min(MAX_RANKS, sorted.size());
      for (int i = 0; i < limit; i++) {
         WorldStatusSnapshot.FactionLine f = sorted.get(i);
         ranks.add(new WorldStatusSnapshot.RankLine(i + 1, f.factionId(), f.displayName(), scoreFn.applyAsLong(f), f.viewerOwned()));
      }
      return ranks;
   }

   private static List<WorldStatusSnapshot.WarLine> buildWars(ServerLevel level, @Nullable Faction viewerFaction) {
      List<WorldStatusSnapshot.WarLine> lines = new ArrayList<>();
      List<War> wars = new ArrayList<>(WarSavedData.get(level).getWarManager().getAllWars());
      wars.sort(Comparator.comparingLong(War::getStartedGameTime).reversed());
      for (War war : wars) {
         if (war == null || !war.getState().isOngoing()) {
            continue;
         }
         if (lines.size() >= MAX_WARS) {
            break;
         }
         String goalCode = "none";
         for (WarGoal goal : war.getGoals().values()) {
            if (goal != null && goal.getType() != null) {
               goalCode = goal.getType().name().toLowerCase(Locale.ROOT);
               break;
            }
         }
         boolean involves = viewerFaction != null && war.isParticipant(viewerFaction.getId());
         lines.add(new WorldStatusSnapshot.WarLine(
            war.getId().asString(),
            war.getInitiatorFactionId().asString(),
            safeName(CivilizationPresentationService.factionDisplayName(level, war.getInitiatorFactionId())),
            war.getPrimaryTargetFactionId().asString(),
            safeName(CivilizationPresentationService.factionDisplayName(level, war.getPrimaryTargetFactionId())),
            war.getState().name().toLowerCase(Locale.ROOT),
            war.getWarScore(),
            goalCode,
            involves
         ));
      }
      return lines;
   }

   private static List<WorldStatusSnapshot.ThreatLine> buildThreats(
      ServerLevel level,
      @Nullable ServerPlayer viewer,
      @Nullable Faction viewerFaction,
      Map<FactionId, FactionMetrics> metrics
   ) {
      List<WorldStatusSnapshot.ThreatLine> threats = new ArrayList<>();
      if (viewerFaction != null) {
         for (War war : WarSavedData.get(level).getWarManager().getAllWars()) {
            if (war == null || !war.getState().isOngoing() || !war.isParticipant(viewerFaction.getId())) {
               continue;
            }
            FactionId other = viewerFaction.getId().equals(war.getInitiatorFactionId())
               ? war.getPrimaryTargetFactionId()
               : war.getInitiatorFactionId();
            String otherName = safeName(CivilizationPresentationService.factionDisplayName(level, other));
            threats.add(new WorldStatusSnapshot.ThreatLine(
               "war",
               other.asString(),
               otherName,
               viewerFaction.getId().asString(),
               safeName(viewerFaction.getDisplayName()),
               war.getState().name().toLowerCase(Locale.ROOT),
               90
            ));
         }

         for (Faction other : FactionQueries.getAllFactions(level)) {
            if (other.getId().equals(viewerFaction.getId())) {
               continue;
            }
            if (!RelationshipQueries.manager(level).isHostile(viewerFaction.getId(), other.getId())
               && !RelationshipQueries.manager(level).isHostile(other.getId(), viewerFaction.getId())) {
               continue;
            }
            int score = RelationshipQueries.getScore(level, other.getId(), viewerFaction.getId());
            threats.add(new WorldStatusSnapshot.ThreatLine(
               "hostile",
               other.getId().asString(),
               safeName(other.getDisplayName()),
               viewerFaction.getId().asString(),
               safeName(viewerFaction.getDisplayName()),
               String.valueOf(score),
               Math.min(80, 40 + Math.abs(score) / 2)
            ));
         }
      }

      for (Entry<FactionId, FactionMetrics> entry : metrics.entrySet()) {
         FactionMetrics m = entry.getValue();
         if ("normal".equals(m.militaryAlertCode)) {
            continue;
         }
         Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(entry.getKey());
         if (faction == null) {
            continue;
         }
         threats.add(new WorldStatusSnapshot.ThreatLine(
            "alert",
            faction.getId().asString(),
            safeName(faction.getDisplayName()),
            "",
            "",
            m.militaryAlertCode,
            alertSeverity(m.militaryAlertCode)
         ));
      }

      threats.sort(Comparator.comparingInt(WorldStatusSnapshot.ThreatLine::severity).reversed());
      if (threats.size() > MAX_THREATS) {
         threats = new ArrayList<>(threats.subList(0, MAX_THREATS));
      }
      return threats;
   }

   private static int alertSeverity(String code) {
      return switch (code) {
         case "critical" -> 95;
         case "high" -> 75;
         case "elevated" -> 55;
         default -> 35;
      };
   }

   private static List<WorldStatusSnapshot.StandingLine> buildStandings(ServerLevel level, @Nullable FactionId focus) {
      List<WorldStatusSnapshot.StandingLine> lines = new ArrayList<>();
      if (focus == null) {
         return lines;
      }
      Set<String> embargoTargets = new HashSet<>();
      for (DiplomaticAgreement agreement : DiplomacySavedData.get(level).getDiplomaticManager().getActiveAgreements()) {
         if (agreement.getType() != AgreementType.EMBARGO || agreement.getStatus() != AgreementStatus.ACTIVE) {
            continue;
         }
         if (focus.equals(agreement.getPrimaryFactionId())) {
            embargoTargets.add(agreement.getSecondaryFactionId().asString());
         } else if (focus.equals(agreement.getSecondaryFactionId())) {
            embargoTargets.add(agreement.getPrimaryFactionId().asString());
         }
      }

      List<Faction> others = new ArrayList<>(FactionQueries.getAllFactions(level));
      others.sort(Comparator.comparing(f -> safeName(f.getDisplayName()), String.CASE_INSENSITIVE_ORDER));
      for (Faction other : others) {
         if (other.getId().equals(focus)) {
            continue;
         }
         if (lines.size() >= MAX_STANDINGS) {
            break;
         }
         int score = RelationshipQueries.getScore(level, focus, other.getId());
         RelationshipCategory category = RelationshipCategory.fromScore(score);
         boolean atWar = WarSavedData.get(level).getWarManager().areAtWar(focus, other.getId());
         lines.add(new WorldStatusSnapshot.StandingLine(
            other.getId().asString(),
            safeName(other.getDisplayName()),
            category.name().toLowerCase(Locale.ROOT),
            score,
            atWar,
            embargoTargets.contains(other.getId().asString())
         ));
      }
      lines.sort(Comparator.comparingInt(WorldStatusSnapshot.StandingLine::score));
      return lines;
   }

   private static List<WorldStatusSnapshot.PlayerSettlementLine> buildPlayerSettlements(
      ServerLevel level,
      @Nullable ServerPlayer viewer
   ) {
      List<WorldStatusSnapshot.PlayerSettlementLine> lines = new ArrayList<>();
      if (viewer == null) {
         return lines;
      }
      for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         boolean controlled = village.isControlledBy(viewer.getUUID());
         boolean sovereign = PlayerSettlementFoundingService.isSovereignPlayerSettlement(level, village)
            && controlled;
         if (!controlled && !sovereign) {
            continue;
         }
         CivilizationVillageSnapshot snap = CivilizationPresentationService.villageSnapshot(level, village, viewer);
         String topNeed = "";
         boolean topNeedProven = false;
         if (!snap.topNeeds().isEmpty()) {
            var need = snap.topNeeds().get(0);
            topNeed = need.summaryKey() == null ? need.detail() : need.summaryKey();
            topNeedProven = need.shortageProven();
         }
         String project = snap.currentProject() == null ? null : snap.currentProject().nameKeyOrLiteral();
         String colonization = snap.colonization() == null ? null : snap.colonization().statusLabelKey();
         lines.add(new WorldStatusSnapshot.PlayerSettlementLine(
            village.getId().uuid().toString(),
            snap.villageName(),
            snap.factionId() == null ? "" : snap.factionId(),
            snap.developmentLabelKey(),
            snap.residents(),
            snap.housingCapacity(),
            snap.overcrowded(),
            snap.inventoryCertainty().labelKey(),
            topNeed,
            topNeedProven,
            project,
            colonization,
            snap.allowedActions()
         ));
      }
      lines.sort(Comparator.comparing(WorldStatusSnapshot.PlayerSettlementLine::villageName, String.CASE_INSENSITIVE_ORDER));
      return lines;
   }

   private static List<WorldStatusSnapshot.DiplomacyActionLine> buildDiplomacyActions(
      ServerLevel level,
      @Nullable ServerPlayer viewer,
      String actorVillageId,
      @Nullable FactionId selectedFactionId,
      @Nullable Faction viewerFaction
   ) {
      List<WorldStatusSnapshot.DiplomacyActionLine> lines = new ArrayList<>();
      if (viewer == null || viewerFaction == null || actorVillageId == null || actorVillageId.isBlank()) {
         return lines;
      }
      Village actorVillage;
      try {
         actorVillage = VillageSavedData.get(level).getVillageManager().getVillage(new VillageId(UUID.fromString(actorVillageId)));
      } catch (IllegalArgumentException e) {
         return lines;
      }
      if (actorVillage == null) {
         return lines;
      }

      // Policy actions
      lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
         CivilizationActionId.SET_IMMIGRATION_POLICY.name(),
         CivilizationActionId.SET_IMMIGRATION_POLICY.i18nKey(),
         cycleImmigration(viewerFaction.getImmigrationPolicy().name()),
         "immigration:" + viewerFaction.getImmigrationPolicy().name().toLowerCase(Locale.ROOT),
         true,
         ""
      ));
      lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
         CivilizationActionId.SET_TRADE_POLICY.name(),
         CivilizationActionId.SET_TRADE_POLICY.i18nKey(),
         cycleTrade(viewerFaction.getTradePolicy().name()),
         "trade:" + viewerFaction.getTradePolicy().name().toLowerCase(Locale.ROOT),
         true,
         ""
      ));

      List<Faction> targets = new ArrayList<>();
      if (selectedFactionId != null && !selectedFactionId.equals(viewerFaction.getId())) {
         Faction selected = FactionSavedData.get(level).getFactionManager().getFaction(selectedFactionId);
         if (selected != null) {
            targets.add(selected);
         }
      }
      for (Faction faction : FactionQueries.getAllFactions(level)) {
         if (faction.getId().equals(viewerFaction.getId())) {
            continue;
         }
         if (targets.stream().anyMatch(t -> t.getId().equals(faction.getId()))) {
            continue;
         }
         boolean interesting = WarSavedData.get(level).getWarManager().areAtWar(viewerFaction.getId(), faction.getId())
            || RelationshipQueries.manager(level).isHostile(viewerFaction.getId(), faction.getId())
            || RelationshipQueries.manager(level).isHostile(faction.getId(), viewerFaction.getId());
         if (interesting) {
            targets.add(faction);
         }
         if (targets.size() >= 12) {
            break;
         }
      }

      for (Faction target : targets) {
         if (lines.size() >= MAX_DIPLOMACY_ACTIONS) {
            break;
         }
         boolean atWar = WarSavedData.get(level).getWarManager().areAtWar(viewerFaction.getId(), target.getId());
         boolean embargo = hasEmbargo(level, viewerFaction.getId(), target.getId());
         String targetId = target.getId().asString();
         String targetName = safeName(target.getDisplayName());

         lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
            CivilizationActionId.DECLARE_WAR.name(),
            CivilizationActionId.DECLARE_WAR.i18nKey(),
            targetId,
            targetName,
            !atWar,
            atWar ? "civilization.millenaire.action.blocked.already_war" : ""
         ));
         lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
            CivilizationActionId.OFFER_PEACE.name(),
            CivilizationActionId.OFFER_PEACE.i18nKey(),
            targetId,
            targetName,
            atWar,
            atWar ? "" : "civilization.millenaire.action.blocked.no_war"
         ));
         lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
            CivilizationActionId.SET_EMBARGO.name(),
            CivilizationActionId.SET_EMBARGO.i18nKey(),
            targetId,
            targetName,
            !embargo,
            embargo ? "civilization.millenaire.action.blocked.already_embargo" : ""
         ));
         lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
            CivilizationActionId.CLEAR_EMBARGO.name(),
            CivilizationActionId.CLEAR_EMBARGO.i18nKey(),
            targetId,
            targetName,
            embargo,
            embargo ? "" : "civilization.millenaire.action.blocked.no_embargo"
         ));
         if (lines.size() < MAX_DIPLOMACY_ACTIONS) {
            lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
               CivilizationActionId.OFFER_CARAVAN_CHARTER.name(),
               CivilizationActionId.OFFER_CARAVAN_CHARTER.i18nKey(),
               targetId,
               targetName,
               !atWar,
               atWar ? "civilization.millenaire.action.blocked.already_war" : ""
            ));
         }
         if (lines.size() < MAX_DIPLOMACY_ACTIONS) {
            lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
               CivilizationActionId.SUBSIDIZE_CORRIDOR.name(),
               CivilizationActionId.SUBSIDIZE_CORRIDOR.i18nKey(),
               targetId,
               targetName,
               true,
               ""
            ));
         }
         if (lines.size() < MAX_DIPLOMACY_ACTIONS) {
            lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
               CivilizationActionId.PAY_BLOOD_MONEY.name(),
               CivilizationActionId.PAY_BLOOD_MONEY.i18nKey(),
               targetId,
               targetName,
               true,
               ""
            ));
         }
         if (lines.size() < MAX_DIPLOMACY_ACTIONS) {
            lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
               CivilizationActionId.EMERGENCY_RELIEF.name(),
               CivilizationActionId.EMERGENCY_RELIEF.i18nKey(),
               targetId,
               targetName,
               true,
               ""
            ));
         }
         if (lines.size() < MAX_DIPLOMACY_ACTIONS) {
            lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
               CivilizationActionId.OFFER_PROTECTORATE.name(),
               CivilizationActionId.OFFER_PROTECTORATE.i18nKey(),
               targetId,
               targetName,
               !atWar,
               ""
            ));
         }
         if (lines.size() < MAX_DIPLOMACY_ACTIONS) {
            lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
               CivilizationActionId.HIRE_MERCENARIES.name(),
               CivilizationActionId.HIRE_MERCENARIES.i18nKey(),
               targetId,
               targetName,
               true,
               ""
            ));
         }
         if (lines.size() < MAX_DIPLOMACY_ACTIONS && atWar) {
            lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
               CivilizationActionId.BROKER_PEACE.name(),
               CivilizationActionId.BROKER_PEACE.i18nKey(),
               targetId,
               targetName,
               true,
               ""
            ));
         }
         if (lines.size() < MAX_DIPLOMACY_ACTIONS) {
            lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
               CivilizationActionId.PROPOSE_JOINT_WORK.name(),
               CivilizationActionId.PROPOSE_JOINT_WORK.i18nKey(),
               targetId,
               targetName,
               !atWar,
               atWar ? "civilization.millenaire.action.blocked.already_war" : ""
            ));
         }
         if (lines.size() < MAX_DIPLOMACY_ACTIONS) {
            lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
               CivilizationActionId.OFFER_FOSTERAGE.name(),
               CivilizationActionId.OFFER_FOSTERAGE.i18nKey(),
               targetId,
               targetName,
               !atWar,
               atWar ? "civilization.millenaire.action.blocked.already_war" : ""
            ));
         }
      }

      InteractionSavedData interaction = InteractionSavedData.get(level);
      for (CallToArmsOffer offer : interaction.callToArms()) {
         if (lines.size() >= MAX_DIPLOMACY_ACTIONS) {
            break;
         }
         if (offer.status() != CallToArmsOffer.Status.PENDING
            || !offer.targetPlayerFaction().equals(viewerFaction.getId().asString())) {
            continue;
         }
         lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
            CivilizationActionId.RESPOND_CALL_TO_ARMS.name(),
            CivilizationActionId.RESPOND_CALL_TO_ARMS.i18nKey(),
            offer.enemyFaction(),
            offer.allyFaction(),
            true,
            ""
         ));
         if (lines.size() < MAX_DIPLOMACY_ACTIONS) {
            lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
               CivilizationActionId.REFUSE_CALL_TO_ARMS.name(),
               CivilizationActionId.REFUSE_CALL_TO_ARMS.i18nKey(),
               offer.enemyFaction(),
               offer.allyFaction(),
               true,
               ""
            ));
         }
      }
      for (EnvoyMission mission : interaction.envoys()) {
         if (lines.size() >= MAX_DIPLOMACY_ACTIONS) {
            break;
         }
         if (!mission.pending() || !mission.playerFaction().equals(viewerFaction.getId().asString())) {
            continue;
         }
         lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
            CivilizationActionId.ACCEPT_ENVOY.name(),
            CivilizationActionId.ACCEPT_ENVOY.i18nKey(),
            mission.cultureFaction(),
            mission.agenda().name().toLowerCase(Locale.ROOT),
            true,
            ""
         ));
         if (lines.size() < MAX_DIPLOMACY_ACTIONS) {
            lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
               CivilizationActionId.DECLINE_ENVOY.name(),
               CivilizationActionId.DECLINE_ENVOY.i18nKey(),
               mission.cultureFaction(),
               mission.agenda().name().toLowerCase(Locale.ROOT),
               true,
               ""
            ));
         }
      }
      if (lines.size() < MAX_DIPLOMACY_ACTIONS) {
         lines.add(new WorldStatusSnapshot.DiplomacyActionLine(
            CivilizationActionId.PROPAGANDA_MARKET.name(),
            CivilizationActionId.PROPAGANDA_MARKET.i18nKey(),
            viewerFaction.getId().asString(),
            safeName(viewerFaction.getDisplayName()),
            true,
            ""
         ));
      }
      return lines;
   }

   private static boolean hasEmbargo(ServerLevel level, FactionId a, FactionId b) {
      for (DiplomaticAgreement agreement : DiplomacySavedData.get(level).getDiplomaticManager().getActiveAgreements()) {
         if (agreement.getType() != AgreementType.EMBARGO) {
            continue;
         }
         boolean match = (a.equals(agreement.getPrimaryFactionId()) && b.equals(agreement.getSecondaryFactionId()))
            || (b.equals(agreement.getPrimaryFactionId()) && a.equals(agreement.getSecondaryFactionId()));
         if (match) {
            return true;
         }
      }
      return false;
   }

   private static String cycleImmigration(String current) {
      return switch (current) {
         case "OPEN" -> "RESTRICTED";
         case "RESTRICTED" -> "CLOSED";
         default -> "OPEN";
      };
   }

   private static String cycleTrade(String current) {
      return switch (current) {
         case "OPEN" -> "INBOUND_ONLY";
         case "INBOUND_ONLY" -> "CLOSED";
         default -> "OPEN";
      };
   }

   private static List<WorldStatusSnapshot.ActivityLine> buildActivity(
      ServerLevel level,
      Map<FactionId, FactionMetrics> metrics,
      long now
   ) {
      List<WorldStatusSnapshot.ActivityLine> lines = new ArrayList<>();
      for (SettlementTradeOperation op : CivilizationSavedData.get(level).tradeOperationsView()) {
         if (op == null) {
            continue;
         }
         lines.add(new WorldStatusSnapshot.ActivityLine(
            "trade",
            op.status() == null ? "trade" : op.status().name().toLowerCase(Locale.ROOT),
            op.lastChangeGameTime() > 0 ? op.lastChangeGameTime() : now
         ));
      }
      for (MigrationOperation op : CivilizationSavedData.get(level).migrationOpsView()) {
         if (op == null || !op.isActive()) {
            continue;
         }
         lines.add(new WorldStatusSnapshot.ActivityLine(
            "migration",
            op.status() == null ? "migration" : op.status().name().toLowerCase(Locale.ROOT),
            op.createdAtGameTime() > 0 ? op.createdAtGameTime() : now
         ));
      }
      for (FactionId factionId : metrics.keySet()) {
         Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(factionId);
         if (faction == null) {
            continue;
         }
         for (VillageId vid : faction.getSettlementIds()) {
            for (ColonizationOperation op : ColonizationService.listForVillage(level, vid.uuid(), 4)) {
               if (op == null || !op.isActive()) {
                  continue;
               }
               lines.add(new WorldStatusSnapshot.ActivityLine(
                  "colonization",
                  op.statusCode(),
                  op.createdAtGameTime() > 0 ? op.createdAtGameTime() : now
               ));
            }
         }
      }
      for (War war : WarSavedData.get(level).getWarManager().getAllWars()) {
         if (war == null) {
            continue;
         }
         lines.add(new WorldStatusSnapshot.ActivityLine(
            "war",
            war.getState().name().toLowerCase(Locale.ROOT)
               + " "
               + safeName(CivilizationPresentationService.factionDisplayName(level, war.getInitiatorFactionId()))
               + " vs "
               + safeName(CivilizationPresentationService.factionDisplayName(level, war.getPrimaryTargetFactionId())),
            war.getStartedGameTime()
         ));
      }
      for (TheaterStory story : InteractionSavedData.get(level).theaterStories()) {
         lines.add(new WorldStatusSnapshot.ActivityLine(
            "theater",
            story.type() + ":" + story.summaryKey(),
            story.gameTime()
         ));
      }
      for (FamineAlertService.FamineAlert alert : FamineAlertService.scan(level)) {
         lines.add(new WorldStatusSnapshot.ActivityLine(
            "famine",
            alert.factionName() + " (" + alert.balance().name().toLowerCase(Locale.ROOT) + ")",
            now
         ));
      }
      lines.sort(Comparator.comparingLong(WorldStatusSnapshot.ActivityLine::gameTime).reversed());
      if (lines.size() > MAX_ACTIVITY) {
         lines = new ArrayList<>(lines.subList(0, MAX_ACTIVITY));
      }
      return lines;
   }

   private static List<WorldStatusSnapshot.TheaterLine> buildTheater(ServerLevel level, long now) {
      List<WorldStatusSnapshot.TheaterLine> lines = new ArrayList<>();
      for (TheaterStory story : InteractionSavedData.get(level).theaterStories()) {
         lines.add(new WorldStatusSnapshot.TheaterLine(
            story.type(),
            story.summaryKey(),
            story.factionA() == null ? "" : story.factionA(),
            story.factionB() == null ? "" : story.factionB(),
            story.severity(),
            story.gameTime() > 0 ? story.gameTime() : now
         ));
         if (lines.size() >= 24) {
            break;
         }
      }
      return lines;
   }

   private static List<WorldStatusSnapshot.CorridorLine> buildCorridors(ServerLevel level) {
      List<WorldStatusSnapshot.CorridorLine> lines = new ArrayList<>();
      for (TradeCorridorRecord c : TradeCorridorService.listForPresentation(level, 24)) {
         lines.add(new WorldStatusSnapshot.CorridorLine(
            c.sourceFaction(),
            c.targetFaction(),
            c.successfulDeliveries(),
            c.recognized(),
            c.disrupted(),
            c.nameKey() == null ? "" : c.nameKey()
         ));
      }
      return lines;
   }

   private static CulturalTemperament temperamentOf(@Nullable ResourceLocation cultureId) {
      if (cultureId == null) {
         return CulturalTemperament.DEFAULT;
      }
      Culture culture = ModCultures.getCulture(cultureId);
      return culture == null ? CulturalTemperament.DEFAULT : culture.temperament();
   }

   private static String cultureNameKey(@Nullable ResourceLocation cultureId) {
      if (cultureId == null) {
         return "culture.millenaire.unknown";
      }
      return "culture.millenaire." + cultureId.getPath();
   }

   private static String villageName(Village village) {
      String name = village.getVillageName();
      return name == null || name.isBlank() ? "???" : name;
   }

   private static String safeName(@Nullable String name) {
      return name == null || name.isBlank() ? "?" : name;
   }

   private static final class CultureAgg {
      @Nullable
      final ResourceLocation cultureId;
      final String cultureIdString;
      int factionCount;
      int settlementCount;
      int residents;
      boolean anyAtWar;

      CultureAgg(@Nullable ResourceLocation cultureId) {
         this.cultureId = cultureId;
         this.cultureIdString = cultureId == null ? "_none" : cultureId.toString();
      }
   }

   private record FactionMetrics(
      @Nullable String leaderName,
      int settlementCount,
      int residents,
      int housingCapacity,
      boolean housingPressure,
      String economyLabelKey,
      long tradeScore,
      int militaryPower,
      String militaryAlertCode,
      boolean atWar,
      int activeTreaties,
      int activeColonizations,
      int activeMigrations,
      List<String> warOpponentNames,
      boolean viewerOwned,
      boolean viewerSovereign
   ) {
   }
}
