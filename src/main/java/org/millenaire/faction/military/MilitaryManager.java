package org.millenaire.faction.military;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionManager;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.diplomacy.DiplomacyQueries;
import org.millenaire.faction.diplomacy.DiplomaticStanding;
import org.millenaire.faction.economy.EconomyQueries;
import org.millenaire.faction.economy.FactionEconomicProfile;
import org.millenaire.faction.relationship.FactionRelationship;
import org.millenaire.faction.relationship.RelationshipQueries;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.slf4j.Logger;

/**
 * Authoritative Phase 5 military organization API. No warfare, battles, sieges, or movement.
 */
public final class MilitaryManager {
   private static final Logger LOGGER = LogUtils.getLogger();

   private final Map<FactionId, MilitaryOrganization> organizations = new LinkedHashMap<>();
   private final Map<ArmyId, Army> armies = new LinkedHashMap<>();
   private final Map<UnitId, MilitaryUnit> units = new LinkedHashMap<>();
   private final Map<CommanderId, Commander> commanders = new LinkedHashMap<>();
   private final Map<UUID, UnitId> entityBindings = new LinkedHashMap<>();
   private final Runnable markDirty;
   private long lastUpdateGameTime;

   MilitaryManager(Runnable markDirty) {
      this.markDirty = Objects.requireNonNull(markDirty, "markDirty");
   }

   public MilitaryOrganization getOrCreateOrganization(FactionId factionId) {
      Objects.requireNonNull(factionId, "factionId");
      return this.organizations.computeIfAbsent(factionId, MilitaryOrganization::new);
   }

   @Nullable
   public MilitaryOrganization getOrganization(FactionId factionId) {
      return factionId == null ? null : this.organizations.get(factionId);
   }

   public Collection<MilitaryOrganization> getAllOrganizations() {
      return Collections.unmodifiableCollection(this.organizations.values());
   }

   @Nullable
   public Army getArmy(ArmyId armyId) {
      return armyId == null ? null : this.armies.get(armyId);
   }

   @Nullable
   public MilitaryUnit getUnit(UnitId unitId) {
      return unitId == null ? null : this.units.get(unitId);
   }

   @Nullable
   public Commander getCommander(CommanderId commanderId) {
      return commanderId == null ? null : this.commanders.get(commanderId);
   }

   public Collection<Army> getArmies(FactionId factionId) {
      MilitaryOrganization organization = this.getOrganization(factionId);
      if (organization == null) {
         return List.of();
      }

      List<Army> result = new ArrayList<>();

      for (ArmyId armyId : organization.getArmyIds()) {
         Army army = this.armies.get(armyId);
         if (army != null) {
            result.add(army);
         }
      }

      return Collections.unmodifiableList(result);
   }

   public int getAvailableManpower(FactionId factionId) {
      MilitaryOrganization organization = this.getOrganization(factionId);
      return organization == null ? 0 : organization.getAvailableManpower();
   }

   public int getMilitaryStrength(FactionId factionId) {
      MilitaryOrganization organization = this.getOrganization(factionId);
      return organization == null ? 0 : organization.getCachedMilitaryPower();
   }

   public int getArmyStrength(ArmyId armyId) {
      Army army = this.getArmy(armyId);
      return army == null ? 0 : army.getCachedStrength();
   }

   public int getUnitStrength(UnitId unitId) {
      MilitaryUnit unit = this.getUnit(unitId);
      return unit == null ? 0 : MilitaryStrengthCalculator.unitStrength(unit, UnitTypeRegistry.get(unit.getUnitTypeId()));
   }

   public int getMilitaryReadiness(FactionId factionId) {
      MilitaryOrganization organization = this.getOrganization(factionId);
      return organization == null ? 0 : organization.getOrganizationReadinessPercent();
   }

   public int getArmyCount(FactionId factionId) {
      MilitaryOrganization organization = this.getOrganization(factionId);
      return organization == null ? 0 : organization.getArmyIds().size();
   }

   public int getActiveSoldierCount(FactionId factionId) {
      MilitaryOrganization organization = this.getOrganization(factionId);
      return organization == null ? 0 : organization.getActiveSoldierCount();
   }

   public int getFoodMaintenanceRequirement(FactionId factionId) {
      MilitaryOrganization organization = this.getOrganization(factionId);
      return organization == null ? 0 : MilitaryStrengthCalculator.foodMaintenanceRequirement(organization, this::getUnit);
   }

   public int getEquipmentValueRequirement(FactionId factionId) {
      MilitaryOrganization organization = this.getOrganization(factionId);
      return organization == null ? 0 : MilitaryStrengthCalculator.equipmentValueRequirement(organization, this::getUnit);
   }

   public Collection<FactionId> getAlliedFactions(ServerLevel level, FactionId factionId) {
      return DiplomacyQueries.allies(level, factionId);
   }

   public Collection<FactionId> getDefensivePactPartners(ServerLevel level, FactionId factionId) {
      return DiplomacyQueries.manager(level).getDefensivePactPartners(factionId);
   }

   public Collection<FactionId> getHostileFactions(ServerLevel level, FactionId factionId) {
      Objects.requireNonNull(level, "level");
      if (factionId == null) {
         return List.of();
      }

      Set<FactionId> result = new LinkedHashSet<>();
      for (FactionRelationship relationship : RelationshipQueries.from(level, factionId)) {
         if (relationship != null && relationship.getCategory().isHostile()) {
            result.add(relationship.getTargetFactionId());
         }
      }

      for (Faction other : FactionSavedData.get(level).getFactionManager().getAllFactions()) {
         if (!factionId.equals(other.getId()) && DiplomacyQueries.standing(level, factionId, other.getId()) == DiplomaticStanding.HOSTILE) {
            result.add(other.getId());
         }
      }

      return Collections.unmodifiableSet(result);
   }

   public int getEconomicStockValue(ServerLevel level, FactionId factionId) {
      FactionEconomicProfile profile = EconomyQueries.profile(level, factionId);
      return profile == null ? 0 : profile.getLastStockValue();
   }

   public Map<UUID, UnitId> getEntityBindings() {
      return Collections.unmodifiableMap(this.entityBindings);
   }

