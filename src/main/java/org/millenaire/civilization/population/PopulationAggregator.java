package org.millenaire.civilization.population;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.civilization.CivilizationConfig;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;

/**
 * Derives population summaries from authoritative VillagerRecord maps and residential plan capacity.
 * Never invents phantom residents. Housing capacity can be lower than resident count (overcrowding visible).
 */
public final class PopulationAggregator {
   private PopulationAggregator() {
   }

   public static void refreshDirtyFactions(ServerLevel level, CivilizationSavedData state) {
      List<Faction> factions = new ArrayList<>(FactionSavedData.get(level).getFactionManager().getAllFactions());
      if (factions.isEmpty()) {
         return;
      }
      factions.sort(Comparator.comparing(f -> f.getId().asString()));
      int budget = Math.min(CivilizationConfig.MAX_FACTIONS_PER_STAGE, MillenaireServerConfig.SERVER.civilizationFactionBudget.getAsInt());
      int n = factions.size();
      int cursor = Math.floorMod(state.populationCursor(), n);
      int scanned = 0;
      int mutated = 0;
      VillageSavedData villages = VillageSavedData.get(level);
      for (int i = 0; i < n && scanned < budget * 4 && mutated < budget; i++) {
         scanned++;
         Faction faction = factions.get((cursor + i) % n);
         PopulationSnapshot snap = aggregateFaction(villages, faction, level.getGameTime());
         String key = faction.getId().asString();
         net.minecraft.nbt.CompoundTag next = snap.save();
         net.minecraft.nbt.CompoundTag prev = state.getPopulationSnapshot(key);
         if (prev != null && populationTagEqualIgnoringTime(prev, next)) {
            continue;
         }
         state.putPopulationSnapshot(key, next);
         mutated++;
      }
      state.setPopulationCursor(cursor + Math.max(1, Math.max(mutated, scanned > 0 ? 1 : 0)));
   }

   private static boolean populationTagEqualIgnoringTime(net.minecraft.nbt.CompoundTag a, net.minecraft.nbt.CompoundTag b) {
      if (a == b) {
         return true;
      }
      if (a == null || b == null) {
         return false;
      }
      for (String key : a.getAllKeys()) {
         if ("at".equals(key)) {
            continue;
         }
         if (!a.get(key).equals(b.get(key))) {
            return false;
         }
      }
      for (String key : b.getAllKeys()) {
         if ("at".equals(key)) {
            continue;
         }
         if (!a.contains(key)) {
            return false;
         }
      }
      return true;
   }

   public static PopulationSnapshot aggregateFaction(VillageSavedData villages, Faction faction, long gameTime) {
      int residents = 0;
      int adults = 0;
      int children = 0;
      int workers = 0;
      int housing = 0;
      int military = 0;
      for (VillageId settlementId : faction.getSettlementIds()) {
         Village village = villages.getVillageManager().getVillage(settlementId);
         if (village == null) {
            continue;
         }
         PopulationSnapshot local = aggregateVillage(village, gameTime);
         residents += local.residents();
         adults += local.adults();
         children += local.children();
         workers += local.workers();
         housing += local.housingCapacity();
         military += local.militaryEligible();
      }
      return new PopulationSnapshot(residents, adults, children, housing, workers, military, gameTime);
   }

   public static PopulationSnapshot aggregateVillage(Village village, long gameTime) {
      int residents = 0;
      int adults = 0;
      int children = 0;
      int workers = 0;
      int military = 0;
      int visitors = 0;
      Map<UUID, VillagerRecord> records = village.getVillagerRecords();
      for (VillagerRecord record : records.values()) {
         if (record == null || record.isKilled()) {
            continue;
         }
         if (record.getRaiderSpawn() > 0L) {
            continue;
         }
         VillagerType type = record.getVillagerTypeId() != null ? ModCultures.getVillagerType(record.getVillagerTypeId()) : null;
         String path = type != null && type.id() != null ? type.id().getPath() : "";
         boolean visitor = path.contains("visitor") || path.contains("merchant_foreign");
         if (visitor) {
            visitors++;
            continue;
         }
         residents++;
         boolean child = type != null && type.isChild();
         if (child) {
            children++;
         } else {
            adults++;
            workers++;
            if (isMilitaryEligible(type, record)) {
               military++;
            }
         }
      }
      int housing = computeHousingCapacity(village);
      // Overcrowding is visible: do NOT mask with Math.max(residents, capacity).
      // Visitors are excluded from residents/housing pressure.
      return new PopulationSnapshot(residents, adults, children, housing, workers, military, gameTime);
   }

