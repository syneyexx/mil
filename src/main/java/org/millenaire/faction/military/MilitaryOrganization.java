package org.millenaire.faction.military;

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
import org.millenaire.faction.FactionId;
import org.millenaire.village.VillageId;

/**
 * Faction-level military organization. Exists even when the faction has no armies.
 */
public final class MilitaryOrganization {
   private final FactionId factionId;
   private int availableManpower;
   private int recruitedManpower;
   private int manpowerCapacity;
   private int reserveSoldierCount;
   private int activeSoldierCount;
   private MobilizationState mobilizationState = MobilizationState.DEMOBILIZED;
   private MilitaryAlertLevel alertLevel = MilitaryAlertLevel.NORMAL;
   private int organizationReadinessPercent;
   private int cachedMilitaryPower;
   private int infrastructureScore;
   private int recordedCasualties;
   private long lastReconciledGameTime;
   private final Set<ArmyId> armyIds = new LinkedHashSet<>();
   private final Set<UnitId> unitIds = new LinkedHashSet<>();
   private final Set<UnitId> reserveUnitIds = new LinkedHashSet<>();
   private final Set<CommanderId> commanderIds = new LinkedHashSet<>();
   private final Map<VillageId, SettlementGarrison> garrisons = new LinkedHashMap<>();
   private final Deque<MilitaryHistoryEntry> history = new ArrayDeque<>();

   public MilitaryOrganization(FactionId factionId) {
      this.factionId = Objects.requireNonNull(factionId, "factionId");
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   public int getAvailableManpower() {
      return this.availableManpower;
   }

   public int getRecruitedManpower() {
      return this.recruitedManpower;
   }

   public int getManpowerCapacity() {
      return this.manpowerCapacity;
   }

   public int getReserveSoldierCount() {
      return this.reserveSoldierCount;
   }

   public int getActiveSoldierCount() {
      return this.activeSoldierCount;
   }

   public MobilizationState getMobilizationState() {
      return this.mobilizationState;
   }

   public MilitaryAlertLevel getAlertLevel() {
      return this.alertLevel;
   }

   public int getOrganizationReadinessPercent() {
      return this.organizationReadinessPercent;
   }

   public int getCachedMilitaryPower() {
      return this.cachedMilitaryPower;
   }

   public int getInfrastructureScore() {
      return this.infrastructureScore;
   }

   /**
    * Cumulative casualty counter for Phase 6+. Phase 5 never increments this from combat.
    */
   public int getRecordedCasualties() {
      return this.recordedCasualties;
   }

   public long getLastReconciledGameTime() {
      return this.lastReconciledGameTime;
   }

   public Set<ArmyId> getArmyIds() {
      return Collections.unmodifiableSet(this.armyIds);
   }

   public Set<UnitId> getUnitIds() {
      return Collections.unmodifiableSet(this.unitIds);
   }

   public Set<UnitId> getReserveUnitIds() {
      return Collections.unmodifiableSet(this.reserveUnitIds);
   }

   public Set<CommanderId> getCommanderIds() {
      return Collections.unmodifiableSet(this.commanderIds);
   }

   public Map<VillageId, SettlementGarrison> getGarrisons() {
      return Collections.unmodifiableMap(this.garrisons);
   }

   public List<MilitaryHistoryEntry> getHistory() {
      return Collections.unmodifiableList(new ArrayList<>(this.history));
   }

   void setManpower(int available, int recruited, int capacity, long gameTime) {
      this.availableManpower = MilitaryConfig.clampNonNegative(available);
      this.recruitedManpower = MilitaryConfig.clampNonNegative(recruited);
      this.manpowerCapacity = MilitaryConfig.clampNonNegative(capacity);
      this.lastReconciledGameTime = gameTime;
   }

   void setSoldierCounts(int reserve, int active) {
      this.reserveSoldierCount = MilitaryConfig.clampNonNegative(reserve);
      this.activeSoldierCount = MilitaryConfig.clampNonNegative(active);
   }

   void setMobilizationState(MobilizationState state) {
      this.mobilizationState = state == null ? MobilizationState.DEMOBILIZED : state;
   }

   void setAlertLevel(MilitaryAlertLevel level) {
      this.alertLevel = level == null ? MilitaryAlertLevel.NORMAL : level;
   }

   void setOrganizationReadinessPercent(int readinessPercent) {
      this.organizationReadinessPercent = MilitaryConfig.clampPercent(readinessPercent);
   }

   void setCachedMilitaryPower(int power) {
      this.cachedMilitaryPower = MilitaryConfig.clampNonNegative(power);
   }

   void setInfrastructureScore(int score) {
      this.infrastructureScore = MilitaryConfig.clampNonNegative(score);
   }

   void recordCasualties(int amount) {
      if (amount > 0) {
         long next = (long)this.recordedCasualties + amount;
         this.recordedCasualties = next > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)next;
      }
   }