   @Nullable
   public UnitId getBoundUnit(UUID entityUuid) {
      return entityUuid == null ? null : this.entityBindings.get(entityUuid);
   }

   public boolean bindEntityToUnit(UUID entityUuid, UnitId unitId) {
      if (entityUuid == null || unitId == null || !this.units.containsKey(unitId)) {
         return false;
      }

      this.entityBindings.put(entityUuid, unitId);
      this.markDirty.run();
      return true;
   }

   public boolean unbindEntity(UUID entityUuid) {
      if (entityUuid == null || this.entityBindings.remove(entityUuid) == null) {
         return false;
      }

      this.markDirty.run();
      return true;
   }

   public void clearEntityBindingsForUnit(UnitId unitId) {
      if (unitId == null) {
         return;
      }

      this.entityBindings.entrySet().removeIf(entry -> unitId.equals(entry.getValue()));
      this.markDirty.run();
   }

   @Nullable
   public MilitaryUnit recruit(
      ServerLevel level,
      FactionId factionId,
      ResourceLocation unitTypeId,
      int soldierCount,
      @Nullable VillageId originSettlementId
   ) {
      Objects.requireNonNull(level, "level");
      if (!this.validateFaction(level, factionId) || soldierCount < MilitaryConfig.MIN_UNIT_SIZE_ON_RECRUIT) {
         return null;
      }

      UnitTypeDefinition definition = UnitTypeRegistry.get(unitTypeId);
      if (definition == null) {
         LOGGER.warn("[Millenaire] Unknown unit type {}", unitTypeId);
         return null;
      }

      soldierCount = Math.min(soldierCount, Math.min(definition.defaultMaxStrength(), MilitaryConfig.MAX_UNIT_SIZE_ON_RECRUIT));
      this.reconcileOrganization(level, factionId);
      MilitaryOrganization organization = this.getOrCreateOrganization(factionId);
      int manpowerCost = soldierCount * definition.manpowerCostPerSoldier();
      if (organization.getAvailableManpower() < manpowerCost) {
         LOGGER.warn(
            "[Millenaire] Insufficient manpower for recruitment in {} (need {}, have {})",
            factionId,
            manpowerCost,
            organization.getAvailableManpower()
         );
         return null;
      }

      long gameTime = level.getGameTime();
      organization.recordHistory(
         new MilitaryHistoryEntry(gameTime, MilitaryEventType.RECRUITMENT_STARTED, factionId, null, null, null, originSettlementId, soldierCount, unitTypeId.toString())
      );
      MilitaryUnit unit = new MilitaryUnit(
         UnitId.random(), factionId, unitTypeId, soldierCount, definition.defaultMaxStrength(), gameTime, originSettlementId
      );
      unit.assignToReserve(gameTime);
      this.units.put(unit.getId(), unit);
      organization.addUnit(unit.getId());
      organization.addReserveUnit(unit.getId());
      organization.setManpower(
         ManpowerCalculator.availableManpower(organization.getManpowerCapacity(), organization.getRecruitedManpower() + manpowerCost),
         organization.getRecruitedManpower() + manpowerCost,
         organization.getManpowerCapacity(),
         gameTime
      );
      this.refreshSoldierCounts(organization);
      organization.recordHistory(
         new MilitaryHistoryEntry(gameTime, MilitaryEventType.RECRUITMENT_COMPLETED, factionId, null, unit.getId(), null, originSettlementId, soldierCount, unitTypeId.toString())
      );
      organization.recordHistory(
         new MilitaryHistoryEntry(gameTime, MilitaryEventType.UNIT_CREATED, factionId, null, unit.getId(), null, originSettlementId, soldierCount, null)
      );
      this.recalculateOrganizationCaches(organization, gameTime);
      this.markDirty.run();
      LOGGER.info("[Millenaire] Recruited unit {} ({} x{}) for faction {}", unit.getId(), unitTypeId, soldierCount, factionId);
      return unit;
   }

   @Nullable
   public Army createArmy(ServerLevel level, FactionId factionId, String name, @Nullable VillageId originSettlementId) {
      Objects.requireNonNull(level, "level");
      if (!this.validateFaction(level, factionId)) {
         return null;
      }

      long gameTime = level.getGameTime();
      MilitaryOrganization organization = this.getOrCreateOrganization(factionId);
      Army army = new Army(ArmyId.random(), factionId, name, gameTime, originSettlementId);
      this.armies.put(army.getId(), army);
      organization.addArmy(army.getId());
      MilitaryHistoryEntry entry = new MilitaryHistoryEntry(
         gameTime, MilitaryEventType.ARMY_CREATED, factionId, army.getId(), null, null, originSettlementId, 0, name
      );
      army.recordHistory(entry);
      organization.recordHistory(entry);
      this.markDirty.run();
      LOGGER.info("[Millenaire] Created army {} ({}) for faction {}", army.getId(), name, factionId);
      return army;
   }

   /**
    * Phase 9: transfer an existing army (units + commander) to another faction without duplicating
    * identities. Used for civil-war military splits and secession inheritance.
    */
   public boolean transferArmyAllegiance(ServerLevel level, ArmyId armyId, FactionId newFactionId) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(newFactionId, "newFactionId");
      Army army = this.armies.get(armyId);
      if (army == null || army.getState() == ArmyState.DISBANDED) {
         return false;
      }

      if (!this.validateFaction(level, newFactionId)) {
         return false;
      }

      if (newFactionId.equals(army.getFactionId())) {
         return true;
      }

      long gameTime = level.getGameTime();
      FactionId oldFactionId = army.getFactionId();
      MilitaryOrganization oldOrg = this.getOrCreateOrganization(oldFactionId);
      MilitaryOrganization newOrg = this.getOrCreateOrganization(newFactionId);

      oldOrg.removeArmy(armyId);
      army.reassignFaction(newFactionId);
      newOrg.addArmy(armyId);

      for (UnitId unitId : new ArrayList<>(army.getUnitIds())) {
         MilitaryUnit unit = this.units.get(unitId);
         if (unit == null) {
            continue;
         }

         oldOrg.removeUnit(unitId);
         unit.reassignFaction(newFactionId);
         newOrg.addUnit(unitId);
      }

