package org.millenaire.faction.military;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import org.millenaire.faction.FactionId;
import org.millenaire.village.VillageId;
import org.slf4j.Logger;

/**
 * World-scoped persistence for Phase 5 military organization state.
 */
public final class MilitarySavedData extends SavedData {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int CURRENT_VERSION = 1;
   public static final String DATA_NAME = "millenaire_faction_military";

   private final MilitaryManager militaryManager = new MilitaryManager(this::setDirty);

   private MilitarySavedData() {
   }

   public static Factory<MilitarySavedData> factory() {
      return new Factory<>(MilitarySavedData::new, (tag, registries) -> load(tag), null);
   }

   public static MilitarySavedData get(ServerLevel level) {
      return (MilitarySavedData)level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
   }

   public MilitaryManager getMilitaryManager() {
      return this.militaryManager;
   }

   public CompoundTag save(CompoundTag root, Provider registries) {
      root.putInt("version", CURRENT_VERSION);
      root.putLong("last_update", this.militaryManager.getLastUpdateGameTime());

      ListTag units = new ListTag();
      for (MilitaryUnit unit : this.militaryManager.getAllUnits()) {
         try {
            units.add(saveUnit(unit));
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize military unit {}", unit.getId(), e);
         }
      }
      root.put("units", units);

      ListTag commanders = new ListTag();
      for (Commander commander : this.militaryManager.getAllCommanders()) {
         try {
            commanders.add(saveCommander(commander));
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize commander {}", commander.getId(), e);
         }
      }
      root.put("commanders", commanders);

      ListTag armies = new ListTag();
      for (Army army : this.militaryManager.getAllArmies()) {
         try {
            armies.add(saveArmy(army));
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize army {}", army.getId(), e);
         }
      }
      root.put("armies", armies);

      ListTag organizations = new ListTag();
      for (MilitaryOrganization organization : this.militaryManager.getAllOrganizations()) {
         try {
            organizations.add(saveOrganization(organization));
         } catch (Exception e) {
            LOGGER.error("[Millenaire] Failed to serialize military organization {}", organization.getFactionId(), e);
         }
      }
      root.put("organizations", organizations);

      ListTag bindings = new ListTag();
      for (Map.Entry<UUID, UnitId> entry : this.militaryManager.getEntityBindings().entrySet()) {
         CompoundTag binding = new CompoundTag();
         binding.putUUID("entity", entry.getKey());
         binding.putUUID("unit", entry.getValue().uuid());
         bindings.add(binding);
      }
      root.put("entity_bindings", bindings);

      LOGGER.debug(
         "[Millenaire] Saved military data: {} org(s), {} army(ies), {} unit(s), {} commander(s)",
         organizations.size(),
         armies.size(),
         units.size(),
         commanders.size()
      );
      return root;
   }

