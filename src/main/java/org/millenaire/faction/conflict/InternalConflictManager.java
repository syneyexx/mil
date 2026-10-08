package org.millenaire.faction.conflict;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionIds;
import org.millenaire.faction.FactionManager;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.FactionType;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.diplomacy.DiplomaticAgreement;
import org.millenaire.faction.diplomacy.DiplomaticStanding;
import org.millenaire.faction.economy.EconomySavedData;
import org.millenaire.faction.economy.TradeDisruptionReason;
import org.millenaire.faction.economy.TradeManager;
import org.millenaire.faction.economy.TradeRoute;
import org.millenaire.faction.military.Army;
import org.millenaire.faction.military.ArmyId;
import org.millenaire.faction.military.ArmyState;
import org.millenaire.faction.military.MilitaryManager;
import org.millenaire.faction.military.MilitarySavedData;
import org.millenaire.faction.military.MilitaryUnit;
import org.millenaire.faction.military.UnitCategory;
import org.millenaire.faction.military.UnitTypeDefinition;
import org.millenaire.faction.military.UnitTypeRegistry;
import org.millenaire.faction.politics.FactionPoliticalState;
import org.millenaire.faction.politics.Government;
import org.millenaire.faction.politics.GovernmentType;
import org.millenaire.faction.politics.LocalAdministrator;
import org.millenaire.faction.politics.PoliticalEventType;
import org.millenaire.faction.politics.PoliticalGroup;
import org.millenaire.faction.politics.PoliticalGroupId;
import org.millenaire.faction.politics.PoliticalLeader;
import org.millenaire.faction.politics.PoliticsManager;
import org.millenaire.faction.politics.PoliticsSavedData;
import org.millenaire.faction.relationship.RelationshipChangeReason;
import org.millenaire.faction.relationship.RelationshipSavedData;
import org.millenaire.faction.territory.ConquestType;
import org.millenaire.faction.territory.Territory;
import org.millenaire.faction.territory.TerritoryId;
import org.millenaire.faction.territory.TerritoryQueries;
import org.millenaire.faction.territory.TerritorySavedData;
import org.millenaire.faction.war.PeaceOutcome;
import org.millenaire.faction.war.War;
import org.millenaire.faction.war.WarGoalType;
import org.millenaire.faction.war.WarId;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * Authoritative Phase 9 internal conflict engine. Armed phases reuse Phase 6 wars via linked {@link WarId}.
 */
public final class InternalConflictManager {
   private static final Logger LOGGER = LogUtils.getLogger();

   private final Map<ConflictId, InternalConflict> conflicts = new LinkedHashMap<>();
   private final Map<UnrestId, Unrest> unrestRecords = new LinkedHashMap<>();
   private final Map<ConflictId, PostConflictRecovery> recoveries = new LinkedHashMap<>();
   private final Map<ConflictId, NegotiatedSettlement> settlements = new LinkedHashMap<>();
   private final Map<ArmyId, MilitaryLoyalty> armyLoyalty = new LinkedHashMap<>();
   private final Map<ConflictId, Set<FactionId>> foreignRecognition = new LinkedHashMap<>();
   private final Map<String, Long> lastIdenticalConflictEnd = new LinkedHashMap<>();
   private final Set<ConflictId> resolvingConflictIds = new HashSet<>();
   private final Runnable markDirty;
   private long lastUpdateGameTime;

   InternalConflictManager(Runnable markDirty) {
      this.markDirty = Objects.requireNonNull(markDirty, "markDirty");
   }

   public enum DecisionAdvice {
      ESCALATE,
      HOLD,
      NEGOTIATE,
      CONCEDE,
      REPRESS,
      FIGHT,
      SEEK_FOREIGN_SUPPORT,
      DISBAND,
      WAIT
   }

   // -------------------------------------------------------------------------
   // Tick
   // -------------------------------------------------------------------------

   public void tick(ServerLevel level) {
      Objects.requireNonNull(level, "level");
      long gameTime = level.getGameTime();
      if (this.lastUpdateGameTime > 0L
         && gameTime - this.lastUpdateGameTime < ConflictConfig.CONFLICT_UPDATE_INTERVAL_TICKS) {
         return;
      }

      this.lastUpdateGameTime = gameTime;
      this.detectUnrest(level, gameTime);
      this.updateUnrestEscalation(level, gameTime);
      this.checkEscalationThresholds(level, gameTime);
      this.updateActiveConflicts(level, gameTime);
      this.updateMilitaryLoyalty(level, gameTime);
      this.applyEconomicDisruption(level, gameTime);
      this.checkResolutionConditions(level, gameTime);
      this.applyRecoveryDecay(level, gameTime);
      this.protectStaleConflicts(level, gameTime);
      this.markDirty.run();
   }

   // -------------------------------------------------------------------------
   // Conflict formation (public starters)
   // -------------------------------------------------------------------------

   @Nullable
   public InternalConflict startRebellion(
      ServerLevel level,
      FactionId originFactionId,
      @Nullable PoliticalGroupId rebelGroupId,
      @Nullable String causeDetail
   ) {
      return this.beginConflict(
         level,
         originFactionId,
         ConflictType.REBELLION,
         RebelObjective.OVERTHROW_GOVERNMENT,
         ConflictStage.ORGANIZED_REBELLION,
         rebelGroupId,
         null,
         null,
         causeDetail
      );
   }

   @Nullable
   public InternalConflict startCivilWar(
      ServerLevel level,
      FactionId originFactionId,
      @Nullable PoliticalGroupId rebelGroupId,
      @Nullable String causeDetail
   ) {
      return this.beginConflict(
         level,
         originFactionId,
         ConflictType.CIVIL_WAR,
         RebelObjective.OVERTHROW_GOVERNMENT,
         ConflictStage.CIVIL_WAR,
         rebelGroupId,
         null,
         null,
         causeDetail
      );
   }

   @Nullable
   public InternalConflict startRevolution(
      ServerLevel level,
      FactionId originFactionId,
      @Nullable PoliticalGroupId rebelGroupId,
      @Nullable String causeDetail
   ) {
      return this.beginConflict(
         level,
         originFactionId,
         ConflictType.REVOLUTION,
         RebelObjective.CHANGE_GOVERNMENT,
         ConflictStage.REVOLUTION,
         rebelGroupId,
         null,
         null,
         causeDetail
      );
   }

   @Nullable
   public InternalConflict startSecession(
      ServerLevel level,
      FactionId originFactionId,
      @Nullable PoliticalGroupId rebelGroupId,
      @Nullable TerritoryId focusTerritoryId,
      @Nullable VillageId focusSettlementId,
      @Nullable String causeDetail
   ) {
      return this.beginConflict(
         level,
         originFactionId,
         ConflictType.SECESSION,
         RebelObjective.SECEDE,
         ConflictStage.SECESSION,
         rebelGroupId,
         focusTerritoryId,
         focusSettlementId,
         causeDetail
      );
   }

   @Nullable
   public InternalConflict startCoupConflict(
      ServerLevel level,
      FactionId originFactionId,
      @Nullable PoliticalGroupId rebelGroupId,
      @Nullable String causeDetail
   ) {
      return this.beginConflict(
         level,
         originFactionId,
         ConflictType.COUP_CONFLICT,
         RebelObjective.REPLACE_LEADER,
         ConflictStage.ORGANIZED_REBELLION,
         rebelGroupId,
         null,
         null,
         causeDetail
      );
   }

   @Nullable
   public InternalConflict startRegionalUprising(
      ServerLevel level,
      FactionId originFactionId,
      @Nullable PoliticalGroupId rebelGroupId,
      @Nullable TerritoryId focusTerritoryId,
      @Nullable VillageId focusSettlementId,
      @Nullable String causeDetail
   ) {
      return this.beginConflict(
         level,
         originFactionId,
         ConflictType.REGIONAL_UPRISING,
         RebelObjective.GAIN_AUTONOMY,
         ConflictStage.LOCAL_UPRISING,
         rebelGroupId,
         focusTerritoryId,
         focusSettlementId,
         causeDetail
      );
   }

   public boolean addConflictParticipant(
      ServerLevel level,
      ConflictId conflictId,
      FactionId factionId,
      boolean governmentSide,
      @Nullable PoliticalGroupId groupId
   ) {
      Objects.requireNonNull(level, "level");
      InternalConflict conflict = this.conflicts.get(conflictId);
      if (conflict == null || !conflict.isActive() || factionId == null) {
         return false;
      }

      if (!FactionSavedData.get(level).getFactionManager().exists(factionId)) {
         return false;
      }

      for (ConflictSide side : conflict.getSides().values()) {
         if (factionId.equals(side.getFactionId()) && side.isActive()) {
            if (groupId != null) {
               side.addSupporter(groupId);
               this.markDirty.run();
            }
            return true;
         }
      }

      long gameTime = level.getGameTime();
      ConflictSide side = new ConflictSide(ConflictSideId.random(), conflictId, factionId, governmentSide);
      if (groupId != null) {
         side.addSupporter(groupId);
      }

      PoliticsManager politics = PoliticsSavedData.get(level).getPoliticsManager();
      PoliticalLeader leader = politics.getLeader(factionId);
      if (leader != null) {
         side.setLeaderId(leader.getId());
      }

      Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(factionId);
      side.setDisplayName(faction == null ? factionId.asString() : faction.getDisplayName());
      conflict.putSide(side);
      conflict.recordHistory(
         new ConflictHistoryEntry(
            gameTime,
            ConflictEventType.SIDE_JOINED,
            conflictId,
            factionId,
            side.getId(),
            conflict.getLinkedWarId(),
            null,
            null,
            0,
            governmentSide ? "government" : "rebel"
         )
      );
      this.markDirty.run();
      return true;
   }

   public boolean removeConflictParticipant(ServerLevel level, ConflictId conflictId, FactionId factionId) {
      Objects.requireNonNull(level, "level");
      InternalConflict conflict = this.conflicts.get(conflictId);
      if (conflict == null || !conflict.isActive() || factionId == null) {
         return false;
      }

      ConflictSide target = null;
      for (ConflictSide side : conflict.getSides().values()) {
         if (factionId.equals(side.getFactionId()) && side.isActive()) {
            target = side;
            break;
         }
      }

      if (target == null) {
         return false;
      }

      if (target.getId().equals(conflict.getGovernmentSideId()) || target.getId().equals(conflict.getPrimaryRebelSideId())) {
         return false;
      }

      long gameTime = level.getGameTime();
      target.deactivate();
      conflict.recordHistory(
         new ConflictHistoryEntry(
            gameTime,
            ConflictEventType.SIDE_LEFT,
            conflictId,
            factionId,
            target.getId(),
            conflict.getLinkedWarId(),
            null,
            null,
            0,
            "left"
         )
      );
      this.markDirty.run();
      return true;
   }

   public boolean assignTerritory(
      ServerLevel level,
      ConflictId conflictId,
      ConflictSideId sideId,
      TerritoryId territoryId,
      boolean captureForRebel
   ) {
      Objects.requireNonNull(level, "level");
      InternalConflict conflict = this.conflicts.get(conflictId);
      if (conflict == null || !conflict.isActive() || sideId == null || territoryId == null) {
         return false;
      }

      ConflictSide side = conflict.getSides().get(sideId);
      Territory territory = TerritoryQueries.territory(level, territoryId);
      if (side == null || !side.isActive() || territory == null) {
         return false;
      }

      long gameTime = level.getGameTime();
      side.addTerritory(territoryId);
      VillageId settlementId = territory.getSettlementId();
      if (settlementId != null) {
         side.addSettlement(settlementId);
      }

      if (captureForRebel && !side.isGovernmentSide() && conflict.getRebelFactionId() != null) {
         TerritorySavedData.get(level)
            .getTerritoryManager()
            .captureSettlement(level, settlementId, conflict.getRebelFactionId(), conflict.getLinkedWarId(), null);
         if (settlementId != null) {
            PoliticsSavedData.get(level)
               .getPoliticsManager()
               .appointRebelAdministrator(
                  level,
                  conflict.getRebelFactionId(),
                  settlementId,
                  side.getLeaderId(),
                  side.getDisplayName()
               );
         }
      }

      conflict.recordHistory(
         new ConflictHistoryEntry(
            gameTime,
            ConflictEventType.TERRITORY_CAPTURED,
            conflictId,
            side.getFactionId(),
            sideId,
            conflict.getLinkedWarId(),
            null,
            settlementId,
            0,
            territoryId.asString()
         )
      );
      this.markDirty.run();
      return true;
   }

   public boolean assignMilitaryForce(ServerLevel level, ConflictId conflictId, ConflictSideId sideId, ArmyId armyId) {
      Objects.requireNonNull(level, "level");
      InternalConflict conflict = this.conflicts.get(conflictId);
      if (conflict == null || !conflict.isActive() || sideId == null || armyId == null) {
         return false;
      }

      ConflictSide side = conflict.getSides().get(sideId);
      Army army = MilitarySavedData.get(level).getMilitaryManager().getArmy(armyId);
      if (side == null || !side.isActive() || army == null || army.getState() == ArmyState.DISBANDED) {
         return false;
      }

      long gameTime = level.getGameTime();
      FactionId desiredFaction = side.getFactionId();
      if (!desiredFaction.equals(army.getFactionId())) {
         if (!MilitarySavedData.get(level).getMilitaryManager().transferArmyAllegiance(level, armyId, desiredFaction)) {
            return false;
         }
      }

      side.addArmy(armyId);
      this.armyLoyalty.put(
         armyId,
         side.isGovernmentSide() ? MilitaryLoyalty.LOYAL_TO_GOVERNMENT : MilitaryLoyalty.SUPPORTS_REBELS
      );
      conflict.recordHistory(
         new ConflictHistoryEntry(
            gameTime,
            ConflictEventType.ARMY_DEFECTED,
            conflictId,
            desiredFaction,
            sideId,
            conflict.getLinkedWarId(),
            null,
            null,
            0,
            armyId.asString()
         )
      );
      this.markDirty.run();
      return true;
   }

