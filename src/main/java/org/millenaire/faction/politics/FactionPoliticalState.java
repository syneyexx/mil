package org.millenaire.faction.politics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.village.VillageId;

/**
 * Authoritative per-faction political aggregate owned by {@link PoliticsManager}.
 */
public final class FactionPoliticalState {
   private final FactionId factionId;
   @Nullable
   private GovernmentId activeGovernmentId;
   private int politicalPressure = PoliticsConfig.PRESSURE_BASE;
   private int polarization = PoliticsConfig.POLARIZATION_BASE;
   private int rebellionPressure = PoliticsConfig.REBELLION_PRESSURE_BASE;
   private InternalConflictState conflictState = InternalConflictState.STABLE;
   private boolean successionPending;
   private long successionStartedGameTime;
   private long lastDecisionGameTime;
   private final Map<PoliticalGroupId, PoliticalGroup> groups = new LinkedHashMap<>();
   private final Map<CoalitionId, PoliticalCoalition> coalitions = new LinkedHashMap<>();
   private final Map<LeaderId, PoliticalLeader> leaders = new LinkedHashMap<>();
   private final Map<CrisisId, PoliticalCrisis> crises = new LinkedHashMap<>();
   private final Map<VillageId, LocalAdministrator> localAdministrators = new LinkedHashMap<>();
   private final List<SuccessionCandidate> successionCandidates = new ArrayList<>();
   private final List<PoliticalDecision> decisions = new ArrayList<>();
   private final Deque<PoliticalHistoryEntry> history = new ArrayDeque<>();
   private final Set<GovernmentId> governmentHistoryIds = new LinkedHashSet<>();

   public FactionPoliticalState(FactionId factionId) {
      this.factionId = Objects.requireNonNull(factionId, "factionId");
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   @Nullable
   public GovernmentId getActiveGovernmentId() {
      return this.activeGovernmentId;
   }

   public int getPoliticalPressure() {
      return this.politicalPressure;
   }

   public int getPolarization() {
      return this.polarization;
   }

   public int getRebellionPressure() {
      return this.rebellionPressure;
   }

   public InternalConflictState getConflictState() {
      return this.conflictState;
   }

   public boolean isSuccessionPending() {
      return this.successionPending;
   }

   public long getSuccessionStartedGameTime() {
      return this.successionStartedGameTime;
   }

   public long getLastDecisionGameTime() {
      return this.lastDecisionGameTime;
   }

   public Map<PoliticalGroupId, PoliticalGroup> getGroups() {
      return Collections.unmodifiableMap(this.groups);
   }

   public Map<CoalitionId, PoliticalCoalition> getCoalitions() {
      return Collections.unmodifiableMap(this.coalitions);
   }

   public Map<LeaderId, PoliticalLeader> getLeaders() {
      return Collections.unmodifiableMap(this.leaders);
   }

   public Map<CrisisId, PoliticalCrisis> getCrises() {
      return Collections.unmodifiableMap(this.crises);
   }

   public Map<VillageId, LocalAdministrator> getLocalAdministrators() {
      return Collections.unmodifiableMap(this.localAdministrators);
   }

   public List<SuccessionCandidate> getSuccessionCandidates() {
      return Collections.unmodifiableList(this.successionCandidates);
   }

   public List<PoliticalDecision> getDecisions() {
      return Collections.unmodifiableList(this.decisions);
   }

   public List<PoliticalHistoryEntry> getHistory() {
      return Collections.unmodifiableList(new ArrayList<>(this.history));
   }

   public Set<GovernmentId> getGovernmentHistoryIds() {
      return Collections.unmodifiableSet(this.governmentHistoryIds);
   }

   void setActiveGovernmentId(@Nullable GovernmentId governmentId) {
      this.activeGovernmentId = governmentId;
      if (governmentId != null) {
         this.governmentHistoryIds.add(governmentId);
      }
   }

   void setPoliticalPressure(int pressure) {
      this.politicalPressure = PoliticsConfig.clamp(pressure);
   }

   void setPolarization(int polarization) {
      this.polarization = PoliticsConfig.clamp(polarization);
   }

   void setRebellionPressure(int rebellionPressure) {
      this.rebellionPressure = PoliticsConfig.clamp(rebellionPressure);
   }

   void setConflictState(InternalConflictState state) {
      this.conflictState = state == null ? InternalConflictState.STABLE : state;
   }

   void setSuccessionPending(boolean pending, long gameTime) {
      this.successionPending = pending;
      if (pending) {
         this.successionStartedGameTime = gameTime;
      }
   }

   void setLastDecisionGameTime(long gameTime) {
      this.lastDecisionGameTime = gameTime;
   }

   void putGroup(PoliticalGroup group) {
      if (group != null) {
         this.groups.put(group.getId(), group);
      }
   }

   void putCoalition(PoliticalCoalition coalition) {
      if (coalition != null) {
         this.coalitions.put(coalition.getId(), coalition);
      }
   }

   void putLeader(PoliticalLeader leader) {
      if (leader != null) {
         this.leaders.put(leader.getId(), leader);
      }
   }

   void putCrisis(PoliticalCrisis crisis) {
      if (crisis != null) {
         this.crises.put(crisis.getId(), crisis);
      }
   }

   void putLocalAdministrator(LocalAdministrator administrator) {
      if (administrator != null) {
         this.localAdministrators.put(administrator.getSettlementId(), administrator);
      }
   }

   void removeLocalAdministrator(VillageId settlementId) {
      this.localAdministrators.remove(settlementId);
   }

   void clearSuccessionCandidates() {
      this.successionCandidates.clear();
   }

   void addSuccessionCandidate(SuccessionCandidate candidate) {
      if (candidate != null) {
         this.successionCandidates.add(candidate);
      }
   }

   void addDecision(PoliticalDecision decision) {
      if (decision != null) {
         this.decisions.add(decision);
         while (this.decisions.size() > 128) {
            this.decisions.remove(0);
         }
      }
   }

   void recordHistory(PoliticalHistoryEntry entry) {
      if (entry == null) {
         return;
      }

      this.history.addLast(entry);
      while (this.history.size() > PoliticsConfig.MAX_FACTION_HISTORY) {
         this.history.removeFirst();
      }
   }

   void loadMetrics(
      @Nullable GovernmentId activeGovernmentId,
      int pressure,
      int polarization,
      int rebellionPressure,
      InternalConflictState conflictState,
      boolean successionPending,
      long successionStarted,
      long lastDecision,
      Set<GovernmentId> governmentHistory
   ) {
      this.activeGovernmentId = activeGovernmentId;
      this.politicalPressure = PoliticsConfig.clamp(pressure);
      this.polarization = PoliticsConfig.clamp(polarization);
      this.rebellionPressure = PoliticsConfig.clamp(rebellionPressure);
      this.conflictState = conflictState == null ? InternalConflictState.STABLE : conflictState;
      this.successionPending = successionPending;
      this.successionStartedGameTime = Math.max(0L, successionStarted);
      this.lastDecisionGameTime = Math.max(0L, lastDecision);
      this.governmentHistoryIds.clear();
      if (governmentHistory != null) {
         this.governmentHistoryIds.addAll(governmentHistory);
      }
   }

   void loadHistory(List<PoliticalHistoryEntry> history) {
      this.history.clear();
      if (history != null) {
         for (PoliticalHistoryEntry entry : history) {
            if (entry != null) {
               this.history.addLast(entry);
            }
         }
      }
   }
}
