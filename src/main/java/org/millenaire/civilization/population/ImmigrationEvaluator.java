package org.millenaire.civilization.population;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.civilization.CivilizationConfig;
import org.millenaire.civilization.CivilizationSavedData;
import org.millenaire.civilization.interaction.DiasporaService;
import org.millenaire.civilization.settlement.PlayerSettlementFoundingService;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.CulturalTemperament;
import org.millenaire.culture.Culture;
import org.millenaire.culture.Gender;
import org.millenaire.culture.ModCultures;
import org.millenaire.faction.Faction;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.faction.FactionType;
import org.millenaire.faction.ImmigrationPolicy;
import org.millenaire.faction.diplomacy.DiplomacySavedData;
import org.millenaire.faction.war.WarSavedData;
import org.millenaire.village.Village;
import org.millenaire.village.VillageGrowthManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

/**
 * Pulls civilians into sovereign PLAYER settlements based on housing and work demand.
 * Primary: cross-faction migration from same-culture overcrowded/surplus villages.
 * Secondary: capped traveler spawns when demand remains and sources are scarce.
 */
public final class ImmigrationEvaluator {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final long SUCCESS_COOLDOWN = 24000L * 2L;
   private static final long TRAVELER_DAY_KEY_TTL = 24000L;

   private ImmigrationEvaluator() {
   }

   public static void evaluate(ServerLevel level, CivilizationSavedData state) {
      if (!Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationImmigrationEnabled.get())) {
         return;
      }
      int maxOps = Math.min(
         CivilizationConfig.MAX_MIGRATIONS_PER_CYCLE,
         MillenaireServerConfig.SERVER.civilizationImmigrationBudget.getAsInt()
      );
      if (maxOps <= 0) {
         return;
      }

      List<Village> destinations = new ArrayList<>();
      for (Village village : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (PlayerSettlementFoundingService.isSovereignPlayerSettlement(level, village)) {
            destinations.add(village);
         }
      }
      if (destinations.isEmpty()) {
         return;
      }
      destinations.sort(Comparator.comparing(v -> v.getId().uuid()));
      int n = destinations.size();
      int start = Math.floorMod(state.immigrationCursor(), n);
      int ops = 0;
      int scanned = 0;
      int maxScans = Math.max(maxOps * 8, 8);

