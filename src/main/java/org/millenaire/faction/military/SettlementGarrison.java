package org.millenaire.faction.military;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.millenaire.faction.FactionId;
import org.millenaire.village.VillageId;

public final class SettlementGarrison {
   private final VillageId settlementId;
   private final FactionId factionId;
   private int capacity;
   private final Set<UnitId> unitIds = new LinkedHashSet<>();
   @Nullable
   private CommanderId commanderId;
   private int cachedStrength;
   private int cachedReadinessPercent;
   private long lastUpdatedGameTime;

   public SettlementGarrison(VillageId settlementId, FactionId factionId, int capacity, long gameTime) {
      this.settlementId = Objects.requireNonNull(settlementId, "settlementId");
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.capacity = Math.max(0, capacity);
      this.lastUpdatedGameTime = gameTime;
   }

   public VillageId getSettlementId() {
      return this.settlementId;
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   public int getCapacity() {
      return this.capacity;
   }

   public Set<UnitId> getUnitIds() {
      return Collections.unmodifiableSet(this.unitIds);
   }

   @Nullable
   public CommanderId getCommanderId() {
      return this.commanderId;
   }

   public int getCachedStrength() {
      return this.cachedStrength;
   }

   public int getCachedReadinessPercent() {
      return this.cachedReadinessPercent;
   }

   public long getLastUpdatedGameTime() {
      return this.lastUpdatedGameTime;
   }

   public int getCurrentSoldierCount(java.util.function.Function<UnitId, MilitaryUnit> resolver) {
      int total = 0;

      for (UnitId unitId : this.unitIds) {
         MilitaryUnit unit = resolver.apply(unitId);
         if (unit != null) {
            total += unit.getSoldierCount();
         }
      }

      return total;
   }

   public int getAvailableCapacity(java.util.function.Function<UnitId, MilitaryUnit> resolver) {
      return Math.max(0, this.capacity - this.getCurrentSoldierCount(resolver));
   }

   void setCapacity(int capacity, long gameTime) {
      this.capacity = Math.max(0, capacity);
      this.lastUpdatedGameTime = gameTime;
   }

   boolean addUnit(UnitId unitId, long gameTime) {
      boolean added = this.unitIds.add(Objects.requireNonNull(unitId, "unitId"));
      this.lastUpdatedGameTime = gameTime;
      return added;
   }

   boolean removeUnit(UnitId unitId, long gameTime) {
      boolean removed = this.unitIds.remove(unitId);
      this.lastUpdatedGameTime = gameTime;
      return removed;
   }

   void setCommanderId(@Nullable CommanderId commanderId, long gameTime) {
      this.commanderId = commanderId;
      this.lastUpdatedGameTime = gameTime;
   }

   void updateCaches(int strength, int readinessPercent, long gameTime) {
      this.cachedStrength = MilitaryConfig.clampNonNegative(strength);
      this.cachedReadinessPercent = MilitaryConfig.clampPercent(readinessPercent);
      this.lastUpdatedGameTime = gameTime;
   }

   void loadState(int capacity, Set<UnitId> unitIds, @Nullable CommanderId commanderId, int cachedStrength, int cachedReadinessPercent, long lastUpdatedGameTime) {
      this.capacity = Math.max(0, capacity);
      this.unitIds.clear();
      if (unitIds != null) {
         this.unitIds.addAll(unitIds);
      }

      this.commanderId = commanderId;
      this.cachedStrength = MilitaryConfig.clampNonNegative(cachedStrength);
      this.cachedReadinessPercent = MilitaryConfig.clampPercent(cachedReadinessPercent);
      this.lastUpdatedGameTime = Math.max(0L, lastUpdatedGameTime);
   }
}