   private static CompoundTag saveUnit(MilitaryUnit unit) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", unit.getId().uuid());
      tag.putString("faction", unit.getFactionId().asString());
      tag.putString("type", unit.getUnitTypeId().toString());
      tag.putInt("soldiers", unit.getSoldierCount());
      tag.putInt("max_strength", unit.getMaxStrength());
      tag.putInt("readiness", unit.getReadinessPercent());
      tag.putString("training", unit.getTrainingLevel().name());
      tag.putString("equipment", unit.getEquipmentLevel().name());
      tag.putString("status", unit.getStatus().name());
      if (unit.getArmyId() != null) {
         tag.putUUID("army", unit.getArmyId().uuid());
      }
      if (unit.getGarrisonSettlementId() != null) {
         tag.putUUID("garrison", unit.getGarrisonSettlementId().uuid());
      }
      if (unit.getOriginSettlementId() != null) {
         tag.putUUID("origin", unit.getOriginSettlementId().uuid());
      }
      if (unit.getCommanderId() != null) {
         tag.putUUID("commander", unit.getCommanderId().uuid());
      }
      tag.putLong("created", unit.getCreatedGameTime());
      tag.putLong("updated", unit.getLastUpdatedGameTime());
      return tag;
   }

   private static CompoundTag saveCommander(Commander commander) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", commander.getId().uuid());
      tag.putString("faction", commander.getFactionId().asString());
      tag.putString("name", commander.getName());
      tag.putString("rank", commander.getRank().name());
      if (commander.getAssignedArmyId() != null) {
         tag.putUUID("army", commander.getAssignedArmyId().uuid());
      }
      tag.putInt("experience", commander.getExperience());
      tag.putInt("leadership", commander.getLeadership());
      if (commander.getOriginSettlementId() != null) {
         tag.putUUID("origin", commander.getOriginSettlementId().uuid());
      }
      tag.putLong("created", commander.getCreatedGameTime());
      tag.putLong("updated", commander.getLastUpdatedGameTime());
      return tag;
   }

   private static CompoundTag saveArmy(Army army) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", army.getId().uuid());
      tag.putString("faction", army.getFactionId().asString());
      tag.putString("name", army.getName());
      tag.putString("state", army.getState().name());
      if (army.getCommanderId() != null) {
         tag.putUUID("commander", army.getCommanderId().uuid());
      }
      if (army.getOriginSettlementId() != null) {
         tag.putUUID("origin", army.getOriginSettlementId().uuid());
      }
      if (army.getStationedSettlementId() != null) {
         tag.putUUID("stationed", army.getStationedSettlementId().uuid());
      }
      ListTag unitIds = new ListTag();
      for (UnitId unitId : army.getUnitIds()) {
         CompoundTag unitTag = new CompoundTag();
         unitTag.putUUID("id", unitId.uuid());
         unitIds.add(unitTag);
      }
      tag.put("units", unitIds);
      tag.putInt("strength", army.getCachedStrength());
      tag.putInt("readiness", army.getCachedReadinessPercent());
      tag.putLong("created", army.getCreatedGameTime());
      tag.putLong("updated", army.getLastUpdatedGameTime());
      ListTag history = new ListTag();
      for (MilitaryHistoryEntry entry : army.getHistory()) {
         history.add(saveHistoryEntry(entry));
      }
      tag.put("history", history);
      return tag;
   }

   private static CompoundTag saveOrganization(MilitaryOrganization organization) {
      CompoundTag tag = new CompoundTag();
      tag.putString("faction", organization.getFactionId().asString());
      tag.putInt("available_manpower", organization.getAvailableManpower());
      tag.putInt("recruited_manpower", organization.getRecruitedManpower());
      tag.putInt("manpower_capacity", organization.getManpowerCapacity());
      tag.putInt("reserve_soldiers", organization.getReserveSoldierCount());
      tag.putInt("active_soldiers", organization.getActiveSoldierCount());
      tag.putInt("recorded_casualties", organization.getRecordedCasualties());
      tag.putString("mobilization", organization.getMobilizationState().name());
      tag.putString("alert", organization.getAlertLevel().name());
      tag.putInt("readiness", organization.getOrganizationReadinessPercent());
      tag.putInt("power", organization.getCachedMilitaryPower());
      tag.putInt("infrastructure", organization.getInfrastructureScore());
      tag.putLong("reconciled", organization.getLastReconciledGameTime());

      ListTag armyIds = new ListTag();
      for (ArmyId armyId : organization.getArmyIds()) {
         CompoundTag armyTag = new CompoundTag();
         armyTag.putUUID("id", armyId.uuid());
         armyIds.add(armyTag);
      }
      tag.put("armies", armyIds);

      ListTag unitIds = new ListTag();
      for (UnitId unitId : organization.getUnitIds()) {
         CompoundTag unitTag = new CompoundTag();
         unitTag.putUUID("id", unitId.uuid());
         unitIds.add(unitTag);
      }
      tag.put("units", unitIds);

      ListTag reserveIds = new ListTag();
      for (UnitId unitId : organization.getReserveUnitIds()) {
         CompoundTag unitTag = new CompoundTag();
         unitTag.putUUID("id", unitId.uuid());
         reserveIds.add(unitTag);
      }
      tag.put("reserves", reserveIds);

      ListTag commanderIds = new ListTag();
      for (CommanderId commanderId : organization.getCommanderIds()) {
         CompoundTag commanderTag = new CompoundTag();
         commanderTag.putUUID("id", commanderId.uuid());
         commanderIds.add(commanderTag);
      }
      tag.put("commanders", commanderIds);

      ListTag garrisons = new ListTag();
      for (SettlementGarrison garrison : organization.getGarrisons().values()) {
         garrisons.add(saveGarrison(garrison));
      }
      tag.put("garrisons", garrisons);

      ListTag history = new ListTag();
      for (MilitaryHistoryEntry entry : organization.getHistory()) {
         history.add(saveHistoryEntry(entry));
      }
      tag.put("history", history);
      return tag;
   }

   private static CompoundTag saveGarrison(SettlementGarrison garrison) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("settlement", garrison.getSettlementId().uuid());
      tag.putString("faction", garrison.getFactionId().asString());
      tag.putInt("capacity", garrison.getCapacity());
      if (garrison.getCommanderId() != null) {
         tag.putUUID("commander", garrison.getCommanderId().uuid());
      }
      tag.putInt("strength", garrison.getCachedStrength());
      tag.putInt("readiness", garrison.getCachedReadinessPercent());
      tag.putLong("updated", garrison.getLastUpdatedGameTime());
      ListTag unitIds = new ListTag();
      for (UnitId unitId : garrison.getUnitIds()) {
         CompoundTag unitTag = new CompoundTag();
         unitTag.putUUID("id", unitId.uuid());
         unitIds.add(unitTag);
      }
      tag.put("units", unitIds);
      return tag;
   }

   private static CompoundTag saveHistoryEntry(MilitaryHistoryEntry entry) {
      CompoundTag tag = new CompoundTag();
      tag.putLong("game_time", entry.getGameTime());
      tag.putString("event", entry.getEventType().name());
      tag.putString("faction", entry.getFactionId().asString());
      if (entry.getArmyId() != null) {
         tag.putUUID("army", entry.getArmyId().uuid());
      }
      if (entry.getUnitId() != null) {
         tag.putUUID("unit", entry.getUnitId().uuid());
      }
      if (entry.getCommanderId() != null) {
         tag.putUUID("commander", entry.getCommanderId().uuid());
      }
      if (entry.getSettlementId() != null) {
         tag.putUUID("settlement", entry.getSettlementId().uuid());
      }
      tag.putInt("quantity", entry.getQuantity());
      if (entry.getDetail() != null) {
         tag.putString("detail", entry.getDetail());
      }
      return tag;
   }

   private static MilitarySavedData load(CompoundTag root) {
      MilitarySavedData data = new MilitarySavedData();
      int version = root.contains("version") ? root.getInt("version") : 0;
      if (version > CURRENT_VERSION) {
         LOGGER.warn("[Millenaire] MilitarySavedData version {} newer than supported {} — best-effort load", version, CURRENT_VERSION);
      } else {
         LOGGER.info("[Millenaire] Loading MilitarySavedData version {}", version);
      }

      if (root.contains("last_update")) {
         data.militaryManager.setLastUpdateGameTime(root.getLong("last_update"));
      }

      Map<UnitId, MilitaryUnit> loadedUnits = new HashMap<>();
      if (root.contains("units")) {
         ListTag units = root.getList("units", 10);
         for (int i = 0; i < units.size(); i++) {
            try {
               MilitaryUnit unit = readUnit(units.getCompound(i));
               if (unit != null) {
                  if (loadedUnits.containsKey(unit.getId())) {
                     LOGGER.warn("[Millenaire] Duplicate military unit id {} — keeping first", unit.getId());
                     continue;
                  }
                  loadedUnits.put(unit.getId(), unit);
                  data.militaryManager.loadUnit(unit);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed military unit at index {}", i, e);
            }
         }
      }

      Map<CommanderId, Commander> loadedCommanders = new HashMap<>();
      if (root.contains("commanders")) {
         ListTag commanders = root.getList("commanders", 10);
         for (int i = 0; i < commanders.size(); i++) {
            try {
               Commander commander = readCommander(commanders.getCompound(i));
               if (commander != null) {
                  if (loadedCommanders.containsKey(commander.getId())) {
                     LOGGER.warn("[Millenaire] Duplicate commander id {} — keeping first", commander.getId());
                     continue;
                  }
                  loadedCommanders.put(commander.getId(), commander);
                  data.militaryManager.loadCommander(commander);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed commander at index {}", i, e);
            }
         }
      }

      Map<ArmyId, Army> loadedArmies = new HashMap<>();
      if (root.contains("armies")) {
         ListTag armies = root.getList("armies", 10);
         for (int i = 0; i < armies.size(); i++) {
            try {
               Army army = readArmy(armies.getCompound(i));
               if (army != null) {
                  if (loadedArmies.containsKey(army.getId())) {
                     LOGGER.warn("[Millenaire] Duplicate army id {} — keeping first", army.getId());
                     continue;
                  }
                  loadedArmies.put(army.getId(), army);
                  data.militaryManager.loadArmy(army);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed army at index {}", i, e);
            }
         }
      }

      if (root.contains("organizations")) {
         ListTag organizations = root.getList("organizations", 10);
         for (int i = 0; i < organizations.size(); i++) {
            try {
               MilitaryOrganization organization = readOrganization(organizations.getCompound(i));
               if (organization != null) {
                  data.militaryManager.loadOrganization(organization);
               }
            } catch (Exception e) {
               LOGGER.error("[Millenaire] Skipping malformed military organization at index {}", i, e);
            }
         }
      }

      if (root.contains("entity_bindings")) {
         ListTag bindings = root.getList("entity_bindings", 10);
         for (int i = 0; i < bindings.size(); i++) {
            CompoundTag binding = bindings.getCompound(i);
            if (!binding.hasUUID("entity") || !binding.hasUUID("unit")) {
               continue;
            }
            UnitId unitId = new UnitId(binding.getUUID("unit"));
            if (loadedUnits.containsKey(unitId)) {
               data.militaryManager.loadEntityBinding(binding.getUUID("entity"), unitId);
            }
         }
      }

      data.militaryManager.validateLoadedConsistency();
      return data;
   }

   @Nullable
   private static MilitaryUnit readUnit(CompoundTag tag) {
      if (!tag.hasUUID("id")) {
         return null;
      }
      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      ResourceLocation typeId = null;
      if (tag.contains("type")) {
         try {
            typeId = ResourceLocation.parse(tag.getString("type"));
         } catch (RuntimeException ignored) {
            typeId = null;
         }
      }
      if (factionId == null || typeId == null) {
         LOGGER.error("[Millenaire] Invalid military unit identity — skipped");
         return null;
      }

      MilitaryUnit unit = new MilitaryUnit(
         new UnitId(tag.getUUID("id")),
         factionId,
         typeId,
         tag.contains("soldiers") ? tag.getInt("soldiers") : 0,
         tag.contains("max_strength") ? tag.getInt("max_strength") : MilitaryConfig.DEFAULT_UNIT_MAX_STRENGTH,
         tag.contains("created") ? tag.getLong("created") : 0L,
         tag.hasUUID("origin") ? new VillageId(tag.getUUID("origin")) : null
      );
      unit.loadState(
         tag.contains("soldiers") ? tag.getInt("soldiers") : 0,
         tag.contains("max_strength") ? tag.getInt("max_strength") : MilitaryConfig.DEFAULT_UNIT_MAX_STRENGTH,
         tag.contains("readiness") ? tag.getInt("readiness") : MilitaryConfig.READINESS_PARTIAL,
         TrainingLevel.tryParse(tag.contains("training") ? tag.getString("training") : null),
         EquipmentLevel.tryParse(tag.contains("equipment") ? tag.getString("equipment") : null),
         UnitStatus.tryParse(tag.contains("status") ? tag.getString("status") : null),
         tag.hasUUID("army") ? new ArmyId(tag.getUUID("army")) : null,
         tag.hasUUID("garrison") ? new VillageId(tag.getUUID("garrison")) : null,
         tag.hasUUID("origin") ? new VillageId(tag.getUUID("origin")) : null,
         tag.hasUUID("commander") ? new CommanderId(tag.getUUID("commander")) : null,
         tag.contains("created") ? tag.getLong("created") : 0L,
         tag.contains("updated") ? tag.getLong("updated") : 0L
      );
      return unit;
   }

   @Nullable
   private static Commander readCommander(CompoundTag tag) {
      if (!tag.hasUUID("id")) {
         return null;
      }
      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      String name = tag.contains("name") ? tag.getString("name") : null;
      if (factionId == null || name == null || name.isBlank()) {
         LOGGER.error("[Millenaire] Invalid commander identity — skipped");
         return null;
      }

      Commander commander = new Commander(
         new CommanderId(tag.getUUID("id")),
         factionId,
         name,
         CommanderRank.tryParse(tag.contains("rank") ? tag.getString("rank") : null),
         tag.contains("created") ? tag.getLong("created") : 0L,
         tag.hasUUID("origin") ? new VillageId(tag.getUUID("origin")) : null
      );
      commander.loadState(
         name,
         CommanderRank.tryParse(tag.contains("rank") ? tag.getString("rank") : null),
         tag.hasUUID("army") ? new ArmyId(tag.getUUID("army")) : null,
         tag.contains("experience") ? tag.getInt("experience") : 0,
         tag.contains("leadership") ? tag.getInt("leadership") : 0,
         tag.hasUUID("origin") ? new VillageId(tag.getUUID("origin")) : null,
         tag.contains("updated") ? tag.getLong("updated") : 0L
      );
      return commander;
   }

   @Nullable
   private static Army readArmy(CompoundTag tag) {
      if (!tag.hasUUID("id")) {
         return null;
      }
      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      String name = tag.contains("name") ? tag.getString("name") : null;
      if (factionId == null || name == null || name.isBlank()) {
         LOGGER.error("[Millenaire] Invalid army identity — skipped");
         return null;
      }

      Army army = new Army(
         new ArmyId(tag.getUUID("id")),
         factionId,
         name,
         tag.contains("created") ? tag.getLong("created") : 0L,
         tag.hasUUID("origin") ? new VillageId(tag.getUUID("origin")) : null
      );
      Set<UnitId> unitIds = new LinkedHashSet<>();
      if (tag.contains("units")) {
         ListTag units = tag.getList("units", 10);
         for (int i = 0; i < units.size(); i++) {
            CompoundTag unitTag = units.getCompound(i);
            if (unitTag.hasUUID("id")) {
               unitIds.add(new UnitId(unitTag.getUUID("id")));
            }
         }
      }
      List<MilitaryHistoryEntry> history = readHistoryList(tag);
      army.loadState(
         name,
         ArmyState.tryParse(tag.contains("state") ? tag.getString("state") : null),
         tag.hasUUID("commander") ? new CommanderId(tag.getUUID("commander")) : null,
         tag.hasUUID("origin") ? new VillageId(tag.getUUID("origin")) : null,
         tag.hasUUID("stationed") ? new VillageId(tag.getUUID("stationed")) : null,
         unitIds,
         tag.contains("strength") ? tag.getInt("strength") : 0,
         tag.contains("readiness") ? tag.getInt("readiness") : 0,
         tag.contains("updated") ? tag.getLong("updated") : 0L,
         history
      );
      return army;
   }

   @Nullable
   private static MilitaryOrganization readOrganization(CompoundTag tag) {
      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      if (factionId == null) {
         LOGGER.error("[Millenaire] Invalid military organization faction — skipped");
         return null;
      }

      MilitaryOrganization organization = new MilitaryOrganization(factionId);
      Set<ArmyId> armyIds = readIdSet(tag, "armies", ArmyId::new);
      Set<UnitId> unitIds = readIdSet(tag, "units", UnitId::new);
      Set<UnitId> reserveIds = readIdSet(tag, "reserves", UnitId::new);
      Set<CommanderId> commanderIds = readIdSet(tag, "commanders", CommanderId::new);
      organization.loadState(
         tag.contains("available_manpower") ? tag.getInt("available_manpower") : 0,
         tag.contains("recruited_manpower") ? tag.getInt("recruited_manpower") : 0,
         tag.contains("manpower_capacity") ? tag.getInt("manpower_capacity") : 0,
         tag.contains("reserve_soldiers") ? tag.getInt("reserve_soldiers") : 0,
         tag.contains("active_soldiers") ? tag.getInt("active_soldiers") : 0,
         MobilizationState.tryParse(tag.contains("mobilization") ? tag.getString("mobilization") : null),
         MilitaryAlertLevel.tryParse(tag.contains("alert") ? tag.getString("alert") : null),
         tag.contains("readiness") ? tag.getInt("readiness") : 0,
         tag.contains("power") ? tag.getInt("power") : 0,
         tag.contains("infrastructure") ? tag.getInt("infrastructure") : 0,
         tag.contains("reconciled") ? tag.getLong("reconciled") : 0L,
         armyIds,
         unitIds,
         reserveIds,
         commanderIds,
         readHistoryList(tag),
         tag.contains("recorded_casualties") ? tag.getInt("recorded_casualties") : 0
      );

      if (tag.contains("garrisons")) {
         ListTag garrisons = tag.getList("garrisons", 10);
         for (int i = 0; i < garrisons.size(); i++) {
            SettlementGarrison garrison = readGarrison(garrisons.getCompound(i), factionId);
            if (garrison != null) {
               organization.putGarrison(garrison);
            }
         }
      }

      return organization;
   }

   @Nullable
   private static SettlementGarrison readGarrison(CompoundTag tag, FactionId fallbackFaction) {
      if (!tag.hasUUID("settlement")) {
         return null;
      }
      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      if (factionId == null) {
         factionId = fallbackFaction;
      }
      SettlementGarrison garrison = new SettlementGarrison(
         new VillageId(tag.getUUID("settlement")),
         factionId,
         tag.contains("capacity") ? tag.getInt("capacity") : MilitaryConfig.BASE_GARRISON_CAPACITY,
         tag.contains("updated") ? tag.getLong("updated") : 0L
      );
      Set<UnitId> unitIds = readIdSet(tag, "units", UnitId::new);
      garrison.loadState(
         tag.contains("capacity") ? tag.getInt("capacity") : MilitaryConfig.BASE_GARRISON_CAPACITY,
         unitIds,
         tag.hasUUID("commander") ? new CommanderId(tag.getUUID("commander")) : null,
         tag.contains("strength") ? tag.getInt("strength") : 0,
         tag.contains("readiness") ? tag.getInt("readiness") : 0,
         tag.contains("updated") ? tag.getLong("updated") : 0L
      );
      return garrison;
   }

   private static List<MilitaryHistoryEntry> readHistoryList(CompoundTag tag) {
      List<MilitaryHistoryEntry> history = new ArrayList<>();
      if (!tag.contains("history")) {
         return history;
      }
      ListTag list = tag.getList("history", 10);
      for (int i = 0; i < list.size(); i++) {
         MilitaryHistoryEntry entry = readHistoryEntry(list.getCompound(i));
         if (entry != null) {
            history.add(entry);
         }
      }
      return history;
   }

   @Nullable
   private static MilitaryHistoryEntry readHistoryEntry(CompoundTag tag) {
      MilitaryEventType eventType = MilitaryEventType.tryParse(tag.contains("event") ? tag.getString("event") : null);
      FactionId factionId = FactionId.tryParse(tag.contains("faction") ? tag.getString("faction") : null);
      if (eventType == null || factionId == null) {
         return null;
      }
      return new MilitaryHistoryEntry(
         tag.contains("game_time") ? tag.getLong("game_time") : 0L,
         eventType,
         factionId,
         tag.hasUUID("army") ? new ArmyId(tag.getUUID("army")) : null,
         tag.hasUUID("unit") ? new UnitId(tag.getUUID("unit")) : null,
         tag.hasUUID("commander") ? new CommanderId(tag.getUUID("commander")) : null,
         tag.hasUUID("settlement") ? new VillageId(tag.getUUID("settlement")) : null,
         tag.contains("quantity") ? tag.getInt("quantity") : 0,
         tag.contains("detail") ? tag.getString("detail") : null
      );
   }

   private static <T> Set<T> readIdSet(CompoundTag tag, String key, java.util.function.Function<UUID, T> factory) {
      Set<T> result = new LinkedHashSet<>();
      if (!tag.contains(key)) {
         return result;
      }
      ListTag list = tag.getList(key, 10);
      for (int i = 0; i < list.size(); i++) {
         CompoundTag entry = list.getCompound(i);
         if (entry.hasUUID("id")) {
            result.add(factory.apply(entry.getUUID("id")));
         }
      }
      return result;
   }
}