      if (army.getCommanderId() != null) {
         Commander commander = this.commanders.get(army.getCommanderId());
         if (commander != null) {
            oldOrg.removeCommander(commander.getId());
            commander.reassignFaction(newFactionId);
            newOrg.addCommander(commander.getId());
         }
      }

      MilitaryHistoryEntry entry = new MilitaryHistoryEntry(
         gameTime,
         MilitaryEventType.ALLEGIANCE_TRANSFERRED,
         newFactionId,
         armyId,
         null,
         army.getCommanderId(),
         army.getOriginSettlementId(),
         0,
         "allegiance_transfer:" + oldFactionId.asString()
      );
      army.recordHistory(entry);
      newOrg.recordHistory(entry);
      this.recalculateOrganizationCaches(oldOrg, gameTime);
      this.recalculateOrganizationCaches(newOrg, gameTime);
      this.markDirty.run();
      LOGGER.info("[Millenaire] Transferred army {} allegiance {} → {}", armyId, oldFactionId, newFactionId);
      return true;
   }

   public boolean disbandArmy(ServerLevel level, ArmyId armyId) {
      Objects.requireNonNull(level, "level");
      Army army = this.armies.get(armyId);
      if (army == null || army.getState() == ArmyState.DISBANDED) {
         return false;
      }

      long gameTime = level.getGameTime();
      MilitaryOrganization organization = this.getOrCreateOrganization(army.getFactionId());
      List<UnitId> unitIds = new ArrayList<>(army.getUnitIds());

      for (UnitId unitId : unitIds) {
         this.transferUnitToReserve(level, unitId, false);
      }

      if (army.getCommanderId() != null) {
         this.unassignCommander(level, army.getCommanderId(), false);
      }

      army.setState(ArmyState.DISBANDED, gameTime);
      organization.removeArmy(armyId);
      MilitaryHistoryEntry entry = new MilitaryHistoryEntry(
         gameTime, MilitaryEventType.ARMY_DISBANDED, army.getFactionId(), armyId, null, null, army.getOriginSettlementId(), 0, army.getName()
      );
      army.recordHistory(entry);
      organization.recordHistory(entry);
      this.recalculateOrganizationCaches(organization, gameTime);
      try {
         org.millenaire.faction.war.WarSavedData.get(level).getWarManager().handleArmyRemoved(level, armyId);
      } catch (Exception ignored) {
         // Warfare layer may not be loaded yet during early bootstrap.
      }
      this.markDirty.run();
      LOGGER.info("[Millenaire] Disbanded army {} for faction {}", armyId, army.getFactionId());
      return true;
   }

   public boolean assignUnitToArmy(ServerLevel level, UnitId unitId, ArmyId armyId) {
      Objects.requireNonNull(level, "level");
      MilitaryUnit unit = this.units.get(unitId);
      Army army = this.armies.get(armyId);
      if (unit == null || army == null || army.getState() == ArmyState.DISBANDED) {
         return false;
      }

      if (!unit.getFactionId().equals(army.getFactionId())) {
         LOGGER.warn("[Millenaire] Cannot assign unit {} to foreign army {}", unitId, armyId);
         return false;
      }

      long gameTime = level.getGameTime();
      this.detachUnitFromCurrentAssignment(unit, gameTime);
      army.addUnit(unitId, gameTime);
      unit.assignToArmy(armyId, gameTime);
      MilitaryOrganization organization = this.getOrCreateOrganization(unit.getFactionId());
      organization.removeReserveUnit(unitId);
      MilitaryHistoryEntry entry = new MilitaryHistoryEntry(
         gameTime, MilitaryEventType.UNIT_TRANSFERRED, unit.getFactionId(), armyId, unitId, null, null, unit.getSoldierCount(), "to_army"
      );
      army.recordHistory(entry);
      organization.recordHistory(entry);
      this.refreshArmyCaches(army, gameTime);
      this.recalculateOrganizationCaches(organization, gameTime);
      this.markDirty.run();
      return true;
   }

   public boolean transferUnitToReserve(ServerLevel level, UnitId unitId) {
      return this.transferUnitToReserve(level, unitId, true);
   }

   public boolean transferUnitBetweenArmies(ServerLevel level, UnitId unitId, ArmyId targetArmyId) {
      return this.assignUnitToArmy(level, unitId, targetArmyId);
   }

   /**
    * Permanently removes a unit and returns its manpower cost to the available pool.
    */
   public boolean releaseUnit(ServerLevel level, UnitId unitId) {
      Objects.requireNonNull(level, "level");
      MilitaryUnit unit = this.units.get(unitId);
      if (unit == null) {
         return false;
      }

      long gameTime = level.getGameTime();
      UnitTypeDefinition definition = UnitTypeRegistry.get(unit.getUnitTypeId());
      int manpowerCost = unit.getSoldierCount() * (definition == null ? 1 : definition.manpowerCostPerSoldier());
      this.detachUnitFromCurrentAssignment(unit, gameTime);
      MilitaryOrganization organization = this.getOrCreateOrganization(unit.getFactionId());
      organization.removeUnit(unitId);
      organization.removeReserveUnit(unitId);
      this.entityBindings.entrySet().removeIf(entry -> unitId.equals(entry.getValue()));
      this.units.remove(unitId);
      int recruited = Math.max(0, organization.getRecruitedManpower() - manpowerCost);
      organization.setManpower(
         ManpowerCalculator.availableManpower(organization.getManpowerCapacity(), recruited),
         recruited,
         organization.getManpowerCapacity(),
         gameTime
      );
      organization.recordHistory(
         new MilitaryHistoryEntry(gameTime, MilitaryEventType.UNIT_TRANSFERRED, unit.getFactionId(), null, unitId, null, null, unit.getSoldierCount(), "released")
      );
      this.refreshSoldierCounts(organization);
      this.recalculateOrganizationCaches(organization, gameTime);
      this.markDirty.run();
      return true;
   }

   public boolean assignUnitToGarrison(ServerLevel level, UnitId unitId, VillageId settlementId) {
      Objects.requireNonNull(level, "level");
      MilitaryUnit unit = this.units.get(unitId);
      if (unit == null || settlementId == null) {
         return false;
      }

      Village village = VillageSavedData.get(level).getVillageManager().getVillage(settlementId);
      if (village == null) {
         return false;
      }

      if (village.getFactionId() == null || !village.getFactionId().equals(unit.getFactionId())) {
         LOGGER.warn("[Millenaire] Cannot garrison unit {} at settlement not owned by its faction", unitId);
         return false;
      }

      long gameTime = level.getGameTime();
      MilitaryOrganization organization = this.getOrCreateOrganization(unit.getFactionId());
      int capacity = MilitaryInfrastructureScanner.garrisonCapacityForSettlement(village);
      SettlementGarrison garrison = organization.getOrCreateGarrison(settlementId, capacity, gameTime);
      garrison.setCapacity(capacity, gameTime);
      if (garrison.getAvailableCapacity(this::getUnit) < unit.getSoldierCount()) {
         LOGGER.warn("[Millenaire] Garrison at {} lacks capacity for unit {}", settlementId.uuid(), unitId);
         return false;
      }

      this.detachUnitFromCurrentAssignment(unit, gameTime);
      garrison.addUnit(unitId, gameTime);
      unit.assignToGarrison(settlementId, gameTime);
      organization.removeReserveUnit(unitId);
      organization.recordHistory(
         new MilitaryHistoryEntry(gameTime, MilitaryEventType.GARRISON_CHANGED, unit.getFactionId(), null, unitId, null, settlementId, unit.getSoldierCount(), "assigned")
      );
      this.refreshGarrisonCaches(garrison, gameTime);
      this.recalculateOrganizationCaches(organization, gameTime);
      this.markDirty.run();
      return true;
   }

   @Nullable
   public Commander createCommander(ServerLevel level, FactionId factionId, String name, CommanderRank rank, @Nullable VillageId originSettlementId) {
      Objects.requireNonNull(level, "level");
      if (!this.validateFaction(level, factionId)) {
         return null;
      }

      long gameTime = level.getGameTime();
      Commander commander = new Commander(CommanderId.random(), factionId, name, rank, gameTime, originSettlementId);
      this.commanders.put(commander.getId(), commander);
      this.getOrCreateOrganization(factionId).addCommander(commander.getId());
      this.markDirty.run();
      LOGGER.info("[Millenaire] Created commander {} ({}) for faction {}", commander.getId(), name, factionId);
      return commander;
   }

   public boolean assignCommander(ServerLevel level, CommanderId commanderId, ArmyId armyId) {
      Objects.requireNonNull(level, "level");
      Commander commander = this.commanders.get(commanderId);
      Army army = this.armies.get(armyId);
      if (commander == null || army == null || army.getState() == ArmyState.DISBANDED) {
         return false;
      }

      if (!commander.getFactionId().equals(army.getFactionId())) {
         LOGGER.warn("[Millenaire] Commander {} cannot command foreign army {}", commanderId, armyId);
         return false;
      }

      if (commander.getAssignedArmyId() != null && !commander.getAssignedArmyId().equals(armyId)) {
         this.unassignCommander(level, commanderId, false);
      }

      if (army.getCommanderId() != null && !army.getCommanderId().equals(commanderId)) {
         this.unassignCommander(level, army.getCommanderId(), false);
      }

      long gameTime = level.getGameTime();
      this.clearGarrisonCommanderReferences(commander.getFactionId(), commanderId, gameTime);
      commander.assignToArmy(armyId, gameTime);
      army.setCommanderId(commanderId, gameTime);
      MilitaryOrganization organization = this.getOrCreateOrganization(army.getFactionId());
      MilitaryHistoryEntry entry = new MilitaryHistoryEntry(
         gameTime, MilitaryEventType.COMMANDER_ASSIGNED, army.getFactionId(), armyId, null, commanderId, null, 0, commander.getName()
      );
      army.recordHistory(entry);
      organization.recordHistory(entry);
      this.refreshArmyCaches(army, gameTime);
      this.markDirty.run();
      return true;
   }

   public boolean unassignCommander(ServerLevel level, CommanderId commanderId) {
      return this.unassignCommander(level, commanderId, true);
   }

   public boolean assignGarrisonCommander(ServerLevel level, CommanderId commanderId, VillageId settlementId) {
      Objects.requireNonNull(level, "level");
      Commander commander = this.commanders.get(commanderId);
      if (commander == null || settlementId == null) {
         return false;
      }

      Village village = VillageSavedData.get(level).getVillageManager().getVillage(settlementId);
      if (village == null || village.getFactionId() == null || !village.getFactionId().equals(commander.getFactionId())) {
         return false;
      }

      if (commander.getAssignedArmyId() != null) {
         this.unassignCommander(level, commanderId, false);
      }

      long gameTime = level.getGameTime();
      MilitaryOrganization organization = this.getOrCreateOrganization(commander.getFactionId());
      SettlementGarrison garrison = organization.getOrCreateGarrison(
         settlementId, MilitaryInfrastructureScanner.garrisonCapacityForSettlement(village), gameTime
      );
      if (garrison.getCommanderId() != null && !garrison.getCommanderId().equals(commanderId)) {
         Commander previous = this.commanders.get(garrison.getCommanderId());
         if (previous != null) {
            previous.clearAssignment(gameTime);
         }
      }

      garrison.setCommanderId(commanderId, gameTime);
      organization.recordHistory(
         new MilitaryHistoryEntry(gameTime, MilitaryEventType.COMMANDER_ASSIGNED, commander.getFactionId(), null, null, commanderId, settlementId, 0, "garrison")
      );
      this.markDirty.run();
      return true;
   }

   public boolean setMobilization(ServerLevel level, FactionId factionId, MobilizationState state) {
      Objects.requireNonNull(level, "level");
      if (!this.validateFaction(level, factionId) || state == null) {
         return false;
      }

      MilitaryOrganization organization = this.getOrCreateOrganization(factionId);
      if (organization.getMobilizationState() == state) {
         return true;
      }

      long gameTime = level.getGameTime();
      organization.setMobilizationState(state);
      organization.recordHistory(
         new MilitaryHistoryEntry(gameTime, MilitaryEventType.MOBILIZATION_CHANGED, factionId, null, null, null, null, 0, state.name())
      );
      this.markDirty.run();
      return true;
   }

   public boolean setAlertLevel(ServerLevel level, FactionId factionId, MilitaryAlertLevel levelValue) {
      Objects.requireNonNull(level, "level");
      if (!this.validateFaction(level, factionId) || levelValue == null) {
         return false;
      }

      MilitaryOrganization organization = this.getOrCreateOrganization(factionId);
      if (organization.getAlertLevel() == levelValue) {
         return true;
      }

      long gameTime = level.getGameTime();
      organization.setAlertLevel(levelValue);
      organization.recordHistory(
         new MilitaryHistoryEntry(gameTime, MilitaryEventType.ALERT_CHANGED, factionId, null, null, null, null, 0, levelValue.name())
      );
      this.markDirty.run();
      return true;
   }

   public boolean mobilize(ServerLevel level, FactionId factionId, MobilizationState state) {
      return this.setMobilization(level, factionId, state == null ? MobilizationState.FULL : state);
   }

   public boolean demobilize(ServerLevel level, FactionId factionId) {
      return this.setMobilization(level, factionId, MobilizationState.DEMOBILIZED);
   }

   public boolean setArmyOrganizationalState(ServerLevel level, ArmyId armyId, ArmyState state) {
      Objects.requireNonNull(level, "level");
      Army army = this.armies.get(armyId);
      if (army == null || state == null || state == ArmyState.DISBANDED) {
         return false;
      }

      if (state == ArmyState.DISABLED || army.getState() == ArmyState.DISBANDED) {
         if (army.getState() == ArmyState.DISBANDED) {
            return false;
         }
      }

      long gameTime = level.getGameTime();
      army.setState(state, gameTime);
      this.markDirty.run();
      return true;
   }

   public boolean stationArmy(ServerLevel level, ArmyId armyId, @Nullable VillageId settlementId) {
      Objects.requireNonNull(level, "level");
      Army army = this.armies.get(armyId);
      if (army == null || army.getState() == ArmyState.DISBANDED) {
         return false;
      }

      if (settlementId != null) {
         Village village = VillageSavedData.get(level).getVillageManager().getVillage(settlementId);
         if (village == null || village.getFactionId() == null || !village.getFactionId().equals(army.getFactionId())) {
            return false;
         }
      }

      long gameTime = level.getGameTime();
      army.setStationedSettlementId(settlementId, gameTime);
      if (settlementId != null && army.getState() != ArmyState.DISABLED) {
         army.setState(ArmyState.STATIONED, gameTime);
      }

      this.markDirty.run();
      return true;
   }

   public boolean setUnitReadiness(ServerLevel level, UnitId unitId, int readinessPercent) {
      Objects.requireNonNull(level, "level");
      MilitaryUnit unit = this.units.get(unitId);
      if (unit == null) {
         return false;
      }

      long gameTime = level.getGameTime();
      unit.setReadinessPercent(readinessPercent, gameTime);
      MilitaryOrganization organization = this.getOrCreateOrganization(unit.getFactionId());
      if (unit.getArmyId() != null) {
         Army army = this.armies.get(unit.getArmyId());
         if (army != null) {
            this.refreshArmyCaches(army, gameTime);
         }
      }

      this.recalculateOrganizationCaches(organization, gameTime);
      this.markDirty.run();
      return true;
   }

   public boolean setUnitEquipment(ServerLevel level, UnitId unitId, EquipmentLevel equipmentLevel) {
      Objects.requireNonNull(level, "level");
      MilitaryUnit unit = this.units.get(unitId);
      if (unit == null || equipmentLevel == null) {
         return false;
      }

      long gameTime = level.getGameTime();
      unit.setEquipmentLevel(equipmentLevel, gameTime);
      MilitaryOrganization organization = this.getOrCreateOrganization(unit.getFactionId());
      if (unit.getArmyId() != null) {
         Army army = this.armies.get(unit.getArmyId());
         if (army != null) {
            this.refreshArmyCaches(army, gameTime);
         }
      }

      this.recalculateOrganizationCaches(organization, gameTime);
      this.markDirty.run();
      return true;
   }

   /**
    * Phase 6+ awards commander experience after battles. Phase 5 never auto-generates experience.
    */
   public boolean awardCommanderExperience(ServerLevel level, CommanderId commanderId, int amount) {
      Objects.requireNonNull(level, "level");
      Commander commander = this.commanders.get(commanderId);
      if (commander == null || amount <= 0) {
         return false;
      }

      commander.awardExperience(amount, level.getGameTime());
      this.markDirty.run();
      return true;
   }

   /**
    * Records future combat casualties against organization statistics. Phase 5 does not invent battle losses.
    */
   public boolean recordCasualties(ServerLevel level, FactionId factionId, int amount) {
      Objects.requireNonNull(level, "level");
      if (!this.validateFaction(level, factionId) || amount <= 0) {
         return false;
      }

      this.getOrCreateOrganization(factionId).recordCasualties(amount);
      this.markDirty.run();
      return true;
   }

   public void ensureOrganizations(ServerLevel level) {
      FactionManager factionManager = FactionSavedData.get(level).getFactionManager();

      for (Faction faction : factionManager.getAllFactions()) {
         this.getOrCreateOrganization(faction.getId());
         this.reconcileOrganization(level, faction.getId());
      }
   }

   public void tick(ServerLevel level) {
      Objects.requireNonNull(level, "level");
      long gameTime = level.getGameTime();
      if (this.lastUpdateGameTime > 0L && gameTime - this.lastUpdateGameTime < MilitaryConfig.MILITARY_UPDATE_INTERVAL_TICKS) {
         return;
      }

      this.lastUpdateGameTime = gameTime;
      FactionManager factionManager = FactionSavedData.get(level).getFactionManager();

      for (Faction faction : factionManager.getAllFactions()) {
         this.reconcileOrganization(level, faction.getId());
      }
   }

   public void reconcileOrganization(ServerLevel level, FactionId factionId) {
      if (!this.validateFaction(level, factionId)) {
         return;
      }

      Faction faction = FactionSavedData.get(level).getFactionManager().getFaction(factionId);
      if (faction == null) {
         return;
      }

      long gameTime = level.getGameTime();
      MilitaryOrganization organization = this.getOrCreateOrganization(factionId);
      int infrastructure = MilitaryInfrastructureScanner.scoreFaction(level, faction);
      organization.setInfrastructureScore(infrastructure);
      int capacity = ManpowerCalculator.computeCapacity(level, faction, infrastructure);
      // Capacity may shrink below recruited when settlements vanish; available clamps to zero.
      organization.setManpower(
         ManpowerCalculator.availableManpower(capacity, organization.getRecruitedManpower()),
         organization.getRecruitedManpower(),
         capacity,
         gameTime
      );
      VillageSavedData villages = VillageSavedData.get(level);
      Set<VillageId> validSettlements = new LinkedHashSet<>(faction.getSettlementIds());
      List<VillageId> staleGarrisons = new ArrayList<>();

      for (VillageId settlementId : organization.getGarrisons().keySet()) {
         if (!validSettlements.contains(settlementId) || villages.getVillageManager().getVillage(settlementId) == null) {
            staleGarrisons.add(settlementId);
         }
      }

      for (VillageId stale : staleGarrisons) {
         this.handleSettlementRemoved(level, factionId, stale, false);
      }

      for (VillageId settlementId : validSettlements) {
         Village village = villages.getVillageManager().getVillage(settlementId);
         if (village != null) {
            SettlementGarrison garrison = organization.getOrCreateGarrison(
               settlementId, MilitaryInfrastructureScanner.garrisonCapacityForSettlement(village), gameTime
            );
            garrison.setCapacity(MilitaryInfrastructureScanner.garrisonCapacityForSettlement(village), gameTime);
            this.refreshGarrisonCaches(garrison, gameTime);
         }
      }

      for (ArmyId armyId : new ArrayList<>(organization.getArmyIds())) {
         Army army = this.armies.get(armyId);
         if (army != null && army.getState() != ArmyState.DISBANDED) {
            this.refreshArmyCaches(army, gameTime);
         }
      }

      this.refreshSoldierCounts(organization);
      this.recalculateOrganizationCaches(organization, gameTime);
      this.markDirty.run();
   }

   public void handleSettlementRemoved(ServerLevel level, FactionId factionId, VillageId settlementId, boolean markDirtyNow) {
      MilitaryOrganization organization = this.getOrganization(factionId);
      if (organization == null || settlementId == null) {
         return;
      }

      long gameTime = level.getGameTime();
      SettlementGarrison garrison = organization.removeGarrison(settlementId);
      if (garrison != null) {
         for (UnitId unitId : new ArrayList<>(garrison.getUnitIds())) {
            MilitaryUnit unit = this.units.get(unitId);
            if (unit != null) {
               unit.assignToReserve(gameTime);
               organization.addReserveUnit(unitId);
            }
         }
      }

      for (UnitId unitId : organization.getUnitIds()) {
         MilitaryUnit unit = this.units.get(unitId);
         if (unit != null) {
            unit.clearOriginIfMatches(settlementId);
         }
      }

      for (ArmyId armyId : organization.getArmyIds()) {
         Army army = this.armies.get(armyId);
         if (army != null) {
            army.clearOriginIfMatches(settlementId);
         }
      }

      for (CommanderId commanderId : organization.getCommanderIds()) {
         Commander commander = this.commanders.get(commanderId);
         if (commander != null) {
            commander.clearOriginIfMatches(settlementId);
         }
      }

      this.refreshSoldierCounts(organization);
      this.recalculateOrganizationCaches(organization, gameTime);
      if (markDirtyNow) {
         this.markDirty.run();
      }
   }

   public int removeFactionMilitary(ServerLevel level, FactionId factionId) {
      MilitaryOrganization organization = this.organizations.get(factionId);
      if (organization == null) {
         return 0;
      }

      long gameTime = level.getGameTime();
      int removedArmies = 0;

      for (ArmyId armyId : new ArrayList<>(organization.getArmyIds())) {
         if (this.disbandArmy(level, armyId)) {
            removedArmies++;
         }
      }

      for (UnitId unitId : new ArrayList<>(organization.getUnitIds())) {
         this.entityBindings.entrySet().removeIf(entry -> unitId.equals(entry.getValue()));
         this.units.remove(unitId);
      }

      for (CommanderId commanderId : new ArrayList<>(organization.getCommanderIds())) {
         this.commanders.remove(commanderId);
      }

      this.organizations.remove(factionId);
      this.markDirty.run();
      LOGGER.info("[Millenaire] Removed military organization for faction {} ({} armies disbanded)", factionId, removedArmies);
      return removedArmies;
   }

   public int purgeInvalid(FactionManager factionManager) {
      List<FactionId> orphanOrgs = new ArrayList<>();

      for (FactionId factionId : this.organizations.keySet()) {
         if (!factionManager.exists(factionId)) {
            orphanOrgs.add(factionId);
         }
      }

      for (FactionId factionId : orphanOrgs) {
         MilitaryOrganization organization = this.organizations.remove(factionId);
         if (organization != null) {
            for (ArmyId armyId : organization.getArmyIds()) {
               this.armies.remove(armyId);
            }

            for (UnitId unitId : organization.getUnitIds()) {
               this.entityBindings.entrySet().removeIf(entry -> unitId.equals(entry.getValue()));
               this.units.remove(unitId);
            }

            for (CommanderId commanderId : organization.getCommanderIds()) {
               this.commanders.remove(commanderId);
            }
         }
      }

      if (!orphanOrgs.isEmpty()) {
         this.markDirty.run();
         LOGGER.warn("[Millenaire] Purged {} orphan military organization(s)", orphanOrgs.size());
      }

      return orphanOrgs.size();
   }

   void loadOrganization(MilitaryOrganization organization) {
      this.organizations.put(organization.getFactionId(), organization);
   }

   void loadArmy(Army army) {
      this.armies.put(army.getId(), army);
   }

   void loadUnit(MilitaryUnit unit) {
      this.units.put(unit.getId(), unit);
   }

   void loadCommander(Commander commander) {
      this.commanders.put(commander.getId(), commander);
   }

   void loadEntityBinding(UUID entityUuid, UnitId unitId) {
      if (entityUuid != null && unitId != null) {
         this.entityBindings.put(entityUuid, unitId);
      }
   }

   /**
    * Repairs membership inconsistencies after load without inventing new forces.
    */
   void validateLoadedConsistency() {
      Map<UnitId, ArmyId> unitToArmy = new HashMap<>();
      Map<CommanderId, ArmyId> commanderToArmy = new HashMap<>();

      for (Army army : this.armies.values()) {
         if (army.getState() == ArmyState.DISBANDED) {
            continue;
         }

         List<UnitId> invalidUnits = new ArrayList<>();
         for (UnitId unitId : army.getUnitIds()) {
            MilitaryUnit unit = this.units.get(unitId);
            if (unit == null || !unit.getFactionId().equals(army.getFactionId())) {
               invalidUnits.add(unitId);
               continue;
            }

            ArmyId existing = unitToArmy.put(unitId, army.getId());
            if (existing != null && !existing.equals(army.getId())) {
               LOGGER.warn("[Millenaire] Unit {} assigned to multiple armies — keeping {}", unitId, existing);
               invalidUnits.add(unitId);
               unitToArmy.put(unitId, existing);
            } else if (unit.getArmyId() == null || !unit.getArmyId().equals(army.getId())) {
               unit.assignToArmy(army.getId(), unit.getLastUpdatedGameTime());
            }
         }

         for (UnitId invalid : invalidUnits) {
            army.removeUnit(invalid, army.getLastUpdatedGameTime());
         }

         if (army.getCommanderId() != null) {
            Commander commander = this.commanders.get(army.getCommanderId());
            if (commander == null || !commander.getFactionId().equals(army.getFactionId())) {
               army.setCommanderId(null, army.getLastUpdatedGameTime());
            } else {
               ArmyId existing = commanderToArmy.put(commander.getId(), army.getId());
               if (existing != null && !existing.equals(army.getId())) {
                  LOGGER.warn("[Millenaire] Commander {} assigned to multiple armies — keeping {}", commander.getId(), existing);
                  army.setCommanderId(null, army.getLastUpdatedGameTime());
               } else if (!army.getId().equals(commander.getAssignedArmyId())) {
                  commander.assignToArmy(army.getId(), commander.getLastUpdatedGameTime());
               }
            }
         }
      }

      for (MilitaryUnit unit : this.units.values()) {
         if (unit.getArmyId() != null) {
            Army army = this.armies.get(unit.getArmyId());
            if (army == null || army.getState() == ArmyState.DISBANDED || !army.containsUnit(unit.getId())) {
               unit.assignToReserve(unit.getLastUpdatedGameTime());
            }
         }
      }

      for (MilitaryOrganization organization : this.organizations.values()) {
         Set<UnitId> missingUnits = new HashSet<>();
         for (UnitId unitId : organization.getUnitIds()) {
            if (!this.units.containsKey(unitId)) {
               missingUnits.add(unitId);
            }
         }

         for (UnitId missing : missingUnits) {
            organization.removeUnit(missing);
         }

         for (UnitId unitId : organization.getUnitIds()) {
            MilitaryUnit unit = this.units.get(unitId);
            if (unit != null && unit.getStatus() == UnitStatus.RESERVE) {
               organization.addReserveUnit(unitId);
            }
         }

         Set<ArmyId> missingArmies = new HashSet<>();
         for (ArmyId armyId : organization.getArmyIds()) {
            Army army = this.armies.get(armyId);
            if (army == null || army.getState() == ArmyState.DISBANDED) {
               missingArmies.add(armyId);
            }
         }

         for (ArmyId missing : missingArmies) {
            organization.removeArmy(missing);
         }

         Set<CommanderId> missingCommanders = new HashSet<>();
         for (CommanderId commanderId : organization.getCommanderIds()) {
            if (!this.commanders.containsKey(commanderId)) {
               missingCommanders.add(commanderId);
            }
         }

         for (CommanderId missing : missingCommanders) {
            organization.removeCommander(missing);
         }

         this.refreshSoldierCounts(organization);
         this.recalculateOrganizationCaches(organization, organization.getLastReconciledGameTime());
      }

      this.entityBindings.entrySet().removeIf(entry -> !this.units.containsKey(entry.getValue()));
      this.markDirty.run();
   }

   Collection<Army> getAllArmies() {
      return Collections.unmodifiableCollection(this.armies.values());
   }

   Collection<MilitaryUnit> getAllUnits() {
      return Collections.unmodifiableCollection(this.units.values());
   }

   Collection<Commander> getAllCommanders() {
      return Collections.unmodifiableCollection(this.commanders.values());
   }

   long getLastUpdateGameTime() {
      return this.lastUpdateGameTime;
   }

   void setLastUpdateGameTime(long gameTime) {
      this.lastUpdateGameTime = gameTime;
   }

   private boolean transferUnitToReserve(ServerLevel level, UnitId unitId, boolean markDirtyNow) {
      MilitaryUnit unit = this.units.get(unitId);
      if (unit == null) {
         return false;
      }

      long gameTime = level.getGameTime();
      this.detachUnitFromCurrentAssignment(unit, gameTime);
      unit.assignToReserve(gameTime);
      MilitaryOrganization organization = this.getOrCreateOrganization(unit.getFactionId());
      organization.addReserveUnit(unitId);
      organization.recordHistory(
         new MilitaryHistoryEntry(gameTime, MilitaryEventType.UNIT_TRANSFERRED, unit.getFactionId(), null, unitId, null, null, unit.getSoldierCount(), "to_reserve")
      );
      this.recalculateOrganizationCaches(organization, gameTime);
      if (markDirtyNow) {
         this.markDirty.run();
      }

      return true;
   }

   private boolean unassignCommander(ServerLevel level, CommanderId commanderId, boolean markDirtyNow) {
      Commander commander = this.commanders.get(commanderId);
      if (commander == null) {
         return false;
      }

      long gameTime = level.getGameTime();
      ArmyId armyId = commander.getAssignedArmyId();
      if (armyId != null) {
         Army army = this.armies.get(armyId);
         if (army != null && commanderId.equals(army.getCommanderId())) {
            army.setCommanderId(null, gameTime);
            army.recordHistory(
               new MilitaryHistoryEntry(gameTime, MilitaryEventType.COMMANDER_REMOVED, army.getFactionId(), armyId, null, commanderId, null, 0, null)
            );
            this.refreshArmyCaches(army, gameTime);
         }
      }

      this.clearGarrisonCommanderReferences(commander.getFactionId(), commanderId, gameTime);
      commander.clearAssignment(gameTime);
      MilitaryOrganization organization = this.getOrCreateOrganization(commander.getFactionId());
      organization.recordHistory(
         new MilitaryHistoryEntry(gameTime, MilitaryEventType.COMMANDER_REMOVED, commander.getFactionId(), armyId, null, commanderId, null, 0, null)
      );
      if (markDirtyNow) {
         this.markDirty.run();
      }

      return true;
   }

   private void clearGarrisonCommanderReferences(FactionId factionId, CommanderId commanderId, long gameTime) {
      MilitaryOrganization organization = this.getOrganization(factionId);
      if (organization == null || commanderId == null) {
         return;
      }

      for (SettlementGarrison garrison : organization.getGarrisons().values()) {
         if (commanderId.equals(garrison.getCommanderId())) {
            garrison.setCommanderId(null, gameTime);
         }
      }
   }

   private void detachUnitFromCurrentAssignment(MilitaryUnit unit, long gameTime) {
      if (unit.getArmyId() != null) {
         Army army = this.armies.get(unit.getArmyId());
         if (army != null) {
            army.removeUnit(unit.getId(), gameTime);
            this.refreshArmyCaches(army, gameTime);
         }
      }

      if (unit.getGarrisonSettlementId() != null) {
         MilitaryOrganization organization = this.getOrganization(unit.getFactionId());
         if (organization != null) {
            SettlementGarrison garrison = organization.getGarrisons().get(unit.getGarrisonSettlementId());
            if (garrison != null) {
               garrison.removeUnit(unit.getId(), gameTime);
               this.refreshGarrisonCaches(garrison, gameTime);
            }
         }
      }

      MilitaryOrganization organization = this.getOrganization(unit.getFactionId());
      if (organization != null) {
         organization.removeReserveUnit(unit.getId());
      }
   }

   private void refreshArmyCaches(Army army, long gameTime) {
      boolean hasCommander = army.getCommanderId() != null && this.commanders.containsKey(army.getCommanderId());
      int strength = MilitaryStrengthCalculator.armyStrength(army, this::getUnit, hasCommander);
      int readiness = MilitaryStrengthCalculator.armyReadiness(army, this::getUnit);
      army.updateCaches(strength, readiness, gameTime);
      if (army.getState() != ArmyState.DISBANDED && army.getState() != ArmyState.DISABLED) {
         if (army.getUnitIds().isEmpty()) {
            army.setState(ArmyState.FORMING, gameTime);
         } else if (army.getState() == ArmyState.FORMING) {
            army.setState(ArmyState.READY, gameTime);
         }
      }
   }

   private void refreshGarrisonCaches(SettlementGarrison garrison, long gameTime) {
      long strength = 0L;
      int soldiers = 0;
      long readinessWeighted = 0L;

      for (UnitId unitId : garrison.getUnitIds()) {
         MilitaryUnit unit = this.units.get(unitId);
         if (unit != null) {
            strength += MilitaryStrengthCalculator.unitStrength(unit, UnitTypeRegistry.get(unit.getUnitTypeId()));
            soldiers += unit.getSoldierCount();
            readinessWeighted += (long)unit.getSoldierCount() * unit.getReadinessPercent();
         }
      }

      int readiness = soldiers <= 0 ? 0 : (int)(readinessWeighted / soldiers);
      garrison.updateCaches(strength > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)strength, readiness, gameTime);
   }

   private void refreshSoldierCounts(MilitaryOrganization organization) {
      int reserve = 0;
      int active = 0;

      for (UnitId unitId : organization.getUnitIds()) {
         MilitaryUnit unit = this.units.get(unitId);
         if (unit == null) {
            continue;
         }

         if (unit.getStatus() == UnitStatus.RESERVE) {
            reserve += unit.getSoldierCount();
         } else if (unit.getStatus() != UnitStatus.DISABLED) {
            active += unit.getSoldierCount();
         }
      }

      organization.setSoldierCounts(reserve, active);
   }

   private void recalculateOrganizationCaches(MilitaryOrganization organization, long gameTime) {
      int power = MilitaryStrengthCalculator.factionMilitaryPower(organization, this::getUnit);
      organization.setCachedMilitaryPower(power);
      int readinessSoldiers = 0;
      long readinessWeighted = 0L;

      for (UnitId unitId : organization.getUnitIds()) {
         MilitaryUnit unit = this.units.get(unitId);
         if (unit != null && unit.getSoldierCount() > 0) {
            readinessSoldiers += unit.getSoldierCount();
            readinessWeighted += (long)unit.getSoldierCount() * unit.getReadinessPercent();
         }
      }

      int readiness = readinessSoldiers <= 0 ? 0 : (int)(readinessWeighted / readinessSoldiers);
      int previous = organization.getOrganizationReadinessPercent();
      organization.setOrganizationReadinessPercent(readiness);
      if (previous != readiness) {
         organization.recordHistory(
            new MilitaryHistoryEntry(gameTime, MilitaryEventType.READINESS_CHANGED, organization.getFactionId(), null, null, null, null, readiness, null)
         );
      }
   }

   private boolean validateFaction(ServerLevel level, FactionId factionId) {
      if (factionId == null) {
         return false;
      }

      return FactionSavedData.get(level).getFactionManager().exists(factionId);
   }
}
