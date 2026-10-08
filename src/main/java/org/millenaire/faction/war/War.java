package org.millenaire.faction.war;

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
import org.millenaire.faction.military.ArmyId;

public final class War {
   private final WarId id;
   private final FactionId initiatorFactionId;
   private final FactionId primaryTargetFactionId;
   private WarState state;
   private int warScore;
   private final long startedGameTime;
   private long mobilizationEndsGameTime;
   private long endedGameTime;
   private final Map<FactionId, WarParticipant> participants = new LinkedHashMap<>();
   private final Map<WarGoalId, WarGoal> goals = new LinkedHashMap<>();
   private final Set<BattleId> battleIds = new LinkedHashSet<>();
   private final Set<ArmyId> participatingArmyIds = new LinkedHashSet<>();
   @Nullable
   private Ceasefire ceasefire;
   @Nullable
   private PeaceSettlement peaceSettlement;
   @Nullable
   private PeaceOutcome outcome;
   private final Deque<WarHistoryEntry> history = new ArrayDeque<>();

   public War(WarId id, FactionId initiatorFactionId, FactionId primaryTargetFactionId, long gameTime) {
      this.id = Objects.requireNonNull(id, "id");
      this.initiatorFactionId = Objects.requireNonNull(initiatorFactionId, "initiatorFactionId");
      this.primaryTargetFactionId = Objects.requireNonNull(primaryTargetFactionId, "primaryTargetFactionId");
      if (initiatorFactionId.equals(primaryTargetFactionId)) {
         throw new IllegalArgumentException("war cannot be self-directed");
      }

      this.state = WarState.DECLARED;
      this.startedGameTime = gameTime;
      this.mobilizationEndsGameTime = gameTime + WarConfig.MOBILIZATION_DURATION_TICKS;
      this.participants.put(
         initiatorFactionId, new WarParticipant(initiatorFactionId, WarSideId.ATTACKER, JoinWarReason.INITIATOR, gameTime)
      );
      this.participants.put(
         primaryTargetFactionId, new WarParticipant(primaryTargetFactionId, WarSideId.DEFENDER, JoinWarReason.TARGET, gameTime)
      );
   }

   public WarId getId() {
      return this.id;
   }

   public FactionId getInitiatorFactionId() {
      return this.initiatorFactionId;
   }

   public FactionId getPrimaryTargetFactionId() {
      return this.primaryTargetFactionId;
   }

   public WarState getState() {
      return this.state;
   }

   public int getWarScore() {
      return this.warScore;
   }

   public long getStartedGameTime() {
      return this.startedGameTime;
   }

   public long getMobilizationEndsGameTime() {
      return this.mobilizationEndsGameTime;
   }

   public long getEndedGameTime() {
      return this.endedGameTime;
   }

   public Map<FactionId, WarParticipant> getParticipants() {
      return Collections.unmodifiableMap(this.participants);
   }

   public CollectionView activeParticipants() {
      List<WarParticipant> active = new ArrayList<>();
      for (WarParticipant participant : this.participants.values()) {
         if (participant.isActive()) {
            active.add(participant);
         }
      }

      return new CollectionView(active);
   }

   public Set<FactionId> getActiveFactionIds(WarSideId side) {
      Set<FactionId> result = new LinkedHashSet<>();
      for (WarParticipant participant : this.participants.values()) {
         if (participant.isActive() && participant.getSide() == side) {
            result.add(participant.getFactionId());
         }
      }

      return Collections.unmodifiableSet(result);
   }

   @Nullable
   public WarParticipant getParticipant(FactionId factionId) {
      return factionId == null ? null : this.participants.get(factionId);
   }

   @Nullable
   public WarSideId getSide(FactionId factionId) {
      WarParticipant participant = this.getParticipant(factionId);
      return participant == null || !participant.isActive() ? null : participant.getSide();
   }

   public boolean isParticipant(FactionId factionId) {
      WarParticipant participant = this.getParticipant(factionId);
      return participant != null && participant.isActive();
   }

   public Map<WarGoalId, WarGoal> getGoals() {
      return Collections.unmodifiableMap(this.goals);
   }

   public Set<BattleId> getBattleIds() {
      return Collections.unmodifiableSet(this.battleIds);
   }

   public Set<ArmyId> getParticipatingArmyIds() {
      return Collections.unmodifiableSet(this.participatingArmyIds);
   }

   @Nullable
   public Ceasefire getCeasefire() {
      return this.ceasefire;
   }

