package org.millenaire.faction.economy;

import java.util.Objects;
import org.millenaire.faction.FactionId;

/**
 * Tracks how much an importer relies on a specific exporter for a resource.
 * Does not apply political pressure by itself.
 */
public final class ResourceDependency {
   private final FactionId importerFactionId;
   private final FactionId exporterFactionId;
   private final EconomicResourceId resourceId;
   private long cumulativeVolume;
   private int dependencyPercent;
   private long lastUpdateGameTime;

   public ResourceDependency(FactionId importerFactionId, FactionId exporterFactionId, EconomicResourceId resourceId) {
      this.importerFactionId = Objects.requireNonNull(importerFactionId, "importerFactionId");
      this.exporterFactionId = Objects.requireNonNull(exporterFactionId, "exporterFactionId");
      this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
   }

   public FactionId getImporterFactionId() {
      return this.importerFactionId;
   }

   public FactionId getExporterFactionId() {
      return this.exporterFactionId;
   }

   public EconomicResourceId getResourceId() {
      return this.resourceId;
   }

   public long getCumulativeVolume() {
      return this.cumulativeVolume;
   }

   /** 0–100 share of the importer's tracked imports of this resource from this exporter. */
   public int getDependencyPercent() {
      return this.dependencyPercent;
   }

   public long getLastUpdateGameTime() {
      return this.lastUpdateGameTime;
   }

   public boolean isSignificant() {
      return this.cumulativeVolume >= EconomicConfig.MIN_VOLUME_FOR_DEPENDENCY
         && this.dependencyPercent >= EconomicConfig.SIGNIFICANT_DEPENDENCY_PERCENT;
   }

   void recordImport(int quantity, long gameTime) {
      this.cumulativeVolume += Math.max(0, quantity);
      this.lastUpdateGameTime = gameTime;
   }

   void setDependencyPercent(int percent) {
      this.dependencyPercent = Math.max(0, Math.min(100, percent));
   }

   void load(long cumulativeVolume, int dependencyPercent, long lastUpdateGameTime) {
      this.cumulativeVolume = Math.max(0L, cumulativeVolume);
      this.dependencyPercent = Math.max(0, Math.min(100, dependencyPercent));
      this.lastUpdateGameTime = Math.max(0L, lastUpdateGameTime);
   }
}
