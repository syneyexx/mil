package org.millenaire.faction.war;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionManager;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.diplomacy.AgreementTerminationReason;
import org.millenaire.faction.diplomacy.AgreementType;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.diplomacy.DiplomaticManager;
import org.millenaire.faction.economy.EconomySavedData;
import org.millenaire.faction.economy.TradeDisruptionReason;
import org.millenaire.faction.economy.TradeManager;
import org.millenaire.faction.military.Army;
import org.millenaire.faction.military.ArmyId;
import org.millenaire.faction.military.ArmyState;
import org.millenaire.faction.military.Commander;
import org.millenaire.faction.military.MilitaryAlertLevel;
import org.millenaire.faction.military.MilitaryManager;
import org.millenaire.faction.military.MilitarySavedData;
import org.millenaire.faction.military.MilitaryUnit;
import org.millenaire.faction.military.MobilizationState;
import org.millenaire.faction.military.UnitId;
import org.millenaire.faction.military.WarMilitaryBridge;
import org.millenaire.faction.relationship.RelationshipChangeReason;
import org.millenaire.faction.relationship.RelationshipSavedData;
import org.millenaire.faction.politics.PoliticsSavedData;
import org.millenaire.faction.territory.Territory;
import org.millenaire.faction.territory.TerritoryManager;
import org.millenaire.faction.territory.TerritorySavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * Authoritative Phase 6 warfare API. Permanent conquest/transfer is owned by Phase 7 territory.
 */
public final class WarManager {
   private static final Logger LOGGER = LogUtils.getLogger();

   private final Map<WarId, War> wars = new LinkedHashMap<>();
   private final Map<BattleId, Battle> battles = new LinkedHashMap<>();
   private final Map<ArmyId, ArmyWartimeState> armyStates = new LinkedHashMap<>();
   private final SoldierPresenceTracker soldierPresence = new SoldierPresenceTracker();
   private final Runnable markDirty;
   private long lastUpdateGameTime;

   WarManager(Runnable markDirty) {
      this.markDirty = Objects.requireNonNull(markDirty, "markDirty");
   }

   @Nullable
   public War getWar(WarId warId) {
      return warId == null ? null : this.wars.get(warId);
   }

   @Nullable
   public Battle getBattle(BattleId battleId) {
      return battleId == null ? null : this.battles.get(battleId);
   }

   @Nullable
   public ArmyWartimeState getArmyWartimeState(ArmyId armyId) {
      return armyId == null ? null : this.armyStates.get(armyId);
   }

   public Collection<War> getAllWars() {
      return Collections.unmodifiableCollection(this.wars.values());
   }

   public Collection<War> getActiveWars() {
      List<War> result = new ArrayList<>();
      for (War war : this.wars.values()) {
         if (war.getState().isOngoing()) {
            result.add(war);
         }
      }

      return Collections.unmodifiableList(result);
   }

   public Collection<Battle> getAllBattles() {
      return Collections.unmodifiableCollection(this.battles.values());
   }

   public Collection<ArmyWartimeState> getAllArmyStates() {
      return Collections.unmodifiableCollection(this.armyStates.values());
   }

   public SoldierPresenceTracker getSoldierPresence() {
      return this.soldierPresence;
   }

   public Collection<ArmyWartimeState> getArmyStatesForFaction(FactionId factionId) {
      List<ArmyWartimeState> result = new ArrayList<>();
      for (ArmyWartimeState state : this.armyStates.values()) {
         if (factionId.equals(state.getFactionId())) {
            result.add(state);
         }
      }

      return Collections.unmodifiableList(result);
   }

   public boolean isAtWar(FactionId factionId) {
      if (factionId == null) {
         return false;
      }

      for (War war : this.wars.values()) {
         if (war.getState().isOngoing() && war.isParticipant(factionId)) {
            return true;
         }
      }

      return false;
   }

   public boolean areAtWar(FactionId a, FactionId b) {
      if (a == null || b == null || a.equals(b)) {
         return false;
      }

      for (War war : this.wars.values()) {
         if (!war.getState().isOngoing()) {
            continue;
         }

         WarSideId sideA = war.getSide(a);
         WarSideId sideB = war.getSide(b);
         if (sideA != null && sideB != null && sideA != sideB) {
            return true;
         }
      }

      return false;
   }

   public boolean hasActiveWarBetween(FactionId a, FactionId b) {
      return this.areAtWar(a, b);
   }

   public Collection<FactionId> getEnemies(FactionId factionId) {
      Set<FactionId> enemies = new LinkedHashSet<>();
      for (War war : this.wars.values()) {
         if (!war.getState().isOngoing() || !war.isParticipant(factionId)) {
            continue;
         }

         WarSideId side = war.getSide(factionId);
         if (side == null) {
            continue;
         }

         enemies.addAll(war.getActiveFactionIds(side.opposite()));
      }

      return Collections.unmodifiableSet(enemies);
   }

   public Collection<War> getWarsInvolving(FactionId factionId) {
      List<War> result = new ArrayList<>();
      for (War war : this.wars.values()) {
         if (war.getParticipant(factionId) != null) {
            result.add(war);
         }
      }

      return Collections.unmodifiableList(result);
   }

   @Nullable
   public War declareWar(ServerLevel level, FactionId attacker, FactionId defender, WarGoalType primaryGoalType) {
      Objects.requireNonNull(level, "level");
      WarDeclarationValidator.Result validation = WarDeclarationValidator.validate(level, attacker, defender, this);
      if (!validation.allowed()) {
         LOGGER.warn("[Millenaire] War declaration denied ({} → {}): {}", attacker, defender, validation.denialReason());
         return null;
      }

      long gameTime = level.getGameTime();
      War war = new War(WarId.random(), attacker, defender, gameTime);
      war.setState(WarState.MOBILIZING);
      this.wars.put(war.getId(), war);

      WarGoalType goalType = primaryGoalType == null ? WarGoalType.MILITARY_SUPERIORITY : primaryGoalType;
      WarGoal goal = new WarGoal(WarGoalId.random(), war.getId(), goalType, attacker, gameTime, defender, null, null);
      war.addGoal(goal);
      WarGoal defendGoal = new WarGoal(
         WarGoalId.random(), war.getId(), WarGoalType.FORCE_PEACE, defender, gameTime, attacker, null, null
      );
      war.addGoal(defendGoal);

      war.recordHistory(
         new WarHistoryEntry(gameTime, WarEventType.WAR_DECLARED, attacker, defender, null, null, null, 0, goalType.name())
      );

      this.applyDiplomaticWarStart(level, war, attacker, defender, gameTime);
      this.mobilizeFaction(level, war, attacker, gameTime);
      this.mobilizeFaction(level, war, defender, gameTime);
      this.inviteEligibleAllies(level, war, attacker, defender, gameTime);

      this.markDirty.run();
      LOGGER.info("[Millenaire] War {} declared: {} → {}", war.getId(), attacker, defender);
      return war;
   }

   public boolean joinWar(ServerLevel level, WarId warId, FactionId factionId, WarSideId side, JoinWarReason reason) {
      Objects.requireNonNull(level, "level");
      War war = this.wars.get(warId);
      if (war == null || !war.getState().isOngoing() || factionId == null || side == null) {
         return false;
      }

      if (!FactionSavedData.get(level).getFactionManager().exists(factionId)) {
         return false;
      }

      if (war.isParticipant(factionId)) {
         return false;
      }

      long gameTime = level.getGameTime();
      WarParticipant participant = new WarParticipant(factionId, side, reason == null ? JoinWarReason.VOLUNTARY : reason, gameTime);
      if (!war.addParticipant(participant)) {
         return false;
      }

      war.recordHistory(
         new WarHistoryEntry(gameTime, WarEventType.FACTION_JOINED, factionId, null, null, null, null, 0, side.name() + ":" + reason)
      );
      this.mobilizeFaction(level, war, factionId, gameTime);

      for (FactionId enemy : war.getActiveFactionIds(side.opposite())) {
         this.breakCrossSideAgreements(level, factionId, enemy, gameTime);
         this.disruptTradeBetween(level, factionId, enemy, gameTime);
         DiplomacySavedData.get(level)
            .getDiplomaticManager()
            .imposeEmbargo(level, factionId, enemy, 0L, "war:" + war.getId().asString());
         DiplomacySavedData.get(level)
            .getDiplomaticManager()
            .imposeEmbargo(level, enemy, factionId, 0L, "war:" + war.getId().asString());
         RelationshipSavedData.get(level)
            .getRelationshipManager()
            .decreaseRelationship(level, factionId, enemy, WarConfig.RELATIONSHIP_WAR_DECLARATION / 2, RelationshipChangeReason.WAR);
         RelationshipSavedData.get(level)
            .getRelationshipManager()
            .decreaseRelationship(level, enemy, factionId, WarConfig.RELATIONSHIP_WAR_DECLARATION / 2, RelationshipChangeReason.WAR);
      }

      for (FactionId ally : war.getActiveFactionIds(side)) {
         if (!ally.equals(factionId)) {
            RelationshipSavedData.get(level)
               .getRelationshipManager()
               .increaseRelationship(level, ally, factionId, WarConfig.RELATIONSHIP_ALLY_JOIN, RelationshipChangeReason.DEFENSIVE_AID);
         }
      }

      this.markDirty.run();
      return true;
   }