   @Nullable
   public PeaceSettlement getPeaceSettlement() {
      return this.peaceSettlement;
   }

   @Nullable
   public PeaceOutcome getOutcome() {
      return this.outcome;
   }

   public List<WarHistoryEntry> getHistory() {
      return Collections.unmodifiableList(new ArrayList<>(this.history));
   }

   void setState(WarState state) {
      this.state = state == null ? WarState.DECLARED : state;
   }

   void adjustWarScore(int delta) {
      this.warScore = WarConfig.clampWarScore(this.warScore + delta);
   }

   void setWarScore(int score) {
      this.warScore = WarConfig.clampWarScore(score);
   }

   boolean addParticipant(WarParticipant participant) {
      Objects.requireNonNull(participant, "participant");
      WarParticipant existing = this.participants.get(participant.getFactionId());
      if (existing != null && existing.isActive()) {
         return false;
      }

      this.participants.put(participant.getFactionId(), participant);
      return true;
   }

   boolean removeParticipant(FactionId factionId, long gameTime) {
      WarParticipant participant = this.participants.get(factionId);
      if (participant == null || !participant.isActive()) {
         return false;
      }

      participant.markLeft(gameTime);
      return true;
   }

   void addGoal(WarGoal goal) {
      this.goals.put(goal.getId(), goal);
   }

   void addBattle(BattleId battleId) {
      this.battleIds.add(Objects.requireNonNull(battleId, "battleId"));
   }

   void addArmy(ArmyId armyId) {
      this.participatingArmyIds.add(Objects.requireNonNull(armyId, "armyId"));
   }

   void removeArmy(ArmyId armyId) {
      this.participatingArmyIds.remove(armyId);
   }

   void setCeasefire(@Nullable Ceasefire ceasefire) {
      this.ceasefire = ceasefire;
   }

   void setPeaceSettlement(PeaceSettlement settlement) {
      this.peaceSettlement = Objects.requireNonNull(settlement, "settlement");
      this.outcome = settlement.getOutcome();
   }

   void end(PeaceOutcome outcome, long gameTime) {
      this.outcome = outcome == null ? PeaceOutcome.WHITE_PEACE : outcome;
      this.state = WarState.ENDED;
      this.endedGameTime = gameTime;
      if (this.ceasefire != null && this.ceasefire.isActive()) {
         this.ceasefire.end(gameTime);
      }
   }

   void recordHistory(WarHistoryEntry entry) {
      Objects.requireNonNull(entry, "entry");
      this.history.addLast(entry);
      while (this.history.size() > WarConfig.MAX_WAR_HISTORY_ENTRIES) {
         this.history.removeFirst();
      }
   }

   void loadState(
      WarState state,
      int warScore,
      long mobilizationEndsGameTime,
      long endedGameTime,
      @Nullable Ceasefire ceasefire,
      @Nullable PeaceSettlement peaceSettlement,
      @Nullable PeaceOutcome outcome,
      Set<BattleId> battleIds,
      Set<ArmyId> armyIds,
      List<WarHistoryEntry> history
   ) {
      this.state = state == null ? WarState.DECLARED : state;
      this.warScore = WarConfig.clampWarScore(warScore);
      this.mobilizationEndsGameTime = Math.max(0L, mobilizationEndsGameTime);
      this.endedGameTime = Math.max(0L, endedGameTime);
      this.ceasefire = ceasefire;
      this.peaceSettlement = peaceSettlement;
      this.outcome = outcome;
      this.battleIds.clear();
      if (battleIds != null) {
         this.battleIds.addAll(battleIds);
      }

      this.participatingArmyIds.clear();
      if (armyIds != null) {
         this.participatingArmyIds.addAll(armyIds);
      }

      this.history.clear();
      if (history != null) {
         for (WarHistoryEntry entry : history) {
            if (entry != null) {
               this.history.addLast(entry);
            }
         }
      }
   }

   void putLoadedParticipant(WarParticipant participant) {
      this.participants.put(participant.getFactionId(), participant);
   }

   void putLoadedGoal(WarGoal goal) {
      this.goals.put(goal.getId(), goal);
   }

   /** Simple unmodifiable list wrapper to avoid exposing mutable internals. */
   public static final class CollectionView {
      private final List<WarParticipant> values;

      CollectionView(List<WarParticipant> values) {
         this.values = Collections.unmodifiableList(values);
      }

      public List<WarParticipant> asList() {
         return this.values;
      }
   }
}