   /**
    * Housing from residential BuildingPlanSet occupant slots on operational buildings only.
    * Work buildings without resident declarations do not count.
    */
   public static int computeHousingCapacity(Village village) {
      int capacity = 0;
      for (BuildingInstance building : village.getBuildings()) {
         if (!building.isOperational()) {
            continue;
         }
         if (building.isPlayerDesignated()
            && building.getPurpose() != null
            && building.getPurpose().providesImmigrantHousing()) {
            capacity += building.hasBedManager() ? building.getBedManager().getBedCount() : 0;
            continue;
         }
         BuildingPlanSet planSet = building.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(building.getPlanSetId()) : null;
         if (planSet == null || !planSet.isResidential()) {
            continue;
         }
         int slots = planSet.maleResidentsAt(building.getVariant(), building.getLevel()).size()
            + planSet.femaleResidentsAt(building.getVariant(), building.getLevel()).size();
         capacity += Math.max(0, slots);
      }
      return capacity;
   }

   /**
    * Housing demand from spare residential slots; work demand from unfilled slots on production buildings.
    */
   public static AttractionDemand computeAttractionDemand(Village village, long gameTime) {
      PopulationSnapshot pop = aggregateVillage(village, gameTime);
      int housingDemand = Math.max(0, pop.housingCapacity() - pop.residents());
      int workSlots = 0;
      int filledOnWork = 0;
      for (BuildingInstance building : village.getBuildings()) {
         if (!building.isOperational()) {
            continue;
         }
         // Designated workplaces pull civilian immigrants into HOUSE beds (farm bridge / pens).
         if (building.isPlayerDesignated() && building.getPurpose() != null && building.getPurpose().isWorkAttraction()) {
            workSlots += building.getPurpose() == org.millenaire.building.BuildingPurpose.FARMLAND ? 2 : 1;
            continue;
         }
         if (building.getPlanSetId() == null) {
            continue;
         }
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(building.getPlanSetId());
         if (planSet == null || !planSet.isResidential()) {
            continue;
         }
         if (!looksLikeWorkBuilding(planSet)) {
            continue;
         }
         int slots = planSet.maleResidentsAt(building.getVariant(), building.getLevel()).size()
            + planSet.femaleResidentsAt(building.getVariant(), building.getLevel()).size();
         workSlots += slots;
         filledOnWork += countResidentsInBuilding(village, building);
      }
      int workDemand = Math.max(0, workSlots - filledOnWork);
      // Housing-only vacancies still attract; work vacancies add pull even when counted in housing.
      return new AttractionDemand(housingDemand, workDemand);
   }

   private static boolean looksLikeWorkBuilding(BuildingPlanSet planSet) {
      String path = planSet.id() != null ? planSet.id().getPath().toLowerCase() : "";
      if (path.startsWith("designated/")) {
         return path.contains("farmland")
            || path.contains("sheep_pen")
            || path.contains("cattle_pen")
            || path.contains("horse_stable")
            || path.contains("chicken_coop")
            || path.contains("pig_pen")
            || path.contains("grove")
            || path.contains("warehouse")
            || path.contains("armory");
      }
      if (path.contains("house") || path.contains("home") || path.contains("hut_empty") || path.contains("apartment")) {
         return false;
      }
      return path.contains("farm")
         || path.contains("bakery")
         || path.contains("forge")
         || path.contains("smith")
         || path.contains("quarry")
         || path.contains("lumber")
         || path.contains("mine")
         || path.contains("workshop")
         || path.contains("kiln")
         || path.contains("mill")
         || path.contains("grove")
         || path.contains("cattle")
         || path.contains("sheep")
         || path.contains("chicken")
         || path.contains("inn")
         || path.contains("market")
         || path.contains("shop")
         || path.contains("guard")
         || path.contains("armoury")
         || path.contains("barrack")
         || !planSet.startingGoods().isEmpty();
   }

   private static int countResidentsInBuilding(Village village, BuildingInstance building) {
      int count = 0;
      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (record == null || record.isKilled() || record.getHomeBuilding() == null) {
            continue;
         }
         if (record.getHomeBuilding().equals(building.getId())) {
            count++;
         }
      }
      return count;
   }

   private static boolean isMilitaryEligible(@javax.annotation.Nullable VillagerType type, VillagerRecord record) {
      if (type == null) {
         return false;
      }
      if (type.isChild()) {
         return false;
      }
      // Prefer existing military strength / role signals over gender heuristics.
      if (record.getMilitaryStrength() > 0) {
         return true;
      }
      String path = type.id() != null ? type.id().getPath() : "";
      return path.contains("soldier")
         || path.contains("guard")
         || path.contains("warrior")
         || path.contains("knight")
         || path.contains("archer")
         || path.contains("lancer");
   }
}