   public boolean leaveWar(ServerLevel level, WarId warId, FactionId factionId, @Nullable String reason) {
      Objects.requireNonNull(level, "level");
      War war = this.wars.get(warId);
      if (war == null || !war.getState().isOngoing() || factionId == null) {
         return false;
      }

      long gameTime = level.getGameTime();
      if (!war.removeParticipant(factionId, gameTime)) {
         return false;
      }

      war.recordHistory(
         new WarHistoryEntry(gameTime, WarEventType.FACTION_LEFT, factionId, null, null, null, null, 0, reason)
      );
      this.clearFactionWarObjectives(level, war, factionId, gameTime);
      this.evaluateWarContinuation(level, war, gameTime);
      this.markDirty.run();
      return true;
   }

   public boolean declareCeasefire(ServerLevel level, WarId warId, long durationTicks) {
      Objects.requireNonNull(level, "level");
      War war = this.wars.get(warId);
      if (war == null || !war.getState().isOngoing()) {
         return false;
      }

      long gameTime = level.getGameTime();
      long duration = durationTicks <= 0L ? WarConfig.DEFAULT_CEASEFIRE_DURATION_TICKS : durationTicks;
      Ceasefire ceasefire = new Ceasefire(gameTime, gameTime + duration);
      war.setCeasefire(ceasefire);
      war.setState(WarState.CEASEFIRE);
      this.pauseOpenBattles(war, gameTime);
      war.recordHistory(
         new WarHistoryEntry(gameTime, WarEventType.CEASEFIRE_DECLARED, null, null, null, null, null, (int)duration, null)
      );
      this.markDirty.run();
      return true;
   }

   public boolean beginPeaceNegotiation(ServerLevel level, WarId warId) {
      Objects.requireNonNull(level, "level");
      War war = this.wars.get(warId);
      if (war == null || !war.getState().isOngoing()) {
         return false;
      }

      long gameTime = level.getGameTime();
      war.setState(WarState.PEACE_NEGOTIATION);
      this.pauseOpenBattles(war, gameTime);
      war.recordHistory(
         new WarHistoryEntry(gameTime, WarEventType.PEACE_NEGOTIATION_STARTED, null, null, null, null, null, 0, null)
      );
      this.markDirty.run();
      return true;
   }

   public boolean acceptPeace(ServerLevel level, WarId warId, PeaceOutcome outcome, @Nullable String termsNote, @Nullable String reason) {
      Objects.requireNonNull(level, "level");
      War war = this.wars.get(warId);
      if (war == null || !war.getState().isOngoing()) {
         return false;
      }

      long gameTime = level.getGameTime();
      PeaceOutcome resolved = outcome == null ? this.deriveOutcome(war) : outcome;
      PeaceSettlement settlement = new PeaceSettlement(war.getId(), resolved, war.getWarScore(), gameTime, termsNote, reason);
      war.setPeaceSettlement(settlement);
      war.recordHistory(
         new WarHistoryEntry(gameTime, WarEventType.PEACE_ACCEPTED, null, null, null, null, null, war.getWarScore(), resolved.name())
      );
      this.endWar(level, war, resolved, gameTime);
      this.markDirty.run();
      return true;
   }

   public boolean issueArmyOrder(ServerLevel level, ArmyId armyId, ArmyOrder order) {
      Objects.requireNonNull(level, "level");
      ArmyWartimeState state = this.armyStates.get(armyId);
      if (state == null || state.isDestroyed() || order == null) {
         return false;
      }

      War war = state.getWarId() == null ? null : this.wars.get(state.getWarId());
      if (war != null && !war.getState().allowsMovementOrders() && order.getType() != ArmyOrderType.HOLD && order.getType() != ArmyOrderType.RETREAT) {
         return false;
      }

      if (war != null && war.getState() == WarState.CEASEFIRE && order.getType() == ArmyOrderType.ATTACK) {
         return false;
      }

      state.setOrder(order);
      if (order.getTargetPos() != null) {
         state.setDestination(order.getTargetPos());
      }

      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      if (order.getType() == ArmyOrderType.MOVE || order.getType() == ArmyOrderType.ATTACK || order.getType() == ArmyOrderType.RETREAT || order.getType() == ArmyOrderType.FOLLOW) {
         military.setArmyOrganizationalState(level, armyId, ArmyState.MOVING);
      } else if (order.getType() == ArmyOrderType.DEFEND || order.getType() == ArmyOrderType.HOLD) {
         military.setArmyOrganizationalState(level, armyId, ArmyState.STATIONED);
      }

      this.markDirty.run();
      return true;
   }

   public void tick(ServerLevel level) {
      Objects.requireNonNull(level, "level");
      long gameTime = level.getGameTime();
      if (this.lastUpdateGameTime > 0L && gameTime - this.lastUpdateGameTime < WarConfig.WAR_UPDATE_INTERVAL_TICKS) {
         return;
      }

      this.lastUpdateGameTime = gameTime;
      for (War war : new ArrayList<>(this.wars.values())) {
         if (!war.getState().isOngoing()) {
            continue;
         }

         this.tickWar(level, war, gameTime);
      }

      this.markDirty.run();
   }

   public int peacePressure(WarId warId, WarSideId side) {
      War war = this.wars.get(warId);
      if (war == null) {
         return 0;
      }

      WarExhaustionCalculator.FactionIdView view = side == WarSideId.ATTACKER
         ? WarExhaustionCalculator.FactionIdView.ATTACKER
         : WarExhaustionCalculator.FactionIdView.DEFENDER;
      return WarExhaustionCalculator.peacePressure(war, view);
   }

   public void handleFactionRemoved(ServerLevel level, FactionId factionId) {
      if (factionId == null) {
         return;
      }

      long gameTime = level.getGameTime();
      for (War war : new ArrayList<>(this.wars.values())) {
         if (war.getParticipant(factionId) == null) {
            continue;
         }

         war.removeParticipant(factionId, gameTime);
         war.recordHistory(
            new WarHistoryEntry(gameTime, WarEventType.FACTION_LEFT, factionId, null, null, null, null, 0, "faction_removed")
         );
         this.clearFactionWarObjectives(level, war, factionId, gameTime);
         if (war.getState().isOngoing()) {
            this.evaluateWarContinuation(level, war, gameTime);
         }
      }

      this.armyStates.entrySet().removeIf(entry -> factionId.equals(entry.getValue().getFactionId()));
      this.markDirty.run();
   }

   public void handleArmyRemoved(ServerLevel level, ArmyId armyId) {
      if (armyId == null) {
         return;
      }

      long gameTime = level.getGameTime();
      ArmyWartimeState state = this.armyStates.remove(armyId);
      if (state != null && state.getWarId() != null) {
         War war = this.wars.get(state.getWarId());
         if (war != null) {
            war.removeArmy(armyId);
            if (state.getBattleId() != null) {
               Battle battle = this.battles.get(state.getBattleId());
               if (battle != null) {
                  battle.removeArmy(armyId);
               }
            }
         }
      }

      for (Battle battle : this.battles.values()) {
         battle.removeArmy(armyId);
      }

      this.soldierPresence.clearArmy(armyId);
      this.markDirty.run();
   }

   public void handleSettlementRemoved(ServerLevel level, VillageId settlementId) {
      if (settlementId == null) {
         return;
      }

      long gameTime = level.getGameTime();
      for (War war : this.wars.values()) {
         for (WarGoal goal : war.getGoals().values()) {
            if (settlementId.equals(goal.getTargetSettlementId()) && !goal.getStatus().isTerminal()) {
               goal.markCancelled(gameTime);
            }
         }
      }

      for (ArmyWartimeState state : this.armyStates.values()) {
         ArmyOrder order = state.getOrder();
         if (order != null && settlementId.equals(order.getTargetSettlementId())) {
            state.setOrder(ArmyOrder.hold(gameTime, state.getPosition()));
         }

         MilitaryObjective objective = state.getObjective();
         if (objective != null && settlementId.equals(objective.getTargetSettlementId())) {
            state.setObjective(null);
         }
      }

      this.markDirty.run();
   }

