package org.millenaire.faction.politics;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionManager;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.economy.EconomicBalanceLevel;
import org.millenaire.faction.economy.EconomyQueries;
import org.millenaire.faction.relationship.RelationshipChangeReason;
import org.millenaire.faction.relationship.RelationshipSavedData;
import org.millenaire.faction.territory.Territory;
import org.millenaire.faction.territory.TerritoryQueries;
import org.millenaire.faction.war.War;
import org.millenaire.faction.war.WarParticipant;
import org.millenaire.faction.war.WarQueries;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

/**
 * Authoritative Phase 8 internal politics API.
 */
public final class PoliticsManager {
   private static final Logger LOGGER = LogUtils.getLogger();

   private final Map<FactionId, FactionPoliticalState> states = new LinkedHashMap<>();
   private final Map<GovernmentId, Government> governments = new LinkedHashMap<>();
   private final List<PoliticalHistoryEntry> globalHistory = new ArrayList<>();
   private final Runnable markDirty;
   private long lastUpdateGameTime;

   PoliticsManager(Runnable markDirty) {
      this.markDirty = Objects.requireNonNull(markDirty, "markDirty");
   }

   @Nullable
   public FactionPoliticalState getState(FactionId factionId) {
      return factionId == null ? null : this.states.get(factionId);
   }

   @Nullable
   public Government getGovernment(GovernmentId id) {
      return id == null ? null : this.governments.get(id);
   }

   @Nullable
   public Government getActiveGovernment(FactionId factionId) {
      FactionPoliticalState state = this.getState(factionId);
      if (state == null || state.getActiveGovernmentId() == null) {
         return null;
      }

      return this.governments.get(state.getActiveGovernmentId());
   }

   @Nullable
   public PoliticalLeader getLeader(FactionId factionId) {
      Government government = this.getActiveGovernment(factionId);
      if (government == null || government.getCurrentLeaderId() == null) {
         return null;
      }

      FactionPoliticalState state = this.getState(factionId);
      return state == null ? null : state.getLeaders().get(government.getCurrentLeaderId());
   }

   public Collection<FactionPoliticalState> getAllStates() {
      return Collections.unmodifiableCollection(this.states.values());
   }

   public Collection<Government> getAllGovernments() {
      return Collections.unmodifiableCollection(this.governments.values());
   }

   public List<PoliticalHistoryEntry> getGlobalHistory() {
      return Collections.unmodifiableList(new ArrayList<>(this.globalHistory));
   }

   public long getLastUpdateGameTime() {
      return this.lastUpdateGameTime;
   }

   void setLastUpdateGameTime(long gameTime) {
      this.lastUpdateGameTime = gameTime;
   }

   public FactionPoliticalState ensurePolitics(ServerLevel level, FactionId factionId) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(factionId, "factionId");
      FactionPoliticalState existing = this.states.get(factionId);
      if (existing != null && existing.getActiveGovernmentId() != null && this.governments.containsKey(existing.getActiveGovernmentId())) {
         return existing;
      }