   public int calculateConflictScore(ServerLevel level, ConflictId conflictId) {
      Objects.requireNonNull(level, "level");
      InternalConflict conflict = this.conflicts.get(conflictId);
      if (conflict == null) {
         return 0;
      }

      ConflictSide gov = conflict.getGovernmentSide();
      ConflictSide rebel = conflict.getPrimaryRebelSide();
      if (gov == null || rebel == null) {
         return 0;
      }

      int territoryDelta = rebel.getControlledTerritoryIds().size() - gov.getControlledTerritoryIds().size();
      int supportDelta = rebel.getSupport() - gov.getSupport();
      int legitimacyDelta = rebel.getLegitimacy() - gov.getLegitimacy();
      int exhaustionPenalty = rebel.getExhaustion() - gov.getExhaustion();
      int warScore = 0;
      if (conflict.getLinkedWarId() != null) {
         War war = WarSavedData.get(level).getWarManager().getWar(conflict.getLinkedWarId());
         if (war != null) {
            // Linked war: rebel is attacker → positive war score favors rebels.
            warScore = war.getWarScore() / 5;
            if (conflict.getRebelFactionId() != null
               && conflict.getRebelFactionId().equals(war.getPrimaryTargetFactionId())) {
               warScore = -warScore;
            }
         }
      }

      // Normalize to -100..100 around zero for rebel advantage.
      int rebelAdvantage = Math.max(
         -100,
         Math.min(
            100,
            territoryDelta * 10
               + supportDelta
               + legitimacyDelta / 2
               - exhaustionPenalty
               + warScore
         )
      );
      rebel.setConflictScore(rebelAdvantage);
      gov.setConflictScore(-rebelAdvantage);
      return rebelAdvantage;
   }

   public boolean resolveConflict(ServerLevel level, ConflictId conflictId, ConflictResolution resolution) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(resolution, "resolution");
      InternalConflict conflict = this.conflicts.get(conflictId);
      if (conflict == null || !conflict.isActive()) {
         return false;
      }

