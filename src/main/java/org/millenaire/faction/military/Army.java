package org.millenaire.faction.military;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.village.VillageId;

public final class Army {
   private final ArmyId id;
   private FactionId factionId;
   private String name;
   private ArmyState state;
   @Nullable
   private CommanderId commanderId;
   @Nullable
   private VillageId originSettlementId;
   @Nullable
   private VillageId stationedSettlementId;
   private final Set<UnitId> unitIds = new LinkedHashSet<>();
   private int cachedStrength;
   private int cachedReadinessPercent;
   private final long createdGameTime;
   private long lastUpdatedGameTime;
   private final Deque<MilitaryHistoryEntry> history = new ArrayDeque<>();

   public Army(ArmyId id, FactionId factionId, String name, long gameTime, @Nullable VillageId originSettlementId) {
      this.id = Objects.requireNonNull(id, "id");
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.name = requireName(name);
      this.state = ArmyState.FORMING;
      this.originSettlementId = originSettlementId;
      this.stationedSettlementId = originSettlementId;
      this.createdGameTime = gameTime;
      this.lastUpdatedGameTime = gameTime;
   }

   public ArmyId getId() {
      return this.id;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   /**
    * Phase 9 civil-war allegiance transfer. Preserves army identity and unit membership.
    */
   void reassignFaction(FactionId newFactionId) {
      this.factionId = Objects.requireNonNull(newFactionId, "newFactionId");
   }

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = requireName(name);
   }

   public ArmyState getState() {
      return this.state;
   }

   @Nullable
   public CommanderId getCommanderId() {
      return this.commanderId;
   }

   @Nullable
   public VillageId getOriginSettlementId() {
      return this.originSettlementId;
   }

   @Nullable
   public VillageId getStationedSettlementId() {
      return this.stationedSettlementId;
   }

   public Set<UnitId> getUnitIds() {
      return Collections.unmodifiableSet(this.unitIds);
   }

   public int getCachedStrength() {
      return this.cachedStrength;
   }

   public int getCachedReadinessPercent() {
      return this.cachedReadinessPercent;
   }

   public long getCreatedGameTime() {
      return this.createdGameTime;
   }

   public long getLastUpdatedGameTime() {
      return this.lastUpdatedGameTime;
   }

   public List<MilitaryHistoryEntry> getHistory() {
      return Collections.unmodifiableList(new ArrayList<>(this.history));
   }

   public boolean containsUnit(UnitId unitId) {
      return unitId != null && this.unitIds.contains(unitId);
   }

   void setState(ArmyState state, long gameTime) {
      this.state = state == null ? ArmyState.FORMING : state;
      this.lastUpdatedGameTime = gameTime;
   }

   void setCommanderId(@Nullable CommanderId commanderId, long gameTime) {
      this.commanderId = commanderId;
      this.lastUpdatedGameTime = gameTime;
   }

   void setStationedSettlementId(@Nullable VillageId settlementId, long gameTime) {
      this.stationedSettlementId = settlementId;
      this.lastUpdatedGameTime = gameTime;
   }

   boolean addUnit(UnitId unitId, long gameTime) {
      boolean added = this.unitIds.add(Objects.requireNonNull(unitId, "unitId"));
      this.lastUpdatedGameTime = gameTime;
      if (added && this.state == ArmyState.FORMING && !this.unitIds.isEmpty()) {
         this.state = ArmyState.READY;
      }

      return added;
   }

   boolean removeUnit(UnitId unitId, long gameTime) {
      boolean removed = this.unitIds.remove(unitId);
      this.lastUpdatedGameTime = gameTime;
      if (removed && this.unitIds.isEmpty() && this.state.isActiveOrganization()) {
         this.state = ArmyState.FORMING;
      }

      return removed;
   }

   void updateCaches(int strength, int readinessPercent, long gameTime) {
      this.cachedStrength = MilitaryConfig.clampNonNegative(strength);
      this.cachedReadinessPercent = MilitaryConfig.clampPercent(readinessPercent);
      this.lastUpdatedGameTime = gameTime;
   }

   void recordHistory(MilitaryHistoryEntry entry) {
      Objects.requireNonNull(entry, "entry");
      this.history.addLast(entry);

      while (this.history.size() > MilitaryConfig.MAX_ARMY_HISTORY_ENTRIES) {
         this.history.removeFirst();
      }
   }

   void clearOriginIfMatches(VillageId settlementId) {
      if (settlementId != null && settlementId.equals(this.originSettlementId)) {
         this.originSettlementId = null;
      }

      if (settlementId != null && settlementId.equals(this.stationedSettlementId)) {
         this.stationedSettlementId = null;
      }
   }

   void loadState(
      String name,
      ArmyState state,
      @Nullable CommanderId commanderId,
      @Nullable VillageId originSettlementId,
      @Nullable VillageId stationedSettlementId,
      Set<UnitId> unitIds,
      int cachedStrength,
      int cachedReadinessPercent,
      long lastUpdatedGameTime,
      List<MilitaryHistoryEntry> history
   ) {
      this.name = requireName(name);
      this.state = state == null ? ArmyState.FORMING : state;
      this.commanderId = commanderId;
      this.originSettlementId = originSettlementId;
      this.stationedSettlementId = stationedSettlementId;
      this.unitIds.clear();
      if (unitIds != null) {
         this.unitIds.addAll(unitIds);
      }

      this.cachedStrength = MilitaryConfig.clampNonNegative(cachedStrength);
      this.cachedReadinessPercent = MilitaryConfig.clampPercent(cachedReadinessPercent);
      this.lastUpdatedGameTime = Math.max(0L, lastUpdatedGameTime);
      this.history.clear();
      if (history != null) {
         for (MilitaryHistoryEntry entry : history) {
            if (entry != null) {
               this.history.addLast(entry);
            }
         }
      }
   }

   private static String requireName(String name) {
      Objects.requireNonNull(name, "name");
      String trimmed = name.trim();
      if (trimmed.isEmpty()) {
         throw new IllegalArgumentException("army name blank");
      }

      return trimmed;
   }
}
