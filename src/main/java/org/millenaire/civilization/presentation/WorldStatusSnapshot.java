package org.millenaire.civilization.presentation;

import java.util.List;
import javax.annotation.Nullable;

/**
 * Full-world civilization command-center snapshot. Server-derived; never mutates gameplay state.
 */
public record WorldStatusSnapshot(
   int tabIndex,
   String selectedFactionId,
   Overview overview,
   List<CultureLine> cultures,
   List<FactionLine> factions,
   List<VillageLine> villages,
   List<RankLine> tradeRanks,
   List<RankLine> militaryRanks,
   List<RankLine> populationRanks,
   List<WarLine> wars,
   List<ThreatLine> threats,
   List<StandingLine> standings,
   List<PlayerSettlementLine> playerSettlements,
   List<DiplomacyActionLine> diplomacyActions,
   List<ActivityLine> activity,
   List<TheaterLine> theater,
   List<CorridorLine> corridors,
   String actorVillageId,
   long capturedAtGameTime
) {
   public record Overview(
      String topTradeFactionId,
      String topTradeFactionName,
      long topTradeScore,
      String topMilitaryFactionId,
      String topMilitaryFactionName,
      int topMilitaryPower,
      String topPopulationFactionId,
      String topPopulationFactionName,
      int topPopulation,
      int activeWarCount,
      int threatCount,
      String playerFactionId,
      String playerFactionName,
      boolean playerAtWar,
      boolean hasPlayerSettlement
   ) {
   }

   public record CultureLine(
      String cultureId,
      String cultureNameKey,
      int factionCount,
      int settlementCount,
      int residents,
      boolean anyAtWar,
      String temperamentPrimary,
      String temperamentSecondary
   ) {
   }

   public record FactionLine(
      String factionId,
      String displayName,
      String cultureId,
      String cultureNameKey,
      String typeCode,
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

   public record VillageLine(
      String villageId,
      String villageName,
      String factionId,
      String factionName,
      String cultureId,
      String cultureNameKey,
      int x,
      int y,
      int z,
      int residents,
      boolean controlledByViewer,
      boolean viewerSovereign
   ) {
   }

   public record RankLine(
      int rank,
      String factionId,
      String displayName,
      long score,
      boolean viewerOwned
   ) {
   }

   public record WarLine(
      String warId,
      String attackerFactionId,
      String attackerName,
      String defenderFactionId,
      String defenderName,
      String stateCode,
      int warScore,
      String primaryGoalCode,
      boolean involvesViewer
   ) {
   }

   public record ThreatLine(
      String kindCode,
      String sourceFactionId,
      String sourceName,
      String targetFactionId,
      String targetName,
      String detail,
      int severity
   ) {
   }

   public record StandingLine(
      String factionId,
      String displayName,
      String categoryCode,
      int score,
      boolean atWar,
      boolean hasEmbargo
   ) {
   }

   public record PlayerSettlementLine(
      String villageId,
      String villageName,
      String factionId,
      String developmentLabelKey,
      int residents,
      int housingCapacity,
      boolean overcrowded,
      String inventoryCertaintyKey,
      String topNeedSummary,
      boolean topNeedProven,
      @Nullable String projectName,
      @Nullable String colonizationStatusKey,
      List<CivilizationVillageSnapshot.AllowedAction> actions
   ) {
   }

   public record DiplomacyActionLine(
      String actionId,
      String labelKey,
      String targetFactionId,
      String targetFactionName,
      boolean enabled,
      String blockedReasonKey
   ) {
   }

   public record ActivityLine(String typeKey, String summary, long gameTime) {
   }

   public record TheaterLine(
      String type,
      String summaryKey,
      String factionA,
      String factionB,
      int severity,
      long gameTime
   ) {
   }

   public record CorridorLine(
      String sourceFaction,
      String targetFaction,
      int deliveries,
      boolean recognized,
      boolean disrupted,
      String nameKey
   ) {
   }
}