      return this.applyResolution(level, conflict, resolution, level.getGameTime(), "manual_resolve");
   }

   @Nullable
   public NegotiatedSettlement negotiateSettlement(
      ServerLevel level,
      ConflictId conflictId,
      ConflictResolution outcome,
      List<String> terms,
      int autonomyGranted,
      boolean leadershipChanged,
      boolean governmentReformed,
      boolean territoryCeded,
      @Nullable String detail
   ) {
      Objects.requireNonNull(level, "level");
      InternalConflict conflict = this.conflicts.get(conflictId);
      if (conflict == null || !conflict.isActive()) {
         return null;
      }

      long gameTime = level.getGameTime();
      conflict.setNegotiating(true);
      conflict.recordHistory(
         new ConflictHistoryEntry(
            gameTime,
            ConflictEventType.NEGOTIATION_STARTED,
            conflictId,
            conflict.getOriginFactionId(),
            conflict.getPrimaryRebelSideId(),
            conflict.getLinkedWarId(),
            null,
            null,
            0,
            detail
         )
      );

      ConflictResolution resolved = outcome == null ? ConflictResolution.NEGOTIATED_SETTLEMENT : outcome;
      PoliticsManager politics = PoliticsSavedData.get(level).getPoliticsManager();
      ConflictSide rebel = conflict.getPrimaryRebelSide();
      PoliticalGroupId primaryGroup = null;
      if (rebel != null) {
         for (PoliticalGroupId groupId : rebel.getSupporterGroupIds()) {
            primaryGroup = groupId;
            break;
         }
      }

      if (autonomyGranted > 0 && rebel != null) {
         for (VillageId settlementId : rebel.getControlledSettlementIds()) {
            politics.grantAutonomy(level, conflict.getOriginFactionId(), settlementId, autonomyGranted);
         }

         if (rebel.getControlledSettlementIds().isEmpty()) {
            Faction origin = FactionSavedData.get(level).getFactionManager().getFaction(conflict.getOriginFactionId());
            if (origin != null) {
               for (VillageId settlementId : origin.getSettlementIds()) {
                  politics.grantAutonomy(level, conflict.getOriginFactionId(), settlementId, autonomyGranted);
                  break;
               }
            }
         }
      }

      if (primaryGroup != null) {
         politics.grantPoliticalRepresentation(level, conflict.getOriginFactionId(), primaryGroup);
         politics.grantConcession(level, conflict.getOriginFactionId(), primaryGroup, detail);
      }

      if (governmentReformed) {
         politics.enactReform(level, conflict.getOriginFactionId(), null, -10);
      }

      if (leadershipChanged && primaryGroup != null) {
         politics.attemptCoup(level, conflict.getOriginFactionId(), primaryGroup);
      }

      if (terms != null) {
         for (String term : terms) {
            if (term == null || term.isBlank()) {
               continue;
            }

            String normalized = term.trim().toLowerCase();
            if (normalized.contains("autonomy") && autonomyGranted <= 0 && rebel != null) {
               for (VillageId settlementId : rebel.getControlledSettlementIds()) {
                  politics.grantAutonomy(level, conflict.getOriginFactionId(), settlementId, 25);
               }
            } else if (normalized.contains("representation") && primaryGroup != null) {
               politics.grantPoliticalRepresentation(level, conflict.getOriginFactionId(), primaryGroup);
            } else if (normalized.contains("reform")) {
               politics.enactReform(level, conflict.getOriginFactionId(), null, -5);
            } else if (normalized.contains("concession") && primaryGroup != null) {
               politics.grantConcession(level, conflict.getOriginFactionId(), primaryGroup, term);
            }
         }
      }

      NegotiatedSettlement settlement = new NegotiatedSettlement(
         conflictId, conflict.getOriginFactionId(), resolved, gameTime
      );
      if (terms != null) {
         for (String term : terms) {
            settlement.addTerm(term);
         }
      }

      settlement.setAutonomyGranted(autonomyGranted);
      settlement.setLeadershipChanged(leadershipChanged);
      settlement.setGovernmentReformed(governmentReformed);
      settlement.setTerritoryCeded(territoryCeded);
      settlement.setDetail(detail);
      this.settlements.put(conflictId, settlement);

      conflict.recordHistory(
         new ConflictHistoryEntry(
            gameTime,
            ConflictEventType.SETTLEMENT_REACHED,
            conflictId,
            conflict.getOriginFactionId(),
            conflict.getPrimaryRebelSideId(),
            conflict.getLinkedWarId(),
            null,
            null,
            autonomyGranted,
            detail
         )
      );
      conflict.recordHistory(
         new ConflictHistoryEntry(
            gameTime,
            ConflictEventType.CONCESSION,
            conflictId,
            conflict.getOriginFactionId(),
            conflict.getPrimaryRebelSideId(),
            null,
            null,
            null,
            autonomyGranted,
            detail
         )
      );

      if (!this.applyResolution(level, conflict, resolved, gameTime, "negotiated")) {
         conflict.setNegotiating(false);
         conflict.recordHistory(
            new ConflictHistoryEntry(
               gameTime,
               ConflictEventType.NEGOTIATION_FAILED,
               conflictId,
               conflict.getOriginFactionId(),
               conflict.getPrimaryRebelSideId(),
               conflict.getLinkedWarId(),
               null,
               null,
               0,
               "resolution_failed"
            )
         );
         this.markDirty.run();
         return null;
      }

      this.markDirty.run();
      return settlement;
   }

   @Nullable
   public FactionId createFactionSplit(
      ServerLevel level,
      ConflictId conflictId,
      String rebelDisplayName,
      @Nullable ResourceLocation cultureId
   ) {
      Objects.requireNonNull(level, "level");
      InternalConflict conflict = this.conflicts.get(conflictId);
      if (conflict == null || !conflict.isActive()) {
         return null;
      }

      if (conflict.getRebelFactionId() != null
         && FactionSavedData.get(level).getFactionManager().exists(conflict.getRebelFactionId())) {
         return conflict.getRebelFactionId();
      }

      long gameTime = level.getGameTime();
      Faction origin = FactionSavedData.get(level).getFactionManager().getFaction(conflict.getOriginFactionId());
      ResourceLocation culture = cultureId;
      if (culture == null && origin != null) {
         culture = origin.getCultureId();
      }

      FactionId rebelId = FactionIds.breakawayFaction(UUID.randomUUID());
      String name = rebelDisplayName == null || rebelDisplayName.isBlank()
         ? (origin == null ? "Breakaway" : origin.getDisplayName() + " Rebels")
         : rebelDisplayName.trim();
      Faction created = FactionSavedData.get(level)
         .getFactionManager()
         .createFaction(rebelId, name, FactionType.BREAKAWAY, culture, gameTime);
      if (created == null) {
         return null;
      }

      PoliticsSavedData.get(level).getPoliticsManager().ensurePolitics(level, rebelId);
      MilitarySavedData.get(level).getMilitaryManager().getOrCreateOrganization(rebelId);
      conflict.setRebelFactionId(rebelId);
      ConflictSide rebelSide = conflict.getPrimaryRebelSide();
      if (rebelSide != null) {
         rebelSide.reassignFaction(rebelId);
         rebelSide.setDisplayName(name);
      }

      conflict.recordHistory(
         new ConflictHistoryEntry(
            gameTime,
            ConflictEventType.FACTION_SPLIT,
            conflictId,
            rebelId,
            conflict.getPrimaryRebelSideId(),
            conflict.getLinkedWarId(),
            null,
            null,
            0,
            name
         )
      );
      this.markDirty.run();
      return rebelId;
   }

   public boolean reintegrateTerritory(
      ServerLevel level,
      ConflictId conflictId,
      TerritoryId territoryId
   ) {
      Objects.requireNonNull(level, "level");
      InternalConflict conflict = this.conflicts.get(conflictId);
      if (conflict == null || territoryId == null) {
         return false;
      }

      long gameTime = level.getGameTime();
      Territory territory = TerritoryQueries.territory(level, territoryId);
      if (territory == null) {
         return false;
      }

      boolean ok;
      if (territory.isOccupied() && conflict.getOriginFactionId().equals(territory.getOwnerFactionId())) {
         ok = TerritorySavedData.get(level)
            .getTerritoryManager()
            .liberateTerritory(level, territoryId, conflict.getOriginFactionId(), conflict.getLinkedWarId());
      } else {
         ok = TerritorySavedData.get(level)
            .getTerritoryManager()
            .transferOwnership(
               level,
               territoryId,
               conflict.getOriginFactionId(),
               ConquestType.TRANSFER,
               conflict.getLinkedWarId(),
               null,
               "reintegration"
            );
      }

      if (!ok) {
         return false;
      }

      conflict.addPostModifier(PostConflictModifier.RECENT_REINTEGRATION);
      conflict.recordHistory(
         new ConflictHistoryEntry(
            gameTime,
            ConflictEventType.REINTEGRATION,
            conflictId,
            conflict.getOriginFactionId(),
            conflict.getGovernmentSideId(),
            conflict.getLinkedWarId(),
            null,
            territory.getSettlementId(),
            0,
            territoryId.asString()
         )
      );
      this.markDirty.run();
      return true;
   }

   public boolean mergeRebelFaction(ServerLevel level, ConflictId conflictId) {
      Objects.requireNonNull(level, "level");
      InternalConflict conflict = this.conflicts.get(conflictId);
      if (conflict == null) {
         return false;
      }

      FactionId rebelId = conflict.getRebelFactionId();
      if (rebelId == null || !FactionSavedData.get(level).getFactionManager().exists(rebelId)) {
         return false;
      }

      long gameTime = level.getGameTime();
      this.mergeRebelMilitaryIntoOrigin(level, conflict);
      FactionManager factions = FactionSavedData.get(level).getFactionManager();
      Faction rebel = factions.getFaction(rebelId);
      if (rebel != null) {
         for (VillageId settlementId : new ArrayList<>(rebel.getSettlementIds())) {
            Village village = VillageSavedData.get(level).getVillageManager().getVillage(settlementId);
            if (village != null) {
               factions.assignVillage(village, conflict.getOriginFactionId());
            }
         }
      }

      factions.removeFaction(level, rebelId);
      conflict.setRebelFactionId(null);
      ConflictSide rebelSide = conflict.getPrimaryRebelSide();
      if (rebelSide != null) {
         rebelSide.reassignFaction(conflict.getOriginFactionId());
      }

      conflict.recordHistory(
         new ConflictHistoryEntry(
            gameTime,
            ConflictEventType.FACTION_MERGED,
            conflictId,
            conflict.getOriginFactionId(),
            conflict.getPrimaryRebelSideId(),
            conflict.getLinkedWarId(),
            null,
            null,
            0,
            rebelId.asString()
         )
      );
      this.markDirty.run();
      return true;
   }

   // -------------------------------------------------------------------------
   // Queries
   // -------------------------------------------------------------------------

   @Nullable
   public InternalConflict getConflict(ConflictId conflictId) {
      return conflictId == null ? null : this.conflicts.get(conflictId);
   }

   public Collection<InternalConflict> getActiveConflicts() {
      List<InternalConflict> result = new ArrayList<>();
      for (InternalConflict conflict : this.conflicts.values()) {
         if (conflict.isActive()) {
            result.add(conflict);
         }
      }

      return Collections.unmodifiableList(result);
   }

   public Collection<InternalConflict> getActiveConflicts(FactionId factionId) {
      if (factionId == null) {
         return List.of();
      }

      List<InternalConflict> result = new ArrayList<>();
      for (InternalConflict conflict : this.conflicts.values()) {
         if (!conflict.isActive()) {
            continue;
         }

         if (factionId.equals(conflict.getOriginFactionId()) || factionId.equals(conflict.getRebelFactionId())) {
            result.add(conflict);
            continue;
         }

         for (ConflictSide side : conflict.getSides().values()) {
            if (side.isActive() && factionId.equals(side.getFactionId())) {
               result.add(conflict);
               break;
            }
         }
      }

      return Collections.unmodifiableList(result);
   }

   public Collection<InternalConflict> getHistoricalConflicts() {
      List<InternalConflict> result = new ArrayList<>();
      for (InternalConflict conflict : this.conflicts.values()) {
         if (!conflict.isActive()) {
            result.add(conflict);
         }
      }

      return Collections.unmodifiableList(result);
   }

   public Collection<InternalConflict> getAllConflicts() {
      return Collections.unmodifiableCollection(this.conflicts.values());
   }

   @Nullable
   public Unrest getUnrest(UnrestId unrestId) {
      return unrestId == null ? null : this.unrestRecords.get(unrestId);
   }

   public Collection<Unrest> getActiveUnrest() {
      List<Unrest> result = new ArrayList<>();
      for (Unrest unrest : this.unrestRecords.values()) {
         if (unrest.isActive()) {
            result.add(unrest);
         }
      }

      return Collections.unmodifiableList(result);
   }

   public Collection<Unrest> getActiveUnrest(FactionId factionId) {
      if (factionId == null) {
         return List.of();
      }

      List<Unrest> result = new ArrayList<>();
      for (Unrest unrest : this.unrestRecords.values()) {
         if (unrest.isActive() && factionId.equals(unrest.getFactionId())) {
            result.add(unrest);
         }
      }

      return Collections.unmodifiableList(result);
   }

   @Nullable
   public PostConflictRecovery getRecovery(ConflictId conflictId) {
      return conflictId == null ? null : this.recoveries.get(conflictId);
   }

   @Nullable
   public MilitaryLoyalty getArmyLoyalty(ArmyId armyId) {
      return armyId == null ? null : this.armyLoyalty.get(armyId);
   }

   public Set<FactionId> getRecognitions(ConflictId conflictId) {
      Set<FactionId> set = this.foreignRecognition.get(conflictId);
      return set == null ? Set.of() : Collections.unmodifiableSet(set);
   }

   @Nullable
   public NegotiatedSettlement getSettlement(ConflictId conflictId) {
      return conflictId == null ? null : this.settlements.get(conflictId);
   }

   public long getLastUpdateGameTime() {
      return this.lastUpdateGameTime;
   }

   // -------------------------------------------------------------------------
   // Unrest
   // -------------------------------------------------------------------------

   @Nullable
   public Unrest createUnrest(
      ServerLevel level,
      FactionId factionId,
      UnrestCause cause,
      UnrestScope scope,
      int severity,
      @Nullable PoliticalGroupId groupId,
      @Nullable VillageId settlementId,
      @Nullable TerritoryId territoryId,
      @Nullable String detail
   ) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(factionId, "factionId");
      if (!FactionSavedData.get(level).getFactionManager().exists(factionId)) {
         return null;
      }

      int activeCount = 0;
      for (Unrest unrest : this.unrestRecords.values()) {
         if (unrest.isActive() && factionId.equals(unrest.getFactionId())) {
            activeCount++;
         }
      }

      if (activeCount >= ConflictConfig.MAX_UNREST_PER_FACTION) {
         return null;
      }

      long gameTime = level.getGameTime();
      Unrest unrest = new Unrest(
         UnrestId.random(),
         factionId,
         cause,
         scope,
         severity,
         gameTime
      );
      unrest.setPoliticalGroupId(groupId);
      unrest.setSettlementId(settlementId);
      unrest.setTerritoryId(territoryId);
      unrest.setDetail(detail);
      this.unrestRecords.put(unrest.getId(), unrest);
      PoliticsSavedData.get(level)
         .getPoliticsManager()
         .recordConflictPoliticalEvent(
            level,
            factionId,
            PoliticalEventType.INTERNAL_UNREST,
            groupId,
            unrest.getSeverity(),
            detail == null ? (cause == null ? null : cause.name()) : detail
         );
      this.markDirty.run();
      return unrest;
   }

   public boolean resolveUnrest(ServerLevel level, UnrestId unrestId, @Nullable String detail) {
      Objects.requireNonNull(level, "level");
      Unrest unrest = this.unrestRecords.get(unrestId);
      if (unrest == null || !unrest.isActive()) {
         return false;
      }

      long gameTime = level.getGameTime();
      unrest.resolve(gameTime);
      unrest.setDetail(detail == null ? unrest.getDetail() : detail);
      this.markDirty.run();
      return true;
   }

   public void evaluateEscalation(ServerLevel level, FactionId factionId) {
      Objects.requireNonNull(level, "level");
      if (factionId == null) {
         return;
      }

      long gameTime = level.getGameTime();
      PoliticsManager politics = PoliticsSavedData.get(level).getPoliticsManager();
      FactionPoliticalState state = politics.getState(factionId);
      Government government = politics.getActiveGovernment(factionId);
      if (state == null || government == null) {
         return;
      }

      int maxPressure = 0;
      Unrest hottest = null;
      for (Unrest unrest : this.getActiveUnrest(factionId)) {
         if (unrest.getEscalationPressure() > maxPressure) {
            maxPressure = unrest.getEscalationPressure();
            hottest = unrest;
         }
      }

      int rebellionPressure = state.getRebellionPressure();
      PoliticalGroupId opposition = this.findStrongestOpposition(state, government);
      if (rebellionPressure >= ConflictConfig.CIVIL_WAR_PRESSURE
         && government.getStability() <= 25
         && this.countActiveForFaction(factionId) < ConflictConfig.MAX_ACTIVE_CONFLICTS_PER_FACTION
         && this.canStartIdentical(factionId, ConflictType.CIVIL_WAR, opposition, gameTime)) {
         InternalConflict conflict = this.startCivilWar(level, factionId, opposition, "escalation_civil_war");
         if (conflict != null && hottest != null) {
            conflict.addRelatedUnrest(hottest.getId());
         }

         return;
      }

      if (rebellionPressure >= ConflictConfig.REBELLION_PRESSURE_FORM
         || maxPressure >= ConflictConfig.UNREST_ESCALATE_REBELLION) {
         if (this.countActiveForFaction(factionId) >= ConflictConfig.MAX_ACTIVE_CONFLICTS_PER_FACTION) {
            return;
         }

         ConflictType type = this.inferEscalationType(level, factionId, state, government, hottest);
         if (!this.canStartIdentical(factionId, type, opposition, gameTime)) {
            return;
         }

         InternalConflict conflict = switch (type) {
            case SECESSION -> this.startSecession(
               level,
               factionId,
               opposition,
               hottest == null ? null : hottest.getTerritoryId(),
               hottest == null ? null : hottest.getSettlementId(),
               "escalation_secession"
            );
            case REVOLUTION -> this.startRevolution(level, factionId, opposition, "escalation_revolution");
            case COUP_CONFLICT -> this.startCoupConflict(level, factionId, opposition, "escalation_coup");
            case REGIONAL_UPRISING -> this.startRegionalUprising(
               level,
               factionId,
               opposition,
               hottest == null ? null : hottest.getTerritoryId(),
               hottest == null ? null : hottest.getSettlementId(),
               "escalation_regional"
            );
            case CIVIL_WAR -> this.startCivilWar(level, factionId, opposition, "escalation_civil_war");
            default -> this.startRebellion(level, factionId, opposition, "escalation_rebellion");
         };
         if (conflict != null && hottest != null) {
            conflict.addRelatedUnrest(hottest.getId());
         }
      }
   }

   public DecisionAdvice rebelDecisionEvaluate(ServerLevel level, ConflictId conflictId) {
      Objects.requireNonNull(level, "level");
      InternalConflict conflict = this.conflicts.get(conflictId);
      if (conflict == null || !conflict.isActive()) {
         return DecisionAdvice.WAIT;
      }

      int score = this.calculateConflictScore(level, conflictId);
      ConflictSide rebel = conflict.getPrimaryRebelSide();
      if (rebel == null) {
         return DecisionAdvice.WAIT;
      }

      if (score >= ConflictConfig.CONFLICT_SCORE_VICTORY) {
         return DecisionAdvice.FIGHT;
      }

      if (score <= ConflictConfig.CONFLICT_SCORE_DEFEAT) {
         return DecisionAdvice.NEGOTIATE;
      }

      if (Math.abs(score) <= ConflictConfig.CONFLICT_SCORE_NEGOTIATE && rebel.getExhaustion() >= 60) {
         return DecisionAdvice.NEGOTIATE;
      }

      if (rebel.getSupport() < ConflictConfig.MIN_REBEL_SUPPORT && rebel.getExhaustion() > 40) {
         return DecisionAdvice.SEEK_FOREIGN_SUPPORT;
      }

      if (!conflict.getStage().isArmed() && rebel.getSupport() >= ConflictConfig.MIN_REBEL_SUPPORT) {
         return DecisionAdvice.ESCALATE;
      }

      return DecisionAdvice.HOLD;
   }

   public DecisionAdvice governmentDecisionEvaluate(ServerLevel level, ConflictId conflictId) {
      Objects.requireNonNull(level, "level");
      InternalConflict conflict = this.conflicts.get(conflictId);
      if (conflict == null || !conflict.isActive()) {
         return DecisionAdvice.WAIT;
      }

      int score = this.calculateConflictScore(level, conflictId);
      ConflictSide gov = conflict.getGovernmentSide();
      PoliticsManager politics = PoliticsSavedData.get(level).getPoliticsManager();
      Government government = politics.getActiveGovernment(conflict.getOriginFactionId());
      FactionPoliticalState state = politics.getState(conflict.getOriginFactionId());
      if (gov == null) {
         return DecisionAdvice.WAIT;
      }

      if (score <= ConflictConfig.CONFLICT_SCORE_DEFEAT) {
         return DecisionAdvice.CONCEDE;
      }

      if (score >= ConflictConfig.CONFLICT_SCORE_VICTORY) {
         return DecisionAdvice.REPRESS;
      }

      if (government != null && government.getStability() < 30 && Math.abs(score) <= ConflictConfig.CONFLICT_SCORE_NEGOTIATE) {
         return DecisionAdvice.NEGOTIATE;
      }

      if (state != null && state.getRebellionPressure() >= ConflictConfig.REBELLION_PRESSURE_FORM && gov.getExhaustion() > 50) {
         return DecisionAdvice.CONCEDE;
      }

      if (conflict.getStage().isArmed() && gov.getSupport() >= 55) {
         return DecisionAdvice.FIGHT;
      }

      return DecisionAdvice.HOLD;
   }

   public boolean recognizeRebelClaim(ServerLevel level, FactionId foreignFactionId, ConflictId conflictId) {
      Objects.requireNonNull(level, "level");
      if (foreignFactionId == null || conflictId == null) {
         return false;
      }

      InternalConflict conflict = this.conflicts.get(conflictId);
      if (conflict == null || !conflict.isActive() || conflict.getRebelFactionId() == null) {
         return false;
      }

      if (!FactionSavedData.get(level).getFactionManager().exists(foreignFactionId)) {
         return false;
      }

      if (foreignFactionId.equals(conflict.getOriginFactionId()) || foreignFactionId.equals(conflict.getRebelFactionId())) {
         return false;
      }

      Set<FactionId> recognizers = this.foreignRecognition.computeIfAbsent(conflictId, id -> new LinkedHashSet<>());
      if (!recognizers.add(foreignFactionId)) {
         return false;
      }

      long gameTime = level.getGameTime();
      RelationshipSavedData.get(level)
         .getRelationshipManager()
         .increaseRelationship(
            level,
            foreignFactionId,
            conflict.getRebelFactionId(),
            ConflictConfig.RELATIONSHIP_RECOGNITION,
            RelationshipChangeReason.POLITICS
         );
      RelationshipSavedData.get(level)
         .getRelationshipManager()
         .decreaseRelationship(
            level,
            foreignFactionId,
            conflict.getOriginFactionId(),
            ConflictConfig.RELATIONSHIP_RECOGNITION,
            RelationshipChangeReason.POLITICS
         );
      ConflictSide rebel = conflict.getPrimaryRebelSide();
      if (rebel != null) {
         rebel.setSupport(rebel.getSupport() + 5);
         rebel.setLegitimacy(rebel.getLegitimacy() + 4);
      }

      conflict.recordHistory(
         new ConflictHistoryEntry(
            gameTime,
            ConflictEventType.FOREIGN_RECOGNITION,
            conflictId,
            foreignFactionId,
            conflict.getPrimaryRebelSideId(),
            conflict.getLinkedWarId(),
            null,
            null,
            ConflictConfig.RELATIONSHIP_RECOGNITION,
            conflict.getRebelFactionId().asString()
         )
      );
      this.markDirty.run();
      return true;
   }

   public void onLinkedWarEnded(ServerLevel level, War war, PeaceOutcome outcome) {
      Objects.requireNonNull(level, "level");
      if (war == null) {
         return;
      }

      InternalConflict linked = null;
      for (InternalConflict conflict : this.conflicts.values()) {
         if (conflict.isActive()
            && war.getId().equals(conflict.getLinkedWarId())
            && !this.resolvingConflictIds.contains(conflict.getId())) {
            linked = conflict;
            break;
         }
      }

      if (linked == null) {
         return;
      }

      long gameTime = level.getGameTime();
      PeaceOutcome resolved = outcome == null ? PeaceOutcome.WHITE_PEACE : outcome;
      boolean rebelIsAttacker = linked.getRebelFactionId() != null
         && linked.getRebelFactionId().equals(war.getInitiatorFactionId());
      ConflictResolution resolution;
      if (resolved == PeaceOutcome.ATTACKER_VICTORY || resolved == PeaceOutcome.CAPITULATION_DEFENDER) {
         resolution = rebelIsAttacker ? ConflictResolution.REBEL_VICTORY : ConflictResolution.GOVERNMENT_VICTORY;
      } else if (resolved == PeaceOutcome.DEFENDER_VICTORY || resolved == PeaceOutcome.CAPITULATION_ATTACKER) {
         resolution = rebelIsAttacker ? ConflictResolution.GOVERNMENT_VICTORY : ConflictResolution.REBEL_VICTORY;
      } else if (resolved == PeaceOutcome.EXHAUSTION) {
         resolution = ConflictResolution.NEGOTIATED_SETTLEMENT;
      } else {
         int score = this.calculateConflictScore(level, linked.getId());
         if (score >= ConflictConfig.CONFLICT_SCORE_VICTORY) {
            resolution = ConflictResolution.REBEL_VICTORY;
         } else if (score <= ConflictConfig.CONFLICT_SCORE_DEFEAT) {
            resolution = ConflictResolution.GOVERNMENT_VICTORY;
         } else {
            resolution = ConflictResolution.NEGOTIATED_SETTLEMENT;
         }
      }

      this.applyResolution(level, linked, resolution, gameTime, "linked_war_ended:" + resolved.name());
   }

   public void validateLoadedConsistency(FactionManager factionManager) {
      Objects.requireNonNull(factionManager, "factionManager");
      for (InternalConflict conflict : this.conflicts.values()) {
         if (conflict.getRebelFactionId() != null && !factionManager.exists(conflict.getRebelFactionId())) {
            conflict.setRebelFactionId(null);
         }

         if (!factionManager.exists(conflict.getOriginFactionId()) && conflict.isActive()) {
            conflict.markEnded(this.lastUpdateGameTime);
         }

         for (ConflictSide side : conflict.getSides().values()) {
            if (!factionManager.exists(side.getFactionId())) {
               side.deactivate();
            }
         }
      }

      this.armyLoyalty.entrySet().removeIf(entry -> entry.getKey() == null);
      this.markDirty.run();
   }

   public int purgeInvalid(FactionManager factionManager) {
      Objects.requireNonNull(factionManager, "factionManager");
      int removed = 0;
      List<ConflictId> orphanConflicts = new ArrayList<>();
      for (InternalConflict conflict : this.conflicts.values()) {
         if (!factionManager.exists(conflict.getOriginFactionId()) && !conflict.isActive()) {
            orphanConflicts.add(conflict.getId());
         }
      }

      for (ConflictId id : orphanConflicts) {
         this.conflicts.remove(id);
         this.recoveries.remove(id);
         this.settlements.remove(id);
         this.foreignRecognition.remove(id);
         removed++;
      }

      List<UnrestId> orphanUnrest = new ArrayList<>();
      for (Unrest unrest : this.unrestRecords.values()) {
         if (!factionManager.exists(unrest.getFactionId()) && !unrest.isActive()) {
            orphanUnrest.add(unrest.getId());
         }
      }

      for (UnrestId id : orphanUnrest) {
         this.unrestRecords.remove(id);
         removed++;
      }

      if (removed > 0) {
         this.markDirty.run();
      }

      return removed;
   }

   /**
    * Called when a faction is removed. Ends conflicts that depended on it and clears recognition.
    */
   public void handleFactionRemoved(ServerLevel level, FactionId factionId) {
      Objects.requireNonNull(level, "level");
      if (factionId == null) {
         return;
      }

      long gameTime = level.getGameTime();
      for (InternalConflict conflict : new ArrayList<>(this.conflicts.values())) {
         if (!conflict.isActive()) {
            if (factionId.equals(conflict.getRebelFactionId())) {
               conflict.setRebelFactionId(null);
            }

            Set<FactionId> recognizers = this.foreignRecognition.get(conflict.getId());
            if (recognizers != null) {
               recognizers.remove(factionId);
            }
            continue;
         }

         if (factionId.equals(conflict.getOriginFactionId())) {
            this.applyResolution(level, conflict, ConflictResolution.STALE_RESOLVED, gameTime, "origin_faction_removed");
         } else if (factionId.equals(conflict.getRebelFactionId())) {
            conflict.setRebelFactionId(null);
            ConflictSide rebel = conflict.getPrimaryRebelSide();
            if (rebel != null) {
               rebel.deactivate();
            }

            this.applyResolution(level, conflict, ConflictResolution.GOVERNMENT_VICTORY, gameTime, "rebel_faction_removed");
         }

         Set<FactionId> recognizers = this.foreignRecognition.get(conflict.getId());
         if (recognizers != null) {
            recognizers.remove(factionId);
         }
      }

      for (Unrest unrest : this.unrestRecords.values()) {
         if (factionId.equals(unrest.getFactionId()) && unrest.isActive()) {
            unrest.resolve(gameTime);
         }
      }

      this.markDirty.run();
   }

   public void handleSettlementRemoved(ServerLevel level, VillageId settlementId) {
      Objects.requireNonNull(level, "level");
      if (settlementId == null) {
         return;
      }

      for (InternalConflict conflict : this.conflicts.values()) {
         for (ConflictSide side : conflict.getSides().values()) {
            side.removeSettlement(settlementId);
         }
      }

      for (Unrest unrest : this.unrestRecords.values()) {
         if (settlementId.equals(unrest.getSettlementId()) && unrest.isActive()) {
            unrest.resolve(level.getGameTime());
         }
      }

      this.markDirty.run();
   }

   // -------------------------------------------------------------------------
   // SavedData / load helpers (package-visible)
   // -------------------------------------------------------------------------

   Map<ConflictId, InternalConflict> getConflictsMap() {
      return this.conflicts;
   }

   Map<UnrestId, Unrest> getUnrestRecordsMap() {
      return this.unrestRecords;
   }

   Map<ConflictId, PostConflictRecovery> getRecoveriesMap() {
      return this.recoveries;
   }

   Map<ConflictId, NegotiatedSettlement> getSettlementsMap() {
      return this.settlements;
   }

   Map<ArmyId, MilitaryLoyalty> getArmyLoyaltyMap() {
      return this.armyLoyalty;
   }

   Map<ConflictId, Set<FactionId>> getForeignRecognitionMap() {
      return this.foreignRecognition;
   }

   Map<String, Long> getLastIdenticalConflictEndMap() {
      return this.lastIdenticalConflictEnd;
   }

   void setLastUpdateGameTime(long gameTime) {
      this.lastUpdateGameTime = Math.max(0L, gameTime);
   }

   void putConflictForLoad(InternalConflict conflict) {
      if (conflict != null) {
         this.conflicts.put(conflict.getId(), conflict);
      }
   }

   void putUnrestForLoad(Unrest unrest) {
      if (unrest != null) {
         this.unrestRecords.put(unrest.getId(), unrest);
      }
   }

   void putRecoveryForLoad(PostConflictRecovery recovery) {
      if (recovery != null) {
         this.recoveries.put(recovery.getConflictId(), recovery);
      }
   }

   void putSettlementForLoad(NegotiatedSettlement settlement) {
      if (settlement != null) {
         this.settlements.put(settlement.getConflictId(), settlement);
      }
   }

   void putArmyLoyaltyForLoad(ArmyId armyId, MilitaryLoyalty loyalty) {
      if (armyId != null && loyalty != null) {
         this.armyLoyalty.put(armyId, loyalty);
      }
   }

   void putForeignRecognitionForLoad(ConflictId conflictId, Set<FactionId> recognizers) {
      if (conflictId == null) {
         return;
      }

      Set<FactionId> set = new LinkedHashSet<>();
      if (recognizers != null) {
         set.addAll(recognizers);
      }

      this.foreignRecognition.put(conflictId, set);
   }

   void putIdenticalCooldownForLoad(String key, long gameTime) {
      if (key != null && !key.isBlank()) {
         this.lastIdenticalConflictEnd.put(key, Math.max(0L, gameTime));
      }
   }

   // -------------------------------------------------------------------------
   // Tick stages
   // -------------------------------------------------------------------------

   private void detectUnrest(ServerLevel level, long gameTime) {
      PoliticsManager politics = PoliticsSavedData.get(level).getPoliticsManager();
      for (FactionPoliticalState state : politics.getAllStates()) {
         FactionId factionId = state.getFactionId();
         Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(factionId);
         if (faction == null || faction.getType() == FactionType.BREAKAWAY) {
            continue;
         }

         Government government = politics.getActiveGovernment(factionId);
         if (government == null) {
            continue;
         }

         if (this.countActiveUnrest(factionId) >= ConflictConfig.MAX_UNREST_PER_FACTION) {
            continue;
         }

         if (government.getStability() < 35) {
            this.ensureCauseUnrest(
               level,
               factionId,
               UnrestCause.LOW_STABILITY,
               UnrestScope.FACTION,
               100 - government.getStability(),
               null,
               null,
               null,
               "low_stability",
               gameTime
            );
         }

         if (government.getLegitimacy() < 35) {
            this.ensureCauseUnrest(
               level,
               factionId,
               UnrestCause.LOW_LEGITIMACY,
               UnrestScope.FACTION,
               100 - government.getLegitimacy(),
               null,
               null,
               null,
               "low_legitimacy",
               gameTime
            );
         }

         if (state.getPoliticalPressure() >= 70) {
            this.ensureCauseUnrest(
               level,
               factionId,
               UnrestCause.OPPRESSION,
               UnrestScope.FACTION,
               state.getPoliticalPressure(),
               null,
               null,
               null,
               "political_pressure",
               gameTime
            );
         }

         if (state.getPolarization() >= 70) {
            this.ensureCauseUnrest(
               level,
               factionId,
               UnrestCause.HIGH_POLARIZATION,
               UnrestScope.FACTION,
               state.getPolarization(),
               null,
               null,
               null,
               "polarization",
               gameTime
            );
         }

         if (state.getRebellionPressure() >= 60) {
            PoliticalGroupId opposition = this.findStrongestOpposition(state, government);
            this.ensureCauseUnrest(
               level,
               factionId,
               UnrestCause.POLITICAL_EXCLUSION,
               opposition == null ? UnrestScope.FACTION : UnrestScope.POLITICAL_GROUP,
               state.getRebellionPressure(),
               opposition,
               null,
               null,
               "rebellion_pressure",
               gameTime
            );
         }

         for (Territory territory : TerritoryQueries.ownedBy(level, factionId)) {
            if (!territory.isOccupied()) {
               continue;
            }

            this.ensureCauseUnrest(
               level,
               factionId,
               UnrestCause.OCCUPATION,
               UnrestScope.TERRITORY,
               Math.max(40, 100 - territory.getStability()),
               null,
               territory.getSettlementId(),
               territory.getId(),
               "occupation",
               gameTime
            );
         }

         for (Map.Entry<VillageId, LocalAdministrator> entry : state.getLocalAdministrators().entrySet()) {
            LocalAdministrator admin = entry.getValue();
            if (!admin.isActive()) {
               continue;
            }

            if (admin.getLoyalty() < 30) {
               Territory territory = TerritoryQueries.forSettlement(level, entry.getKey());
               this.ensureCauseUnrest(
                  level,
                  factionId,
                  UnrestCause.RESISTANCE,
                  UnrestScope.SETTLEMENT,
                  100 - admin.getLoyalty(),
                  null,
                  entry.getKey(),
                  territory == null ? null : territory.getId(),
                  "low_loyalty_admin",
                  gameTime
               );
            }
         }
      }
   }

   private void updateUnrestEscalation(ServerLevel level, long gameTime) {
      PoliticsManager politics = PoliticsSavedData.get(level).getPoliticsManager();
      for (Unrest unrest : this.unrestRecords.values()) {
         if (!unrest.isActive()) {
            continue;
         }

         FactionPoliticalState state = politics.getState(unrest.getFactionId());
         Government government = politics.getActiveGovernment(unrest.getFactionId());
         int pressure = unrest.getEscalationPressure();
         pressure += Math.max(0, unrest.getSeverity() / 20);
         if (state != null) {
            pressure += state.getRebellionPressure() / 25;
            pressure += state.getPolarization() / 30;
         }

         if (government != null) {
            if (government.getStability() < 40) {
               pressure += 2;
            }

            if (government.getLegitimacy() < 40) {
               pressure += 1;
            }
         }

         if (this.hasActiveConflictForFaction(unrest.getFactionId())) {
            pressure += 1;
         } else if (government != null && government.getStability() > 65 && state != null && state.getRebellionPressure() < 40) {
            pressure -= 2;
         }

         int before = unrest.getEscalationPressure();
         unrest.setEscalationPressure(pressure, gameTime);
         if (unrest.getEscalationPressure() > before + 5) {
            // mild severity drift with pressure
            unrest.setSeverity(Math.min(100, unrest.getSeverity() + 1), gameTime);
         } else if (unrest.getEscalationPressure() < before - 5) {
            unrest.setSeverity(Math.max(0, unrest.getSeverity() - 1), gameTime);
         }

         if (unrest.getSeverity() <= 10 && unrest.getEscalationPressure() <= 15
            && government != null && government.getStability() >= 60) {
            unrest.resolve(gameTime);
         }
      }
   }

   private void checkEscalationThresholds(ServerLevel level, long gameTime) {
      Set<FactionId> seen = new HashSet<>();
      for (Unrest unrest : this.unrestRecords.values()) {
         if (!unrest.isActive() || !seen.add(unrest.getFactionId())) {
            continue;
         }

         if (unrest.getEscalationPressure() >= ConflictConfig.UNREST_ESCALATE_UPRISING
            || PoliticsSavedData.get(level).getPoliticsManager().getState(unrest.getFactionId()) != null
               && PoliticsSavedData.get(level).getPoliticsManager().getState(unrest.getFactionId()).getRebellionPressure()
                  >= ConflictConfig.REBELLION_PRESSURE_FORM) {
            this.evaluateEscalation(level, unrest.getFactionId());
         }
      }

      // Also escalate existing non-armed conflicts by stage.
      for (InternalConflict conflict : new ArrayList<>(this.conflicts.values())) {
         if (!conflict.isActive() || conflict.getStage().isArmed()) {
            continue;
         }

         int pressure = this.maxRelatedUnrestPressure(conflict);
         FactionPoliticalState state = PoliticsSavedData.get(level).getPoliticsManager().getState(conflict.getOriginFactionId());
         if (state != null) {
            pressure = Math.max(pressure, state.getRebellionPressure());
         }

         ConflictStage next = conflict.getStage().escalateToward(conflict.getType());
         boolean shouldEscalate = switch (conflict.getStage()) {
            case POLITICAL_TENSION -> pressure >= ConflictConfig.UNREST_ESCALATE_LOCAL;
            case UNREST -> pressure >= ConflictConfig.UNREST_ESCALATE_UPRISING;
            case LOCAL_UPRISING -> pressure >= ConflictConfig.UNREST_ESCALATE_REBELLION;
            default -> false;
         };
         if (!shouldEscalate || next == conflict.getStage()) {
            continue;
         }

         conflict.setStage(next, gameTime);
         conflict.recordHistory(
            new ConflictHistoryEntry(
               gameTime,
               ConflictEventType.STAGE_CHANGED,
               conflict.getId(),
               conflict.getOriginFactionId(),
               conflict.getPrimaryRebelSideId(),
               conflict.getLinkedWarId(),
               null,
               null,
               0,
               next.name()
            )
         );
         if (next.isArmed()) {
            this.armConflict(level, conflict, gameTime);
         }
      }
   }

   private void updateActiveConflicts(ServerLevel level, long gameTime) {
      for (InternalConflict conflict : this.conflicts.values()) {
         if (!conflict.isActive()) {
            continue;
         }

         this.refreshSideMetrics(level, conflict);
         this.calculateConflictScore(level, conflict.getId());
         ConflictSide gov = conflict.getGovernmentSide();
         ConflictSide rebel = conflict.getPrimaryRebelSide();
         if (gov != null) {
            gov.setExhaustion(Math.min(100, gov.getExhaustion() + 1));
         }

         if (rebel != null) {
            rebel.setExhaustion(Math.min(100, rebel.getExhaustion() + 1));
         }

         // Drift stage toward type peak once armed and war is linked.
         if (conflict.getStage().isArmed()
            && conflict.getLinkedWarId() != null
            && conflict.getStage() != ConflictStage.CIVIL_WAR
            && conflict.getStage() != ConflictStage.REVOLUTION
            && conflict.getStage() != ConflictStage.SECESSION) {
            ConflictStage typed = switch (conflict.getType()) {
               case CIVIL_WAR -> ConflictStage.CIVIL_WAR;
               case REVOLUTION -> ConflictStage.REVOLUTION;
               case SECESSION -> ConflictStage.SECESSION;
               default -> ConflictStage.INTERNAL_WAR;
            };
            if (typed != conflict.getStage()
               && (conflict.getStage() == ConflictStage.ORGANIZED_REBELLION || conflict.getStage() == ConflictStage.INTERNAL_WAR)) {
               ConflictStage next = conflict.getStage() == ConflictStage.ORGANIZED_REBELLION
                  ? ConflictStage.INTERNAL_WAR
                  : typed;
               conflict.setStage(next, gameTime);
               conflict.recordHistory(
                  new ConflictHistoryEntry(
                     gameTime,
                     ConflictEventType.STAGE_CHANGED,
                     conflict.getId(),
                     conflict.getOriginFactionId(),
                     conflict.getPrimaryRebelSideId(),
                     conflict.getLinkedWarId(),
                     null,
                     null,
                     0,
                     next.name()
                  )
               );
            }
         }
      }
   }

   private void updateMilitaryLoyalty(ServerLevel level, long gameTime) {
      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      for (InternalConflict conflict : this.conflicts.values()) {
         if (!conflict.isActive()) {
            continue;
         }

         ConflictSide gov = conflict.getGovernmentSide();
         ConflictSide rebel = conflict.getPrimaryRebelSide();
         if (gov == null || rebel == null) {
            continue;
         }

         for (Army army : military.getArmies(conflict.getOriginFactionId())) {
            if (army.getState() == ArmyState.DISBANDED) {
               continue;
            }

            MilitaryLoyalty loyalty = this.armyLoyalty.getOrDefault(army.getId(), MilitaryLoyalty.LOYAL_TO_GOVERNMENT);
            int rebelPull = rebel.getSupport() + rebel.getLegitimacy() / 2;
            int govPull = gov.getSupport() + gov.getLegitimacy() / 2;
            if (rebelPull > govPull + 25 && loyalty == MilitaryLoyalty.LOYAL_TO_GOVERNMENT) {
               loyalty = MilitaryLoyalty.NEUTRAL;
            } else if (rebelPull > govPull + 40 && (loyalty == MilitaryLoyalty.NEUTRAL || loyalty == MilitaryLoyalty.SPLIT)) {
               loyalty = MilitaryLoyalty.SUPPORTS_REBELS;
               if (conflict.getRebelFactionId() != null && conflict.getStage().isArmed()) {
                  if (military.transferArmyAllegiance(level, army.getId(), conflict.getRebelFactionId())) {
                     rebel.addArmy(army.getId());
                     gov.removeArmy(army.getId());
                     conflict.recordHistory(
                        new ConflictHistoryEntry(
                           gameTime,
                           ConflictEventType.ARMY_DEFECTED,
                           conflict.getId(),
                           conflict.getRebelFactionId(),
                           rebel.getId(),
                           conflict.getLinkedWarId(),
                           null,
                           null,
                           0,
                           army.getId().asString()
                        )
                     );
                  }
               }
            } else if (govPull > rebelPull + 20 && loyalty == MilitaryLoyalty.SUPPORTS_REBELS) {
               loyalty = MilitaryLoyalty.NEUTRAL;
            } else if (govPull > rebelPull + 35 && loyalty == MilitaryLoyalty.NEUTRAL) {
               loyalty = MilitaryLoyalty.LOYAL_TO_GOVERNMENT;
            }

            this.armyLoyalty.put(army.getId(), loyalty);
         }

         if (conflict.getRebelFactionId() != null) {
            for (Army army : military.getArmies(conflict.getRebelFactionId())) {
               this.armyLoyalty.putIfAbsent(army.getId(), MilitaryLoyalty.SUPPORTS_REBELS);
            }
         }
      }
   }

   private void applyEconomicDisruption(ServerLevel level, long gameTime) {
      TradeManager trade = EconomySavedData.get(level).getTradeManager();
      for (InternalConflict conflict : this.conflicts.values()) {
         if (!conflict.isActive() || !conflict.getStage().isArmed() || conflict.getRebelFactionId() == null) {
            continue;
         }

         FactionId origin = conflict.getOriginFactionId();
         FactionId rebel = conflict.getRebelFactionId();
         trade.setRouteDisrupted(origin, rebel, TradeDisruptionReason.INTERNAL_CONFLICT, gameTime);
         trade.setRouteDisrupted(rebel, origin, TradeDisruptionReason.INTERNAL_CONFLICT, gameTime);

         ConflictSide rebelSide = conflict.getPrimaryRebelSide();
         if (rebelSide == null) {
            continue;
         }

         for (VillageId settlementId : rebelSide.getControlledSettlementIds()) {
            Village village = VillageSavedData.get(level).getVillageManager().getVillage(settlementId);
            if (village == null || village.getFactionId() == null) {
               continue;
            }

            FactionId controller = village.getFactionId();
            if (!controller.equals(origin)) {
               continue;
            }

            for (TradeRoute route : trade.getRoutesInvolving(origin)) {
               if (route.getSourceFactionId().equals(origin) || route.getTargetFactionId().equals(origin)) {
                  trade.setRouteDisrupted(
                     route.getSourceFactionId(),
                     route.getTargetFactionId(),
                     TradeDisruptionReason.INTERNAL_CONFLICT,
                     gameTime
                  );
               }
            }
         }
      }
   }

   private void checkResolutionConditions(ServerLevel level, long gameTime) {
      for (InternalConflict conflict : new ArrayList<>(this.conflicts.values())) {
         if (!conflict.isActive() || conflict.isNegotiating()) {
            continue;
         }

         int score = this.calculateConflictScore(level, conflict.getId());
         ConflictSide gov = conflict.getGovernmentSide();
         ConflictSide rebel = conflict.getPrimaryRebelSide();
         if (gov == null || rebel == null) {
            continue;
         }

         if (score >= ConflictConfig.CONFLICT_SCORE_VICTORY) {
            ConflictResolution resolution = this.rebelVictoryResolution(conflict, rebel);
            this.applyResolution(level, conflict, resolution, gameTime, "score_rebel_victory");
            continue;
         }

         if (score <= ConflictConfig.CONFLICT_SCORE_DEFEAT) {
            this.applyResolution(level, conflict, ConflictResolution.GOVERNMENT_VICTORY, gameTime, "score_government_victory");
            continue;
         }

         if (gov.getExhaustion() >= 90 && rebel.getExhaustion() >= 90) {
            this.applyResolution(level, conflict, ConflictResolution.NEGOTIATED_SETTLEMENT, gameTime, "mutual_exhaustion");
            continue;
         }

         if (rebel.getPrimaryObjective().requiresFactionSplit()
            && rebel.getControlledTerritoryIds().size() >= Math.max(1, TerritoryQueries.ownedBy(level, conflict.getOriginFactionId()).size() / 3)
            && score >= 40) {
            this.applyResolution(level, conflict, ConflictResolution.SECESSION, gameTime, "secession_objective");
         }
      }
   }

   private void applyRecoveryDecay(ServerLevel level, long gameTime) {
      PoliticsManager politics = PoliticsSavedData.get(level).getPoliticsManager();
      for (PostConflictRecovery recovery : new ArrayList<>(this.recoveries.values())) {
         if (!recovery.isActive()) {
            continue;
         }

         if (recovery.isComplete(gameTime)) {
            recovery.complete();
            InternalConflict conflict = this.conflicts.get(recovery.getConflictId());
            if (conflict != null) {
               conflict.markEnded(gameTime);
               conflict.recordHistory(
                  new ConflictHistoryEntry(
                     gameTime,
                     ConflictEventType.RECOVERY_COMPLETED,
                     conflict.getId(),
                     recovery.getFactionId(),
                     null,
                     null,
                     null,
                     null,
                     0,
                     null
                  )
               );
            }

            continue;
         }

         recovery.tickDecay();
         if (recovery.getRemainingStabilityPenalty() > 0 || recovery.getRemainingPressureBonus() > 0) {
            politics.applyPostConflictModifiers(
               level,
               recovery.getFactionId(),
               Math.min(2, recovery.getRemainingStabilityPenalty()),
               Math.min(2, recovery.getRemainingPressureBonus()),
               "recovery_tick"
            );
         }
      }
   }

   private void protectStaleConflicts(ServerLevel level, long gameTime) {
      for (InternalConflict conflict : new ArrayList<>(this.conflicts.values())) {
         if (!conflict.isActive()) {
            continue;
         }

         long idle = gameTime - conflict.getLastUpdatedGameTime();
         if (idle < ConflictConfig.STALE_CONFLICT_TICKS) {
            continue;
         }

         ConflictSide gov = conflict.getGovernmentSide();
         ConflictSide rebel = conflict.getPrimaryRebelSide();
         boolean inactiveScores = gov != null
            && rebel != null
            && Math.abs(gov.getConflictScore()) < 15
            && Math.abs(rebel.getConflictScore()) < 15;
         boolean noWar = conflict.getLinkedWarId() == null
            || WarSavedData.get(level).getWarManager().getWar(conflict.getLinkedWarId()) == null
            || !WarSavedData.get(level).getWarManager().getWar(conflict.getLinkedWarId()).getState().isOngoing();
         if (inactiveScores && noWar) {
            conflict.recordHistory(
               new ConflictHistoryEntry(
                  gameTime,
                  ConflictEventType.STALE_RESOLVED,
                  conflict.getId(),
                  conflict.getOriginFactionId(),
                  conflict.getPrimaryRebelSideId(),
                  conflict.getLinkedWarId(),
                  null,
                  null,
                  0,
                  "stale"
               )
            );
            this.applyResolution(level, conflict, ConflictResolution.STALE_RESOLVED, gameTime, "stale_protection");
         }
      }
   }

   // -------------------------------------------------------------------------
   // Atomic formation / arming / resolution
   // -------------------------------------------------------------------------

   @Nullable
   private InternalConflict beginConflict(
      ServerLevel level,
      FactionId originFactionId,
      ConflictType type,
      RebelObjective objective,
      ConflictStage initialStage,
      @Nullable PoliticalGroupId rebelGroupId,
      @Nullable TerritoryId focusTerritoryId,
      @Nullable VillageId focusSettlementId,
      @Nullable String causeDetail
   ) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(originFactionId, "originFactionId");
      Objects.requireNonNull(type, "type");

      FactionManager factions = FactionSavedData.get(level).getFactionManager();
      if (!factions.exists(originFactionId)) {
         LOGGER.warn("[Millenaire] Cannot start {} — unknown faction {}", type, originFactionId);
         return null;
      }

      Faction origin = factions.getFaction(originFactionId);
      if (origin != null && origin.getType() == FactionType.BREAKAWAY) {
         LOGGER.warn("[Millenaire] Refusing internal conflict on breakaway faction {}", originFactionId);
         return null;
      }

      long gameTime = level.getGameTime();
      if (!this.canStartIdentical(originFactionId, type, rebelGroupId, gameTime)) {
         LOGGER.info("[Millenaire] Duplicate/cooldown blocked {} for {}", type, originFactionId);
         return null;
      }

      if (this.countActiveForFaction(originFactionId) >= ConflictConfig.MAX_ACTIVE_CONFLICTS_PER_FACTION) {
         LOGGER.info("[Millenaire] Max active conflicts reached for {}", originFactionId);
         return null;
      }

      PoliticsManager politics = PoliticsSavedData.get(level).getPoliticsManager();
      FactionPoliticalState state = politics.ensurePolitics(level, originFactionId);
      Government government = politics.getActiveGovernment(originFactionId);
      if (government == null) {
         LOGGER.warn("[Millenaire] Cannot start conflict — no government for {}", originFactionId);
         return null;
      }

      PoliticalGroup rebelGroup = null;
      if (rebelGroupId != null) {
         rebelGroup = state.getGroups().get(rebelGroupId);
      }

      if (rebelGroup == null) {
         PoliticalGroupId oppositionId = this.findStrongestOpposition(state, government);
         if (oppositionId != null) {
            rebelGroupId = oppositionId;
            rebelGroup = state.getGroups().get(oppositionId);
         }
      }

      if (type == ConflictType.CIVIL_WAR && !this.meetsCivilWarPreconditions(level, state, government, rebelGroup)) {
         LOGGER.info("[Millenaire] Civil war preconditions unmet for {}", originFactionId);
         return null;
      }

      if (type == ConflictType.SECESSION && !this.meetsSecessionPreconditions(level, government, focusTerritoryId, focusSettlementId)) {
         LOGGER.info("[Millenaire] Secession preconditions unmet for {}", originFactionId);
         return null;
      }

      // ATOMIC create: conflict + sides + objectives + history + politics marker
      ConflictId conflictId = ConflictId.random();
      InternalConflict conflict = new InternalConflict(conflictId, originFactionId, type, gameTime);
      conflict.setCauseDetail(causeDetail);
      ConflictStage stage = initialStage == null ? ConflictStage.POLITICAL_TENSION : initialStage;
      conflict.setStage(stage, gameTime);

      ConflictSide govSide = new ConflictSide(ConflictSideId.random(), conflictId, originFactionId, true);
      govSide.setDisplayName(origin == null ? originFactionId.asString() : origin.getDisplayName());
      govSide.setPrimaryObjective(RebelObjective.LIBERATION);
      PoliticalLeader leader = politics.getLeader(originFactionId);
      if (leader != null) {
         govSide.setLeaderId(leader.getId());
      }

      govSide.setLegitimacy(government.getLegitimacy());
      govSide.setSupport(Math.max(30, government.getStability()));
      for (Territory territory : TerritoryQueries.ownedBy(level, originFactionId)) {
         if (!territory.isOccupied()) {
            govSide.addTerritory(territory.getId());
            if (territory.getSettlementId() != null) {
               govSide.addSettlement(territory.getSettlementId());
            }
         }
      }

      for (Army army : MilitarySavedData.get(level).getMilitaryManager().getArmies(originFactionId)) {
         if (army.getState() != ArmyState.DISBANDED) {
            govSide.addArmy(army.getId());
            this.armyLoyalty.put(army.getId(), MilitaryLoyalty.LOYAL_TO_GOVERNMENT);
         }
      }

      ConflictSide rebelSide = new ConflictSide(ConflictSideId.random(), conflictId, originFactionId, false);
      rebelSide.setPrimaryObjective(objective);
      rebelSide.setDisplayName(rebelGroup == null ? "Rebels" : rebelGroup.getType().name() + " Opposition");
      if (rebelGroup != null) {
         rebelSide.addSupporter(rebelGroup.getId());
         rebelSide.setSupport(Math.max(ConflictConfig.MIN_REBEL_SUPPORT, rebelGroup.getPoliticalPower()));
         rebelSide.setLegitimacy(Math.max(20, 100 - government.getLegitimacy()));
      } else {
         rebelSide.setSupport(ConflictConfig.MIN_REBEL_SUPPORT);
         rebelSide.setLegitimacy(25);
      }

      if (focusTerritoryId != null) {
         rebelSide.addTerritory(focusTerritoryId);
      }

      if (focusSettlementId != null) {
         rebelSide.addSettlement(focusSettlementId);
      }

      conflict.putSide(govSide);
      conflict.putSide(rebelSide);
      conflict.recordHistory(
         new ConflictHistoryEntry(
            gameTime,
            ConflictEventType.CONFLICT_STARTED,
            conflictId,
            originFactionId,
            rebelSide.getId(),
            null,
            null,
            focusSettlementId,
            0,
            type.name()
         )
      );
      conflict.recordHistory(
         new ConflictHistoryEntry(
            gameTime,
            ConflictEventType.OBJECTIVE_SET,
            conflictId,
            originFactionId,
            rebelSide.getId(),
            null,
            null,
            null,
            0,
            objective.name()
         )
      );

      this.conflicts.put(conflictId, conflict);
      politics.markInternalConflict(originFactionId);
      politics.recordConflictPoliticalEvent(
         level,
         originFactionId,
         PoliticalEventType.INTERNAL_CONFLICT_STARTED,
         rebelGroupId,
         state.getRebellionPressure(),
         type.name()
      );

      this.applyDiplomaticReaction(level, conflict);

      if (stage.isArmed()) {
         this.armConflict(level, conflict, gameTime);
      }

      this.markDirty.run();
      LOGGER.info(
         "[Millenaire] Internal conflict {} ({}) started in faction {} stage={}",
         conflictId,
         type,
         originFactionId,
         conflict.getStage()
      );
      return conflict;
   }

   private void armConflict(ServerLevel level, InternalConflict conflict, long gameTime) {
      if (conflict.getRebelFactionId() != null && conflict.getLinkedWarId() != null) {
         return;
      }

      FactionManager factions = FactionSavedData.get(level).getFactionManager();
      Faction origin = factions.getFaction(conflict.getOriginFactionId());
      if (origin == null) {
         return;
      }

      FactionId rebelId = conflict.getRebelFactionId();
      if (rebelId == null || !factions.exists(rebelId)) {
         rebelId = FactionIds.breakawayFaction(UUID.randomUUID());
         String name = origin.getDisplayName() + " Rebels";
         Faction created = factions.createFaction(rebelId, name, FactionType.BREAKAWAY, origin.getCultureId(), gameTime);
         if (created == null) {
            LOGGER.error("[Millenaire] Failed to create breakaway rebel faction for conflict {}", conflict.getId());
            return;
         }

         conflict.setRebelFactionId(rebelId);
      }

      PoliticsManager politics = PoliticsSavedData.get(level).getPoliticsManager();
      politics.ensurePolitics(level, rebelId);
      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      military.getOrCreateOrganization(rebelId);

      ConflictSide rebelSide = conflict.getPrimaryRebelSide();
      if (rebelSide != null) {
         rebelSide.reassignFaction(rebelId);
         Faction rebelFaction = factions.getFaction(rebelId);
         if (rebelFaction != null) {
            rebelSide.setDisplayName(rebelFaction.getDisplayName());
         }
      }

      this.splitMilitaryToRebels(level, conflict, gameTime);
      this.seedRebelMilitia(level, conflict, gameTime);

      WarGoalType goalType = conflict.getType() == ConflictType.SECESSION || conflict.getType().isLocalized()
         ? WarGoalType.CONQUER_SETTLEMENT
         : WarGoalType.MILITARY_SUPERIORITY;
      War war = WarSavedData.get(level).getWarManager().declareWar(level, rebelId, conflict.getOriginFactionId(), goalType);
      if (war != null) {
         conflict.setLinkedWarId(war.getId());
         conflict.recordHistory(
            new ConflictHistoryEntry(
               gameTime,
               ConflictEventType.WAR_LINKED,
               conflict.getId(),
               rebelId,
               conflict.getPrimaryRebelSideId(),
               war.getId(),
               null,
               null,
               0,
               goalType.name()
            )
         );
      } else {
         LOGGER.warn("[Millenaire] Failed to declare linked war for conflict {}", conflict.getId());
      }

      TradeManager trade = EconomySavedData.get(level).getTradeManager();
      trade.setRouteDisrupted(rebelId, conflict.getOriginFactionId(), TradeDisruptionReason.INTERNAL_CONFLICT, gameTime);
      trade.setRouteDisrupted(conflict.getOriginFactionId(), rebelId, TradeDisruptionReason.INTERNAL_CONFLICT, gameTime);

      int relationHit = switch (conflict.getType()) {
         case CIVIL_WAR -> ConflictConfig.RELATIONSHIP_CIVIL_WAR;
         case REVOLUTION -> ConflictConfig.RELATIONSHIP_REVOLUTION;
         case SECESSION -> ConflictConfig.RELATIONSHIP_SECESSION;
         default -> ConflictConfig.RELATIONSHIP_REVOLUTION / 2;
      };
      RelationshipSavedData.get(level)
         .getRelationshipManager()
         .decreaseRelationship(level, rebelId, conflict.getOriginFactionId(), Math.abs(relationHit), RelationshipChangeReason.WAR);
      RelationshipSavedData.get(level)
         .getRelationshipManager()
         .decreaseRelationship(level, conflict.getOriginFactionId(), rebelId, Math.abs(relationHit), RelationshipChangeReason.WAR);

      this.applyDiplomaticReaction(level, conflict);

      if (conflict.getStage() == ConflictStage.ORGANIZED_REBELLION
         || conflict.getStage() == ConflictStage.LOCAL_UPRISING) {
         ConflictStage armed = ConflictStage.INTERNAL_WAR;
         conflict.setStage(armed, gameTime);
         conflict.recordHistory(
            new ConflictHistoryEntry(
               gameTime,
               ConflictEventType.STAGE_CHANGED,
               conflict.getId(),
               conflict.getOriginFactionId(),
               conflict.getPrimaryRebelSideId(),
               conflict.getLinkedWarId(),
               null,
               null,
               0,
               armed.name()
            )
         );
      }

      conflict.recordHistory(
         new ConflictHistoryEntry(
            gameTime,
            ConflictEventType.MILITARY_SPLIT,
            conflict.getId(),
            rebelId,
            conflict.getPrimaryRebelSideId(),
            conflict.getLinkedWarId(),
            null,
            null,
            rebelSide == null ? 0 : rebelSide.getArmyIds().size(),
            "armed"
         )
      );
   }

   private void splitMilitaryToRebels(ServerLevel level, InternalConflict conflict, long gameTime) {
      FactionId rebelId = conflict.getRebelFactionId();
      ConflictSide rebelSide = conflict.getPrimaryRebelSide();
      ConflictSide govSide = conflict.getGovernmentSide();
      if (rebelId == null || rebelSide == null || govSide == null) {
         return;
      }

      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      List<Army> originArmies = new ArrayList<>(military.getArmies(conflict.getOriginFactionId()));
      if (originArmies.isEmpty()) {
         return;
      }

      int transferCount = Math.max(1, (int) Math.round(originArmies.size() * ConflictConfig.MILITARY_SPLIT_OPPOSITION_RATIO));
      // Prefer armies with lower implied loyalty / later in list for determinism by id string.
      originArmies.sort((a, b) -> a.getId().asString().compareTo(b.getId().asString()));
      int transferred = 0;
      for (Army army : originArmies) {
         if (transferred >= transferCount) {
            break;
         }

         if (army.getState() == ArmyState.DISBANDED) {
            continue;
         }

         if (military.transferArmyAllegiance(level, army.getId(), rebelId)) {
            govSide.removeArmy(army.getId());
            rebelSide.addArmy(army.getId());
            this.armyLoyalty.put(army.getId(), MilitaryLoyalty.SUPPORTS_REBELS);
            transferred++;
            conflict.recordHistory(
               new ConflictHistoryEntry(
                  gameTime,
                  ConflictEventType.ARMY_DEFECTED,
                  conflict.getId(),
                  rebelId,
                  rebelSide.getId(),
                  conflict.getLinkedWarId(),
                  null,
                  null,
                  0,
                  army.getId().asString()
               )
            );
         }
      }
   }

   private void seedRebelMilitia(ServerLevel level, InternalConflict conflict, long gameTime) {
      FactionId rebelId = conflict.getRebelFactionId();
      ConflictSide rebelSide = conflict.getPrimaryRebelSide();
      if (rebelId == null || rebelSide == null) {
         return;
      }

      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      if (!military.getArmies(rebelId).isEmpty()) {
         return;
      }

      Faction rebel = FactionSavedData.get(level).getFactionManager().getFaction(rebelId);
      ResourceLocation cultureId = rebel == null ? null : rebel.getCultureId();
      VillageId originSettlement = null;
      for (VillageId settlementId : rebelSide.getControlledSettlementIds()) {
         originSettlement = settlementId;
         break;
      }

      Army army = military.createArmy(level, rebelId, "Rebel Host", originSettlement);
      if (army == null) {
         return;
      }

      rebelSide.addArmy(army.getId());
      this.armyLoyalty.put(army.getId(), MilitaryLoyalty.SUPPORTS_REBELS);
      UnitTypeDefinition militia = UnitTypeRegistry.resolveDefault(cultureId, UnitCategory.MILITIA);
      if (militia == null) {
         militia = UnitTypeRegistry.defaultInfantry(cultureId);
      }

      MilitaryUnit unit = military.recruit(
         level,
         rebelId,
         militia.id(),
         ConflictConfig.REBEL_RECRUIT_SOLDIERS,
         originSettlement
      );
      if (unit != null) {
         military.assignUnitToArmy(level, unit.getId(), army.getId());
      }
   }

   private boolean applyResolution(
      ServerLevel level,
      InternalConflict conflict,
      ConflictResolution resolution,
      long gameTime,
      @Nullable String reason
   ) {
      if (conflict == null || !conflict.isActive() || this.resolvingConflictIds.contains(conflict.getId())) {
         return false;
      }

      this.resolvingConflictIds.add(conflict.getId());
      try {
         return this.applyResolutionLocked(level, conflict, resolution, gameTime, reason);
      } finally {
         this.resolvingConflictIds.remove(conflict.getId());
      }
   }

   private boolean applyResolutionLocked(
      ServerLevel level,
      InternalConflict conflict,
      ConflictResolution resolution,
      long gameTime,
      @Nullable String reason
   ) {
      ConflictResolution resolved = resolution == null ? ConflictResolution.STALE_RESOLVED : resolution;
      PoliticsManager politics = PoliticsSavedData.get(level).getPoliticsManager();
      ConflictSide rebel = conflict.getPrimaryRebelSide();
      PoliticalGroupId rebelGroupId = null;
      if (rebel != null) {
         for (PoliticalGroupId id : rebel.getSupporterGroupIds()) {
            rebelGroupId = id;
            break;
         }
      }

      this.endLinkedWarIfNeeded(level, conflict, resolved, gameTime);

      switch (resolved) {
         case GOVERNMENT_VICTORY, REINTEGRATION, SURRENDER -> this.resolveGovernmentVictory(level, conflict, gameTime);
         case REBEL_VICTORY, SECESSION, FACTION_SPLIT -> this.resolveRebelVictory(level, conflict, resolved, gameTime);
         case FACTION_MERGE -> {
            this.mergeRebelFaction(level, conflict.getId());
            politics.overthrowGovernment(
               level,
               conflict.getOriginFactionId(),
               GovernmentType.COUNCIL,
               rebelGroupId,
               reason
            );
         }
         case NEGOTIATED_SETTLEMENT, PARTIAL_AUTONOMY -> this.resolveNegotiated(level, conflict, resolved, gameTime);
         case LEADERSHIP_CHANGE -> {
            // Direct political outcome — do not re-enter attemptCoup (avoids recursive conflict spawn).
            politics.overthrowGovernment(
               level,
               conflict.getOriginFactionId(),
               GovernmentType.MILITARY_RULE,
               rebelGroupId,
               "conflict_leadership_change"
            );
            this.dissolveRebellionKeepUnity(level, conflict, gameTime);
         }
         case GOVERNMENT_REFORM -> {
            politics.enactReform(level, conflict.getOriginFactionId(), null, -15);
            if (rebelGroupId != null) {
               politics.grantPoliticalRepresentation(level, conflict.getOriginFactionId(), rebelGroupId);
            }

            this.dissolveRebellionKeepUnity(level, conflict, gameTime);
         }
         case STALE_RESOLVED -> this.dissolveRebellionKeepUnity(level, conflict, gameTime);
      }

      PostConflictModifier modifier = switch (conflict.getType()) {
         case CIVIL_WAR -> PostConflictModifier.RECENT_CIVIL_WAR;
         case REVOLUTION -> PostConflictModifier.RECENT_REVOLUTION;
         case SECESSION -> PostConflictModifier.RECENT_SECESSION;
         default -> PostConflictModifier.RECENT_REBELLION;
      };
      if (resolved == ConflictResolution.GOVERNMENT_VICTORY || resolved == ConflictResolution.SURRENDER) {
         conflict.addPostModifier(PostConflictModifier.RECENT_DEFEAT);
         conflict.addPostModifier(PostConflictModifier.RECENT_REPRESSION);
      }

      conflict.addPostModifier(modifier);
      conflict.resolve(resolved, gameTime);
      conflict.recordHistory(
         new ConflictHistoryEntry(
            gameTime,
            ConflictEventType.CONFLICT_RESOLVED,
            conflict.getId(),
            conflict.getOriginFactionId(),
            conflict.getPrimaryRebelSideId(),
            conflict.getLinkedWarId(),
            null,
            null,
            0,
            reason == null ? resolved.name() : reason
         )
      );

      PostConflictRecovery recovery = new PostConflictRecovery(
         conflict.getId(),
         conflict.getOriginFactionId(),
         gameTime,
         ConflictConfig.POST_CONFLICT_RECOVERY_TICKS
      );
      recovery.setResolvedAs(resolved);
      recovery.addModifier(modifier);
      for (PostConflictModifier extra : conflict.getPostModifiers()) {
         recovery.addModifier(extra);
      }

      this.recoveries.put(conflict.getId(), recovery);
      conflict.recordHistory(
         new ConflictHistoryEntry(
            gameTime,
            ConflictEventType.RECOVERY_STARTED,
            conflict.getId(),
            conflict.getOriginFactionId(),
            null,
            null,
            null,
            null,
            recovery.getRemainingStabilityPenalty(),
            resolved.name()
         )
      );

      politics.applyPostConflictModifiers(
         level,
         conflict.getOriginFactionId(),
         recovery.getRemainingStabilityPenalty(),
         recovery.getRemainingPressureBonus(),
         resolved.name()
      );
      politics.clearInternalConflictMarker(conflict.getOriginFactionId());
      politics.recordConflictPoliticalEvent(
         level,
         conflict.getOriginFactionId(),
         PoliticalEventType.INTERNAL_CONFLICT_RESOLVED,
         rebelGroupId,
         0,
         resolved.name()
      );

      this.lastIdenticalConflictEnd.put(
         this.identicalKey(conflict.getOriginFactionId(), conflict.getType(), rebelGroupId),
         gameTime
      );

      for (UnrestId unrestId : conflict.getRelatedUnrestIds()) {
         Unrest unrest = this.unrestRecords.get(unrestId);
         if (unrest != null && unrest.isActive()) {
            unrest.resolve(gameTime);
         }
      }

      ConflictSide govSide = conflict.getGovernmentSide();
      if (govSide != null) {
         govSide.deactivate();
      }

      if (rebel != null) {
         rebel.deactivate();
      }

      this.markDirty.run();
      LOGGER.info(
         "[Millenaire] Internal conflict {} resolved as {} ({})",
         conflict.getId(),
         resolved,
         reason
      );
      return true;
   }

   private void resolveGovernmentVictory(ServerLevel level, InternalConflict conflict, long gameTime) {
      PoliticsManager politics = PoliticsSavedData.get(level).getPoliticsManager();
      FactionId rebelId = conflict.getRebelFactionId();
      ConflictSide rebel = conflict.getPrimaryRebelSide();

      if (rebel != null) {
         for (TerritoryId territoryId : new ArrayList<>(rebel.getControlledTerritoryIds())) {
            Territory territory = TerritoryQueries.territory(level, territoryId);
            if (territory != null && territory.isOccupied()
               && conflict.getOriginFactionId().equals(territory.getOwnerFactionId())) {
               TerritorySavedData.get(level)
                  .getTerritoryManager()
                  .liberateTerritory(level, territoryId, conflict.getOriginFactionId(), conflict.getLinkedWarId());
            }
         }
      }

      this.mergeRebelMilitaryIntoOrigin(level, conflict);

      if (rebelId != null
         && FactionSavedData.get(level).getFactionManager().exists(rebelId)
         && !conflict.getType().seeksIndependence()) {
         FactionManager factions = FactionSavedData.get(level).getFactionManager();
         Faction rebelFaction = factions.getFaction(rebelId);
         if (rebelFaction != null) {
            for (VillageId settlementId : new ArrayList<>(rebelFaction.getSettlementIds())) {
               Village village = VillageSavedData.get(level).getVillageManager().getVillage(settlementId);
               if (village != null) {
                  factions.assignVillage(village, conflict.getOriginFactionId());
               }
            }
         }

         factions.removeFaction(level, rebelId);
         conflict.setRebelFactionId(null);
      }

      politics.applyRepression(level, conflict.getOriginFactionId(), 25);
      conflict.recordHistory(
         new ConflictHistoryEntry(
            gameTime,
            ConflictEventType.REPRESSION,
            conflict.getId(),
            conflict.getOriginFactionId(),
            conflict.getGovernmentSideId(),
            conflict.getLinkedWarId(),
            null,
            null,
            25,
            "government_victory"
         )
      );
   }

   private void resolveRebelVictory(
      ServerLevel level,
      InternalConflict conflict,
      ConflictResolution resolved,
      long gameTime
   ) {
      PoliticsManager politics = PoliticsSavedData.get(level).getPoliticsManager();
      ConflictSide rebel = conflict.getPrimaryRebelSide();
      FactionId rebelId = conflict.getRebelFactionId();
      PoliticalGroupId rebelGroupId = null;
      if (rebel != null) {
         for (PoliticalGroupId id : rebel.getSupporterGroupIds()) {
            rebelGroupId = id;
            break;
         }
      }

      boolean independence = conflict.getType().seeksIndependence()
         || (rebel != null && rebel.getPrimaryObjective().requiresFactionSplit())
         || resolved == ConflictResolution.SECESSION
         || resolved == ConflictResolution.FACTION_SPLIT;

      if (independence && rebelId != null && rebel != null) {
         for (TerritoryId territoryId : new ArrayList<>(rebel.getControlledTerritoryIds())) {
            TerritorySavedData.get(level)
               .getTerritoryManager()
               .transferOwnership(
                  level,
                  territoryId,
                  rebelId,
                  ConquestType.TRANSFER,
                  conflict.getLinkedWarId(),
                  null,
                  "secession_victory"
               );
         }

         for (VillageId settlementId : new ArrayList<>(rebel.getControlledSettlementIds())) {
            Village village = VillageSavedData.get(level).getVillageManager().getVillage(settlementId);
            if (village != null) {
               FactionSavedData.get(level).getFactionManager().assignVillage(village, rebelId);
            }
         }

         conflict.recordHistory(
            new ConflictHistoryEntry(
               gameTime,
               ConflictEventType.FACTION_SPLIT,
               conflict.getId(),
               rebelId,
               rebel.getId(),
               conflict.getLinkedWarId(),
               null,
               null,
               rebel.getControlledTerritoryIds().size(),
               "independence"
            )
         );
         conflict.addPostModifier(PostConflictModifier.RECENT_SECESSION);
         return;
      }

      politics.overthrowGovernment(
         level,
         conflict.getOriginFactionId(),
         GovernmentType.MILITARY_RULE,
         rebelGroupId,
         "rebel_victory"
      );
      this.mergeRebelMilitaryIntoOrigin(level, conflict);
      if (rebelId != null && FactionSavedData.get(level).getFactionManager().exists(rebelId)) {
         FactionManager factions = FactionSavedData.get(level).getFactionManager();
         Faction rebelFaction = factions.getFaction(rebelId);
         if (rebelFaction != null) {
            for (VillageId settlementId : new ArrayList<>(rebelFaction.getSettlementIds())) {
               Village village = VillageSavedData.get(level).getVillageManager().getVillage(settlementId);
               if (village != null) {
                  factions.assignVillage(village, conflict.getOriginFactionId());
               }
            }
         }

         factions.removeFaction(level, rebelId);
         conflict.setRebelFactionId(null);
      }

      conflict.recordHistory(
         new ConflictHistoryEntry(
            gameTime,
            ConflictEventType.FACTION_MERGED,
            conflict.getId(),
            conflict.getOriginFactionId(),
            conflict.getPrimaryRebelSideId(),
            conflict.getLinkedWarId(),
            null,
            null,
            0,
            "rebel_government_change"
         )
      );
   }

   private void resolveNegotiated(
      ServerLevel level,
      InternalConflict conflict,
      ConflictResolution resolved,
      long gameTime
   ) {
      PoliticsManager politics = PoliticsSavedData.get(level).getPoliticsManager();
      ConflictSide rebel = conflict.getPrimaryRebelSide();
      PoliticalGroupId rebelGroupId = null;
      VillageId autonomySettlement = null;
      if (rebel != null) {
         for (PoliticalGroupId id : rebel.getSupporterGroupIds()) {
            rebelGroupId = id;
            break;
         }

         for (VillageId settlementId : rebel.getControlledSettlementIds()) {
            autonomySettlement = settlementId;
            break;
         }
      }

      if (autonomySettlement != null) {
         politics.grantAutonomy(
            level,
            conflict.getOriginFactionId(),
            autonomySettlement,
            resolved == ConflictResolution.PARTIAL_AUTONOMY ? 40 : 25
         );
      }

      if (rebelGroupId != null) {
         politics.grantPoliticalRepresentation(level, conflict.getOriginFactionId(), rebelGroupId);
         politics.grantConcession(level, conflict.getOriginFactionId(), rebelGroupId, resolved.name());
      }

      politics.enactReform(level, conflict.getOriginFactionId(), null, -8);
      this.dissolveRebellionKeepUnity(level, conflict, gameTime);
      conflict.recordHistory(
         new ConflictHistoryEntry(
            gameTime,
            ConflictEventType.SETTLEMENT_REACHED,
            conflict.getId(),
            conflict.getOriginFactionId(),
            conflict.getPrimaryRebelSideId(),
            conflict.getLinkedWarId(),
            null,
            autonomySettlement,
            0,
            resolved.name()
         )
      );
   }

   private void dissolveRebellionKeepUnity(ServerLevel level, InternalConflict conflict, long gameTime) {
      this.mergeRebelMilitaryIntoOrigin(level, conflict);
      FactionId rebelId = conflict.getRebelFactionId();
      if (rebelId != null && FactionSavedData.get(level).getFactionManager().exists(rebelId)) {
         FactionManager factions = FactionSavedData.get(level).getFactionManager();
         Faction rebelFaction = factions.getFaction(rebelId);
         if (rebelFaction != null) {
            for (VillageId settlementId : new ArrayList<>(rebelFaction.getSettlementIds())) {
               Village village = VillageSavedData.get(level).getVillageManager().getVillage(settlementId);
               if (village != null) {
                  factions.assignVillage(village, conflict.getOriginFactionId());
               }
            }
         }

         factions.removeFaction(level, rebelId);
         conflict.setRebelFactionId(null);
      }

      ConflictSide rebel = conflict.getPrimaryRebelSide();
      if (rebel != null) {
         for (TerritoryId territoryId : new ArrayList<>(rebel.getControlledTerritoryIds())) {
            Territory territory = TerritoryQueries.territory(level, territoryId);
            if (territory != null
               && territory.isOccupied()
               && conflict.getOriginFactionId().equals(territory.getOwnerFactionId())) {
               TerritorySavedData.get(level)
                  .getTerritoryManager()
                  .liberateTerritory(level, territoryId, conflict.getOriginFactionId(), conflict.getLinkedWarId());
            }
         }
      }
   }

   private void mergeRebelMilitaryIntoOrigin(ServerLevel level, InternalConflict conflict) {
      FactionId rebelId = conflict.getRebelFactionId();
      if (rebelId == null) {
         return;
      }

      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      ConflictSide rebel = conflict.getPrimaryRebelSide();
      ConflictSide gov = conflict.getGovernmentSide();
      List<Army> rebelArmies = new ArrayList<>(military.getArmies(rebelId));
      for (Army army : rebelArmies) {
         if (army.getState() == ArmyState.DISBANDED) {
            continue;
         }

         boolean transferred = military.transferArmyAllegiance(level, army.getId(), conflict.getOriginFactionId());
         if (transferred) {
            if (rebel != null) {
               rebel.removeArmy(army.getId());
            }

            if (gov != null) {
               gov.addArmy(army.getId());
            }

            this.armyLoyalty.put(army.getId(), MilitaryLoyalty.LOYAL_TO_GOVERNMENT);
         } else {
            military.disbandArmy(level, army.getId());
            this.armyLoyalty.remove(army.getId());
         }
      }
   }

   private void endLinkedWarIfNeeded(
      ServerLevel level,
      InternalConflict conflict,
      ConflictResolution resolution,
      long gameTime
   ) {
      WarId warId = conflict.getLinkedWarId();
      if (warId == null) {
         return;
      }

      War war = WarSavedData.get(level).getWarManager().getWar(warId);
      if (war == null || !war.getState().isOngoing()) {
         conflict.setLinkedWarId(null);
         return;
      }

      PeaceOutcome outcome;
      boolean rebelAttacker = conflict.getRebelFactionId() != null
         && conflict.getRebelFactionId().equals(war.getInitiatorFactionId());
      switch (resolution) {
         case REBEL_VICTORY, SECESSION, FACTION_SPLIT ->
            outcome = rebelAttacker ? PeaceOutcome.ATTACKER_VICTORY : PeaceOutcome.DEFENDER_VICTORY;
         case GOVERNMENT_VICTORY, REINTEGRATION, SURRENDER ->
            outcome = rebelAttacker ? PeaceOutcome.DEFENDER_VICTORY : PeaceOutcome.ATTACKER_VICTORY;
         case NEGOTIATED_SETTLEMENT, PARTIAL_AUTONOMY, GOVERNMENT_REFORM, LEADERSHIP_CHANGE, STALE_RESOLVED ->
            outcome = PeaceOutcome.WHITE_PEACE;
         default -> outcome = PeaceOutcome.EXHAUSTION;
      }

      // Unlink before acceptPeace so onLinkedWarEnded does not re-enter resolution.
      conflict.setLinkedWarId(null);
      WarSavedData.get(level)
         .getWarManager()
         .acceptPeace(level, warId, outcome, resolution.name(), "internal_conflict_resolution");
   }

   // -------------------------------------------------------------------------
   // Helpers
   // -------------------------------------------------------------------------

   private void ensureCauseUnrest(
      ServerLevel level,
      FactionId factionId,
      UnrestCause cause,
      UnrestScope scope,
      int severity,
      @Nullable PoliticalGroupId groupId,
      @Nullable VillageId settlementId,
      @Nullable TerritoryId territoryId,
      @Nullable String detail,
      long gameTime
   ) {
      for (Unrest existing : this.unrestRecords.values()) {
         if (!existing.isActive() || !factionId.equals(existing.getFactionId())) {
            continue;
         }

         if (existing.getCause() == cause
            && Objects.equals(existing.getPoliticalGroupId(), groupId)
            && Objects.equals(existing.getSettlementId(), settlementId)
            && Objects.equals(existing.getTerritoryId(), territoryId)) {
            existing.setSeverity(Math.max(existing.getSeverity(), severity), gameTime);
            existing.setEscalationPressure(
               Math.max(existing.getEscalationPressure(), severity),
               gameTime
            );
            return;
         }
      }

      if (this.countActiveUnrest(factionId) >= ConflictConfig.MAX_UNREST_PER_FACTION) {
         return;
      }

      this.createUnrest(level, factionId, cause, scope, severity, groupId, settlementId, territoryId, detail);
   }

   private boolean meetsCivilWarPreconditions(
      ServerLevel level,
      FactionPoliticalState state,
      Government government,
      @Nullable PoliticalGroup rebelGroup
   ) {
      if (state.getRebellionPressure() < ConflictConfig.CIVIL_WAR_PRESSURE) {
         return false;
      }

      if (government.getStability() > 30) {
         return false;
      }

      if (rebelGroup != null
         && (rebelGroup.getPoliticalPower() < ConflictConfig.MIN_REBEL_GROUP_POWER
            || rebelGroup.getApproval() > 55)) {
         return false;
      }

      int armies = MilitarySavedData.get(level).getMilitaryManager().getArmies(state.getFactionId()).size();
      return armies >= 2 || MilitarySavedData.get(level).getMilitaryManager().getMilitaryStrength(state.getFactionId()) >= 20;
   }

   private boolean meetsSecessionPreconditions(
      ServerLevel level,
      Government government,
      @Nullable TerritoryId focusTerritoryId,
      @Nullable VillageId focusSettlementId
   ) {
      if (government.getCentralization() > ConflictConfig.SECESSION_CENTRALIZATION_MAX
         && focusTerritoryId == null
         && focusSettlementId == null) {
         return false;
      }

      if (focusSettlementId != null) {
         FactionPoliticalState state = PoliticsSavedData.get(level).getPoliticsManager().getState(government.getFactionId());
         if (state != null) {
            LocalAdministrator admin = state.getLocalAdministrators().get(focusSettlementId);
            if (admin != null && admin.getAutonomy() < ConflictConfig.SECESSION_AUTONOMY_MIN) {
               return false;
            }
         }
      }

      return TerritoryQueries.ownedBy(level, government.getFactionId()).size() >= 2
         || focusTerritoryId != null
         || focusSettlementId != null;
   }

   private ConflictType inferEscalationType(
      ServerLevel level,
      FactionId factionId,
      FactionPoliticalState state,
      Government government,
      @Nullable Unrest hottest
   ) {
      if (hottest != null && (hottest.getCause() == UnrestCause.OCCUPATION || hottest.getScope() == UnrestScope.TERRITORY
         || hottest.getScope() == UnrestScope.SETTLEMENT || hottest.getScope() == UnrestScope.REGION)) {
         LocalAdministrator admin = hottest.getSettlementId() == null
            ? null
            : state.getLocalAdministrators().get(hottest.getSettlementId());
         if (admin != null && admin.getAutonomy() >= ConflictConfig.SECESSION_AUTONOMY_MIN
            && government.getCentralization() <= ConflictConfig.SECESSION_CENTRALIZATION_MAX) {
            return ConflictType.SECESSION;
         }

         return ConflictType.REGIONAL_UPRISING;
      }

      if (government.getLegitimacy() <= ConflictConfig.MIN_GOV_LEGITIMACY_FOR_COUP_WAR
         && state.getPolarization() >= 70) {
         return ConflictType.COUP_CONFLICT;
      }

      if (state.getRebellionPressure() >= ConflictConfig.CIVIL_WAR_PRESSURE && government.getStability() <= 25) {
         return ConflictType.CIVIL_WAR;
      }

      if (state.getPolarization() >= 80 && government.getLegitimacy() < 40) {
         return ConflictType.REVOLUTION;
      }

      return ConflictType.REBELLION;
   }

   private ConflictResolution rebelVictoryResolution(InternalConflict conflict, ConflictSide rebel) {
      if (conflict.getType().seeksIndependence() || rebel.getPrimaryObjective().requiresFactionSplit()) {
         return ConflictResolution.SECESSION;
      }

      if (rebel.getPrimaryObjective().requiresGovernmentChange()) {
         return ConflictResolution.REBEL_VICTORY;
      }

      return ConflictResolution.REBEL_VICTORY;
   }

   @Nullable
   private PoliticalGroupId findStrongestOpposition(FactionPoliticalState state, Government government) {
      PoliticalGroupId best = null;
      int bestPower = -1;
      for (PoliticalGroupId oppositionId : government.getOppositionGroupIds()) {
         PoliticalGroup group = state.getGroups().get(oppositionId);
         if (group == null || !group.isActive()) {
            continue;
         }

         int power = group.getPoliticalPower() + group.getMilitaryInfluence() / 2;
         if (power > bestPower) {
            bestPower = power;
            best = oppositionId;
         }
      }

      if (best != null) {
         return best;
      }

      for (PoliticalGroup group : state.getGroups().values()) {
         if (!group.isActive()) {
            continue;
         }

         int power = group.getPoliticalPower();
         if (power > bestPower && group.getApproval() < 45) {
            bestPower = power;
            best = group.getId();
         }
      }

      return best;
   }

   private void refreshSideMetrics(ServerLevel level, InternalConflict conflict) {
      PoliticsManager politics = PoliticsSavedData.get(level).getPoliticsManager();
      ConflictSide gov = conflict.getGovernmentSide();
      ConflictSide rebel = conflict.getPrimaryRebelSide();
      Government government = politics.getActiveGovernment(conflict.getOriginFactionId());
      FactionPoliticalState state = politics.getState(conflict.getOriginFactionId());
      if (gov != null && government != null) {
         gov.setLegitimacy(government.getLegitimacy());
         gov.setSupport(Math.max(10, government.getStability()));
         Set<TerritoryId> owned = new LinkedHashSet<>();
         for (Territory territory : TerritoryQueries.ownedBy(level, conflict.getOriginFactionId())) {
            if (!territory.isOccupied()) {
               owned.add(territory.getId());
            }
         }

         for (TerritoryId id : owned) {
            gov.addTerritory(id);
         }
      }

      if (rebel != null) {
         int support = rebel.getSupport();
         if (state != null) {
            for (PoliticalGroupId groupId : rebel.getSupporterGroupIds()) {
               PoliticalGroup group = state.getGroups().get(groupId);
               if (group != null) {
                  support = Math.max(support, group.getPoliticalPower());
               }
            }
         }

         Set<FactionId> recognizers = this.foreignRecognition.get(conflict.getId());
         if (recognizers != null) {
            support = Math.min(100, support + recognizers.size() * 3);
         }

         rebel.setSupport(support);
         if (conflict.getRebelFactionId() != null) {
            for (Territory territory : TerritoryQueries.occupiedBy(level, conflict.getRebelFactionId())) {
               rebel.addTerritory(territory.getId());
               if (territory.getSettlementId() != null) {
                  rebel.addSettlement(territory.getSettlementId());
               }
            }

            for (Territory territory : TerritoryQueries.ownedBy(level, conflict.getRebelFactionId())) {
               rebel.addTerritory(territory.getId());
               if (territory.getSettlementId() != null) {
                  rebel.addSettlement(territory.getSettlementId());
               }
            }
         }
      }
   }

   private void applyDiplomaticReaction(ServerLevel level, InternalConflict conflict) {
      FactionId origin = conflict.getOriginFactionId();
      int hit = switch (conflict.getType()) {
         case CIVIL_WAR -> ConflictConfig.RELATIONSHIP_CIVIL_WAR / 4;
         case REVOLUTION -> ConflictConfig.RELATIONSHIP_REVOLUTION / 4;
         case SECESSION -> ConflictConfig.RELATIONSHIP_SECESSION / 4;
         default -> 5;
      };

      for (DiplomaticAgreement agreement : DiplomacySavedData.get(level).getDiplomaticManager().getAgreementsInvolving(origin)) {
         if (!agreement.isActive()) {
            continue;
         }

         FactionId other = origin.equals(agreement.getFactionA()) ? agreement.getFactionB() : agreement.getFactionA();
         if (other == null || other.equals(origin) || other.equals(conflict.getRebelFactionId())) {
            continue;
         }

         DiplomaticStanding standing = DiplomacySavedData.get(level)
            .getDiplomaticManager()
            .getDiplomaticStanding(level, origin, other);
         if (standing == DiplomaticStanding.ALLIANCE || standing == DiplomaticStanding.DEFENSIVE_PACT) {
            RelationshipSavedData.get(level)
               .getRelationshipManager()
               .decreaseRelationship(level, other, origin, Math.abs(hit), RelationshipChangeReason.POLITICS);
         } else if (conflict.getRebelFactionId() != null && standing == DiplomaticStanding.HOSTILE) {
            RelationshipSavedData.get(level)
               .getRelationshipManager()
               .increaseRelationship(
                  level,
                  other,
                  conflict.getRebelFactionId(),
                  ConflictConfig.RELATIONSHIP_RECOGNITION / 2,
                  RelationshipChangeReason.POLITICS
               );
         }
      }
   }

   private boolean canStartIdentical(
      FactionId factionId,
      ConflictType type,
      @Nullable PoliticalGroupId groupId,
      long gameTime
   ) {
      for (InternalConflict conflict : this.conflicts.values()) {
         if (!conflict.isActive() || !factionId.equals(conflict.getOriginFactionId())) {
            continue;
         }

         if (conflict.getType() != type) {
            continue;
         }

         ConflictSide rebel = conflict.getPrimaryRebelSide();
         if (groupId == null) {
            return false;
         }

         if (rebel != null && rebel.getSupporterGroupIds().contains(groupId)) {
            return false;
         }

         if (rebel != null && rebel.getSupporterGroupIds().isEmpty()) {
            return false;
         }
      }

      Long lastEnd = this.lastIdenticalConflictEnd.get(this.identicalKey(factionId, type, groupId));
      return lastEnd == null || gameTime - lastEnd >= ConflictConfig.IDENTICAL_CONFLICT_COOLDOWN_TICKS;
   }

   private String identicalKey(FactionId factionId, ConflictType type, @Nullable PoliticalGroupId groupId) {
      return factionId.asString()
         + "|"
         + type.name()
         + "|"
         + (groupId == null ? "-" : groupId.asString());
   }

   private int countActiveForFaction(FactionId factionId) {
      int count = 0;
      for (InternalConflict conflict : this.conflicts.values()) {
         if (conflict.isActive() && factionId.equals(conflict.getOriginFactionId())) {
            count++;
         }
      }

      return count;
   }

   private int countActiveUnrest(FactionId factionId) {
      int count = 0;
      for (Unrest unrest : this.unrestRecords.values()) {
         if (unrest.isActive() && factionId.equals(unrest.getFactionId())) {
            count++;
         }
      }

      return count;
   }

   private boolean hasActiveConflictForFaction(FactionId factionId) {
      return this.countActiveForFaction(factionId) > 0;
   }

   private int maxRelatedUnrestPressure(InternalConflict conflict) {
      int max = 0;
      for (UnrestId unrestId : conflict.getRelatedUnrestIds()) {
         Unrest unrest = this.unrestRecords.get(unrestId);
         if (unrest != null && unrest.isActive()) {
            max = Math.max(max, unrest.getEscalationPressure());
         }
      }

      for (Unrest unrest : this.unrestRecords.values()) {
         if (unrest.isActive() && conflict.getOriginFactionId().equals(unrest.getFactionId())) {
            max = Math.max(max, unrest.getEscalationPressure());
         }
      }

      return max;
   }
}