   boolean addArmy(ArmyId armyId) {
      return this.armyIds.add(Objects.requireNonNull(armyId, "armyId"));
   }

   boolean removeArmy(ArmyId armyId) {
      return this.armyIds.remove(armyId);
   }

   boolean addUnit(UnitId unitId) {
      return this.unitIds.add(Objects.requireNonNull(unitId, "unitId"));
   }

   boolean removeUnit(UnitId unitId) {
      this.reserveUnitIds.remove(unitId);
      return this.unitIds.remove(unitId);
   }

   boolean addReserveUnit(UnitId unitId) {
      this.unitIds.add(Objects.requireNonNull(unitId, "unitId"));
      return this.reserveUnitIds.add(unitId);
   }

   boolean removeReserveUnit(UnitId unitId) {
      return this.reserveUnitIds.remove(unitId);
   }

   boolean addCommander(CommanderId commanderId) {
      return this.commanderIds.add(Objects.requireNonNull(commanderId, "commanderId"));
   }

   boolean removeCommander(CommanderId commanderId) {
      return this.commanderIds.remove(commanderId);
   }

   SettlementGarrison getOrCreateGarrison(VillageId settlementId, int capacity, long gameTime) {
      return this.garrisons.computeIfAbsent(settlementId, id -> new SettlementGarrison(id, this.factionId, capacity, gameTime));
   }

   @javax.annotation.Nullable
   SettlementGarrison removeGarrison(VillageId settlementId) {
      return this.garrisons.remove(settlementId);
   }

   void recordHistory(MilitaryHistoryEntry entry) {
      Objects.requireNonNull(entry, "entry");
      this.history.addLast(entry);

      while (this.history.size() > MilitaryConfig.MAX_MILITARY_HISTORY_ENTRIES) {
         this.history.removeFirst();
      }
   }

   void loadState(
      int availableManpower,
      int recruitedManpower,
      int manpowerCapacity,
      int reserveSoldierCount,
      int activeSoldierCount,
      MobilizationState mobilizationState,
      MilitaryAlertLevel alertLevel,
      int organizationReadinessPercent,
      int cachedMilitaryPower,
      int infrastructureScore,
      long lastReconciledGameTime,
      Set<ArmyId> armyIds,
      Set<UnitId> unitIds,
      Set<UnitId> reserveUnitIds,
      Set<CommanderId> commanderIds,
      List<MilitaryHistoryEntry> history,
      int recordedCasualties
   ) {
      this.availableManpower = MilitaryConfig.clampNonNegative(availableManpower);
      this.recruitedManpower = MilitaryConfig.clampNonNegative(recruitedManpower);
      this.manpowerCapacity = MilitaryConfig.clampNonNegative(manpowerCapacity);
      this.reserveSoldierCount = MilitaryConfig.clampNonNegative(reserveSoldierCount);
      this.activeSoldierCount = MilitaryConfig.clampNonNegative(activeSoldierCount);
      this.mobilizationState = mobilizationState == null ? MobilizationState.DEMOBILIZED : mobilizationState;
      this.alertLevel = alertLevel == null ? MilitaryAlertLevel.NORMAL : alertLevel;
      this.organizationReadinessPercent = MilitaryConfig.clampPercent(organizationReadinessPercent);
      this.cachedMilitaryPower = MilitaryConfig.clampNonNegative(cachedMilitaryPower);
      this.infrastructureScore = MilitaryConfig.clampNonNegative(infrastructureScore);
      this.recordedCasualties = MilitaryConfig.clampNonNegative(recordedCasualties);
      this.lastReconciledGameTime = Math.max(0L, lastReconciledGameTime);
      this.armyIds.clear();
      if (armyIds != null) {
         this.armyIds.addAll(armyIds);
      }

      this.unitIds.clear();
      if (unitIds != null) {
         this.unitIds.addAll(unitIds);
      }

      this.reserveUnitIds.clear();
      if (reserveUnitIds != null) {
         this.reserveUnitIds.addAll(reserveUnitIds);
      }

      this.commanderIds.clear();
      if (commanderIds != null) {
         this.commanderIds.addAll(commanderIds);
      }

      this.history.clear();
      if (history != null) {
         for (MilitaryHistoryEntry entry : history) {
            if (entry != null) {
               this.history.addLast(entry);
            }
         }
      }
   }

   void putGarrison(SettlementGarrison garrison) {
      this.garrisons.put(garrison.getSettlementId(), garrison);
   }
}