      return this.bootstrapFaction(level, factionId);
   }

   public void ensureAllPolitics(ServerLevel level) {
      for (Faction faction : FactionSavedData.get(level).getFactionManager().getAllFactions()) {
         this.ensurePolitics(level, faction.getId());
      }
   }

   public void tick(ServerLevel level) {
      Objects.requireNonNull(level, "level");
      long gameTime = level.getGameTime();
      if (this.lastUpdateGameTime > 0L && gameTime - this.lastUpdateGameTime < PoliticsConfig.POLITICS_UPDATE_INTERVAL_TICKS) {
         return;
      }

      this.lastUpdateGameTime = gameTime;
      for (FactionId factionId : new ArrayList<>(this.states.keySet())) {
         if (!FactionSavedData.get(level).getFactionManager().exists(factionId)) {
            continue;
         }

         this.tickFaction(level, factionId, gameTime);
      }

      this.markDirty.run();
   }

   public boolean changePolicy(ServerLevel level, FactionId factionId, PolicyType type, PolicyStance stance) {
      Objects.requireNonNull(level, "level");
      Government government = this.getActiveGovernment(factionId);
      FactionPoliticalState state = this.getState(factionId);
      if (government == null || state == null || type == null || stance == null || !government.isActive()) {
         return false;
      }

      long gameTime = level.getGameTime();
      if (gameTime - state.getLastDecisionGameTime() < PoliticsConfig.DECISION_COOLDOWN_TICKS) {
         return false;
      }

      GovernmentPolicy policy = government.getPolicy(type);
      if (policy == null) {
         policy = new GovernmentPolicy(type, stance, gameTime);
         government.putPolicy(policy);
      } else {
         policy.setStance(stance, gameTime);
      }

      if (type == PolicyType.CENTRALIZATION) {
         government.setCentralization(PolicyEffects.centralizationTarget(government));
         this.syncLocalAutonomy(level, state, government);
      }

      for (PoliticalGroup group : state.getGroups().values()) {
         if (!group.isActive()) {
            continue;
         }

         group.setApproval(group.getApproval() + PolicyEffects.groupApprovalDelta(group, type, stance));
      }

      government.setStability(government.getStability() + PolicyEffects.stabilityDelta(type, stance));
      PoliticalDecision decision = new PoliticalDecision(DecisionId.random(), factionId, DecisionType.CHANGE_POLICY, gameTime, government.getCurrentLeaderId(), null);
      decision.finalizeOutcome(true, 5, 50, "policy_change", type.name() + "=" + stance.name(), "applied");
      state.addDecision(decision);
      state.setLastDecisionGameTime(gameTime);
      this.record(
         new PoliticalHistoryEntry(
            gameTime, PoliticalEventType.POLICY_CHANGED, factionId, government.getId(), government.getCurrentLeaderId(), null, null, null, decision.getId(), stance.intensity(), type.name()
         ),
         state
      );
      this.recalculatePower(level, state, government, gameTime);
      this.markDirty.run();
      return true;
   }

   public boolean grantConcession(ServerLevel level, FactionId factionId, PoliticalGroupId groupId, @Nullable String demand) {
      Government government = this.getActiveGovernment(factionId);
      FactionPoliticalState state = this.getState(factionId);
      if (government == null || state == null || groupId == null) {
         return false;
      }

      PoliticalGroup group = state.getGroups().get(groupId);
      if (group == null || !group.isActive()) {
         return false;
      }

      long gameTime = level.getGameTime();
      group.setApproval(group.getApproval() + 8);
      group.setPoliticalPower(group.getPoliticalPower() + 2);
      if (demand != null) {
         group.clearDemand(demand);
      }

      state.setPoliticalPressure(state.getPoliticalPressure() - 8);
      government.setStability(government.getStability() - 2);
      government.setLegitimacy(government.getLegitimacy() + 2);
      PoliticalDecision decision = new PoliticalDecision(DecisionId.random(), factionId, DecisionType.CONCESSION, gameTime, government.getCurrentLeaderId(), groupId);
      decision.addAffectedGroup(groupId);
      decision.finalizeOutcome(true, 8, group.getApproval(), "concession", demand, "granted");
      state.addDecision(decision);
      state.setLastDecisionGameTime(gameTime);
      this.record(
         new PoliticalHistoryEntry(
            gameTime, PoliticalEventType.CONCESSION_GRANTED, factionId, government.getId(), government.getCurrentLeaderId(), groupId, null, null, decision.getId(), 8, demand
         ),
         state
      );
      this.recalculatePower(level, state, government, gameTime);
      this.markDirty.run();
      return true;
   }

   public boolean enactReform(ServerLevel level, FactionId factionId, @Nullable GovernmentType newType, int centralizationDelta) {
      Government government = this.getActiveGovernment(factionId);
      FactionPoliticalState state = this.getState(factionId);
      if (government == null || state == null) {
         return false;
      }

      long gameTime = level.getGameTime();
      int support = 0;
      int opposition = 0;
      for (PoliticalGroup group : state.getGroups().values()) {
         if (!group.isActive()) {
            continue;
         }

         int reformInterest = group.getInterestStrength(InterestType.REFORM);
         if (reformInterest >= 50) {
            support += group.getPoliticalPower();
         } else if (group.getInterestStrength(InterestType.TRADITION) >= 60) {
            opposition += group.getPoliticalPower();
         }
      }

      if (support < opposition + 10) {
         state.setPoliticalPressure(state.getPoliticalPressure() + 5);
         this.openCrisis(level, state, government, CrisisType.REFORM_CRISIS, "failed_reform", 40, gameTime);
         this.markDirty.run();
         return false;
      }

      GovernmentType oldType = government.getType();
      if (newType != null && newType != oldType) {
         this.changeGovernmentType(level, factionId, newType, SuccessionReason.GOVERNMENT_CHANGE, "reform");
         government = this.getActiveGovernment(factionId);
         if (government == null) {
            return false;
         }
      } else {
         government.setCentralization(government.getCentralization() + centralizationDelta);
         this.syncLocalAutonomy(level, state, government);
      }

      government.setLegitimacy(government.getLegitimacy() + 3);
      state.setPoliticalPressure(Math.max(0, state.getPoliticalPressure() - 10));
      PoliticalDecision decision = new PoliticalDecision(DecisionId.random(), factionId, DecisionType.REFORM, gameTime, government.getCurrentLeaderId(), null);
      decision.finalizeOutcome(true, 12, support, "reform_support", "centralization_delta=" + centralizationDelta, "passed");
      state.addDecision(decision);
      this.record(
         new PoliticalHistoryEntry(
            gameTime, PoliticalEventType.REFORM_PASSED, factionId, government.getId(), government.getCurrentLeaderId(), null, null, null, decision.getId(), support, oldType.name()
         ),
         state
      );
      this.notifyForeignRelations(level, factionId, PoliticsConfig.RELATIONSHIP_REFORM);
      this.markDirty.run();
      return true;
   }

   public boolean changeGovernmentType(ServerLevel level, FactionId factionId, GovernmentType newType, SuccessionReason reason, @Nullable String detail) {
      Objects.requireNonNull(level, "level");
      FactionPoliticalState state = this.ensurePolitics(level, factionId);
      Government old = this.getActiveGovernment(factionId);
      if (newType == null) {
         return false;
      }

      long gameTime = level.getGameTime();
      LeaderId previousLeader = old == null ? null : old.getCurrentLeaderId();
      if (old != null) {
         old.deactivate();
      }

      Government government = new Government(GovernmentId.random(), factionId, newType, gameTime);
      this.governments.put(government.getId(), government);
      state.setActiveGovernmentId(government.getId());
      this.ensureGroupsForGovernment(state, government);
      this.formRulingCoalition(state, government, gameTime);
      PoliticalLeader leader = this.createLeader(level, state, government, previousLeader, gameTime, reason == null ? SuccessionReason.GOVERNMENT_CHANGE : reason);
      government.setCurrentLeaderId(leader.getId());
      government.setLegitimacy(PoliticsConfig.LEGITIMACY_BASE + GovernmentTypeDefinition.of(newType).getLegitimacyBonus());
      government.setStability(PoliticsConfig.STABILITY_BASE + GovernmentTypeDefinition.of(newType).getStabilityBonus());
      this.record(
         new PoliticalHistoryEntry(
            gameTime,
            PoliticalEventType.GOVERNMENT_CHANGED,
            factionId,
            government.getId(),
            leader.getId(),
            null,
            government.getRulingCoalitionId(),
            null,
            null,
            0,
            detail == null ? newType.name() : detail
         ),
         state
      );
      this.recalculatePower(level, state, government, gameTime);
      this.markDirty.run();
      return true;
   }

   public boolean beginSuccession(ServerLevel level, FactionId factionId, SuccessionReason reason, @Nullable String detail) {
      FactionPoliticalState state = this.ensurePolitics(level, factionId);
      Government government = this.getActiveGovernment(factionId);
      if (government == null) {
         return false;
      }

      long gameTime = level.getGameTime();
      LeaderId oldLeaderId = government.getCurrentLeaderId();
      PoliticalLeader oldLeader = oldLeaderId == null ? null : state.getLeaders().get(oldLeaderId);
      if (oldLeader != null && oldLeader.isActive()) {
         oldLeader.end(gameTime);
         this.record(
            new PoliticalHistoryEntry(
               gameTime, PoliticalEventType.LEADER_REMOVED, factionId, government.getId(), oldLeaderId, null, null, null, null, 0, reason == null ? null : reason.name()
            ),
            state
         );
      }

      government.setCurrentLeaderId(null);
      GovernmentTypeDefinition definition = GovernmentTypeDefinition.of(government.getType());
      government.setInterimType(definition.getInterimType(), gameTime);
      government.setLegitimacy(government.getLegitimacy() + PoliticsConfig.LEGITIMACY_INTERIM);
      state.setSuccessionPending(true, gameTime);
      this.buildSuccessionCandidates(level, state, government);
      this.openCrisis(level, state, government, CrisisType.SUCCESSION_CRISIS, reason == null ? "succession" : reason.name(), 45, gameTime);
      this.record(
         new PoliticalHistoryEntry(
            gameTime, PoliticalEventType.INTERIM_STARTED, factionId, government.getId(), oldLeaderId, null, null, null, null, 0, definition.getInterimType().name()
         ),
         state
      );
      this.markDirty.run();
      return true;
   }

   public boolean completeSuccession(ServerLevel level, FactionId factionId) {
      FactionPoliticalState state = this.getState(factionId);
      Government government = this.getActiveGovernment(factionId);
      if (state == null || government == null || !state.isSuccessionPending()) {
         return false;
      }

      long gameTime = level.getGameTime();
      SuccessionCandidate winner = this.selectSuccessionWinner(state, government);
      if (winner == null) {
         PoliticalLeader interim = this.createLeader(level, state, government, null, gameTime, SuccessionReason.APPOINTMENT);
         interim.setRole(LeadershipRole.REGENT);
         interim.setDisplayName("Interim Authority");
         government.setCurrentLeaderId(interim.getId());
      } else {
         PoliticalLeader leader = winner.getLeaderId() == null
            ? this.createLeader(level, state, government, null, gameTime, this.reasonForMechanism(government.getSuccessionMechanism()))
            : state.getLeaders().get(winner.getLeaderId());
         if (leader == null) {
            leader = this.createLeader(level, state, government, null, gameTime, this.reasonForMechanism(government.getSuccessionMechanism()));
         }

         if (winner.getDisplayName() != null) {
            leader.setDisplayName(winner.getDisplayName());
         }

         leader.setLegitimacy(winner.getLegitimacy());
         leader.setPopularity(winner.getPopularity());
         leader.setMilitaryInfluence(winner.getMilitaryInfluence());
         leader.setEconomicInfluence(winner.getEconomicInfluence());
         leader.setPoliticalInfluence(winner.getPoliticalInfluence());
         government.setCurrentLeaderId(leader.getId());
         int legitimacyBonus = switch (government.getSuccessionMechanism()) {
            case HEREDITARY -> PoliticsConfig.LEGITIMACY_SUCCESSION_HEREDITARY;
            case ELECTIVE -> PoliticsConfig.LEGITIMACY_SUCCESSION_ELECTIVE;
            default -> 3;
         };
         government.setLegitimacy(PoliticsConfig.LEGITIMACY_BASE + legitimacyBonus + GovernmentTypeDefinition.of(government.getType()).getLegitimacyBonus());
         this.record(
            new PoliticalHistoryEntry(
               gameTime,
               PoliticalEventType.SUCCESSION,
               factionId,
               government.getId(),
               leader.getId(),
               winner.getPoliticalGroupId(),
               null,
               null,
               null,
               winner.getSupport(),
               government.getSuccessionMechanism().name()
            ),
            state
         );
         this.record(
            new PoliticalHistoryEntry(
               gameTime, PoliticalEventType.LEADER_APPOINTED, factionId, government.getId(), leader.getId(), winner.getPoliticalGroupId(), null, null, null, 0, null
            ),
            state
         );
      }

      government.setInterimType(InterimGovernmentType.NONE, gameTime);
      state.setSuccessionPending(false, gameTime);
      state.clearSuccessionCandidates();
      this.resolveCrisesOfType(state, CrisisType.SUCCESSION_CRISIS, gameTime, "succession_complete", "new_leader");
      this.record(
         new PoliticalHistoryEntry(
            gameTime, PoliticalEventType.INTERIM_ENDED, factionId, government.getId(), government.getCurrentLeaderId(), null, null, null, null, 0, null
         ),
         state
      );
      this.recalculatePower(level, state, government, gameTime);
      this.notifyForeignRelations(level, factionId, PoliticsConfig.RELATIONSHIP_LEADERSHIP_CHANGE);
      this.markDirty.run();
      return true;
   }

   public boolean attemptCoup(ServerLevel level, FactionId factionId, PoliticalGroupId instigatorGroupId) {
      FactionPoliticalState state = this.getState(factionId);
      Government government = this.getActiveGovernment(factionId);
      if (state == null || government == null || instigatorGroupId == null) {
         return false;
      }

      if (!GovernmentTypeDefinition.of(government.getType()).allowsCoup()) {
         return false;
      }

      PoliticalGroup group = state.getGroups().get(instigatorGroupId);
      if (group == null || !group.isActive()) {
         return false;
      }

      long gameTime = level.getGameTime();
      boolean eligible = government.getLegitimacy() <= PoliticsConfig.COUP_LEGITIMACY_MAX
         && government.getStability() <= PoliticsConfig.COUP_STABILITY_MAX
         && state.getPoliticalPressure() >= PoliticsConfig.COUP_PRESSURE_THRESHOLD
         && group.getPoliticalPower() >= PoliticsConfig.COUP_MIN_GROUP_POWER;

      this.record(
         new PoliticalHistoryEntry(
            gameTime, PoliticalEventType.COUP_ATTEMPTED, factionId, government.getId(), government.getCurrentLeaderId(), instigatorGroupId, null, null, null, group.getPoliticalPower(), null
         ),
         state
      );

      if (!eligible) {
         group.setApproval(group.getApproval() - 10);
         government.setLegitimacy(government.getLegitimacy() + 5);
         state.setPoliticalPressure(state.getPoliticalPressure() + 5);
         this.record(
            new PoliticalHistoryEntry(
               gameTime, PoliticalEventType.COUP_FAILED, factionId, government.getId(), government.getCurrentLeaderId(), instigatorGroupId, null, null, null, 0, "insufficient_conditions"
            ),
            state
         );
         this.markDirty.run();
         return false;
      }

      // If the government still retains meaningful legitimacy, the coup becomes an armed conflict
      // instead of an instant peaceful takeover (Phase 9 coup escalation).
      if (government.getLegitimacy() >= org.millenaire.faction.conflict.ConflictConfig.MIN_GOV_LEGITIMACY_FOR_COUP_WAR
         || group.getMilitaryInfluence() < 55) {
         try {
            org.millenaire.faction.conflict.InternalConflict armed =
               org.millenaire.faction.conflict.ConflictSavedData.get(level)
                  .getConflictManager()
                  .startCoupConflict(level, factionId, instigatorGroupId, "coup_escalated_to_conflict");
            if (armed != null) {
               state.setPolarization(state.getPolarization() + 20);
               state.setPoliticalPressure(state.getPoliticalPressure() + 10);
               this.record(
                  new PoliticalHistoryEntry(
                     gameTime,
                     PoliticalEventType.COUP_ATTEMPTED,
                     factionId,
                     government.getId(),
                     government.getCurrentLeaderId(),
                     instigatorGroupId,
                     null,
                     null,
                     null,
                     group.getPoliticalPower(),
                     "armed_coup_conflict:" + armed.getId().uuid()
                  ),
                  state
               );
               this.markDirty.run();
               return true;
            }
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Coup conflict escalation failed for {}: {}", factionId, e.toString());
         }
      }

      LeaderId oldLeader = government.getCurrentLeaderId();
      if (oldLeader != null) {
         PoliticalLeader previous = state.getLeaders().get(oldLeader);
         if (previous != null) {
            previous.end(gameTime);
         }
      }

      PoliticalLeader coupLeader = this.createLeader(level, state, government, null, gameTime, SuccessionReason.COUP);
      coupLeader.setRole(LeadershipRole.WAR_LEADER);
      coupLeader.setDisplayName(group.getType().name() + " Strongman");
      coupLeader.setMilitaryInfluence(Math.max(coupLeader.getMilitaryInfluence(), group.getMilitaryInfluence()));
      coupLeader.setLegitimacy(PoliticsConfig.LEGITIMACY_BASE + PoliticsConfig.LEGITIMACY_COUP);
      government.setCurrentLeaderId(coupLeader.getId());
      government.setType(GovernmentType.MILITARY_RULE, gameTime);
      government.setSuccessionMechanism(SuccessionMechanism.MILITARY_SELECTION);
      government.setLegitimacy(PoliticsConfig.LEGITIMACY_BASE + PoliticsConfig.LEGITIMACY_COUP);
      government.setStability(Math.max(25, government.getStability() - 15));
      group.setPoliticalPower(group.getPoliticalPower() + 15);
      group.setApproval(group.getApproval() + 5);
      state.setPoliticalPressure(Math.max(0, state.getPoliticalPressure() - 15));
      state.setPolarization(state.getPolarization() + 15);
      this.ensureGroupsForGovernment(state, government);
      this.formRulingCoalition(state, government, gameTime);
      this.record(
         new PoliticalHistoryEntry(
            gameTime, PoliticalEventType.COUP_SUCCEEDED, factionId, government.getId(), coupLeader.getId(), instigatorGroupId, government.getRulingCoalitionId(), null, null, 0, null
         ),
         state
      );
      this.notifyForeignRelations(level, factionId, PoliticsConfig.RELATIONSHIP_COUP);
      this.recalculatePower(level, state, government, gameTime);
      this.markDirty.run();
      return true;
   }

   public void onSettlementLeaderDied(ServerLevel level, Village village, UUID npcUuid, @Nullable net.minecraft.resources.ResourceLocation villagerTypeId) {
      if (level == null || village == null || npcUuid == null || village.getFactionId() == null) {
         return;
      }

      boolean chief = false;
      if (villagerTypeId != null) {
         VillagerType type = ModCultures.getVillagerType(villagerTypeId);
         chief = type != null && type.hasTag("chief");
      }

      FactionPoliticalState state = this.ensurePolitics(level, village.getFactionId());
      LocalAdministrator local = state.getLocalAdministrators().get(village.getId());
      if (local != null && npcUuid.equals(local.getLinkedNpcUuid())) {
         local.deactivate();
         this.record(
            new PoliticalHistoryEntry(
               level.getGameTime(),
               PoliticalEventType.LEADER_REMOVED,
               village.getFactionId(),
               state.getActiveGovernmentId(),
               local.getLinkedLeaderId(),
               null,
               null,
               null,
               null,
               0,
               "local_npc_death"
            ),
            state
         );
         this.appointLocalAdministrator(level, village);
      }

      PoliticalLeader leader = this.getLeader(village.getFactionId());
      if (leader != null && npcUuid.equals(leader.getLinkedNpcUuid())) {
         this.beginSuccession(level, village.getFactionId(), SuccessionReason.DEATH, "npc_death");
      } else if (chief && leader != null && leader.getOriginSettlementId() != null && leader.getOriginSettlementId().equals(village.getId())) {
         this.beginSuccession(level, village.getFactionId(), SuccessionReason.DEATH, "chief_death");
      }

      this.markDirty.run();
   }

   public void onWarEnded(ServerLevel level, War war, org.millenaire.faction.war.PeaceOutcome outcome) {
      if (level == null || war == null || outcome == null) {
         return;
      }

      long gameTime = level.getGameTime();
      boolean attackerWins = outcome == org.millenaire.faction.war.PeaceOutcome.ATTACKER_VICTORY
         || outcome == org.millenaire.faction.war.PeaceOutcome.CAPITULATION_DEFENDER;
      boolean defenderWins = outcome == org.millenaire.faction.war.PeaceOutcome.DEFENDER_VICTORY
         || outcome == org.millenaire.faction.war.PeaceOutcome.CAPITULATION_ATTACKER;

      for (WarParticipant participant : war.getParticipants().values()) {
         FactionId factionId = participant.getFactionId();
         FactionPoliticalState state = this.ensurePolitics(level, factionId);
         Government government = this.getActiveGovernment(factionId);
         PoliticalLeader leader = this.getLeader(factionId);
         boolean onAttacker = war.getSide(factionId) == org.millenaire.faction.war.WarSideId.ATTACKER;
         boolean won = attackerWins && onAttacker || defenderWins && !onAttacker;
         boolean lost = attackerWins && !onAttacker || defenderWins && onAttacker;
         if (won) {
            if (government != null) {
               government.setLegitimacy(government.getLegitimacy() + PoliticsConfig.LEGITIMACY_WAR_VICTORY);
               government.setStability(government.getStability() + 4);
            }

            if (leader != null) {
               leader.setLegitimacy(leader.getLegitimacy() + PoliticsConfig.LEGITIMACY_WAR_VICTORY);
               leader.setPopularity(leader.getPopularity() + 5);
            }

            this.boostGroupType(state, PoliticalGroupType.MILITARY, 4, 4);
            state.setPoliticalPressure(Math.max(0, state.getPoliticalPressure() - 5));
         } else if (lost) {
            if (government != null) {
               government.setLegitimacy(government.getLegitimacy() + PoliticsConfig.LEGITIMACY_WAR_DEFEAT);
               government.setStability(government.getStability() - 6);
            }

            if (leader != null) {
               leader.setLegitimacy(leader.getLegitimacy() + PoliticsConfig.LEGITIMACY_WAR_DEFEAT);
               leader.setPopularity(leader.getPopularity() - 8);
            }

            state.setPoliticalPressure(state.getPoliticalPressure() + 10);
            this.openCrisis(level, state, government, CrisisType.MILITARY_CRISIS, "war_defeat", 60, gameTime);
         } else {
            state.setPoliticalPressure(state.getPoliticalPressure() + participant.getWarExhaustion() / 20);
         }

         this.recalculatePower(level, state, government, gameTime);
      }

      this.markDirty.run();
   }

   public void onTerritoryCaptured(ServerLevel level, FactionId capturer, FactionId formerOwner) {
      if (level == null) {
         return;
      }

      long gameTime = level.getGameTime();
      if (capturer != null) {
         FactionPoliticalState state = this.ensurePolitics(level, capturer);
         Government government = this.getActiveGovernment(capturer);
         PoliticalLeader leader = this.getLeader(capturer);
         if (government != null) {
            government.setLegitimacy(government.getLegitimacy() + PoliticsConfig.LEGITIMACY_CONQUEST);
         }

         if (leader != null) {
            leader.setLegitimacy(leader.getLegitimacy() + PoliticsConfig.LEGITIMACY_CONQUEST);
            leader.setPopularity(leader.getPopularity() + 3);
         }

         this.boostGroupType(state, PoliticalGroupType.MILITARY, 5, 3);
         state.setPoliticalPressure(state.getPoliticalPressure() + 2);
         this.recalculatePower(level, state, government, gameTime);
      }

      if (formerOwner != null) {
         FactionPoliticalState state = this.ensurePolitics(level, formerOwner);
         Government government = this.getActiveGovernment(formerOwner);
         if (government != null) {
            government.setLegitimacy(government.getLegitimacy() + PoliticsConfig.LEGITIMACY_TERRITORIAL_LOSS);
            government.setStability(government.getStability() - 5);
         }

         state.setPoliticalPressure(state.getPoliticalPressure() + 8);
         this.openCrisis(level, state, government, CrisisType.MILITARY_CRISIS, "territorial_loss", 50, gameTime);
         this.recalculatePower(level, state, government, gameTime);
      }

      this.markDirty.run();
   }

   public void handleSettlementRemoved(ServerLevel level, VillageId settlementId) {
      if (settlementId == null) {
         return;
      }

      for (FactionPoliticalState state : this.states.values()) {
         state.removeLocalAdministrator(settlementId);
         for (PoliticalLeader leader : state.getLeaders().values()) {
            if (settlementId.equals(leader.getOriginSettlementId())) {
               leader.setOriginSettlementId(null);
            }
         }
      }

      this.markDirty.run();
   }

   public void handleFactionRemoved(ServerLevel level, FactionId factionId) {
      if (factionId == null) {
         return;
      }

      FactionPoliticalState state = this.states.remove(factionId);
      if (state != null && state.getActiveGovernmentId() != null) {
         Government government = this.governments.get(state.getActiveGovernmentId());
         if (government != null) {
            government.deactivate();
         }
      }

      this.markDirty.run();
   }

   public int purgeInvalid(FactionManager factionManager) {
      int removed = 0;
      List<FactionId> orphan = new ArrayList<>();
      for (FactionId factionId : this.states.keySet()) {
         if (!factionManager.exists(factionId)) {
            orphan.add(factionId);
         }
      }

      for (FactionId factionId : orphan) {
         this.states.remove(factionId);
         removed++;
      }

      if (removed > 0) {
         this.markDirty.run();
      }

      return removed;
   }

   public void validateLoadedConsistency(FactionManager factionManager) {
      for (FactionPoliticalState state : this.states.values()) {
         if (state.getActiveGovernmentId() != null && !this.governments.containsKey(state.getActiveGovernmentId())) {
            state.setActiveGovernmentId(null);
         }

         if (state.getActiveGovernmentId() == null && factionManager.exists(state.getFactionId())) {
            // repaired lazily on ensurePolitics
         }
      }

      this.markDirty.run();
   }

   void loadState(FactionPoliticalState state) {
      this.states.put(state.getFactionId(), state);
   }

   void loadGovernment(Government government) {
      this.governments.put(government.getId(), government);
   }

   void loadGlobalHistory(List<PoliticalHistoryEntry> history) {
      this.globalHistory.clear();
      if (history != null) {
         this.globalHistory.addAll(history);
      }
   }

   private FactionPoliticalState bootstrapFaction(ServerLevel level, FactionId factionId) {
      long gameTime = level.getGameTime();
      FactionPoliticalState state = this.states.computeIfAbsent(factionId, FactionPoliticalState::new);
      GovernmentType type = this.inferGovernmentType(level, factionId);
      Government government = new Government(GovernmentId.random(), factionId, type, gameTime);
      this.governments.put(government.getId(), government);
      state.setActiveGovernmentId(government.getId());
      this.ensureGroupsForGovernment(state, government);
      this.formRulingCoalition(state, government, gameTime);
      PoliticalLeader leader = this.createLeader(level, state, government, null, gameTime, SuccessionReason.INITIALIZATION);
      this.linkLeaderToChiefNpc(level, factionId, leader);
      government.setCurrentLeaderId(leader.getId());
      government.setLegitimacy(PoliticsConfig.LEGITIMACY_BASE + GovernmentTypeDefinition.of(type).getLegitimacyBonus());
      government.setStability(PoliticsConfig.STABILITY_BASE + GovernmentTypeDefinition.of(type).getStabilityBonus());
      this.ensureLocalAdministrators(level, state, government);
      this.record(
         new PoliticalHistoryEntry(
            gameTime, PoliticalEventType.GOVERNMENT_FORMED, factionId, government.getId(), leader.getId(), null, government.getRulingCoalitionId(), null, null, 0, type.name()
         ),
         state
      );
      this.recalculatePower(level, state, government, gameTime);
      this.markDirty.run();
      return state;
   }

   private GovernmentType inferGovernmentType(ServerLevel level, FactionId factionId) {
      Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(factionId);
      if (faction == null) {
         return GovernmentType.CHIEFDOM;
      }

      int settlements = faction.getSettlementCount();
      if (settlements <= 1) {
         return GovernmentType.CHIEFDOM;
      }

      if (settlements <= 3) {
         return GovernmentType.TRIBAL_COUNCIL;
      }

      return GovernmentType.COUNCIL;
   }

   private void ensureGroupsForGovernment(FactionPoliticalState state, Government government) {
      Set<PoliticalGroupType> desired = GovernmentTypeDefinition.of(government.getType()).getDefaultGroups();
      Map<PoliticalGroupType, PoliticalGroup> byType = new HashMap<>();
      for (PoliticalGroup group : state.getGroups().values()) {
         if (group.isActive()) {
            byType.put(group.getType(), group);
         }
      }

      for (PoliticalGroupType type : desired) {
         if (!byType.containsKey(type)) {
            PoliticalGroup group = new PoliticalGroup(PoliticalGroupId.random(), state.getFactionId(), type);
            state.putGroup(group);
         }
      }
   }

   private void formRulingCoalition(FactionPoliticalState state, Government government, long gameTime) {
      for (PoliticalCoalition coalition : state.getCoalitions().values()) {
         if (coalition.isActive() && coalition.isRuling()) {
            coalition.dissolve(gameTime);
            this.record(
               new PoliticalHistoryEntry(
                  gameTime, PoliticalEventType.COALITION_COLLAPSED, state.getFactionId(), government.getId(), null, null, coalition.getId(), null, null, 0, null
               ),
               state
            );
         }
      }

      PoliticalCoalition ruling = new PoliticalCoalition(CoalitionId.random(), state.getFactionId(), gameTime, true);
      PoliticalGroupType dominant = GovernmentTypeDefinition.of(government.getType()).getDominantDefaultGroup();
      for (PoliticalGroup group : state.getGroups().values()) {
         if (!group.isActive()) {
            continue;
         }

         if (group.getType() == dominant || group.getType() == PoliticalGroupType.RULING_ELITE || group.getPoliticalPower() >= 40) {
            ruling.addMember(group.getId());
            group.setCoalitionId(ruling.getId());
         }
      }

      if (ruling.getMemberGroupIds().isEmpty()) {
         for (PoliticalGroup group : state.getGroups().values()) {
            if (group.isActive()) {
               ruling.addMember(group.getId());
               group.setCoalitionId(ruling.getId());
               break;
            }
         }
      }

      state.putCoalition(ruling);
      government.setRulingCoalitionId(ruling.getId());
      this.refreshCoalitionMetrics(state, ruling);
      this.refreshOpposition(state, government, ruling);
      this.record(
         new PoliticalHistoryEntry(
            gameTime, PoliticalEventType.COALITION_FORMED, state.getFactionId(), government.getId(), government.getCurrentLeaderId(), null, ruling.getId(), null, null, ruling.getSupport(), "ruling"
         ),
         state
      );
   }

   private void refreshCoalitionMetrics(FactionPoliticalState state, PoliticalCoalition coalition) {
      int support = 0;
      int power = 0;
      for (PoliticalGroupId groupId : coalition.getMemberGroupIds()) {
         PoliticalGroup group = state.getGroups().get(groupId);
         if (group == null || !group.isActive()) {
            continue;
         }

         support += group.getPopulationSupport();
         power += group.getPoliticalPower();
      }

      coalition.setSupport(Math.min(100, support / Math.max(1, coalition.getMemberGroupIds().size())));
      coalition.setPoliticalPower(Math.min(100, power));
      coalition.setStability(PoliticsConfig.clamp(60 + coalition.getSupport() / 5 - state.getPolarization() / 5));
   }

   private void refreshOpposition(FactionPoliticalState state, Government government, PoliticalCoalition ruling) {
      Set<PoliticalGroupId> opposition = new LinkedHashSet<>();
      for (PoliticalGroup group : state.getGroups().values()) {
         if (!group.isActive()) {
            continue;
         }

         if (!ruling.getMemberGroupIds().contains(group.getId())) {
            opposition.add(group.getId());
            group.setCoalitionId(null);
         }
      }

      government.setOpposition(opposition);
   }

   private PoliticalLeader createLeader(
      ServerLevel level,
      FactionPoliticalState state,
      Government government,
      @Nullable LeaderId preferredId,
      long gameTime,
      SuccessionReason reason
   ) {
      LeaderId id = preferredId == null ? LeaderId.random() : preferredId;
      PoliticalLeader existing = state.getLeaders().get(id);
      if (existing != null && existing.isActive()) {
         return existing;
      }

      LeadershipRole role = GovernmentTypeDefinition.of(government.getType()).getDefaultLeaderRole();
      PoliticalLeader leader = new PoliticalLeader(id, state.getFactionId(), role, gameTime, role.name());
      GovernmentTypeDefinition definition = GovernmentTypeDefinition.of(government.getType());
      leader.setMilitaryInfluence(40 + definition.getMilitaryInfluenceBonus());
      leader.setEconomicInfluence(40 + definition.getEconomicInfluenceBonus());
      leader.setPoliticalInfluence(55);
      leader.setCompetence(50);
      state.putLeader(leader);
      this.record(
         new PoliticalHistoryEntry(
            gameTime, PoliticalEventType.LEADER_APPOINTED, state.getFactionId(), government.getId(), leader.getId(), null, null, null, null, 0, reason.name()
         ),
         state
      );
      return leader;
   }

   private void linkLeaderToChiefNpc(ServerLevel level, FactionId factionId, PoliticalLeader leader) {
      Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(factionId);
      if (faction == null) {
         return;
      }

      for (VillageId settlementId : faction.getSettlementIds()) {
         Village village = VillageSavedData.get(level).getVillageManager().getVillage(settlementId);
         if (village == null) {
            continue;
         }

         for (VillagerRecord record : village.getVillagerRecords().values()) {
            if (record.isKilled()) {
               continue;
            }

            VillagerType type = ModCultures.getVillagerType(record.getVillagerTypeId());
            if (type != null && type.hasTag("chief")) {
               leader.setLinkedNpcUuid(record.getUuid());
               leader.setOriginSettlementId(settlementId);
               leader.setDisplayName(record.getUuid().toString().substring(0, 8));
               return;
            }
         }
      }
   }

   private void ensureLocalAdministrators(ServerLevel level, FactionPoliticalState state, Government government) {
      Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(state.getFactionId());
      if (faction == null) {
         return;
      }

      int autonomy = PolicyEffects.localAutonomyFromCentralization(government.getCentralization());
      for (VillageId settlementId : faction.getSettlementIds()) {
         if (state.getLocalAdministrators().containsKey(settlementId)) {
            LocalAdministrator existing = state.getLocalAdministrators().get(settlementId);
            existing.setAutonomy(autonomy);
            continue;
         }

         Village village = VillageSavedData.get(level).getVillageManager().getVillage(settlementId);
         if (village != null) {
            this.appointLocalAdministrator(level, village);
         }
      }
   }

   private void appointLocalAdministrator(ServerLevel level, Village village) {
      if (village.getFactionId() == null) {
         return;
      }

      FactionPoliticalState state = this.ensurePolitics(level, village.getFactionId());
      Government government = this.getActiveGovernment(village.getFactionId());
      LocalAdministrator admin = new LocalAdministrator(village.getId(), village.getFactionId());
      admin.setAutonomy(government == null ? 50 : PolicyEffects.localAutonomyFromCentralization(government.getCentralization()));
      admin.setRole(LeadershipRole.GOVERNOR);
      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (record.isKilled()) {
            continue;
         }

         VillagerType type = ModCultures.getVillagerType(record.getVillagerTypeId());
         if (type != null && type.hasTag("chief")) {
            admin.setLinkedNpcUuid(record.getUuid());
            admin.setRole(LeadershipRole.CHIEF);
            admin.setDisplayName("Chief");
            break;
         }
      }

      state.putLocalAdministrator(admin);
      this.record(
         new PoliticalHistoryEntry(
            level.getGameTime(),
            PoliticalEventType.LOCAL_ADMIN_APPOINTED,
            village.getFactionId(),
            state.getActiveGovernmentId(),
            admin.getLinkedLeaderId(),
            null,
            null,
            null,
            null,
            admin.getAutonomy(),
            village.getId().uuid().toString()
         ),
         state
      );
   }

   private void syncLocalAutonomy(ServerLevel level, FactionPoliticalState state, Government government) {
      int autonomy = PolicyEffects.localAutonomyFromCentralization(government.getCentralization());
      for (LocalAdministrator admin : state.getLocalAdministrators().values()) {
         if (admin.isActive()) {
            admin.setAutonomy(autonomy);
         }
      }
   }

   private void tickFaction(ServerLevel level, FactionId factionId, long gameTime) {
      FactionPoliticalState state = this.ensurePolitics(level, factionId);
      Government government = this.getActiveGovernment(factionId);
      if (government == null) {
         return;
      }

      if (state.isSuccessionPending()) {
         if (gameTime - state.getSuccessionStartedGameTime() >= PoliticsConfig.SUCCESSION_INTERIM_TICKS) {
            this.completeSuccession(level, factionId);
         }

         return;
      }

      PoliticalLeader leader = this.getLeader(factionId);
      this.applyExternalConditions(level, state, government, leader);
      this.recalculatePower(level, state, government, gameTime);
      this.updatePressureAndPolarization(state, government, leader);
      this.updateConflictState(state);
      this.evaluateCrises(level, state, government, gameTime);
      this.evaluateCoupOpportunity(level, state, government, gameTime);
      this.validateLeader(level, state, government, leader, gameTime);
   }

   private void applyExternalConditions(ServerLevel level, FactionPoliticalState state, Government government, @Nullable PoliticalLeader leader) {
      EconomicBalanceLevel balance = EconomyQueries.balanceLevel(level, state.getFactionId());
      if (balance == EconomicBalanceLevel.HEALTHY || balance == EconomicBalanceLevel.BALANCED) {
         government.setLegitimacy(government.getLegitimacy() + PoliticsConfig.LEGITIMACY_ECONOMIC_HEALTHY);
         if (leader != null) {
            leader.setPopularity(leader.getPopularity() + PoliticsConfig.POPULARITY_PROSPERITY);
         }
      } else if (balance == EconomicBalanceLevel.UNDER_PRESSURE || balance == EconomicBalanceLevel.IMPORT_DEPENDENT) {
         government.setLegitimacy(government.getLegitimacy() + PoliticsConfig.LEGITIMACY_ECONOMIC_PRESSURE);
         if (leader != null) {
            leader.setPopularity(leader.getPopularity() + PoliticsConfig.POPULARITY_SHORTAGE);
         }

         state.setPoliticalPressure(state.getPoliticalPressure() + 3);
      }

      int exhaustion = 0;
      for (War war : WarQueries.warsInvolving(level, state.getFactionId())) {
         if (!war.getState().isOngoing()) {
            continue;
         }

         WarParticipant participant = war.getParticipant(state.getFactionId());
         if (participant != null) {
            exhaustion = Math.max(exhaustion, participant.getWarExhaustion());
         }
      }

      if (exhaustion > 0 && leader != null) {
         leader.setPopularity(leader.getPopularity() + PoliticsConfig.POPULARITY_WAR_EXHAUSTION_PER_20 * (exhaustion / 20));
         state.setPoliticalPressure(state.getPoliticalPressure() + exhaustion / 25);
      }

      int occupied = TerritoryQueries.occupiedBy(level, state.getFactionId()).size();
      if (occupied > 0) {
         this.boostGroupType(state, PoliticalGroupType.MILITARY, 2, 0);
         this.boostGroupType(state, PoliticalGroupType.MERCHANTS, -2, -1);
         state.setPoliticalPressure(state.getPoliticalPressure() + occupied);
      }

      int resistanceSum = 0;
      int resistanceCount = 0;
      for (Territory territory : TerritoryQueries.ownedBy(level, state.getFactionId())) {
         resistanceSum += territory.getResistance();
         resistanceCount++;
         if (territory.getOccupation() != null && territory.getOccupation().isActive()) {
            state.setRebellionPressure(Math.max(state.getRebellionPressure(), territory.getOccupation().getRebellionPressure()));
         }
      }

      if (resistanceCount > 0) {
         state.setRebellionPressure(Math.max(state.getRebellionPressure(), resistanceSum / resistanceCount / 2));
      }

      this.applyCulturalPolitics(level, state);
   }

   private void applyCulturalPolitics(ServerLevel level, FactionPoliticalState state) {
      Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(state.getFactionId());
      if (faction == null || faction.getCultureId() == null) {
         return;
      }

      int mismatches = 0;
      int total = 0;
      for (VillageId settlementId : faction.getSettlementIds()) {
         Village village = VillageSavedData.get(level).getVillageManager().getVillage(settlementId);
         if (village == null || village.getCultureId() == null) {
            continue;
         }

         total++;
         if (!village.getCultureId().equals(faction.getCultureId())) {
            mismatches++;
         }
      }

      if (total > 0 && mismatches > 0) {
         this.boostGroupType(state, PoliticalGroupType.TRADITIONALISTS, 2, 1);
         this.boostGroupType(state, PoliticalGroupType.REFORMERS, 1, 0);
         state.setPolarization(state.getPolarization() + mismatches);
         state.setRebellionPressure(state.getRebellionPressure() + mismatches * 2);
      }
   }

   private void recalculatePower(ServerLevel level, FactionPoliticalState state, @Nullable Government government, long gameTime) {
      if (government == null) {
         return;
      }

      List<PoliticalGroup> active = new ArrayList<>();
      int rawPower = 0;
      for (PoliticalGroup group : state.getGroups().values()) {
         if (!group.isActive()) {
            continue;
         }

         active.add(group);
         rawPower += Math.max(1, group.getPoliticalPower());
      }

      if (active.isEmpty()) {
         return;
      }

      // Normalize political power so the distribution remains valid and bounded.
      for (PoliticalGroup group : active) {
         int normalized = (int)Math.round(100.0 * Math.max(1, group.getPoliticalPower()) / rawPower);
         int previous = group.getPoliticalPower();
         group.setPoliticalPower(normalized);
         if (normalized > previous + 5) {
            this.record(
               new PoliticalHistoryEntry(
                  gameTime, PoliticalEventType.GROUP_GAINED_POWER, state.getFactionId(), government.getId(), null, group.getId(), null, null, null, normalized, null
               ),
               state
            );
         } else if (normalized + 5 < previous) {
            this.record(
               new PoliticalHistoryEntry(
                  gameTime, PoliticalEventType.GROUP_LOST_POWER, state.getFactionId(), government.getId(), null, group.getId(), null, null, null, normalized, null
               ),
               state
            );
         }
      }

      PoliticalCoalition ruling = government.getRulingCoalitionId() == null ? null : state.getCoalitions().get(government.getRulingCoalitionId());
      if (ruling != null && ruling.isActive()) {
         this.refreshCoalitionMetrics(state, ruling);
         this.refreshOpposition(state, government, ruling);
         if (ruling.getSupport() < PoliticsConfig.RULING_SUPPORT_LOW) {
            government.setLegitimacy(government.getLegitimacy() - 3);
            government.setStability(government.getStability() - 4);
            state.setPoliticalPressure(state.getPoliticalPressure() + PoliticsConfig.PRESSURE_OPPOSITION);
         }
      }

      int stability = PoliticsConfig.STABILITY_BASE
         + GovernmentTypeDefinition.of(government.getType()).getStabilityBonus()
         + (government.getLegitimacy() - 50) / 5
         - state.getPoliticalPressure() / 10
         - state.getPolarization() / 10;
      PoliticalLeader leader = government.getCurrentLeaderId() == null ? null : state.getLeaders().get(government.getCurrentLeaderId());
      if (leader != null) {
         stability += (leader.getPopularity() - 50) / 10;
         leader.setLegitimacy((leader.getLegitimacy() + government.getLegitimacy()) / 2);
      }

      government.setStability(stability);
      this.record(
         new PoliticalHistoryEntry(
            gameTime, PoliticalEventType.POWER_REDISTRIBUTED, state.getFactionId(), government.getId(), government.getCurrentLeaderId(), null, government.getRulingCoalitionId(), null, null, active.size(), null
         ),
         state
      );
   }

   private void updatePressureAndPolarization(FactionPoliticalState state, Government government, @Nullable PoliticalLeader leader) {
      int pressure = state.getPoliticalPressure();
      if (government.getLegitimacy() < 40) {
         pressure += PoliticsConfig.PRESSURE_LOW_LEGITIMACY;
      }

      if (government.getStability() < 40) {
         pressure += PoliticsConfig.PRESSURE_LOW_STABILITY;
      }

      pressure += PoliticsConfig.PRESSURE_DECAY;
      if (leader != null && leader.getPopularity() < 35) {
         pressure += 2;
      }

      state.setPoliticalPressure(pressure);

      int approvalSpread = 0;
      int count = 0;
      int min = 100;
      int max = 0;
      for (PoliticalGroup group : state.getGroups().values()) {
         if (!group.isActive()) {
            continue;
         }

         min = Math.min(min, group.getApproval());
         max = Math.max(max, group.getApproval());
         approvalSpread += group.getApproval();
         count++;
      }

      int polarization = count == 0 ? PoliticsConfig.POLARIZATION_BASE : Math.max(0, max - min);
      if (count > 0 && government.getOppositionGroupIds().size() > state.getGroups().size() / 2) {
         polarization += 10;
      }

      state.setPolarization(polarization);
      state.setRebellionPressure(
         Math.max(state.getRebellionPressure(), (100 - government.getStability() + state.getPoliticalPressure()) / 3)
      );
   }

   private void updateConflictState(FactionPoliticalState state) {
      // Phase 9 InternalConflictManager owns INTERNAL_CONFLICT while an armed/political conflict is active.
      if (state.getConflictState() == InternalConflictState.INTERNAL_CONFLICT) {
         return;
      }

      if (state.getRebellionPressure() >= PoliticsConfig.REBELLION_PRESSURE_THRESHOLD) {
         state.setConflictState(InternalConflictState.REBELLION_PRESSURE);
      } else if (state.getPoliticalPressure() >= PoliticsConfig.SEVERE_CRISIS_PRESSURE_THRESHOLD) {
         state.setConflictState(InternalConflictState.CIVIL_UNREST);
      } else if (state.getPoliticalPressure() >= PoliticsConfig.CRISIS_PRESSURE_THRESHOLD || state.getPolarization() >= PoliticsConfig.POLARIZATION_CRISIS_THRESHOLD) {
         state.setConflictState(InternalConflictState.POLITICAL_TENSION);
      } else {
         state.setConflictState(InternalConflictState.STABLE);
      }
   }

   /**
    * Phase 9: mark faction as undergoing an active internal conflict (server-authoritative).
    */
   public void markInternalConflict(FactionId factionId) {
      FactionPoliticalState state = this.getState(factionId);
      if (state == null) {
         return;
      }

      state.setConflictState(InternalConflictState.INTERNAL_CONFLICT);
      this.markDirty.run();
   }

   /**
    * Phase 9: clear INTERNAL_CONFLICT ownership so normal pressure-based state can resume.
    */
   public void clearInternalConflictMarker(FactionId factionId) {
      FactionPoliticalState state = this.getState(factionId);
      if (state == null || state.getConflictState() != InternalConflictState.INTERNAL_CONFLICT) {
         return;
      }

      this.updateConflictState(state);
      this.markDirty.run();
   }

   /**
    * Phase 9: apply post-conflict stability/pressure penalties without inventing a second politics engine.
    */
   public void applyPostConflictModifiers(ServerLevel level, FactionId factionId, int stabilityPenalty, int pressureBonus, @Nullable String detail) {
      Objects.requireNonNull(level, "level");
      FactionPoliticalState state = this.ensurePolitics(level, factionId);
      Government government = this.getActiveGovernment(factionId);
      if (government != null) {
         government.setStability(Math.max(5, government.getStability() - Math.max(0, stabilityPenalty)));
         government.setLegitimacy(Math.max(5, government.getLegitimacy() - Math.max(0, stabilityPenalty) / 2));
      }

      state.setPoliticalPressure(state.getPoliticalPressure() + Math.max(0, pressureBonus));
      state.setRebellionPressure(Math.max(0, state.getRebellionPressure() - Math.max(0, pressureBonus) / 2));
      this.record(
         new PoliticalHistoryEntry(
            level.getGameTime(),
            PoliticalEventType.INTERNAL_CONFLICT_RESOLVED,
            factionId,
            state.getActiveGovernmentId(),
            government == null ? null : government.getCurrentLeaderId(),
            null,
            null,
            null,
            null,
            stabilityPenalty,
            detail
         ),
         state
      );
      this.markDirty.run();
   }

   /**
    * Phase 9: temporary repression — lowers unrest pressure, raises polarization and future rebellion risk.
    */
   public void applyRepression(ServerLevel level, FactionId factionId, int intensity) {
      Objects.requireNonNull(level, "level");
      FactionPoliticalState state = this.ensurePolitics(level, factionId);
      Government government = this.getActiveGovernment(factionId);
      int amount = PoliticsConfig.clamp(intensity);
      state.setPoliticalPressure(Math.max(0, state.getPoliticalPressure() - amount / 2));
      state.setPolarization(state.getPolarization() + amount / 3);
      state.setRebellionPressure(state.getRebellionPressure() + amount / 4);
      if (government != null) {
         government.setStability(Math.min(100, government.getStability() + amount / 5));
         government.setLegitimacy(Math.max(0, government.getLegitimacy() - amount / 5));
      }

      for (PoliticalGroupId oppositionId : government == null ? Set.<PoliticalGroupId>of() : government.getOppositionGroupIds()) {
         PoliticalGroup group = state.getGroups().get(oppositionId);
         if (group != null) {
            group.setApproval(group.getApproval() - amount / 4);
            group.setPoliticalPower(group.getPoliticalPower() + amount / 10);
         }
      }

      this.record(
         new PoliticalHistoryEntry(
            level.getGameTime(),
            PoliticalEventType.DECISION_TAKEN,
            factionId,
            state.getActiveGovernmentId(),
            government == null ? null : government.getCurrentLeaderId(),
            null,
            null,
            null,
            null,
            amount,
            "repression"
         ),
         state
      );
      this.markDirty.run();
   }

   /**
    * Phase 9: increase local autonomy after negotiated settlement.
    */
   public boolean grantAutonomy(ServerLevel level, FactionId factionId, VillageId settlementId, int autonomyBoost) {
      Objects.requireNonNull(level, "level");
      FactionPoliticalState state = this.ensurePolitics(level, factionId);
      Government government = this.getActiveGovernment(factionId);
      if (government == null) {
         return false;
      }

      LocalAdministrator admin = state.getLocalAdministrators().get(settlementId);
      if (admin == null) {
         admin = new LocalAdministrator(settlementId, factionId);
         state.putLocalAdministrator(admin);
      }

      admin.setAutonomy(admin.getAutonomy() + Math.max(0, autonomyBoost));
      admin.setLoyalty(Math.min(100, admin.getLoyalty() + 5));
      government.setCentralization(Math.max(0, government.getCentralization() - Math.max(0, autonomyBoost) / 2));
      state.setPoliticalPressure(Math.max(0, state.getPoliticalPressure() - Math.max(0, autonomyBoost) / 2));
      state.setRebellionPressure(Math.max(0, state.getRebellionPressure() - Math.max(0, autonomyBoost)));
      this.record(
         new PoliticalHistoryEntry(
            level.getGameTime(),
            PoliticalEventType.AUTONOMY_GRANTED,
            factionId,
            government.getId(),
            government.getCurrentLeaderId(),
            null,
            null,
            null,
            null,
            autonomyBoost,
            settlementId.uuid().toString()
         ),
         state
      );
      this.markDirty.run();
      return true;
   }

   /**
    * Phase 9: install rebel administration on a contested settlement (temporary political control).
    */
   public void appointRebelAdministrator(ServerLevel level, FactionId rebelFactionId, VillageId settlementId, @Nullable LeaderId rebelLeaderId, String displayName) {
      Objects.requireNonNull(level, "level");
      FactionPoliticalState state = this.ensurePolitics(level, rebelFactionId);
      LocalAdministrator admin = new LocalAdministrator(settlementId, rebelFactionId);
      admin.setRole(LeadershipRole.GOVERNOR);
      admin.setLinkedLeaderId(rebelLeaderId);
      admin.setDisplayName(displayName == null ? "Rebel Administrator" : displayName);
      admin.setAutonomy(70);
      admin.setLoyalty(80);
      state.putLocalAdministrator(admin);
      this.record(
         new PoliticalHistoryEntry(
            level.getGameTime(),
            PoliticalEventType.LOCAL_ADMIN_APPOINTED,
            rebelFactionId,
            state.getActiveGovernmentId(),
            rebelLeaderId,
            null,
            null,
            null,
            null,
            0,
            "rebel_admin:" + settlementId.uuid()
         ),
         state
      );
      this.markDirty.run();
   }

   /**
    * Phase 9: overthrow origin government and establish a new political order from rebel victory.
    */
   public boolean overthrowGovernment(ServerLevel level, FactionId factionId, GovernmentType newType, @Nullable PoliticalGroupId empoweringGroupId, @Nullable String detail) {
      Objects.requireNonNull(level, "level");
      FactionPoliticalState state = this.ensurePolitics(level, factionId);
      Government old = this.getActiveGovernment(factionId);
      long gameTime = level.getGameTime();
      if (old != null) {
         LeaderId oldLeaderId = old.getCurrentLeaderId();
         if (oldLeaderId != null) {
            PoliticalLeader previous = state.getLeaders().get(oldLeaderId);
            if (previous != null) {
               previous.end(gameTime);
            }
         }

         old.deactivate();
      }

      GovernmentType type = newType == null ? GovernmentType.COUNCIL : newType;
      Government government = new Government(GovernmentId.random(), factionId, type, gameTime);
      this.governments.put(government.getId(), government);
      state.setActiveGovernmentId(government.getId());
      this.ensureGroupsForGovernment(state, government);
      if (empoweringGroupId != null) {
         PoliticalGroup group = state.getGroups().get(empoweringGroupId);
         if (group != null) {
            group.setPoliticalPower(group.getPoliticalPower() + 20);
            group.setApproval(group.getApproval() + 10);
         }
      }

      this.formRulingCoalition(state, government, gameTime);
      PoliticalLeader leader = this.createLeader(level, state, government, null, gameTime, SuccessionReason.MILITARY_TAKEOVER);
      leader.setRole(LeadershipRole.WAR_LEADER);
      government.setCurrentLeaderId(leader.getId());
      government.setLegitimacy(PoliticsConfig.LEGITIMACY_BASE + 5);
      government.setStability(Math.max(20, PoliticsConfig.STABILITY_BASE - 20));
      state.setPoliticalPressure(Math.max(20, state.getPoliticalPressure() - 25));
      state.setPolarization(state.getPolarization() + 10);
      this.record(
         new PoliticalHistoryEntry(
            gameTime,
            PoliticalEventType.GOVERNMENT_OVERTHROWN,
            factionId,
            government.getId(),
            leader.getId(),
            empoweringGroupId,
            government.getRulingCoalitionId(),
            null,
            null,
            0,
            detail == null ? type.name() : detail
         ),
         state
      );
      this.notifyForeignRelations(level, factionId, PoliticsConfig.RELATIONSHIP_COUP);
      this.recalculatePower(level, state, government, gameTime);
      this.markDirty.run();
      return true;
   }

   /**
    * Phase 9: grant opposition group representation in the ruling coalition.
    */
   public boolean grantPoliticalRepresentation(ServerLevel level, FactionId factionId, PoliticalGroupId groupId) {
      Objects.requireNonNull(level, "level");
      FactionPoliticalState state = this.ensurePolitics(level, factionId);
      Government government = this.getActiveGovernment(factionId);
      PoliticalGroup group = state.getGroups().get(groupId);
      if (government == null || group == null || !group.isActive()) {
         return false;
      }

      long gameTime = level.getGameTime();
      government.removeOpposition(groupId);
      group.setPoliticalPower(group.getPoliticalPower() + 10);
      group.setApproval(group.getApproval() + 8);
      this.formRulingCoalition(state, government, gameTime);
      PoliticalCoalition ruling = state.getCoalitions().get(government.getRulingCoalitionId());
      if (ruling != null) {
         ruling.addMember(groupId);
      }

      state.setPoliticalPressure(Math.max(0, state.getPoliticalPressure() - 15));
      state.setRebellionPressure(Math.max(0, state.getRebellionPressure() - 20));
      this.record(
         new PoliticalHistoryEntry(
            gameTime,
            PoliticalEventType.GROUP_GAINED_POWER,
            factionId,
            government.getId(),
            government.getCurrentLeaderId(),
            groupId,
            government.getRulingCoalitionId(),
            null,
            null,
            group.getPoliticalPower(),
            "representation_settlement"
         ),
         state
      );
      this.markDirty.run();
      return true;
   }

   public void recordConflictPoliticalEvent(
      ServerLevel level,
      FactionId factionId,
      PoliticalEventType type,
      @Nullable PoliticalGroupId groupId,
      int quantity,
      @Nullable String detail
   ) {
      FactionPoliticalState state = this.getState(factionId);
      Government government = this.getActiveGovernment(factionId);
      this.record(
         new PoliticalHistoryEntry(
            level.getGameTime(),
            type == null ? PoliticalEventType.INTERNAL_UNREST : type,
            factionId,
            government == null ? null : government.getId(),
            government == null ? null : government.getCurrentLeaderId(),
            groupId,
            government == null ? null : government.getRulingCoalitionId(),
            null,
            null,
            quantity,
            detail
         ),
         state
      );
      this.markDirty.run();
   }

   private void evaluateCrises(ServerLevel level, FactionPoliticalState state, Government government, long gameTime) {
      int activeCrises = 0;
      for (PoliticalCrisis crisis : state.getCrises().values()) {
         if (crisis.isActive()) {
            activeCrises++;
            if (state.getPoliticalPressure() >= PoliticsConfig.SEVERE_CRISIS_PRESSURE_THRESHOLD) {
               CrisisStage before = crisis.getStage();
               crisis.escalate();
               if (crisis.getStage() != before) {
                  this.record(
                     new PoliticalHistoryEntry(
                        gameTime, PoliticalEventType.POLITICAL_CRISIS_ESCALATED, state.getFactionId(), government.getId(), null, null, null, crisis.getId(), null, crisis.getSeverity(), crisis.getStage().name()
                     ),
                     state
                  );
               }
            } else if (state.getPoliticalPressure() < PoliticsConfig.CRISIS_PRESSURE_THRESHOLD - 10 && government.getStability() > 55) {
               crisis.deescalate();
               if (crisis.getStage() == CrisisStage.NORMAL || crisis.getSeverity() <= 20) {
                  crisis.resolve(gameTime, "stabilization", "pressure_reduced");
                  this.record(
                     new PoliticalHistoryEntry(
                        gameTime, PoliticalEventType.POLITICAL_CRISIS_RESOLVED, state.getFactionId(), government.getId(), null, null, null, crisis.getId(), null, 0, "stabilization"
                     ),
                     state
                  );
               }
            }
         }
      }

      if (activeCrises >= PoliticsConfig.MAX_ACTIVE_CRISES_PER_FACTION) {
         return;
      }

      if (government.getLegitimacy() < 35) {
         this.openCrisis(level, state, government, CrisisType.LEGITIMACY_CRISIS, "low_legitimacy", 55, gameTime);
      }

      PoliticalCoalition ruling = government.getRulingCoalitionId() == null ? null : state.getCoalitions().get(government.getRulingCoalitionId());
      if (ruling != null && ruling.getSupport() < PoliticsConfig.RULING_SUPPORT_LOW) {
         this.openCrisis(level, state, government, CrisisType.COALITION_CRISIS, "weak_ruling_coalition", 50, gameTime);
      }
   }

   private void openCrisis(
      ServerLevel level,
      FactionPoliticalState state,
      @Nullable Government government,
      CrisisType type,
      String cause,
      int severity,
      long gameTime
   ) {
      for (PoliticalCrisis existing : state.getCrises().values()) {
         if (existing.isActive() && existing.getType() == type) {
            existing.setSeverity(Math.max(existing.getSeverity(), severity));
            return;
         }
      }

      PoliticalCrisis crisis = new PoliticalCrisis(CrisisId.random(), state.getFactionId(), type, cause, gameTime, severity);
      if (government != null) {
         for (PoliticalGroupId oppositionId : government.getOppositionGroupIds()) {
            crisis.addInvolvedGroup(oppositionId);
         }
      }

      state.putCrisis(crisis);
      this.record(
         new PoliticalHistoryEntry(
            gameTime,
            PoliticalEventType.POLITICAL_CRISIS_STARTED,
            state.getFactionId(),
            government == null ? null : government.getId(),
            government == null ? null : government.getCurrentLeaderId(),
            null,
            null,
            crisis.getId(),
            null,
            severity,
            type.name()
         ),
         state
      );
      this.record(
         new PoliticalHistoryEntry(
            gameTime, PoliticalEventType.INTERNAL_UNREST, state.getFactionId(), government == null ? null : government.getId(), null, null, null, crisis.getId(), null, severity, cause
         ),
         state
      );
   }

   private void resolveCrisesOfType(FactionPoliticalState state, CrisisType type, long gameTime, String path, String outcome) {
      for (PoliticalCrisis crisis : state.getCrises().values()) {
         if (crisis.isActive() && crisis.getType() == type) {
            crisis.resolve(gameTime, path, outcome);
            this.record(
               new PoliticalHistoryEntry(
                  gameTime, PoliticalEventType.POLITICAL_CRISIS_RESOLVED, state.getFactionId(), state.getActiveGovernmentId(), null, null, null, crisis.getId(), null, 0, path
               ),
               state
            );
         }
      }
   }

   private void evaluateCoupOpportunity(ServerLevel level, FactionPoliticalState state, Government government, long gameTime) {
      if (!GovernmentTypeDefinition.of(government.getType()).allowsCoup()) {
         return;
      }

      if (!PoliticsEvaluator.isGovernmentVulnerable(state, government)) {
         return;
      }

      PoliticalGroup strongest = null;
      for (PoliticalGroup group : state.getGroups().values()) {
         if (!group.isActive()) {
            continue;
         }

         if (government.getOppositionGroupIds().contains(group.getId())
            && group.getPoliticalPower() >= PoliticsConfig.COUP_MIN_GROUP_POWER
            && (strongest == null || group.getPoliticalPower() > strongest.getPoliticalPower())) {
            strongest = group;
         }
      }

      // Deterministic threshold coup — not a random daily roll.
      if (strongest != null
         && state.getPoliticalPressure() >= PoliticsConfig.COUP_PRESSURE_THRESHOLD
         && government.getLegitimacy() <= PoliticsConfig.COUP_LEGITIMACY_MAX
         && strongest.getMilitaryInfluence() >= 50) {
         this.attemptCoup(level, state.getFactionId(), strongest.getId());
      }
   }

   private void validateLeader(ServerLevel level, FactionPoliticalState state, Government government, @Nullable PoliticalLeader leader, long gameTime) {
      if (leader == null) {
         if (!state.isSuccessionPending()) {
            this.beginSuccession(level, state.getFactionId(), SuccessionReason.REMOVAL, "missing_leader");
         }

         return;
      }

      if (!leader.isActive()) {
         this.beginSuccession(level, state.getFactionId(), SuccessionReason.REMOVAL, "inactive_leader");
         return;
      }

      if (leader.getLinkedNpcUuid() != null && leader.getOriginSettlementId() != null) {
         Village village = VillageSavedData.get(level).getVillageManager().getVillage(leader.getOriginSettlementId());
         if (village != null) {
            VillagerRecord record = village.getVillagerRecords().get(leader.getLinkedNpcUuid());
            if (record != null && record.isKilled()) {
               this.beginSuccession(level, state.getFactionId(), SuccessionReason.DEATH, "linked_npc_killed");
            }
         }
      }
   }

   private void buildSuccessionCandidates(ServerLevel level, FactionPoliticalState state, Government government) {
      state.clearSuccessionCandidates();
      for (PoliticalGroup group : state.getGroups().values()) {
         if (!group.isActive()) {
            continue;
         }

         SuccessionCandidate candidate = new SuccessionCandidate(
            CandidateId.random(), state.getFactionId(), null, group.getId(), group.getType().name() + " Candidate"
         );
         int claim = group.getType() == GovernmentTypeDefinition.of(government.getType()).getDominantDefaultGroup() ? 70 : 40;
         candidate.setScores(
            group.getApproval(),
            group.getPopulationSupport(),
            group.getMilitaryInfluence(),
            group.getWealthInfluence(),
            group.getPoliticalPower(),
            claim,
            (group.getPopulationSupport() + group.getPoliticalPower()) / 2
         );
         state.addSuccessionCandidate(candidate);
      }

      PoliticalLeader existing = this.getLeader(state.getFactionId());
      if (existing != null) {
         // advisors / high-influence historical leaders remain eligible as appointment candidates
         SuccessionCandidate fromLeader = new SuccessionCandidate(
            CandidateId.random(), state.getFactionId(), existing.getId(), null, existing.getDisplayName()
         );
         fromLeader.setScores(
            existing.getLegitimacy(),
            existing.getPopularity(),
            existing.getMilitaryInfluence(),
            existing.getEconomicInfluence(),
            existing.getPoliticalInfluence(),
            50,
            existing.getPopularity()
         );
         state.addSuccessionCandidate(fromLeader);
      }
   }

   @Nullable
   private SuccessionCandidate selectSuccessionWinner(FactionPoliticalState state, Government government) {
      return state.getSuccessionCandidates()
         .stream()
         .max(Comparator.comparingInt(candidate -> candidate.score(government.getSuccessionMechanism())))
         .orElse(null);
   }

   private SuccessionReason reasonForMechanism(SuccessionMechanism mechanism) {
      return switch (mechanism) {
         case HEREDITARY -> SuccessionReason.HEREDITARY_SUCCESSION;
         case ELECTIVE -> SuccessionReason.ELECTION;
         case COUNCIL_SELECTION -> SuccessionReason.COUNCIL_DECISION;
         case MILITARY_SELECTION -> SuccessionReason.MILITARY_TAKEOVER;
         case APPOINTMENT, CUSTOM -> SuccessionReason.APPOINTMENT;
      };
   }

   private void boostGroupType(FactionPoliticalState state, PoliticalGroupType type, int powerDelta, int approvalDelta) {
      for (PoliticalGroup group : state.getGroups().values()) {
         if (group.isActive() && group.getType() == type) {
            group.setPoliticalPower(group.getPoliticalPower() + powerDelta);
            group.setApproval(group.getApproval() + approvalDelta);
         }
      }
   }

   private void notifyForeignRelations(ServerLevel level, FactionId factionId, int delta) {
      for (FactionId ally : DiplomacySavedData.get(level).getDiplomaticManager().getAllies(factionId)) {
         if (delta < 0) {
            RelationshipSavedData.get(level)
               .getRelationshipManager()
               .decreaseRelationship(level, ally, factionId, Math.abs(delta), RelationshipChangeReason.POLITICS);
         } else if (delta > 0) {
            RelationshipSavedData.get(level)
               .getRelationshipManager()
               .increaseRelationship(level, ally, factionId, delta, RelationshipChangeReason.POLITICS);
         }
      }
   }

   private void record(PoliticalHistoryEntry entry, @Nullable FactionPoliticalState state) {
      if (state != null) {
         state.recordHistory(entry);
      }

      this.globalHistory.add(entry);
      while (this.globalHistory.size() > PoliticsConfig.MAX_POLITICAL_HISTORY) {
         this.globalHistory.remove(0);
      }
   }
}