   public int purgeInvalid(FactionManager factionManager) {
      int removed = 0;
      List<WarId> orphanWars = new ArrayList<>();
      for (War war : this.wars.values()) {
         if (!factionManager.exists(war.getInitiatorFactionId()) && !factionManager.exists(war.getPrimaryTargetFactionId())) {
            orphanWars.add(war.getId());
         }
      }

      for (WarId warId : orphanWars) {
         this.wars.remove(warId);
         removed++;
      }

      this.armyStates.entrySet().removeIf(entry -> !factionManager.exists(entry.getValue().getFactionId()));
      if (removed > 0) {
         this.markDirty.run();
      }

      return removed;
   }

   void loadWar(War war) {
      this.wars.put(war.getId(), war);
   }

   void loadBattle(Battle battle) {
      this.battles.put(battle.getId(), battle);
   }

   void loadArmyState(ArmyWartimeState state) {
      this.armyStates.put(state.getArmyId(), state);
   }

   public void validateLoadedConsistency(FactionManager factionManager, MilitaryManager military) {
      for (War war : this.wars.values()) {
         List<FactionId> invalid = new ArrayList<>();
         for (FactionId factionId : war.getParticipants().keySet()) {
            if (!factionManager.exists(factionId)) {
               invalid.add(factionId);
            }
         }

         for (FactionId factionId : invalid) {
            war.removeParticipant(factionId, war.getEndedGameTime());
         }
      }

      List<BattleId> badBattles = new ArrayList<>();
      for (Battle battle : this.battles.values()) {
         if (!this.wars.containsKey(battle.getWarId())) {
            badBattles.add(battle.getId());
            continue;
         }

         for (ArmyId armyId : new ArrayList<>(battle.getAttackerArmyIds())) {
            if (military.getArmy(armyId) == null) {
               battle.removeArmy(armyId);
            }
         }

         for (ArmyId armyId : new ArrayList<>(battle.getDefenderArmyIds())) {
            if (military.getArmy(armyId) == null) {
               battle.removeArmy(armyId);
            }
         }
      }

      for (BattleId battleId : badBattles) {
         this.battles.remove(battleId);
      }

      List<ArmyId> badStates = new ArrayList<>();
      for (ArmyWartimeState state : this.armyStates.values()) {
         if (military.getArmy(state.getArmyId()) == null || !factionManager.exists(state.getFactionId())) {
            badStates.add(state.getArmyId());
         }
      }

      for (ArmyId armyId : badStates) {
         this.armyStates.remove(armyId);
      }

      this.markDirty.run();
   }

   long getLastUpdateGameTime() {
      return this.lastUpdateGameTime;
   }

   void setLastUpdateGameTime(long gameTime) {
      this.lastUpdateGameTime = gameTime;
   }

   private void tickWar(ServerLevel level, War war, long gameTime) {
      if (war.getState() == WarState.MOBILIZING && gameTime >= war.getMobilizationEndsGameTime()) {
         war.setState(WarState.ACTIVE);
      }

      if (war.getCeasefire() != null && war.getCeasefire().isActive() && war.getCeasefire().isExpired(gameTime)) {
         war.getCeasefire().end(gameTime);
         war.setState(WarState.ACTIVE);
         war.recordHistory(
            new WarHistoryEntry(gameTime, WarEventType.CEASEFIRE_ENDED, null, null, null, null, null, 0, null)
         );
      }

      this.updateExhaustion(war, gameTime);
      this.ensureArmyStates(level, war, gameTime);

      if (war.getState().allowsCombat()) {
         if (gameTime % WarConfig.AI_EVALUATION_INTERVAL_TICKS < WarConfig.WAR_UPDATE_INTERVAL_TICKS) {
            this.evaluateAi(level, war, gameTime);
         }

         this.moveArmies(level, war, gameTime);
         this.detectBattles(level, war, gameTime);
         this.tickBattles(level, war, gameTime);
         this.pulseReinforcements(level, war, gameTime);
      } else if (war.getState() == WarState.CEASEFIRE || war.getState() == WarState.PEACE_NEGOTIATION) {
         this.moveArmies(level, war, gameTime);
      }

      this.updateWarGoals(level, war, gameTime);
      this.evaluateAutomaticEnd(level, war, gameTime);
   }

   private void applyDiplomaticWarStart(ServerLevel level, War war, FactionId attacker, FactionId defender, long gameTime) {
      this.breakCrossSideAgreements(level, attacker, defender, gameTime);
      DiplomaticManager diplomacy = DiplomacySavedData.get(level).getDiplomaticManager();
      diplomacy.imposeEmbargo(level, attacker, defender, 0L, "war:" + war.getId().asString());
      diplomacy.imposeEmbargo(level, defender, attacker, 0L, "war:" + war.getId().asString());
      this.disruptTradeBetween(level, attacker, defender, gameTime);
      RelationshipSavedData.get(level)
         .getRelationshipManager()
         .decreaseRelationship(level, attacker, defender, WarConfig.RELATIONSHIP_WAR_DECLARATION, RelationshipChangeReason.WAR);
      RelationshipSavedData.get(level)
         .getRelationshipManager()
         .decreaseRelationship(level, defender, attacker, WarConfig.RELATIONSHIP_WAR_DECLARATION, RelationshipChangeReason.WAR);
   }

   private void breakCrossSideAgreements(ServerLevel level, FactionId a, FactionId b, long gameTime) {
      DiplomaticManager diplomacy = DiplomacySavedData.get(level).getDiplomaticManager();
      diplomacy.terminateActiveAgreement(level, AgreementType.PEACE, a, b, AgreementTerminationReason.CONFLICT);
      diplomacy.terminateActiveAgreement(level, AgreementType.NON_AGGRESSION, a, b, AgreementTerminationReason.CONFLICT);
      diplomacy.terminateActiveAgreement(level, AgreementType.TRADE_AGREEMENT, a, b, AgreementTerminationReason.CONFLICT);
      diplomacy.terminateActiveAgreement(level, AgreementType.ALLIANCE, a, b, AgreementTerminationReason.CONFLICT);
      diplomacy.terminateActiveAgreement(level, AgreementType.DEFENSIVE_PACT, a, b, AgreementTerminationReason.CONFLICT);
   }

   private void disruptTradeBetween(ServerLevel level, FactionId a, FactionId b, long gameTime) {
      TradeManager trade = EconomySavedData.get(level).getTradeManager();
      trade.setRouteDisrupted(a, b, TradeDisruptionReason.WAR, gameTime);
      trade.setRouteDisrupted(b, a, TradeDisruptionReason.WAR, gameTime);
   }

   private void clearTradeBetween(ServerLevel level, FactionId a, FactionId b, long gameTime) {
      TradeManager trade = EconomySavedData.get(level).getTradeManager();
      trade.clearRouteDisruption(a, b, gameTime);
      trade.clearRouteDisruption(b, a, gameTime);
   }

   private void inviteEligibleAllies(ServerLevel level, War war, FactionId attacker, FactionId defender, long gameTime) {
      DiplomaticManager diplomacy = DiplomacySavedData.get(level).getDiplomaticManager();
      // Eligible allies/defensive partners are recorded for explicit joinWar(...) — never auto-forced.
      for (FactionId partner : diplomacy.getDefensivePactPartners(defender)) {
         if (partner.equals(attacker) || war.isParticipant(partner)) {
            continue;
         }

         war.recordHistory(
            new WarHistoryEntry(gameTime, WarEventType.ALLY_NOTIFIED, partner, defender, null, null, null, 0, "defensive_pact")
         );
      }

      for (FactionId ally : diplomacy.getAllies(attacker)) {
         if (ally.equals(defender) || war.isParticipant(ally)) {
            continue;
         }

         war.recordHistory(
            new WarHistoryEntry(gameTime, WarEventType.ALLY_NOTIFIED, ally, attacker, null, null, null, 0, "alliance")
         );
      }
   }

   private void mobilizeFaction(ServerLevel level, War war, FactionId factionId, long gameTime) {
      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      military.mobilize(level, factionId, MobilizationState.FULL);
      military.setAlertLevel(level, factionId, MilitaryAlertLevel.HIGH);
      this.ensureArmyStates(level, war, gameTime);
      for (Army army : military.getArmies(factionId)) {
         if (army.getState() == ArmyState.DISBANDED || army.getState() == ArmyState.DISABLED) {
            continue;
         }

         ArmyWartimeState state = this.armyStates.get(army.getId());
         if (state != null) {
            state.assignWar(war.getId());
            war.addArmy(army.getId());
            military.setArmyOrganizationalState(level, army.getId(), ArmyState.MUSTERED);
            war.recordHistory(
               new WarHistoryEntry(gameTime, WarEventType.ARMY_MOBILIZED, factionId, null, army.getId(), null, null, army.getCachedStrength(), null)
            );
         }
      }

      // Mobilize reserve units into an army when none exist and reserves are available.
      if (military.getArmyCount(factionId) == 0) {
         var org = military.getOrCreateOrganization(factionId);
         if (!org.getReserveUnitIds().isEmpty()) {
            Army army = military.createArmy(level, factionId, "War Host", null);
            if (army != null) {
               for (UnitId unitId : new ArrayList<>(org.getReserveUnitIds())) {
                  military.assignUnitToArmy(level, unitId, army.getId());
               }

               this.ensureArmyStateFor(level, war, army, gameTime);
               war.addArmy(army.getId());
               war.recordHistory(
                  new WarHistoryEntry(gameTime, WarEventType.ARMY_MOBILIZED, factionId, null, army.getId(), null, null, army.getCachedStrength(), "from_reserve")
               );
            }
         }
      }
   }