      for (int i = 0; i < n && ops < maxOps && scanned < maxScans; i++) {
         scanned++;
         Village destination = destinations.get((start + i) % n);
         Faction destFaction = FactionSavedData.get(level).getFactionManager().getFaction(destination.getFactionId());
         if (destFaction == null || destFaction.getType() != FactionType.PLAYER) {
            continue;
         }
         ImmigrationPolicy policy = destFaction.getImmigrationPolicy();
         if (policy == ImmigrationPolicy.CLOSED) {
            continue;
         }

         AttractionDemand demand = PopulationAggregator.computeAttractionDemand(destination, level.getGameTime());
         if (!demand.hasDemand() || demand.housingDemand() <= 0) {
            continue;
         }

         boolean immigrated = tryPullImmigrant(level, state, destination, destFaction, policy, demand, false);
         if (!immigrated
            && policy == ImmigrationPolicy.OPEN
            && DiasporaService.hasGuestQuarter(destination)) {
            immigrated = tryPullImmigrant(level, state, destination, destFaction, policy, demand, true);
         }
         if (immigrated) {
            ops++;
            // High-attraction OPEN settlements get a second pull attempt within the same stage budget.
            if (policy == ImmigrationPolicy.OPEN
               && demand.attractionScore() >= 6
               && ops < maxOps
               && tryPullImmigrant(level, state, destination, destFaction, policy, demand, false)) {
               ops++;
            }
            continue;
         }

         int travelerScoreNeed = policy == ImmigrationPolicy.OPEN && demand.housingDemand() >= 2 ? 2 : 4;
         if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationTravelerImmigrationEnabled.get())
            && demand.attractionScore() >= travelerScoreNeed
            && trySpawnTraveler(level, state, destination, demand)) {
            ops++;
         }
      }
      state.setImmigrationCursor(start + Math.max(1, scanned));
   }

   private static boolean tryPullImmigrant(
      ServerLevel level,
      CivilizationSavedData state,
      Village destination,
      Faction destFaction,
      ImmigrationPolicy policy,
      AttractionDemand demand,
      boolean allowDiaspora
   ) {
      List<Village> sources = new ArrayList<>();
      for (Village other : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (other.getId().equals(destination.getId())) {
            continue;
         }
         if (PlayerSettlementFoundingService.isSovereignPlayerSettlement(level, other)) {
            continue;
         }
         boolean sameCulture = destination.getCultureId() != null && destination.getCultureId().equals(other.getCultureId());
         if (!sameCulture) {
            if (!allowDiaspora
               || other.getCultureId() == null
               || !DiasporaService.allowsCrossCulture(level, destination, destFaction, other.getCultureId())) {
               continue;
            }
         }
         Faction otherFaction = other.getFactionId() != null
            ? FactionSavedData.get(level).getFactionManager().getFaction(other.getFactionId())
            : null;
         if (otherFaction != null && destFaction.getId().equals(otherFaction.getId())) {
            continue;
         }
         if (otherFaction != null && WarSavedData.get(level).getWarManager().areAtWar(destFaction.getId(), otherFaction.getId())) {
            continue;
         }
         if (otherFaction != null
            && !DiplomacySavedData.get(level).getDiplomaticManager().canTrade(destFaction.getId(), otherFaction.getId())) {
            continue;
         }
         PopulationSnapshot pop = PopulationAggregator.aggregateVillage(other, level.getGameTime());
         boolean overcrowded = pop.residents() > pop.housingCapacity() && pop.housingCapacity() > 0;
         boolean surplus = pop.adults() >= 6 && pop.residents() >= pop.housingCapacity();
         if (policy == ImmigrationPolicy.RESTRICTED && !overcrowded) {
            continue;
         }
         if (!overcrowded && !surplus) {
            continue;
         }
         // Hospitality of the source culture weights attraction chance.
         CulturalTemperament sourceTemperament = temperamentOf(other);
         double hospitality = sourceTemperament.hospitalityWeight();
         if (hospitality < 0.85 && Math.floorMod(other.getId().uuid().hashCode() + (int) level.getGameTime(), 100) > (int) (hospitality * 100)) {
            continue;
         }
         sources.add(other);
      }
      if (sources.isEmpty()) {
         return false;
      }
      sources.sort(Comparator
         .comparingLong((Village v) -> distSq(destination, v))
         .thenComparing(v -> v.getId().uuid()));

      for (Village origin : sources) {
         UUID migrant = selectMigrant(level, origin, state);
         if (migrant == null) {
            continue;
         }
         MigrationResult result = MigrationTransactionService.migrateForImmigration(
            level, origin.getId(), destination.getId(), migrant
         );
         if (result.completed()) {
            state.tryMarkPublished("immig-cd:" + migrant, level.getGameTime(), SUCCESS_COOLDOWN);
            if (!sameCultureIds(destination, origin) && origin.getCultureId() != null) {
               DiasporaService.recordAssimilationProgress(level, destFaction.getId(), origin.getCultureId());
            }
            if (state.updateStateMarkerAllowFirst("immig-first:" + destination.getId().uuid(), "1")) {
               destination.recordChronicleEvent(
                  level,
                  org.millenaire.village.VillageEventType.VILLAGER_SPAWNED,
                  "First immigrant",
                  destination.getVillageName()
               );
            }
            if (Boolean.TRUE.equals(MillenaireServerConfig.SERVER.civilizationMetricsLogging.get())) {
               LOGGER.debug(
                  "[Millenaire] Immigration {} -> {} (demand h={} w={})",
                  origin.getId().uuid(),
                  destination.getId().uuid(),
                  demand.housingDemand(),
                  demand.workDemand()
               );
            }
            return true;
         }
      }
      return false;
   }

   private static boolean sameCultureIds(Village a, Village b) {
      return a.getCultureId() != null && a.getCultureId().equals(b.getCultureId());
   }

   private static CulturalTemperament temperamentOf(Village village) {
      if (village.getCultureId() == null) {
         return CulturalTemperament.DEFAULT;
      }
      Culture culture = ModCultures.getCulture(village.getCultureId());
      return culture == null ? CulturalTemperament.DEFAULT : culture.temperament();
   }

   private static boolean trySpawnTraveler(
      ServerLevel level, CivilizationSavedData state, Village destination, AttractionDemand demand
   ) {
      long day = level.getGameTime() / 24000L;
      String dayKey = "traveler-day:" + destination.getId().uuid() + ":" + day;
      int cap = MillenaireServerConfig.SERVER.civilizationTravelerImmigrationDailyCap.getAsInt();
      int used = 0;
      String usedRaw = state.getStateMarker(dayKey);
      if (usedRaw != null) {
         try {
            used = Integer.parseInt(usedRaw);
         } catch (NumberFormatException ignored) {
            used = 0;
         }
      }
      if (used >= cap) {
         return false;
      }

      BuildingInstance home = findOpenHome(destination);
      if (home == null || home.getPlanSetId() == null) {
         return false;
      }
      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(home.getPlanSetId());
      if (planSet == null) {
         return false;
      }
      String typeName = pickUnfilledResidentType(destination, home, planSet);
      if (typeName == null) {
         return false;
      }
      var villager = VillageGrowthManager.spawnImmigrantOccupant(level, destination, typeName, home);
      if (villager == null) {
         return false;
      }
      state.updateStateMarker(dayKey, String.valueOf(used + 1));
      state.tryMarkPublished(dayKey + ":mark", level.getGameTime(), TRAVELER_DAY_KEY_TTL);
      if (state.updateStateMarkerAllowFirst("immig-first:" + destination.getId().uuid(), "1")) {
         destination.recordChronicleEvent(
            level,
            org.millenaire.village.VillageEventType.VILLAGER_SPAWNED,
            "First immigrant",
            destination.getVillageName()
         );
      }
      LOGGER.info(
         "[Millenaire] Traveler immigrant {} arrived at {} (demand score={})",
         typeName,
         destination.getVillageName(),
         demand.attractionScore()
      );
      return true;
   }

   private static UUID selectMigrant(ServerLevel level, Village origin, CivilizationSavedData state) {
      List<UUID> eligible = new ArrayList<>();
      for (VillagerRecord record : origin.getVillagerRecords().values()) {
         if (!MigrationTransactionService.isEligibleForPermanentMigration(level, origin, record)) {
            continue;
         }
         UUID id = record.getUuid();
         if (org.millenaire.civilization.settlement.ColonizationService.isReservedFounder(level, id)) {
            continue;
         }
         if (state.isPublished("immig-cd:" + id, level.getGameTime(), SUCCESS_COOLDOWN)) {
            continue;
         }
         if (state.isPublished("mig-cd:" + id, level.getGameTime(), SUCCESS_COOLDOWN)) {
            continue;
         }
         eligible.add(id);
      }
      if (eligible.isEmpty()) {
         return null;
      }
      eligible.sort(Comparator.naturalOrder());
      return eligible.getFirst();
   }

   private static BuildingInstance findOpenHome(Village village) {
      for (BuildingInstance building : village.getBuildings()) {
         if (!building.isOperational() || building.getPlanSetId() == null) {
            continue;
         }
         if (building.isPlayerDesignated()
            && building.getPurpose() != null
            && building.getPurpose().providesImmigrantHousing()) {
            if (pickDesignatedHouseImmigrantType(village, building) != null) {
               return building;
            }
            continue;
         }
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(building.getPlanSetId());
         if (planSet == null || !planSet.isResidential()) {
            continue;
         }
         if (pickUnfilledResidentType(village, building, planSet) != null) {
            return building;
         }
      }
      return null;
   }

   private static String pickUnfilledResidentType(Village village, BuildingInstance building, BuildingPlanSet planSet) {
      if (building.isPlayerDesignated()
         && building.getPurpose() != null
         && building.getPurpose().providesImmigrantHousing()) {
         return pickDesignatedHouseImmigrantType(village, building);
      }
      int males = 0;
      int females = 0;
      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (record == null || record.isKilled() || record.getHomeBuilding() == null) {
            continue;
         }
         if (!record.getHomeBuilding().equals(building.getId())) {
            continue;
         }
         var type = record.getVillagerTypeId() != null ? ModCultures.getVillagerType(record.getVillagerTypeId()) : null;
         if (type != null && type.gender() == Gender.FEMALE) {
            females++;
         } else {
            males++;
         }
      }
      List<String> maleSlots = planSet.maleResidentsAt(building.getVariant(), building.getLevel());
      List<String> femaleSlots = planSet.femaleResidentsAt(building.getVariant(), building.getLevel());
      if (males < maleSlots.size()) {
         return maleSlots.get(males);
      }
      if (females < femaleSlots.size()) {
         return femaleSlots.get(females);
      }
      return null;
   }

   @Nullable
   private static String pickDesignatedHouseImmigrantType(Village village, BuildingInstance building) {
      int beds = building.hasBedManager() ? building.getBedManager().getBedCount() : 0;
      if (beds <= 0) {
         return null;
      }

      int residents = 0;
      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (record == null || record.isKilled() || record.getHomeBuilding() == null) {
            continue;
         }
         if (record.getHomeBuilding().equals(building.getId())) {
            residents++;
         }
      }

      if (residents >= beds) {
         return null;
      }

      net.minecraft.resources.ResourceLocation cultureId = village.getCultureId();
      if (cultureId == null) {
         return null;
      }

      String culturePath = cultureId.getPath();
      boolean preferFemale = residents % 2 == 1;
      String female = findCultureAdultKey(culturePath, Gender.FEMALE);
      String male = findCultureAdultKey(culturePath, Gender.MALE);
      if (preferFemale && female != null) {
         return female;
      }
      if (male != null) {
         return male;
      }
      return female;
   }

   @Nullable
   private static String findCultureAdultKey(String culturePath, Gender gender) {
      List<String> keys = new ArrayList<>();
      for (var entry : ModCultures.getAllVillagerTypes().entrySet()) {
         var type = entry.getValue();
         if (type.isChild() || type.gender() != gender) {
            continue;
         }
         String path = type.id().getPath();
         if (!path.startsWith(culturePath + "/")) {
            continue;
         }
         String key = path.substring(culturePath.length() + 1);
         String lower = key.toLowerCase();
         if (lower.contains("soldier")
            || lower.contains("guard")
            || lower.contains("merchant")
            || lower.contains("visitor")
            || lower.contains("chief")
            || lower.contains("raider")) {
            continue;
         }
         keys.add(key);
      }
      if (keys.isEmpty()) {
         return null;
      }
      // Prefer ordinary civilians; keep selection stable across runs.
      keys.sort(Comparator
         .comparingInt((String k) -> civilianPreference(k))
         .thenComparing(k -> k));
      return keys.getFirst();
   }

   private static int civilianPreference(String key) {
      String lower = key.toLowerCase();
      if (lower.contains("farmer") || lower.contains("wife") || lower.contains("husband") || lower.equals("man") || lower.equals("woman")) {
         return 0;
      }
      if (lower.contains("lumber") || lower.contains("miner") || lower.contains("shepherd") || lower.contains("artisan")) {
         return 1;
      }
      return 2;
   }

   private static long distSq(Village a, Village b) {
      long dx = (long) a.getCenter().getX() - b.getCenter().getX();
      long dz = (long) a.getCenter().getZ() - b.getCenter().getZ();
      return dx * dx + dz * dz;
   }
}
