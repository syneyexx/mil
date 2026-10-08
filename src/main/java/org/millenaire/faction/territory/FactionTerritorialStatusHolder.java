package org.millenaire.faction.territory;

/**
 * Mutable territorial posture for a faction, owned by TerritoryManager.
 */
public final class FactionTerritorialStatusHolder {
   private FactionTerritorialStatus status = FactionTerritorialStatus.SETTLED;
   private int overextension;
   private int administrativeCapacity = TerritoryConfig.ADMIN_CAPACITY_BASE;

   public FactionTerritorialStatus getStatus() {
      return this.status;
   }

   public int getOverextension() {
      return this.overextension;
   }

   public int getAdministrativeCapacity() {
      return this.administrativeCapacity;
   }

   public void setStatus(FactionTerritorialStatus status) {
      this.status = status == null ? FactionTerritorialStatus.SETTLED : status;
   }

   public void setOverextension(int overextension) {
      this.overextension = TerritoryConfig.clampPercent(overextension);
   }

   public void setAdministrativeCapacity(int capacity) {
      this.administrativeCapacity = Math.max(0, capacity);
   }
}
