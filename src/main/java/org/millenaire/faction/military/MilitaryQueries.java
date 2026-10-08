package org.millenaire.faction.military;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.FactionId;
import org.millenaire.village.VillageId;

/**
 * Internal/debug accessors for Phase 5 military organization. Not a player-facing GUI.
 * <p>
 * Phase 6 warfare systems should query through these APIs rather than mutating saved data directly.
 */
public final class MilitaryQueries {
   private MilitaryQueries() {
   }

   public static MilitaryManager manager(ServerLevel level) {
      return MilitarySavedData.get(level).getMilitaryManager();
   }

   @Nullable
   public static MilitaryOrganization organization(ServerLevel level, FactionId factionId) {
      return manager(level).getOrganization(factionId);
   }

   public static MilitaryOrganization organizationOrCreate(ServerLevel level, FactionId factionId) {
      return manager(level).getOrCreateOrganization(factionId);
   }

   @Nullable
   public static Army army(ServerLevel level, ArmyId armyId) {
      return manager(level).getArmy(armyId);
   }

   @Nullable
   public static MilitaryUnit unit(ServerLevel level, UnitId unitId) {
      return manager(level).getUnit(unitId);
   }

   @Nullable
   public static Commander commander(ServerLevel level, CommanderId commanderId) {
      return manager(level).getCommander(commanderId);
   }

   public static Collection<Army> armies(ServerLevel level, FactionId factionId) {
      return manager(level).getArmies(factionId);
   }

   public static int availableManpower(ServerLevel level, FactionId factionId) {
      return manager(level).getAvailableManpower(factionId);
   }

   public static int militaryStrength(ServerLevel level, FactionId factionId) {
      return manager(level).getMilitaryStrength(factionId);
   }

   public static int militaryPower(ServerLevel level, FactionId factionId) {
      return manager(level).getMilitaryStrength(factionId);
   }

   public static int armyStrength(ServerLevel level, ArmyId armyId) {
      return manager(level).getArmyStrength(armyId);
   }

   public static int unitStrength(ServerLevel level, UnitId unitId) {
      return manager(level).getUnitStrength(unitId);
   }

   public static int militaryReadiness(ServerLevel level, FactionId factionId) {
      return manager(level).getMilitaryReadiness(factionId);
   }

   public static int armyCount(ServerLevel level, FactionId factionId) {
      return manager(level).getArmyCount(factionId);
   }

   public static int activeSoldierCount(ServerLevel level, FactionId factionId) {
      return manager(level).getActiveSoldierCount(factionId);
   }

   public static int foodMaintenanceRequirement(ServerLevel level, FactionId factionId) {
      return manager(level).getFoodMaintenanceRequirement(factionId);
   }

   public static int equipmentValueRequirement(ServerLevel level, FactionId factionId) {
      return manager(level).getEquipmentValueRequirement(factionId);
   }

   public static MilitaryResourceRequirements resourceRequirements(ServerLevel level, FactionId factionId) {
      return MilitaryResourceRequirements.of(level, factionId);
   }

   public static Collection<FactionId> alliedFactions(ServerLevel level, FactionId factionId) {
      return manager(level).getAlliedFactions(level, factionId);
   }

   public static Collection<FactionId> defensivePactPartners(ServerLevel level, FactionId factionId) {
      return manager(level).getDefensivePactPartners(level, factionId);
   }

   public static Collection<FactionId> hostileFactions(ServerLevel level, FactionId factionId) {
      return manager(level).getHostileFactions(level, factionId);
   }

   @Nullable
   public static SettlementGarrison garrison(ServerLevel level, FactionId factionId, VillageId settlementId) {
      MilitaryOrganization organization = manager(level).getOrganization(factionId);
      if (organization == null || settlementId == null) {
         return null;
      }

      return organization.getGarrisons().get(settlementId);
   }

   public static List<MilitaryHistoryEntry> history(ServerLevel level, FactionId factionId) {
      MilitaryOrganization organization = manager(level).getOrganization(factionId);
      return organization == null ? List.of() : organization.getHistory();
   }

   @Nullable
   public static UnitId boundUnit(ServerLevel level, UUID entityUuid) {
      return manager(level).getBoundUnit(entityUuid);
   }
}
