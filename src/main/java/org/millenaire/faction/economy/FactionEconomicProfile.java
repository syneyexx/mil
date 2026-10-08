package org.millenaire.faction.economy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.millenaire.faction.FactionId;

/**
 * Faction-level economic profile.
 * <p>
 * Stock/demand snapshots are computed from Millénaire settlements and are not a second inventory.
 * Cumulative import/export statistics and history are persisted trade/economic metadata.
 */
public final class FactionEconomicProfile {
   private final FactionId factionId;
   private long cumulativeExportVolume;
   private long cumulativeImportVolume;
   private long cumulativeExportValue;
   private long cumulativeImportValue;
   private long lastSimulatedGameTime;
   private int lastStockValue;
   private int lastDemandPressure;
   private int lastSurplusResources;
   private int lastDeficitResources;
   private int lastSignificantDependencies;
   private EconomicBalanceLevel balanceLevel = EconomicBalanceLevel.BALANCED;
   private final Map<EconomicResourceId, Long> exportVolumes = new LinkedHashMap<>();
   private final Map<EconomicResourceId, Long> importVolumes = new LinkedHashMap<>();
   private final Deque<EconomicHistoryEntry> history = new ArrayDeque<>();
   private final Map<EconomicResourceId, ResourceBalance> lastBalances = new LinkedHashMap<>();

   public FactionEconomicProfile(FactionId factionId) {
      this.factionId = Objects.requireNonNull(factionId, "factionId");
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   public long getCumulativeExportVolume() {
      return this.cumulativeExportVolume;
   }

   public long getCumulativeImportVolume() {
      return this.cumulativeImportVolume;
   }

   public long getCumulativeExportValue() {
      return this.cumulativeExportValue;
   }

   public long getCumulativeImportValue() {
      return this.cumulativeImportValue;
   }

   public long getLastSimulatedGameTime() {
      return this.lastSimulatedGameTime;
   }

   public int getLastStockValue() {
      return this.lastStockValue;
   }

   public int getLastDemandPressure() {
      return this.lastDemandPressure;
   }

   public int getLastSurplusResources() {
      return this.lastSurplusResources;
   }

   public int getLastDeficitResources() {
      return this.lastDeficitResources;
   }

   public int getLastSignificantDependencies() {
      return this.lastSignificantDependencies;
   }

   public EconomicBalanceLevel getBalanceLevel() {
      return this.balanceLevel;
   }

   public Map<EconomicResourceId, Long> getExportVolumes() {
      return Collections.unmodifiableMap(this.exportVolumes);
   }

   public Map<EconomicResourceId, Long> getImportVolumes() {
      return Collections.unmodifiableMap(this.importVolumes);
   }

   public Map<EconomicResourceId, ResourceBalance> getLastBalances() {
      return Collections.unmodifiableMap(this.lastBalances);
   }

   public List<EconomicHistoryEntry> getHistory() {
      return Collections.unmodifiableList(new ArrayList<>(this.history));
   }

   void recordExport(EconomicResourceId resourceId, int quantity, int value) {
      this.cumulativeExportVolume += Math.max(0, quantity);
      this.cumulativeExportValue += Math.max(0, value);
      this.addTrackedVolume(this.exportVolumes, resourceId, quantity);
   }

   void recordImport(EconomicResourceId resourceId, int quantity, int value) {
      this.cumulativeImportVolume += Math.max(0, quantity);
      this.cumulativeImportValue += Math.max(0, value);
      this.addTrackedVolume(this.importVolumes, resourceId, quantity);
   }

   void applySnapshot(
      Map<EconomicResourceId, ResourceBalance> balances,
      int stockValue,
      int demandPressure,
      int surplusResources,
      int deficitResources,
      int significantDependencies,
      long gameTime
   ) {
      this.lastBalances.clear();
      if (balances != null) {
         this.lastBalances.putAll(balances);
      }

      this.lastStockValue = Math.max(0, stockValue);
      this.lastDemandPressure = Math.max(0, demandPressure);
      this.lastSurplusResources = Math.max(0, surplusResources);
      this.lastDeficitResources = Math.max(0, deficitResources);
      this.lastSignificantDependencies = Math.max(0, significantDependencies);
      this.balanceLevel = EconomicBalanceLevel.fromMetrics(
         this.lastSurplusResources, this.lastDeficitResources, this.lastSignificantDependencies, this.lastStockValue, this.lastDemandPressure
      );
      this.lastSimulatedGameTime = gameTime;
   }

   void recordHistory(EconomicHistoryEntry entry) {
      Objects.requireNonNull(entry, "entry");
      this.history.addLast(entry);

      while (this.history.size() > EconomicConfig.MAX_ECONOMIC_HISTORY_ENTRIES) {
         this.history.removeFirst();
      }
   }

   long totalImportsOf(EconomicResourceId resourceId) {
      return this.importVolumes.getOrDefault(resourceId, 0L);
   }

   void loadTotals(
      long exportVolume,
      long importVolume,
      long exportValue,
      long importValue,
      long lastSimulatedGameTime,
      int lastStockValue,
      int lastDemandPressure,
      int lastSurplusResources,
      int lastDeficitResources,
      int lastSignificantDependencies,
      EconomicBalanceLevel balanceLevel
   ) {
      this.cumulativeExportVolume = Math.max(0L, exportVolume);
      this.cumulativeImportVolume = Math.max(0L, importVolume);
      this.cumulativeExportValue = Math.max(0L, exportValue);
      this.cumulativeImportValue = Math.max(0L, importValue);
      this.lastSimulatedGameTime = Math.max(0L, lastSimulatedGameTime);
      this.lastStockValue = Math.max(0, lastStockValue);
      this.lastDemandPressure = Math.max(0, lastDemandPressure);
      this.lastSurplusResources = Math.max(0, lastSurplusResources);
      this.lastDeficitResources = Math.max(0, lastDeficitResources);
      this.lastSignificantDependencies = Math.max(0, lastSignificantDependencies);
      this.balanceLevel = balanceLevel == null ? EconomicBalanceLevel.BALANCED : balanceLevel;
   }

   void replaceExportVolumes(Map<EconomicResourceId, Long> volumes) {
      this.exportVolumes.clear();
      if (volumes != null) {
         this.exportVolumes.putAll(volumes);
      }
   }

   void replaceImportVolumes(Map<EconomicResourceId, Long> volumes) {
      this.importVolumes.clear();
      if (volumes != null) {
         this.importVolumes.putAll(volumes);
      }
   }

   void replaceHistory(List<EconomicHistoryEntry> entries) {
      this.history.clear();
      if (entries == null) {
         return;
      }

      for (EconomicHistoryEntry entry : entries) {
         if (entry != null) {
            this.history.addLast(entry);
         }
      }

      while (this.history.size() > EconomicConfig.MAX_ECONOMIC_HISTORY_ENTRIES) {
         this.history.removeFirst();
      }
   }

   private void addTrackedVolume(Map<EconomicResourceId, Long> map, EconomicResourceId resourceId, int quantity) {
      if (resourceId == null || quantity <= 0) {
         return;
      }

      map.merge(resourceId, (long)quantity, Long::sum);
      while (map.size() > EconomicConfig.MAX_TRACKED_RESOURCES_PER_FACTION) {
         EconomicResourceId first = map.keySet().iterator().next();
         map.remove(first);
      }
   }
}
