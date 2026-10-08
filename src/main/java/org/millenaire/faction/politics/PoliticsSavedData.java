package org.millenaire.faction.politics;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.millenaire.faction.FactionId;
import org.millenaire.village.VillageId;
import org.slf4j.Logger;

public final class PoliticsSavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int CURRENT_VERSION = 1;
   public static final String DATA_NAME = "millenaire_faction_politics";

   private final PoliticsManager politicsManager = new PoliticsManager(this::setDirty);

   private PoliticsSavedData() {
   }

   public static Factory<PoliticsSavedData> factory() {
      return new Factory<>(PoliticsSavedData::new, (tag, registries) -> load(tag), null);
   }

   public static PoliticsSavedData get(ServerLevel level) {
      return (PoliticsSavedData)level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public PoliticsManager getPoliticsManager() {
      return this.politicsManager;
   }

   public CompoundTag save(CompoundTag root, Provider registries) {
      root.putInt("version", CURRENT_VERSION);
      root.putLong("last_update", this.politicsManager.getLastUpdateGameTime());

      ListTag governments = new ListTag();
      for (Government government : this.politicsManager.getAllGovernments()) {
         try {
            governments.add(saveGovernment(government));
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize government {}", government.getId(), e);
         }
      }
      root.put("governments", governments);

      ListTag factionStates = new ListTag();
      for (FactionPoliticalState state : this.politicsManager.getAllStates()) {
         try {
            factionStates.add(saveFactionState(state));
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize faction political state {}", state.getFactionId(), e);
         }
      }
      root.put("faction_states", factionStates);

      ListTag history = new ListTag();
      for (PoliticalHistoryEntry entry : this.politicsManager.getGlobalHistory()) {
         history.add(saveHistory(entry));
      }
      root.put("history", history);
      return root;
   }

   private static CompoundTag saveGovernment(Government government) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", government.getId().uuid());
      tag.putString("faction", government.getFactionId().asString());
      tag.putString("type", government.getType().name());
      tag.putLong("created", government.getCreatedGameTime());
      if (government.getCurrentLeaderId() != null) {
         tag.putUUID("leader", government.getCurrentLeaderId().uuid());
      }
      tag.putInt("legitimacy", government.getLegitimacy());
      tag.putInt("stability", government.getStability());
      tag.putInt("centralization", government.getCentralization());
      tag.putString("succession_mechanism", government.getSuccessionMechanism().name());
      tag.putString("interim_type", government.getInterimType().name());
      if (government.getRulingCoalitionId() != null) {
         tag.putUUID("ruling_coalition", government.getRulingCoalitionId().uuid());
      }

      ListTag policies = new ListTag();
      for (GovernmentPolicy policy : government.getPolicies().values()) {
         CompoundTag p = new CompoundTag();
         p.putString("type", policy.getType().name());
         p.putString("stance", policy.getStance().name());
         p.putLong("last_changed", policy.getLastChangedGameTime());
         if (policy.getNote() != null) {
            p.putString("note", policy.getNote());
         }
         policies.add(p);
      }
      tag.put("policies", policies);

      ListTag opposition = new ListTag();
      for (PoliticalGroupId groupId : government.getOppositionGroupIds()) {
         CompoundTag o = new CompoundTag();
         o.putUUID("id", groupId.uuid());
         opposition.add(o);
      }
      tag.put("opposition", opposition);

      tag.putLong("last_major_change", government.getLastMajorChangeGameTime());
      tag.putBoolean("active", government.isActive());
      return tag;
   }

   private static CompoundTag saveFactionState(FactionPoliticalState state) {
      CompoundTag tag = new CompoundTag();
      tag.putString("faction", state.getFactionId().asString());
      if (state.getActiveGovernmentId() != null) {
         tag.putUUID("active_government", state.getActiveGovernmentId().uuid());
      }
      tag.putInt("pressure", state.getPoliticalPressure());
      tag.putInt("polarization", state.getPolarization());
      tag.putInt("rebellion_pressure", state.getRebellionPressure());
      tag.putString("conflict_state", state.getConflictState().name());
      tag.putBoolean("succession_pending", state.isSuccessionPending());
      tag.putLong("succession_started", state.getSuccessionStartedGameTime());
      tag.putLong("last_decision", state.getLastDecisionGameTime());

      ListTag governmentHistory = new ListTag();
      for (GovernmentId governmentId : state.getGovernmentHistoryIds()) {
         CompoundTag g = new CompoundTag();
         g.putUUID("id", governmentId.uuid());
         governmentHistory.add(g);
      }
      tag.put("government_history", governmentHistory);

      ListTag groups = new ListTag();
      for (PoliticalGroup group : state.getGroups().values()) {
         groups.add(saveGroup(group));
      }
      tag.put("groups", groups);

      ListTag coalitions = new ListTag();
      for (PoliticalCoalition coalition : state.getCoalitions().values()) {
         coalitions.add(saveCoalition(coalition));
      }
      tag.put("coalitions", coalitions);

      ListTag leaders = new ListTag();
      for (PoliticalLeader leader : state.getLeaders().values()) {
         leaders.add(saveLeader(leader));
      }
      tag.put("leaders", leaders);

      ListTag crises = new ListTag();
      for (PoliticalCrisis crisis : state.getCrises().values()) {
         crises.add(saveCrisis(crisis));
      }
      tag.put("crises", crises);

      ListTag localAdmins = new ListTag();
      for (LocalAdministrator admin : state.getLocalAdministrators().values()) {
         localAdmins.add(saveLocalAdministrator(admin));
      }
      tag.put("local_administrators", localAdmins);

      ListTag candidates = new ListTag();
      for (SuccessionCandidate candidate : state.getSuccessionCandidates()) {
         candidates.add(saveSuccessionCandidate(candidate));
      }
      tag.put("succession_candidates", candidates);

      ListTag decisions = new ListTag();
      for (PoliticalDecision decision : state.getDecisions()) {
         decisions.add(saveDecision(decision));
      }
      tag.put("decisions", decisions);

      ListTag history = new ListTag();
      for (PoliticalHistoryEntry entry : state.getHistory()) {
         history.add(saveHistory(entry));
      }
      tag.put("history", history);
      return tag;
   }

   private static CompoundTag saveGroup(PoliticalGroup group) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", group.getId().uuid());
      tag.putString("faction", group.getFactionId().asString());
      tag.putString("type", group.getType().name());
      tag.putInt("population_support", group.getPopulationSupport());
      tag.putInt("political_power", group.getPoliticalPower());
      tag.putInt("wealth_influence", group.getWealthInfluence());
      tag.putInt("military_influence", group.getMilitaryInfluence());
      tag.putInt("approval", group.getApproval());
      tag.putString("foreign_preference", group.getForeignPreference().name());
      tag.putBoolean("active", group.isActive());
      if (group.getCoalitionId() != null) {
         tag.putUUID("coalition", group.getCoalitionId().uuid());
      }

      ListTag interests = new ListTag();
      for (PoliticalInterest interest : group.getInterests().values()) {
         CompoundTag i = new CompoundTag();
         i.putString("type", interest.getType().name());
         i.putInt("strength", interest.getStrength());
         interests.add(i);
      }
      tag.put("interests", interests);

      ListTag demands = new ListTag();
      for (String demand : group.getDemands()) {
         demands.add(StringTag.valueOf(demand));
      }
      tag.put("demands", demands);
      return tag;
   }

   private static CompoundTag saveCoalition(PoliticalCoalition coalition) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", coalition.getId().uuid());
      tag.putString("faction", coalition.getFactionId().asString());
      tag.putBoolean("ruling", coalition.isRuling());
      tag.putInt("support", coalition.getSupport());
      tag.putInt("political_power", coalition.getPoliticalPower());
      tag.putInt("stability", coalition.getStability());
      tag.putLong("created", coalition.getCreatedGameTime());
      tag.putBoolean("active", coalition.isActive());
      tag.putLong("dissolved", coalition.getDissolvedGameTime());
      if (coalition.getSharedInterestNote() != null) {
         tag.putString("shared_interest", coalition.getSharedInterestNote());
      }

      ListTag members = new ListTag();
      for (PoliticalGroupId groupId : coalition.getMemberGroupIds()) {
         CompoundTag m = new CompoundTag();
         m.putUUID("id", groupId.uuid());
         members.add(m);
      }
      tag.put("members", members);
      return tag;
   }

   private static CompoundTag saveLeader(PoliticalLeader leader) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", leader.getId().uuid());
      tag.putString("faction", leader.getFactionId().asString());
      if (leader.getDisplayName() != null) {
         tag.putString("display_name", leader.getDisplayName());
      }
      tag.putString("role", leader.getRole().name());
      tag.putInt("legitimacy", leader.getLegitimacy());
      tag.putInt("popularity", leader.getPopularity());
      tag.putInt("competence", leader.getCompetence());
      tag.putInt("military_influence", leader.getMilitaryInfluence());
      tag.putInt("economic_influence", leader.getEconomicInfluence());
      tag.putInt("diplomatic_influence", leader.getDiplomaticInfluence());
      tag.putInt("political_influence", leader.getPoliticalInfluence());
      tag.putLong("leadership_start", leader.getLeadershipStartGameTime());
      tag.putBoolean("active", leader.isActive());
      tag.putLong("ended", leader.getEndedGameTime());
      if (leader.getOriginSettlementId() != null) {
         tag.putUUID("origin_settlement", leader.getOriginSettlementId().uuid());
      }
      if (leader.getLinkedNpcUuid() != null) {
         tag.putUUID("linked_npc", leader.getLinkedNpcUuid());
      }
      return tag;
   }

   private static CompoundTag saveCrisis(PoliticalCrisis crisis) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", crisis.getId().uuid());
      tag.putString("faction", crisis.getFactionId().asString());
      tag.putString("type", crisis.getType().name());
      tag.putString("cause", crisis.getCause());
      tag.putInt("severity", crisis.getSeverity());
      tag.putString("stage", crisis.getStage().name());
      tag.putLong("started", crisis.getStartedGameTime());
      tag.putLong("resolved", crisis.getResolvedGameTime());
      tag.putBoolean("active", crisis.isActive());
      if (crisis.getResolutionPath() != null) {
         tag.putString("resolution_path", crisis.getResolutionPath());
      }
      if (crisis.getOutcome() != null) {
         tag.putString("outcome", crisis.getOutcome());
      }

      ListTag involved = new ListTag();
      for (PoliticalGroupId groupId : crisis.getInvolvedGroupIds()) {
         CompoundTag g = new CompoundTag();
         g.putUUID("id", groupId.uuid());
         involved.add(g);
      }
      tag.put("involved_groups", involved);
      return tag;
   }

   private static CompoundTag saveLocalAdministrator(LocalAdministrator admin) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("settlement", admin.getSettlementId().uuid());
      tag.putString("faction", admin.getFactionId().asString());
      tag.putString("role", admin.getRole().name());
      tag.putInt("autonomy", admin.getAutonomy());
      tag.putInt("loyalty", admin.getLoyalty());
      tag.putInt("local_influence", admin.getLocalInfluence());
      if (admin.getLinkedLeaderId() != null) {
         tag.putUUID("linked_leader", admin.getLinkedLeaderId().uuid());
      }
      if (admin.getLinkedNpcUuid() != null) {
         tag.putUUID("linked_npc", admin.getLinkedNpcUuid());
      }
      if (admin.getDisplayName() != null) {
         tag.putString("display_name", admin.getDisplayName());
      }
      tag.putBoolean("active", admin.isActive());
      return tag;
   }

   private static CompoundTag saveSuccessionCandidate(SuccessionCandidate candidate) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", candidate.getId().uuid());
      tag.putString("faction", candidate.getFactionId().asString());
      if (candidate.getLeaderId() != null) {
         tag.putUUID("leader", candidate.getLeaderId().uuid());
      }
      if (candidate.getPoliticalGroupId() != null) {
         tag.putUUID("group", candidate.getPoliticalGroupId().uuid());
      }
      tag.putInt("legitimacy", candidate.getLegitimacy());
      tag.putInt("popularity", candidate.getPopularity());
      tag.putInt("military_influence", candidate.getMilitaryInfluence());
      tag.putInt("economic_influence", candidate.getEconomicInfluence());
      tag.putInt("political_influence", candidate.getPoliticalInfluence());
      tag.putInt("succession_claim", candidate.getSuccessionClaim());
      tag.putInt("support", candidate.getSupport());
      if (candidate.getDisplayName() != null) {
         tag.putString("display_name", candidate.getDisplayName());
      }
      return tag;
   }

   private static CompoundTag saveDecision(PoliticalDecision decision) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", decision.getId().uuid());
      tag.putString("faction", decision.getFactionId().asString());
      tag.putString("type", decision.getType().name());
      tag.putLong("decided", decision.getDecidedGameTime());
      if (decision.getProposerLeaderId() != null) {
         tag.putUUID("proposer_leader", decision.getProposerLeaderId().uuid());
      }
      if (decision.getProposerGroupId() != null) {
         tag.putUUID("proposer_group", decision.getProposerGroupId().uuid());
      }
      tag.putInt("political_cost", decision.getPoliticalCost());
      tag.putInt("approval_support", decision.getApprovalSupport());
      tag.putBoolean("successful", decision.isSuccessful());
      if (decision.getRequirementsNote() != null) {
         tag.putString("requirements", decision.getRequirementsNote());
      }
      if (decision.getEffectsNote() != null) {
         tag.putString("effects", decision.getEffectsNote());
      }
      if (decision.getOutcome() != null) {
         tag.putString("outcome", decision.getOutcome());
      }

      ListTag affected = new ListTag();
      for (PoliticalGroupId groupId : decision.getAffectedGroupIds()) {
         CompoundTag a = new CompoundTag();
         a.putUUID("id", groupId.uuid());
         affected.add(a);
      }
      tag.put("affected_groups", affected);
      return tag;
   }

   private static CompoundTag saveHistory(PoliticalHistoryEntry entry) {
      CompoundTag tag = new CompoundTag();
      tag.putLong("game_time", entry.getGameTime());
      tag.putString("event", entry.getEventType().name());
      tag.putString("faction", entry.getFactionId().asString());
      if (entry.getGovernmentId() != null) {
         tag.putUUID("government", entry.getGovernmentId().uuid());
      }
      if (entry.getLeaderId() != null) {
         tag.putUUID("leader", entry.getLeaderId().uuid());
      }
      if (entry.getGroupId() != null) {
         tag.putUUID("group", entry.getGroupId().uuid());
      }
      if (entry.getCoalitionId() != null) {
         tag.putUUID("coalition", entry.getCoalitionId().uuid());
      }
      if (entry.getCrisisId() != null) {
         tag.putUUID("crisis", entry.getCrisisId().uuid());
      }
      if (entry.getDecisionId() != null) {
         tag.putUUID("decision", entry.getDecisionId().uuid());
      }
      tag.putInt("quantity", entry.getQuantity());
      if (entry.getDetail() != null) {
         tag.putString("detail", entry.getDetail());
      }
      return tag;
   }

   private static PoliticsSavedData load(CompoundTag root) {
      PoliticsSavedData data = new PoliticsSavedData();
      int version = root.contains("version") ? root.getInt("version") : 0;
      if (version > CURRENT_VERSION) {
         LOGGER.warn("[Millenaire] PoliticsSavedData version {} newer than supported {}", version, CURRENT_VERSION);
      } else {
         LOGGER.info("[Millenaire] Loading PoliticsSavedData version {}", version);
      }

      if (root.contains("last_update")) {
         data.politicsManager.setLastUpdateGameTime(root.getLong("last_update"));
      }

      if (root.contains("governments")) {
         ListTag list = root.getList("governments", 10);
         for (int i = 0; i < list.size(); i++) {
            try {
               Government government = readGovernment(list.getCompound(i));
               if (government != null) {
                  data.politicsManager.loadGovernment(government);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed government at index {}", i, e);
            }
         }
      }

      if (root.contains("faction_states")) {
         ListTag list = root.getList("faction_states", 10);
         for (int i = 0; i < list.size(); i++) {
            try {
               FactionPoliticalState state = readFactionState(list.getCompound(i));
               if (state != null) {
                  data.politicsManager.loadState(state);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed faction political state at index {}", i, e);
            }
         }
      }

      if (root.contains("history")) {
         ListTag list = root.getList("history", 10);
         List<PoliticalHistoryEntry> history = new ArrayList<>();
         for (int i = 0; i < list.size(); i++) {
            try {
               PoliticalHistoryEntry entry = readHistory(list.getCompound(i));
               if (entry != null) {
                  history.add(entry);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed global political history at index {}", i, e);
            }
         }

         data.politicsManager.loadGlobalHistory(history);
      }

      return data;
   }

   @Nullable
   private static Government readGovernment(CompoundTag tag) {
      if (!tag.hasUUID("id")) {
         return null;
      }

      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      if (factionId == null) {
         return null;
      }

      Government government = new Government(
         new GovernmentId(tag.getUUID("id")),
         factionId,
         GovernmentType.tryParse(tag.contains("type") ? tag.getString("type") : null),
         tag.contains("created") ? tag.getLong("created") : 0L
      );

      EnumMap<PolicyType, GovernmentPolicy> policies = new EnumMap<>(PolicyType.class);
      if (tag.contains("policies")) {
         ListTag list = tag.getList("policies", 10);
         for (int i = 0; i < list.size(); i++) {
            CompoundTag p = list.getCompound(i);
            PolicyType type = PolicyType.tryParse(p.contains("type") ? p.getString("type") : null);
            if (type == null) {
               continue;
            }

            GovernmentPolicy policy = new GovernmentPolicy(type, PolicyStance.NORMAL, 0L);
            policy.loadState(
               PolicyStance.tryParse(p.contains("stance") ? p.getString("stance") : null),
               p.contains("last_changed") ? p.getLong("last_changed") : 0L,
               p.contains("note") ? p.getString("note") : null
            );
            policies.put(type, policy);
         }
      }

      government.loadState(
         GovernmentType.tryParse(tag.contains("type") ? tag.getString("type") : null),
         tag.hasUUID("leader") ? new LeaderId(tag.getUUID("leader")) : null,
         tag.contains("legitimacy") ? tag.getInt("legitimacy") : PoliticsConfig.LEGITIMACY_BASE,
         tag.contains("stability") ? tag.getInt("stability") : PoliticsConfig.STABILITY_BASE,
         tag.contains("centralization") ? tag.getInt("centralization") : PoliticsConfig.CENTRALIZATION_BASE,
         SuccessionMechanism.tryParse(tag.contains("succession_mechanism") ? tag.getString("succession_mechanism") : null),
         InterimGovernmentType.tryParse(tag.contains("interim_type") ? tag.getString("interim_type") : null),
         tag.hasUUID("ruling_coalition") ? new CoalitionId(tag.getUUID("ruling_coalition")) : null,
         policies,
         readGroupIdSet(tag, "opposition"),
         tag.contains("last_major_change") ? tag.getLong("last_major_change") : 0L,
         !tag.contains("active") || tag.getBoolean("active")
      );
      return government;
   }

   @Nullable
   private static FactionPoliticalState readFactionState(CompoundTag tag) {
      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      if (factionId == null) {
         return null;
      }

      FactionPoliticalState state = new FactionPoliticalState(factionId);

      if (tag.contains("groups")) {
         ListTag list = tag.getList("groups", 10);
         for (int i = 0; i < list.size(); i++) {
            PoliticalGroup group = readGroup(list.getCompound(i));
            if (group != null) {
               state.putGroup(group);
            }
         }
      }

      if (tag.contains("coalitions")) {
         ListTag list = tag.getList("coalitions", 10);
         for (int i = 0; i < list.size(); i++) {
            PoliticalCoalition coalition = readCoalition(list.getCompound(i));
            if (coalition != null) {
               state.putCoalition(coalition);
            }
         }
      }

      if (tag.contains("leaders")) {
         ListTag list = tag.getList("leaders", 10);
         for (int i = 0; i < list.size(); i++) {
            PoliticalLeader leader = readLeader(list.getCompound(i));
            if (leader != null) {
               state.putLeader(leader);
            }
         }
      }

      if (tag.contains("crises")) {
         ListTag list = tag.getList("crises", 10);
         for (int i = 0; i < list.size(); i++) {
            PoliticalCrisis crisis = readCrisis(list.getCompound(i));
            if (crisis != null) {
               state.putCrisis(crisis);
            }
         }
      }

      if (tag.contains("local_administrators")) {
         ListTag list = tag.getList("local_administrators", 10);
         for (int i = 0; i < list.size(); i++) {
            LocalAdministrator admin = readLocalAdministrator(list.getCompound(i));
            if (admin != null) {
               state.putLocalAdministrator(admin);
            }
         }
      }

      if (tag.contains("succession_candidates")) {
         ListTag list = tag.getList("succession_candidates", 10);
         for (int i = 0; i < list.size(); i++) {
            SuccessionCandidate candidate = readSuccessionCandidate(list.getCompound(i));
            if (candidate != null) {
               state.addSuccessionCandidate(candidate);
            }
         }
      }

      if (tag.contains("decisions")) {
         ListTag list = tag.getList("decisions", 10);
         for (int i = 0; i < list.size(); i++) {
            PoliticalDecision decision = readDecision(list.getCompound(i));
            if (decision != null) {
               state.addDecision(decision);
            }
         }
      }

      Set<GovernmentId> governmentHistory = new LinkedHashSet<>();
      if (tag.contains("government_history")) {
         ListTag list = tag.getList("government_history", 10);
         for (int i = 0; i < list.size(); i++) {
            CompoundTag g = list.getCompound(i);
            if (g.hasUUID("id")) {
               governmentHistory.add(new GovernmentId(g.getUUID("id")));
            }
         }
      }

      state.loadMetrics(
         tag.hasUUID("active_government") ? new GovernmentId(tag.getUUID("active_government")) : null,
         tag.contains("pressure") ? tag.getInt("pressure") : PoliticsConfig.PRESSURE_BASE,
         tag.contains("polarization") ? tag.getInt("polarization") : PoliticsConfig.POLARIZATION_BASE,
         tag.contains("rebellion_pressure") ? tag.getInt("rebellion_pressure") : PoliticsConfig.REBELLION_PRESSURE_BASE,
         InternalConflictState.tryParse(tag.contains("conflict_state") ? tag.getString("conflict_state") : null),
         tag.contains("succession_pending") && tag.getBoolean("succession_pending"),
         tag.contains("succession_started") ? tag.getLong("succession_started") : 0L,
         tag.contains("last_decision") ? tag.getLong("last_decision") : 0L,
         governmentHistory
      );

      if (tag.contains("history")) {
         ListTag list = tag.getList("history", 10);
         List<PoliticalHistoryEntry> history = new ArrayList<>();
         for (int i = 0; i < list.size(); i++) {
            PoliticalHistoryEntry entry = readHistory(list.getCompound(i));
            if (entry != null) {
               history.add(entry);
            }
         }

         state.loadHistory(history);
      }

      return state;
   }

   @Nullable
   private static PoliticalGroup readGroup(CompoundTag tag) {
      if (!tag.hasUUID("id")) {
         return null;
      }

      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      PoliticalGroupType type = PoliticalGroupType.tryParse(tag.contains("type") ? tag.getString("type") : null);
      if (factionId == null || type == null) {
         return null;
      }

      PoliticalGroup group = new PoliticalGroup(new PoliticalGroupId(tag.getUUID("id")), factionId, type);

      Map<InterestType, Integer> interests = new EnumMap<>(InterestType.class);
      if (tag.contains("interests")) {
         ListTag list = tag.getList("interests", 10);
         for (int i = 0; i < list.size(); i++) {
            CompoundTag interestTag = list.getCompound(i);
            InterestType interestType = InterestType.tryParse(interestTag.contains("type") ? interestTag.getString("type") : null);
            if (interestType != null) {
               interests.put(interestType, interestTag.contains("strength") ? interestTag.getInt("strength") : 0);
            }
         }
      }

      Set<String> demands = new LinkedHashSet<>();
      if (tag.contains("demands")) {
         ListTag list = tag.getList("demands", 8);
         for (int i = 0; i < list.size(); i++) {
            demands.add(list.getString(i));
         }
      }

      group.loadState(
         tag.contains("population_support") ? tag.getInt("population_support") : 30,
         tag.contains("political_power") ? tag.getInt("political_power") : 20,
         tag.contains("wealth_influence") ? tag.getInt("wealth_influence") : 20,
         tag.contains("military_influence") ? tag.getInt("military_influence") : 20,
         tag.contains("approval") ? tag.getInt("approval") : 50,
         ForeignPolicyPreference.tryParse(tag.contains("foreign_preference") ? tag.getString("foreign_preference") : null),
         !tag.contains("active") || tag.getBoolean("active"),
         tag.hasUUID("coalition") ? new CoalitionId(tag.getUUID("coalition")) : null,
         interests,
         demands
      );
      return group;
   }

   @Nullable
   private static PoliticalCoalition readCoalition(CompoundTag tag) {
      if (!tag.hasUUID("id")) {
         return null;
      }

      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      if (factionId == null) {
         return null;
      }

      PoliticalCoalition coalition = new PoliticalCoalition(
         new CoalitionId(tag.getUUID("id")),
         factionId,
         tag.contains("created") ? tag.getLong("created") : 0L,
         tag.contains("ruling") && tag.getBoolean("ruling")
      );
      coalition.loadState(
         readGroupIdSet(tag, "members"),
         tag.contains("ruling") && tag.getBoolean("ruling"),
         tag.contains("support") ? tag.getInt("support") : 0,
         tag.contains("political_power") ? tag.getInt("political_power") : 0,
         tag.contains("stability") ? tag.getInt("stability") : 60,
         !tag.contains("active") || tag.getBoolean("active"),
         tag.contains("dissolved") ? tag.getLong("dissolved") : 0L,
         tag.contains("shared_interest") ? tag.getString("shared_interest") : null
      );
      return coalition;
   }

   @Nullable
   private static PoliticalLeader readLeader(CompoundTag tag) {
      if (!tag.hasUUID("id")) {
         return null;
      }

      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      if (factionId == null) {
         return null;
      }

      PoliticalLeader leader = new PoliticalLeader(
         new LeaderId(tag.getUUID("id")),
         factionId,
         LeadershipRole.tryParse(tag.contains("role") ? tag.getString("role") : null),
         tag.contains("leadership_start") ? tag.getLong("leadership_start") : 0L,
         tag.contains("display_name") ? tag.getString("display_name") : null
      );
      leader.loadState(
         tag.contains("display_name") ? tag.getString("display_name") : null,
         LeadershipRole.tryParse(tag.contains("role") ? tag.getString("role") : null),
         tag.contains("legitimacy") ? tag.getInt("legitimacy") : PoliticsConfig.LEGITIMACY_BASE,
         tag.contains("popularity") ? tag.getInt("popularity") : PoliticsConfig.POPULARITY_BASE,
         tag.contains("competence") ? tag.getInt("competence") : 50,
         tag.contains("military_influence") ? tag.getInt("military_influence") : 40,
         tag.contains("economic_influence") ? tag.getInt("economic_influence") : 40,
         tag.contains("diplomatic_influence") ? tag.getInt("diplomatic_influence") : 40,
         tag.contains("political_influence") ? tag.getInt("political_influence") : 50,
         !tag.contains("active") || tag.getBoolean("active"),
         tag.contains("ended") ? tag.getLong("ended") : 0L,
         tag.hasUUID("origin_settlement") ? new VillageId(tag.getUUID("origin_settlement")) : null,
         tag.hasUUID("linked_npc") ? tag.getUUID("linked_npc") : null
      );
      return leader;
   }

   @Nullable
   private static PoliticalCrisis readCrisis(CompoundTag tag) {
      if (!tag.hasUUID("id")) {
         return null;
      }

      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      if (factionId == null) {
         return null;
      }

      PoliticalCrisis crisis = new PoliticalCrisis(
         new CrisisId(tag.getUUID("id")),
         factionId,
         CrisisType.tryParse(tag.contains("type") ? tag.getString("type") : null),
         tag.contains("cause") ? tag.getString("cause") : "unspecified",
         tag.contains("started") ? tag.getLong("started") : 0L,
         tag.contains("severity") ? tag.getInt("severity") : 0
      );
      crisis.loadState(
         readGroupIdSet(tag, "involved_groups"),
         tag.contains("severity") ? tag.getInt("severity") : 0,
         CrisisStage.tryParse(tag.contains("stage") ? tag.getString("stage") : null),
         !tag.contains("active") || tag.getBoolean("active"),
         tag.contains("resolved") ? tag.getLong("resolved") : 0L,
         tag.contains("resolution_path") ? tag.getString("resolution_path") : null,
         tag.contains("outcome") ? tag.getString("outcome") : null
      );
      return crisis;
   }

   @Nullable
   private static LocalAdministrator readLocalAdministrator(CompoundTag tag) {
      if (!tag.hasUUID("settlement")) {
         return null;
      }

      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      if (factionId == null) {
         return null;
      }

      LocalAdministrator admin = new LocalAdministrator(new VillageId(tag.getUUID("settlement")), factionId);
      admin.loadState(
         LeadershipRole.tryParse(tag.contains("role") ? tag.getString("role") : null),
         tag.contains("autonomy") ? tag.getInt("autonomy") : 50,
         tag.contains("loyalty") ? tag.getInt("loyalty") : 60,
         tag.contains("local_influence") ? tag.getInt("local_influence") : 40,
         tag.hasUUID("linked_leader") ? new LeaderId(tag.getUUID("linked_leader")) : null,
         tag.hasUUID("linked_npc") ? tag.getUUID("linked_npc") : null,
         tag.contains("display_name") ? tag.getString("display_name") : null,
         !tag.contains("active") || tag.getBoolean("active")
      );
      return admin;
   }

   @Nullable
   private static SuccessionCandidate readSuccessionCandidate(CompoundTag tag) {
      if (!tag.hasUUID("id")) {
         return null;
      }

      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      if (factionId == null) {
         return null;
      }

      SuccessionCandidate candidate = new SuccessionCandidate(
         new CandidateId(tag.getUUID("id")),
         factionId,
         tag.hasUUID("leader") ? new LeaderId(tag.getUUID("leader")) : null,
         tag.hasUUID("group") ? new PoliticalGroupId(tag.getUUID("group")) : null,
         tag.contains("display_name") ? tag.getString("display_name") : null
      );
      candidate.loadState(
         tag.contains("legitimacy") ? tag.getInt("legitimacy") : 0,
         tag.contains("popularity") ? tag.getInt("popularity") : 0,
         tag.contains("military_influence") ? tag.getInt("military_influence") : 0,
         tag.contains("economic_influence") ? tag.getInt("economic_influence") : 0,
         tag.contains("political_influence") ? tag.getInt("political_influence") : 0,
         tag.contains("succession_claim") ? tag.getInt("succession_claim") : 0,
         tag.contains("support") ? tag.getInt("support") : 0,
         tag.contains("display_name") ? tag.getString("display_name") : null
      );
      return candidate;
   }

   @Nullable
   private static PoliticalDecision readDecision(CompoundTag tag) {
      if (!tag.hasUUID("id")) {
         return null;
      }

      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      DecisionType type = DecisionType.tryParse(tag.contains("type") ? tag.getString("type") : null);
      if (factionId == null || type == null) {
         return null;
      }

      PoliticalDecision decision = new PoliticalDecision(
         new DecisionId(tag.getUUID("id")),
         factionId,
         type,
         tag.contains("decided") ? tag.getLong("decided") : 0L,
         tag.hasUUID("proposer_leader") ? new LeaderId(tag.getUUID("proposer_leader")) : null,
         tag.hasUUID("proposer_group") ? new PoliticalGroupId(tag.getUUID("proposer_group")) : null
      );
      decision.loadState(
         readGroupIdSet(tag, "affected_groups"),
         tag.contains("political_cost") ? tag.getInt("political_cost") : 0,
         tag.contains("approval_support") ? tag.getInt("approval_support") : 0,
         tag.contains("successful") && tag.getBoolean("successful"),
         tag.contains("requirements") ? tag.getString("requirements") : null,
         tag.contains("effects") ? tag.getString("effects") : null,
         tag.contains("outcome") ? tag.getString("outcome") : null
      );
      return decision;
   }

   @Nullable
   private static PoliticalHistoryEntry readHistory(CompoundTag tag) {
      PoliticalEventType eventType = PoliticalEventType.tryParse(tag.contains("event") ? tag.getString("event") : null);
      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      if (eventType == null || factionId == null) {
         return null;
      }

      return new PoliticalHistoryEntry(
         tag.contains("game_time") ? tag.getLong("game_time") : 0L,
         eventType,
         factionId,
         tag.hasUUID("government") ? new GovernmentId(tag.getUUID("government")) : null,
         tag.hasUUID("leader") ? new LeaderId(tag.getUUID("leader")) : null,
         tag.hasUUID("group") ? new PoliticalGroupId(tag.getUUID("group")) : null,
         tag.hasUUID("coalition") ? new CoalitionId(tag.getUUID("coalition")) : null,
         tag.hasUUID("crisis") ? new CrisisId(tag.getUUID("crisis")) : null,
         tag.hasUUID("decision") ? new DecisionId(tag.getUUID("decision")) : null,
         tag.contains("quantity") ? tag.getInt("quantity") : 0,
         tag.contains("detail") ? tag.getString("detail") : null
      );
   }

   private static Set<PoliticalGroupId> readGroupIdSet(CompoundTag tag, String key) {
      Set<PoliticalGroupId> ids = new LinkedHashSet<>();
      if (!tag.contains(key)) {
         return ids;
      }

      ListTag list = tag.getList(key, 10);
      for (int i = 0; i < list.size(); i++) {
         CompoundTag entry = list.getCompound(i);
         if (entry.hasUUID("id")) {
            ids.add(new PoliticalGroupId(entry.getUUID("id")));
         }
      }

      return ids;
   }
}