   private void ensureArmyStates(ServerLevel level, War war, long gameTime) {
      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      for (FactionId factionId : war.getParticipants().keySet()) {
         if (!war.isParticipant(factionId)) {
            continue;
         }

         for (Army army : military.getArmies(factionId)) {
            this.ensureArmyStateFor(level, war, army, gameTime);
         }
      }
   }

   private void ensureArmyStateFor(ServerLevel level, War war, Army army, long gameTime) {
      if (army == null || army.getState() == ArmyState.DISBANDED) {
         return;
      }

      ArmyWartimeState state = this.armyStates.get(army.getId());
      if (state == null) {
         BlockPos pos = this.resolveArmySpawnPosition(level, army);
         ResourceLocation dimension = level.dimension().location();
         state = new ArmyWartimeState(army.getId(), army.getFactionId(), dimension, pos, gameTime);
         Commander commander = army.getCommanderId() == null
            ? null
            : MilitarySavedData.get(level).getMilitaryManager().getCommander(army.getCommanderId());
         int leadership = commander == null ? 0 : commander.getLeadership();
         state.setMorale(MoraleCalculator.initialArmyMorale(army.getCachedReadinessPercent(), WarConfig.SUPPLY_BASE, leadership));
         this.armyStates.put(army.getId(), state);
      }

      state.assignWar(war.getId());
      war.addArmy(army.getId());
   }

   private BlockPos resolveArmySpawnPosition(ServerLevel level, Army army) {
      VillageId settlementId = army.getStationedSettlementId() != null ? army.getStationedSettlementId() : army.getOriginSettlementId();
      if (settlementId != null) {
         Village village = VillageSavedData.get(level).getVillageManager().getVillage(settlementId);
         if (village != null) {
            return ArmyMovementController.snapToSurface(level, village.getCenter());
         }
      }

      Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(army.getFactionId());
      if (faction != null) {
         for (VillageId id : faction.getSettlementIds()) {
            Village village = VillageSavedData.get(level).getVillageManager().getVillage(id);
            if (village != null) {
               return ArmyMovementController.snapToSurface(level, village.getCenter());
            }
         }
      }

      return ArmyMovementController.snapToSurface(level, BlockPos.ZERO);
   }

   private void evaluateAi(ServerLevel level, War war, long gameTime) {
      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      for (ArmyId armyId : war.getParticipatingArmyIds()) {
         Army army = military.getArmy(armyId);
         ArmyWartimeState state = this.armyStates.get(armyId);
         if (army == null || state == null || state.isDestroyed() || state.isInBattle()) {
            continue;
         }

         if (gameTime - state.getLastAiEvaluationGameTime() < WarConfig.AI_EVALUATION_INTERVAL_TICKS) {
            continue;
         }

         MilitaryAiEvaluator.EvaluatedObjective evaluated = MilitaryAiEvaluator.evaluate(level, war, army, state, military, this, gameTime);
         state.setObjective(evaluated.objective());
         state.setOrder(evaluated.order());
         state.setLastAiEvaluationGameTime(gameTime);
         war.recordHistory(
            new WarHistoryEntry(
               gameTime,
               WarEventType.OBJECTIVE_ASSIGNED,
               army.getFactionId(),
               null,
               armyId,
               null,
               evaluated.objective().getTargetSettlementId(),
               evaluated.objective().getPriority(),
               evaluated.objective().getType().name()
            )
         );
      }
   }

   private void moveArmies(ServerLevel level, War war, long gameTime) {
      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      for (ArmyId armyId : war.getParticipatingArmyIds()) {
         ArmyWartimeState state = this.armyStates.get(armyId);
         Army army = military.getArmy(armyId);
         if (state == null || army == null || state.isDestroyed() || state.isInBattle()) {
            continue;
         }

         if (gameTime - state.getLastMovedGameTime() < WarConfig.ARMY_MOVE_INTERVAL_TICKS) {
            continue;
         }

         BlockPos destination = this.resolveOrderDestination(level, state);
         if (destination == null) {
            continue;
         }

         boolean arrived = ArmyMovementController.advanceToward(level, state, destination, gameTime);
         military.setArmyOrganizationalState(level, armyId, arrived ? ArmyState.STATIONED : ArmyState.MOVING);
         this.consumeSupplyForMovement(state);
      }
   }

   @Nullable
   private BlockPos resolveOrderDestination(ServerLevel level, ArmyWartimeState state) {
      ArmyOrder order = state.getOrder();
      if (order == null || order.getType() == ArmyOrderType.NONE || order.getType() == ArmyOrderType.HOLD) {
         return null;
      }

      if (order.getTargetPos() != null) {
         return order.getTargetPos();
      }

      if (order.getTargetSettlementId() != null) {
         Village village = VillageSavedData.get(level).getVillageManager().getVillage(order.getTargetSettlementId());
         return village == null ? null : village.getCenter();
      }

      if (order.getTargetArmyId() != null) {
         ArmyWartimeState target = this.armyStates.get(order.getTargetArmyId());
         return target == null || target.isDestroyed() ? null : target.getPosition();
      }

      if (order.getTargetBattleId() != null) {
         Battle battle = this.battles.get(order.getTargetBattleId());
         return battle == null ? null : battle.getLocation();
      }

      return state.getDestination();
   }

   private void consumeSupplyForMovement(ArmyWartimeState state) {
      state.setSupplyPercent(Math.max(WarConfig.SUPPLY_MIN, state.getSupplyPercent() - 1));
   }

