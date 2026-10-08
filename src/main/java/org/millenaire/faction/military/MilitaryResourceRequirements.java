package org.millenaire.faction.military;

import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.economy.EconomyQueries;
import org.millenaire.faction.economy.FactionEconomicProfile;

/**
 * Snapshot of military supply/maintenance needs exposed to Phase 3 economy consumers.
 * Does not drain inventories or invent a parallel currency.
 */
public final class MilitaryResourceRequirements {
   private final FactionId factionId;
   private final int foodMaintenance;
   private final int equipmentValue;
   private final int activeSoldiers;
   private final int economicStockValue;

   private MilitaryResourceRequirements(FactionId factionId, int foodMaintenance, int equipmentValue, int activeSoldiers, int economicStockValue) {
      this.factionId = Objects.requireNonNull(factionId, "factionId");
      this.foodMaintenance = Math.max(0, foodMaintenance);
      this.equipmentValue = Math.max(0, equipmentValue);
      this.activeSoldiers = Math.max(0, activeSoldiers);
      this.economicStockValue = Math.max(0, economicStockValue);
   }

   public static MilitaryResourceRequirements of(ServerLevel level, FactionId factionId) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(factionId, "factionId");
      MilitaryManager manager = MilitarySavedData.get(level).getMilitaryManager();
      FactionEconomicProfile profile = EconomyQueries.profile(level, factionId);
      return new MilitaryResourceRequirements(
         factionId,
         manager.getFoodMaintenanceRequirement(factionId),
         manager.getEquipmentValueRequirement(factionId),
         manager.getActiveSoldierCount(factionId),
         profile == null ? 0 : profile.getLastStockValue()
      );
   }

   public FactionId getFactionId() {
      return this.factionId;
   }

   public int getFoodMaintenance() {
      return this.foodMaintenance;
   }

   public int getEquipmentValue() {
      return this.equipmentValue;
   }

   public int getActiveSoldiers() {
      return this.activeSoldiers;
   }

   public int getEconomicStockValue() {
      return this.economicStockValue;
   }

   public boolean exceedsStockRoughly() {
      return this.equipmentValue + this.foodMaintenance > this.economicStockValue && this.activeSoldiers > 0;
   }
}