   private void detectBattles(ServerLevel level, War war, long gameTime) {
      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      List<ArmyId> armyIds = new ArrayList<>(war.getParticipatingArmyIds());
      for (int i = 0; i < armyIds.size(); i++) {
         ArmyWartimeState aState = this.armyStates.get(armyIds.get(i));
         Army aArmy = military.getArmy(armyIds.get(i));
         if (aState == null || aArmy == null || aState.isDestroyed()) {
            continue;
         }

         WarSideId aSide = war.getSide(aArmy.getFactionId());
         if (aSide == null) {
            continue;
         }

         // Settlement defense detection — uses military controller (occupier) when occupied.
         TerritoryManager territories = TerritorySavedData.get(level).getTerritoryManager();
         for (FactionId enemyFaction : war.getActiveFactionIds(aSide.opposite())) {
            for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
               FactionId controller = this.settlementMilitaryController(territories, village);
               if (controller == null || !controller.equals(enemyFaction)) {
                  continue;
               }

               if (!ArmyMovementController.sameDimension(aState.getDimensionId(), level.dimension().location())) {
                  continue;
               }

               if (ArmyMovementController.distance(aState.getPosition(), village.getCenter()) <= WarConfig.ENGAGEMENT_RANGE_BLOCKS) {
                  this.startOrJoinSettlementBattle(level, war, aArmy, aState, aSide, village, controller, gameTime);
               }
            }
         }

         for (int j = i + 1; j < armyIds.size(); j++) {
            ArmyWartimeState bState = this.armyStates.get(armyIds.get(j));
            Army bArmy = military.getArmy(armyIds.get(j));
            if (bState == null || bArmy == null || bState.isDestroyed()) {
               continue;
            }

            WarSideId bSide = war.getSide(bArmy.getFactionId());
            if (bSide == null || aSide == bSide) {
               continue;
            }

            if (!ArmyMovementController.sameDimension(aState.getDimensionId(), bState.getDimensionId())) {
               continue;
            }

            if (gameTime < aState.getRetreatImmunityUntil() || gameTime < bState.getRetreatImmunityUntil()) {
               continue;
            }

            if (ArmyMovementController.distance(aState.getPosition(), bState.getPosition()) <= WarConfig.ENGAGEMENT_RANGE_BLOCKS) {
               this.startOrJoinFieldBattle(level, war, aArmy, aState, aSide, bArmy, bState, bSide, gameTime);
            }
         }
      }
   }

   private void startOrJoinSettlementBattle(
      ServerLevel level,
      War war,
      Army attackerArmy,
      ArmyWartimeState attackerState,
      WarSideId attackerSide,
      Village village,
      FactionId defendingFactionId,
      long gameTime
   ) {
      Battle existing = this.findOpenBattleNear(war, attackerState.getPosition(), village.getId());
      if (existing != null) {
         this.joinBattle(level, existing, attackerArmy, attackerState, attackerSide, gameTime);
         return;
      }

      if (attackerState.isInBattle()) {
         return;
      }

      FactionId defenderFaction = defendingFactionId != null ? defendingFactionId : village.getFactionId();
      Battle battle = new Battle(
         BattleId.random(),
         war.getId(),
         attackerState.getDimensionId(),
         village.getCenter().immutable(),
         gameTime,
         village.getId()
      );
      boolean nearDefenses = defenderFaction != null && MilitarySavedData.get(level).getMilitaryManager().getOrganization(defenderFaction) != null;
      battle.setTerrain(TerrainType.classify(level, village.getCenter(), true, nearDefenses));
      this.battles.put(battle.getId(), battle);
      war.addBattle(battle.getId());
      this.joinBattle(level, battle, attackerArmy, attackerState, attackerSide, gameTime);

      // Attach garrisoned units as defender presence via nearby controller armies / station.
      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      if (defenderFaction != null) {
         for (Army defenderArmy : military.getArmies(defenderFaction)) {
            ArmyWartimeState defenderState = this.armyStates.get(defenderArmy.getId());
            if (defenderState == null) {
               this.ensureArmyStateFor(level, war, defenderArmy, gameTime);
               defenderState = this.armyStates.get(defenderArmy.getId());
            }

            if (defenderState != null
               && !defenderState.isDestroyed()
               && ArmyMovementController.distance(defenderState.getPosition(), village.getCenter()) <= WarConfig.BATTLE_JOIN_RANGE_BLOCKS) {
               WarSideId defenderSide = war.getSide(defenderArmy.getFactionId());
               if (defenderSide != null) {
                  this.joinBattle(level, battle, defenderArmy, defenderState, defenderSide, gameTime);
               }
            }
         }
      }

      battle.setState(BattleState.ACTIVE);
      war.recordHistory(
         new WarHistoryEntry(
            gameTime,
            WarEventType.BATTLE_STARTED,
            attackerArmy.getFactionId(),
            defenderFaction,
            attackerArmy.getId(),
            battle.getId(),
            village.getId(),
            0,
            "settlement_defense"
         )
      );
      battle.recordHistory(
         new WarHistoryEntry(gameTime, WarEventType.BATTLE_STARTED, attackerArmy.getFactionId(), defenderFaction, null, battle.getId(), village.getId(), 0, null)
      );
   }

   @Nullable
   private FactionId settlementMilitaryController(TerritoryManager territories, Village village) {
      if (village == null) {
         return null;
      }

      Territory territory = territories.getTerritoryForSettlement(village.getId());
      if (territory != null && territory.getControllerFactionId() != null) {
         return territory.getControllerFactionId();
      }

      return village.getFactionId();
   }

   private void startOrJoinFieldBattle(
      ServerLevel level,
      War war,
      Army aArmy,
      ArmyWartimeState aState,
      WarSideId aSide,
      Army bArmy,
      ArmyWartimeState bState,
      WarSideId bSide,
      long gameTime
   ) {
      Battle existing = this.findOpenBattleNear(war, aState.getPosition(), null);
      if (existing != null) {
         this.joinBattle(level, existing, aArmy, aState, aSide, gameTime);
         this.joinBattle(level, existing, bArmy, bState, bSide, gameTime);
         return;
      }

      if (aState.isInBattle() || bState.isInBattle()) {
         if (aState.isInBattle() && aState.getBattleId() != null) {
            Battle battle = this.battles.get(aState.getBattleId());
            if (battle != null && battle.getState().isOpen()) {
               this.joinBattle(level, battle, bArmy, bState, bSide, gameTime);
            }
         }

         if (bState.isInBattle() && bState.getBattleId() != null) {
            Battle battle = this.battles.get(bState.getBattleId());
            if (battle != null && battle.getState().isOpen()) {
               this.joinBattle(level, battle, aArmy, aState, aSide, gameTime);
            }
         }

         return;
      }

      BlockPos mid = new BlockPos(
         (aState.getPosition().getX() + bState.getPosition().getX()) / 2,
         (aState.getPosition().getY() + bState.getPosition().getY()) / 2,
         (aState.getPosition().getZ() + bState.getPosition().getZ()) / 2
      );
      mid = ArmyMovementController.snapToSurface(level, mid);
      Battle battle = new Battle(BattleId.random(), war.getId(), aState.getDimensionId(), mid, gameTime, null);
      battle.setTerrain(TerrainType.classify(level, mid, false, false));
      this.battles.put(battle.getId(), battle);
      war.addBattle(battle.getId());
      this.joinBattle(level, battle, aArmy, aState, aSide, gameTime);
      this.joinBattle(level, battle, bArmy, bState, bSide, gameTime);
      battle.setState(BattleState.ACTIVE);
      war.recordHistory(
         new WarHistoryEntry(gameTime, WarEventType.BATTLE_STARTED, aArmy.getFactionId(), bArmy.getFactionId(), aArmy.getId(), battle.getId(), null, 0, "field")
      );
   }

   @Nullable
   private Battle findOpenBattleNear(War war, BlockPos pos, @Nullable VillageId settlementId) {
      for (BattleId battleId : war.getBattleIds()) {
         Battle battle = this.battles.get(battleId);
         if (battle == null || !battle.getState().isOpen()) {
            continue;
         }

         if (settlementId != null && settlementId.equals(battle.getSettlementId())) {
            return battle;
         }

         if (ArmyMovementController.distance(battle.getLocation(), pos) <= WarConfig.ENGAGEMENT_RANGE_BLOCKS) {
            return battle;
         }
      }

      return null;
   }

   private void joinBattle(ServerLevel level, Battle battle, Army army, ArmyWartimeState state, WarSideId side, long gameTime) {
      if (battle == null || army == null || state == null || state.isDestroyed() || !battle.getState().isOpen()) {
         return;
      }

      if (state.isInBattle() && !battle.getId().equals(state.getBattleId())) {
         return;
      }

      if (gameTime < state.getRetreatImmunityUntil()) {
         return;
      }

      battle.addArmy(army.getId(), army.getFactionId(), side);
      state.assignBattle(battle.getId());
      MilitarySavedData.get(level).getMilitaryManager().setArmyOrganizationalState(level, army.getId(), ArmyState.MUSTERED);
   }

   private void tickBattles(ServerLevel level, War war, long gameTime) {
      for (BattleId battleId : new ArrayList<>(war.getBattleIds())) {
         Battle battle = this.battles.get(battleId);
         if (battle == null || !battle.getState().isOpen()) {
            continue;
         }

         if (gameTime - battle.getLastRoundGameTime() < WarConfig.BATTLE_ROUND_INTERVAL_TICKS) {
            continue;
         }

         this.resolveBattleRound(level, war, battle, gameTime);
      }
   }

   private void resolveBattleRound(ServerLevel level, War war, Battle battle, long gameTime) {
      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      List<Army> attackers = this.resolveArmies(military, battle.getAttackerArmyIds());
      List<Army> defenders = this.resolveArmies(military, battle.getDefenderArmyIds());
      if (attackers.isEmpty() || defenders.isEmpty()) {
         WarSideId victor = attackers.isEmpty() ? WarSideId.DEFENDER : WarSideId.ATTACKER;
         this.finishBattle(level, war, battle, victor, gameTime);
         return;
      }

      int attackerSupply = this.averageSupply(battle.getAttackerArmyIds());
      int defenderSupply = this.averageSupply(battle.getDefenderArmyIds());
      int attackerPower = CombatCalculator.sideCombatPower(
         attackers, military::getUnit, military::getCommander, battle.getAttackerMorale(), attackerSupply, battle.getTerrain(), true
      );
      int defenderPower = CombatCalculator.sideCombatPower(
         defenders, military::getUnit, military::getCommander, battle.getDefenderMorale(), defenderSupply, battle.getTerrain(), false
      );
      attackerPower = CombatCalculator.applyBoundedRandomness(attackerPower, battle.getId().uuid().getLeastSignificantBits() ^ battle.getRoundIndex());
      defenderPower = CombatCalculator.applyBoundedRandomness(defenderPower, battle.getId().uuid().getMostSignificantBits() ^ battle.getRoundIndex());

      int attackerSoldiers = this.countSoldiers(military, battle.getAttackerArmyIds());
      int defenderSoldiers = this.countSoldiers(military, battle.getDefenderArmyIds());
      int attackerLoss = CasualtyCalculator.casualtiesForSide(attackerPower, defenderPower, attackerSoldiers, battle.getRoundIndex());
      int defenderLoss = CasualtyCalculator.casualtiesForSide(defenderPower, attackerPower, defenderSoldiers, battle.getRoundIndex() + 17);

      this.applyCasualties(level, war, battle, WarSideId.ATTACKER, attackerLoss, gameTime);
      this.applyCasualties(level, war, battle, WarSideId.DEFENDER, defenderLoss, gameTime);

      boolean attackerDisadvantaged = attackerPower < defenderPower;
      boolean defenderDisadvantaged = defenderPower < attackerPower;
      battle.setMorale(
         WarSideId.ATTACKER,
         MoraleCalculator.afterCasualties(battle.getAttackerMorale(), attackerLoss, Math.max(1, attackerSoldiers), attackerDisadvantaged)
      );
      battle.setMorale(
         WarSideId.DEFENDER,
         MoraleCalculator.afterCasualties(battle.getDefenderMorale(), defenderLoss, Math.max(1, defenderSoldiers), defenderDisadvantaged)
      );
      this.syncArmyMoraleFromBattle(battle);

      battle.advanceRound(gameTime);

      if (MoraleCalculator.shouldRetreat(battle.getAttackerMorale()) || this.countSoldiers(military, battle.getAttackerArmyIds()) <= 0) {
         this.retreatSide(level, war, battle, WarSideId.ATTACKER, gameTime);
         this.finishBattle(level, war, battle, WarSideId.DEFENDER, gameTime);
         return;
      }

      if (MoraleCalculator.shouldRetreat(battle.getDefenderMorale()) || this.countSoldiers(military, battle.getDefenderArmyIds()) <= 0) {
         this.retreatSide(level, war, battle, WarSideId.DEFENDER, gameTime);
         this.finishBattle(level, war, battle, WarSideId.ATTACKER, gameTime);
      }
   }

   private List<Army> resolveArmies(MilitaryManager military, Set<ArmyId> armyIds) {
      List<Army> armies = new ArrayList<>();
      for (ArmyId armyId : armyIds) {
         Army army = military.getArmy(armyId);
         ArmyWartimeState state = this.armyStates.get(armyId);
         if (army != null && state != null && !state.isDestroyed() && army.getState() != ArmyState.DISBANDED) {
            armies.add(army);
         }
      }

      return armies;
   }

   private int countSoldiers(MilitaryManager military, Set<ArmyId> armyIds) {
      int total = 0;
      for (ArmyId armyId : armyIds) {
         Army army = military.getArmy(armyId);
         if (army == null) {
            continue;
         }

         for (UnitId unitId : army.getUnitIds()) {
            MilitaryUnit unit = military.getUnit(unitId);
            if (unit != null) {
               total += unit.getSoldierCount();
            }
         }
      }

      return total;
   }

   private int averageSupply(Set<ArmyId> armyIds) {
      int total = 0;
      int count = 0;
      for (ArmyId armyId : armyIds) {
         ArmyWartimeState state = this.armyStates.get(armyId);
         if (state != null) {
            total += state.getSupplyPercent();
            count++;
         }
      }

      return count == 0 ? WarConfig.SUPPLY_BASE : total / count;
   }

   private void applyCasualties(ServerLevel level, War war, Battle battle, WarSideId side, int casualties, long gameTime) {
      if (casualties <= 0) {
         return;
      }

      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      Set<ArmyId> armyIds = side == WarSideId.ATTACKER ? battle.getAttackerArmyIds() : battle.getDefenderArmyIds();
      int remaining = casualties;
      for (ArmyId armyId : armyIds) {
         if (remaining <= 0) {
            break;
         }

         Army army = military.getArmy(armyId);
         if (army == null) {
            continue;
         }

         for (UnitId unitId : new ArrayList<>(army.getUnitIds())) {
            if (remaining <= 0) {
               break;
            }

            MilitaryUnit unit = military.getUnit(unitId);
            if (unit == null || unit.getSoldierCount() <= 0) {
               continue;
            }

            int loss = Math.min(remaining, unit.getSoldierCount());
            // Use package-visible setter via manager-owned mutation path: recreate count through release/recruit not available;
            // apply via dedicated wartime casualty helper on manager.
            this.reduceUnitSoldiers(military, unit, loss, gameTime);
            remaining -= loss;
            military.recordCasualties(level, army.getFactionId(), loss);
            WarParticipant participant = war.getParticipant(army.getFactionId());
            if (participant != null) {
               participant.addCasualtiesSuffered(loss);
            }
         }

         if (this.countSoldiers(military, Set.of(armyId)) <= 0) {
            this.destroyArmy(level, war, armyId, side, gameTime);
         }
      }

      int inflicted = casualties - Math.max(0, remaining);
      battle.addCasualties(side, inflicted);
      WarSideId opposite = side.opposite();
      for (FactionId factionId : opposite == WarSideId.ATTACKER ? battle.getAttackerFactionIds() : battle.getDefenderFactionIds()) {
         WarParticipant participant = war.getParticipant(factionId);
         if (participant != null) {
            participant.addCasualtiesInflicted(inflicted);
         }
      }

      war.adjustWarScore(
         WarScoreCalculator.casualtyDelta(
            side == WarSideId.DEFENDER ? inflicted : 0,
            side == WarSideId.ATTACKER ? inflicted : 0
         )
      );
   }

   private void reduceUnitSoldiers(MilitaryManager military, MilitaryUnit unit, int loss, long gameTime) {
      WarMilitaryBridge.reduceSoldiers(unit, loss, gameTime);
   }

   private void destroyArmy(ServerLevel level, War war, ArmyId armyId, WarSideId side, long gameTime) {
      ArmyWartimeState state = this.armyStates.get(armyId);
      if (state != null) {
         state.markDestroyed();
         state.assignBattle(null);
      }

      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      Army army = military.getArmy(armyId);
      FactionId factionId = army == null ? null : army.getFactionId();
      military.disbandArmy(level, armyId);
      war.removeArmy(armyId);
      war.adjustWarScore(WarScoreCalculator.armyDestroyedDelta(side));
      war.recordHistory(
         new WarHistoryEntry(gameTime, WarEventType.ARMY_DESTROYED, factionId, null, armyId, null, null, 0, side.name())
      );
      if (army != null && army.getCommanderId() != null) {
         // No XP on destruction.
      }
   }

   private void retreatSide(ServerLevel level, War war, Battle battle, WarSideId side, long gameTime) {
      battle.setState(BattleState.RETREATING);
      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      Set<ArmyId> armyIds = side == WarSideId.ATTACKER ? battle.getAttackerArmyIds() : battle.getDefenderArmyIds();
      for (ArmyId armyId : armyIds) {
         ArmyWartimeState state = this.armyStates.get(armyId);
         Army army = military.getArmy(armyId);
         if (state == null || army == null || state.isDestroyed()) {
            continue;
         }

         BlockPos retreat = ArmyMovementController.retreatPosition(state.getPosition(), battle.getLocation());
         retreat = ArmyMovementController.snapToSurface(level, retreat);
         state.assignBattle(null);
         state.setMorale(MoraleCalculator.afterDefeat(state.getMorale()));
         state.setRetreatImmunityUntil(gameTime + WarConfig.BATTLE_ROUND_INTERVAL_TICKS * 5L);
         this.issueArmyOrder(level, armyId, ArmyOrder.retreat(gameTime, retreat));
         war.recordHistory(
            new WarHistoryEntry(gameTime, WarEventType.ARMY_RETREATED, army.getFactionId(), null, armyId, battle.getId(), battle.getSettlementId(), 0, null)
         );
      }
   }

   private void syncArmyMoraleFromBattle(Battle battle) {
      for (ArmyId armyId : battle.getAttackerArmyIds()) {
         ArmyWartimeState state = this.armyStates.get(armyId);
         if (state != null) {
            state.setMorale(battle.getAttackerMorale());
         }
      }

      for (ArmyId armyId : battle.getDefenderArmyIds()) {
         ArmyWartimeState state = this.armyStates.get(armyId);
         if (state != null) {
            state.setMorale(battle.getDefenderMorale());
         }
      }
   }

   private void finishBattle(ServerLevel level, War war, Battle battle, @Nullable WarSideId victor, long gameTime) {
      if (battle.getState().isTerminal()) {
         return;
      }

      battle.decide(victor, gameTime);
      war.adjustWarScore(WarScoreCalculator.battleOutcomeDelta(victor));
      if (victor == WarSideId.DEFENDER && battle.getSettlementId() != null) {
         war.adjustWarScore(WarScoreCalculator.successfulDefenseDelta());
         war.recordHistory(
            new WarHistoryEntry(gameTime, WarEventType.SETTLEMENT_DEFENDED, null, null, null, battle.getId(), battle.getSettlementId(), 0, null)
         );
      }

      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      Set<ArmyId> winners = victor == WarSideId.ATTACKER ? battle.getAttackerArmyIds() : battle.getDefenderArmyIds();
      for (ArmyId armyId : winners) {
         ArmyWartimeState state = this.armyStates.get(armyId);
         Army army = military.getArmy(armyId);
         if (state != null) {
            state.assignBattle(null);
            state.setMorale(MoraleCalculator.afterVictory(state.getMorale()));
         }

         if (army != null && army.getCommanderId() != null) {
            military.awardCommanderExperience(level, army.getCommanderId(), 10 + battle.getRoundIndex());
         }
      }

      Set<ArmyId> losers = victor == WarSideId.ATTACKER ? battle.getDefenderArmyIds() : battle.getAttackerArmyIds();
      for (ArmyId armyId : losers) {
         ArmyWartimeState state = this.armyStates.get(armyId);
         if (state != null && state.getBattleId() != null && state.getBattleId().equals(battle.getId())) {
            state.assignBattle(null);
         }
      }

      if (victor != null && battle.getSettlementId() != null) {
         this.applySettlementBattleTerritoryOutcome(level, war, battle, victor, gameTime);
      }

      battle.finish(gameTime);
      battle.recordHistory(
         new WarHistoryEntry(gameTime, WarEventType.BATTLE_ENDED, null, null, null, battle.getId(), battle.getSettlementId(), war.getWarScore(), victor == null ? "draw" : victor.name())
      );
      war.recordHistory(
         new WarHistoryEntry(gameTime, WarEventType.BATTLE_ENDED, null, null, null, battle.getId(), battle.getSettlementId(), war.getWarScore(), victor == null ? "draw" : victor.name())
      );
      this.progressGoalsAfterBattle(war, victor, gameTime);
   }

   private void applySettlementBattleTerritoryOutcome(ServerLevel level, War war, Battle battle, WarSideId victor, long gameTime) {
      VillageId settlementId = battle.getSettlementId();
      if (settlementId == null) {
         return;
      }

      FactionId capturer = this.primaryFactionFromBattleSide(battle, victor);
      if (capturer == null) {
         return;
      }

      TerritorySavedData.get(level).getTerritoryManager().captureSettlement(level, settlementId, capturer, war.getId(), battle.getId());
      if (victor == WarSideId.ATTACKER) {
         war.recordHistory(
            new WarHistoryEntry(gameTime, WarEventType.SETTLEMENT_CAPTURED, capturer, null, null, battle.getId(), settlementId, 0, "occupation")
         );
      }
   }

   @Nullable
   private FactionId primaryFactionFromBattleSide(Battle battle, WarSideId side) {
      Set<FactionId> factions = side == WarSideId.ATTACKER ? battle.getAttackerFactionIds() : battle.getDefenderFactionIds();
      if (factions.isEmpty()) {
         return null;
      }

      return factions.iterator().next();
   }

   private void progressGoalsAfterBattle(War war, @Nullable WarSideId victor, long gameTime) {
      if (victor == null) {
         return;
      }

      for (WarGoal goal : war.getGoals().values()) {
         if (goal.getStatus().isTerminal()) {
            continue;
         }

         WarSideId ownerSide = war.getSide(goal.getInitiatingFactionId());
         if (ownerSide == null) {
            continue;
         }

         if (goal.getType() == WarGoalType.DEFEAT_ENEMY_ARMY || goal.getType() == WarGoalType.DESTROY_MILITARY_FORCE || goal.getType() == WarGoalType.MILITARY_SUPERIORITY) {
            if (ownerSide == victor) {
               goal.setProgress(Math.min(100, goal.getProgressPercent() + 25), gameTime);
               war.adjustWarScore(WarScoreCalculator.goalProgressDelta(ownerSide));
               war.recordHistory(
                  new WarHistoryEntry(gameTime, WarEventType.WAR_GOAL_PROGRESSED, goal.getInitiatingFactionId(), null, null, null, null, goal.getProgressPercent(), goal.getType().name())
               );
               if (goal.getStatus() == WarGoalStatus.COMPLETED) {
                  war.recordHistory(
                     new WarHistoryEntry(gameTime, WarEventType.WAR_GOAL_COMPLETED, goal.getInitiatingFactionId(), null, null, null, null, 100, goal.getType().name())
                  );
               }
            }
         }

         if (goal.getType() == WarGoalType.DEFEND_SETTLEMENT && ownerSide == victor) {
            goal.setProgress(Math.min(100, goal.getProgressPercent() + 20), gameTime);
         }

         if ((goal.getType() == WarGoalType.CONQUER_SETTLEMENT || goal.getType() == WarGoalType.LIBERATE_SETTLEMENT)
            && ownerSide == victor
            && victor == WarSideId.ATTACKER) {
            goal.setProgress(Math.min(100, goal.getProgressPercent() + 40), gameTime);
            war.adjustWarScore(WarScoreCalculator.goalProgressDelta(ownerSide));
            war.recordHistory(
               new WarHistoryEntry(
                  gameTime,
                  WarEventType.WAR_GOAL_PROGRESSED,
                  goal.getInitiatingFactionId(),
                  null,
                  null,
                  null,
                  goal.getTargetSettlementId(),
                  goal.getProgressPercent(),
                  goal.getType().name()
               )
            );
            if (goal.getStatus() == WarGoalStatus.COMPLETED) {
               war.recordHistory(
                  new WarHistoryEntry(
                     gameTime,
                     WarEventType.WAR_GOAL_COMPLETED,
                     goal.getInitiatingFactionId(),
                     null,
                     null,
                     null,
                     goal.getTargetSettlementId(),
                     100,
                     goal.getType().name()
                  )
               );
            }
         }
      }
   }

   private void pulseReinforcements(ServerLevel level, War war, long gameTime) {
      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      for (ArmyId armyId : war.getParticipatingArmyIds()) {
         ArmyWartimeState state = this.armyStates.get(armyId);
         Army army = military.getArmy(armyId);
         if (state == null || army == null || state.isDestroyed() || state.isInBattle()) {
            continue;
         }

         if (gameTime - state.getLastReinforcementGameTime() < WarConfig.REINFORCEMENT_INTERVAL_TICKS) {
            continue;
         }

         if (military.getAvailableManpower(army.getFactionId()) < WarConfig.REINFORCEMENT_SOLDIERS_PER_PULSE) {
            continue;
         }

         // Reinforcement progresses by recruiting a small unit into the army when manpower allows.
         ResourceLocation culture = null;
         Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(army.getFactionId());
         if (faction != null) {
            culture = faction.getCultureId();
         }

         var definition = org.millenaire.faction.military.UnitTypeRegistry.defaultInfantry(culture);
         MilitaryUnit unit = military.recruit(level, army.getFactionId(), definition.id(), WarConfig.REINFORCEMENT_SOLDIERS_PER_PULSE, army.getOriginSettlementId());
         if (unit != null) {
            military.assignUnitToArmy(level, unit.getId(), army.getId());
            state.setLastReinforcementGameTime(gameTime);
            state.setSupplyPercent(Math.min(WarConfig.SUPPLY_MAX, state.getSupplyPercent() + 5));
            war.recordHistory(
               new WarHistoryEntry(gameTime, WarEventType.REINFORCEMENT_ARRIVED, army.getFactionId(), null, army.getId(), null, null, unit.getSoldierCount(), null)
            );
         }
      }
   }

   private void updateWarGoals(ServerLevel level, War war, long gameTime) {
      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      for (WarGoal goal : war.getGoals().values()) {
         if (goal.getStatus().isTerminal()) {
            continue;
         }

         if (goal.getType() == WarGoalType.MILITARY_SUPERIORITY) {
            WarSideId side = war.getSide(goal.getInitiatingFactionId());
            if (side == null) {
               continue;
            }

            int own = 0;
            int enemy = 0;
            for (FactionId factionId : war.getActiveFactionIds(side)) {
               own += military.getMilitaryStrength(factionId);
            }

            for (FactionId factionId : war.getActiveFactionIds(side.opposite())) {
               enemy += military.getMilitaryStrength(factionId);
            }

            if (enemy <= 0 && own > 0) {
               goal.setProgress(100, gameTime);
            } else if (own > enemy * 2) {
               goal.setProgress(Math.max(goal.getProgressPercent(), 70), gameTime);
            }
         }

         if (goal.getType() == WarGoalType.FORCE_PEACE && Math.abs(war.getWarScore()) >= WarConfig.DECISIVE_WAR_SCORE) {
            goal.setProgress(Math.max(goal.getProgressPercent(), 80), gameTime);
         }
      }
   }

   private void updateExhaustion(War war, long gameTime) {
      long duration = Math.max(0L, gameTime - war.getStartedGameTime());
      Map<WarSideId, Integer> lostBattles = new HashMap<>();
      lostBattles.put(WarSideId.ATTACKER, 0);
      lostBattles.put(WarSideId.DEFENDER, 0);
      for (BattleId battleId : war.getBattleIds()) {
         Battle battle = this.battles.get(battleId);
         if (battle != null && battle.getState().isTerminal() && battle.getVictorSide() != null) {
            WarSideId loser = battle.getVictorSide().opposite();
            lostBattles.put(loser, lostBattles.get(loser) + 1);
         }
      }

      for (WarParticipant participant : war.getParticipants().values()) {
         if (!participant.isActive()) {
            continue;
         }

         int lost = lostBattles.getOrDefault(participant.getSide(), 0);
         participant.setWarExhaustion(WarExhaustionCalculator.compute(participant, duration, lost));
      }
   }

   private void evaluateAutomaticEnd(ServerLevel level, War war, long gameTime) {
      if (war.getState() == WarState.PEACE_NEGOTIATION) {
         return;
      }

      if (Math.abs(war.getWarScore()) >= WarConfig.DECISIVE_WAR_SCORE) {
         PeaceOutcome outcome = war.getWarScore() >= WarConfig.DECISIVE_WAR_SCORE ? PeaceOutcome.ATTACKER_VICTORY : PeaceOutcome.DEFENDER_VICTORY;
         this.acceptPeace(level, war.getId(), outcome, "decisive_military_result", "war_score");
         return;
      }

      int attackerPressure = this.peacePressure(war.getId(), WarSideId.ATTACKER);
      int defenderPressure = this.peacePressure(war.getId(), WarSideId.DEFENDER);
      if (WarExhaustionCalculator.shouldSeekPeace(attackerPressure) && WarExhaustionCalculator.shouldSeekPeace(defenderPressure)) {
         this.acceptPeace(level, war.getId(), PeaceOutcome.EXHAUSTION, "mutual_exhaustion", "war_exhaustion");
         return;
      }

      boolean attackersAlive = !war.getActiveFactionIds(WarSideId.ATTACKER).isEmpty();
      boolean defendersAlive = !war.getActiveFactionIds(WarSideId.DEFENDER).isEmpty();
      if (!attackersAlive || !defendersAlive) {
         PeaceOutcome outcome = attackersAlive ? PeaceOutcome.ATTACKER_VICTORY : PeaceOutcome.DEFENDER_VICTORY;
         this.acceptPeace(level, war.getId(), outcome, "side_eliminated", "no_active_participants");
      }
   }

   private void evaluateWarContinuation(ServerLevel level, War war, long gameTime) {
      boolean attackersAlive = !war.getActiveFactionIds(WarSideId.ATTACKER).isEmpty();
      boolean defendersAlive = !war.getActiveFactionIds(WarSideId.DEFENDER).isEmpty();
      if (!attackersAlive || !defendersAlive) {
         PeaceOutcome outcome = attackersAlive ? PeaceOutcome.ATTACKER_VICTORY : (!defendersAlive && !attackersAlive ? PeaceOutcome.WHITE_PEACE : PeaceOutcome.DEFENDER_VICTORY);
         this.endWar(level, war, outcome, gameTime);
      }
   }

   private void endWar(ServerLevel level, War war, PeaceOutcome outcome, long gameTime) {
      if (war.getState() == WarState.ENDED) {
         return;
      }

      this.pauseOpenBattles(war, gameTime);
      for (BattleId battleId : war.getBattleIds()) {
         Battle battle = this.battles.get(battleId);
         if (battle != null && battle.getState().isOpen()) {
            battle.finish(gameTime);
         }
      }

      PeaceOutcome resolved = outcome == null ? PeaceOutcome.WHITE_PEACE : outcome;
      if (war.getPeaceSettlement() == null) {
         war.setPeaceSettlement(new PeaceSettlement(war.getId(), resolved, war.getWarScore(), gameTime, null, "war_end"));
      }

      war.end(resolved, gameTime);
      war.recordHistory(
         new WarHistoryEntry(gameTime, WarEventType.WAR_ENDED, war.getInitiatorFactionId(), war.getPrimaryTargetFactionId(), null, null, null, war.getWarScore(), resolved.name())
      );

      DiplomaticManager diplomacy = DiplomacySavedData.get(level).getDiplomaticManager();
      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      for (WarParticipant participant : war.getParticipants().values()) {
         military.demobilize(level, participant.getFactionId());
         military.setAlertLevel(level, participant.getFactionId(), MilitaryAlertLevel.NORMAL);
         this.clearFactionWarObjectives(level, war, participant.getFactionId(), gameTime);
      }

      // Restore peace between opposing active participants without territorial terms.
      for (WarParticipant left : war.getParticipants().values()) {
         for (WarParticipant right : war.getParticipants().values()) {
            if (left.getSide() == right.getSide() || left.getFactionId().equals(right.getFactionId())) {
               continue;
            }

            diplomacy.terminateActiveAgreement(level, AgreementType.EMBARGO, left.getFactionId(), right.getFactionId(), AgreementTerminationReason.SUPERSEDED);
            this.clearTradeBetween(level, left.getFactionId(), right.getFactionId(), gameTime);
            diplomacy.establishPeace(level, left.getFactionId(), right.getFactionId(), 0L);
         }
      }

      RelationshipSavedData.get(level)
         .getRelationshipManager()
         .increaseRelationship(level, war.getInitiatorFactionId(), war.getPrimaryTargetFactionId(), WarConfig.RELATIONSHIP_PEACE, RelationshipChangeReason.PEACE);
      RelationshipSavedData.get(level)
         .getRelationshipManager()
         .increaseRelationship(level, war.getPrimaryTargetFactionId(), war.getInitiatorFactionId(), WarConfig.RELATIONSHIP_PEACE, RelationshipChangeReason.PEACE);

      if (resolved == PeaceOutcome.ATTACKER_VICTORY) {
         RelationshipSavedData.get(level)
            .getRelationshipManager()
            .decreaseRelationship(level, war.getPrimaryTargetFactionId(), war.getInitiatorFactionId(), Math.abs(WarConfig.RELATIONSHIP_MAJOR_VICTORY), RelationshipChangeReason.WAR);
      } else if (resolved == PeaceOutcome.DEFENDER_VICTORY) {
         RelationshipSavedData.get(level)
            .getRelationshipManager()
            .decreaseRelationship(level, war.getInitiatorFactionId(), war.getPrimaryTargetFactionId(), Math.abs(WarConfig.RELATIONSHIP_MAJOR_DEFEAT), RelationshipChangeReason.WAR);
      }

      // Phase 7: apply territorial peace terms from occupations and war goals.
      TerritorySavedData.get(level).getTerritoryManager().finalizePeaceFromWar(level, war, resolved, "war_end");

      // Phase 8: war outcomes feed leader legitimacy / political pressure.
      try {
         PoliticsSavedData.get(level).getPoliticsManager().onWarEnded(level, war, resolved);
      } catch (Exception e) {
         LOGGER.error("[Millenaire] Politics war-end hook failed for war {}: {}", war.getId(), e.toString());
      }

      // Phase 9: linked internal conflicts resolve from the same war outcome.
      try {
         org.millenaire.faction.conflict.ConflictSavedData.get(level)
            .getConflictManager()
            .onLinkedWarEnded(level, war, resolved);
      } catch (Exception e) {
         LOGGER.error("[Millenaire] Internal conflict war-end hook failed for war {}: {}", war.getId(), e.toString());
      }

      LOGGER.info("[Millenaire] War {} ended ({}) score={}", war.getId(), resolved, war.getWarScore());
   }

   private PeaceOutcome deriveOutcome(War war) {
      if (war.getWarScore() >= WarConfig.DECISIVE_WAR_SCORE / 2) {
         return PeaceOutcome.ATTACKER_VICTORY;
      }

      if (war.getWarScore() <= -WarConfig.DECISIVE_WAR_SCORE / 2) {
         return PeaceOutcome.DEFENDER_VICTORY;
      }

      return PeaceOutcome.WHITE_PEACE;
   }

   private void pauseOpenBattles(War war, long gameTime) {
      for (BattleId battleId : war.getBattleIds()) {
         Battle battle = this.battles.get(battleId);
         if (battle != null && battle.getState().isOpen()) {
            battle.setState(BattleState.DECIDED);
            battle.finish(gameTime);
            for (ArmyId armyId : battle.getAttackerArmyIds()) {
               ArmyWartimeState state = this.armyStates.get(armyId);
               if (state != null) {
                  state.assignBattle(null);
               }
            }

            for (ArmyId armyId : battle.getDefenderArmyIds()) {
               ArmyWartimeState state = this.armyStates.get(armyId);
               if (state != null) {
                  state.assignBattle(null);
               }
            }
         }
      }
   }

   private void clearFactionWarObjectives(ServerLevel level, War war, FactionId factionId, long gameTime) {
      MilitaryManager military = MilitarySavedData.get(level).getMilitaryManager();
      for (Army army : military.getArmies(factionId)) {
         ArmyWartimeState state = this.armyStates.get(army.getId());
         if (state == null) {
            continue;
         }

         if (war.getId().equals(state.getWarId())) {
            state.assignWar(null);
            state.assignBattle(null);
            state.setObjective(null);
            state.setOrder(ArmyOrder.hold(gameTime, state.getPosition()));
            military.setArmyOrganizationalState(level, army.getId(), ArmyState.RESERVE);
         }
      }
   }
}
