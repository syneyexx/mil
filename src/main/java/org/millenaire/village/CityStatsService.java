package org.millenaire.village;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.perf.StaggeredVillageJob;
import org.millenaire.perf.TickPerfCounters;

/**
 * Dirty/staggered CityStats cache for controlled villages (Phase 0/1).
 */
public final class CityStatsService {
   private CityStatsService() {
   }

   public static void tick(ServerLevel level, Village village) {
      if (!village.isPlayerControlled() || !village.isActive()) {
         return;
      }
      int failsafe = MillenaireServerConfig.SERVER.cityStatsFailsafeTicks.getAsInt();
      if (village.isCityStatsDirty() || StaggeredVillageJob.shouldRun(level, village, failsafe)) {
         refresh(level, village);
      }
   }

   public static CityStats getOrRefresh(ServerLevel level, Village village) {
      CityStats cached = village.getCityStatsCache();
      if (village.isCityStatsDirty() || cached == null || cached.isStale(level.getGameTime(), 100L)) {
         return refresh(level, village);
      }
      return cached;
   }

   public static CityStats refresh(ServerLevel level, Village village) {
      TickPerfCounters.CITY_STATS_REFRESHES.incrementAndGet();
      TickPerfCounters.SLOT_RECOUNTS.incrementAndGet();

      Map<UUID, Integer> occupantsByBuilding = new HashMap<>();
      int population = 0;
      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (record.isKilled() || record.isRaidingVillage()) {
            continue;
         }
         population++;
         if (record.getHomeBuilding() != null) {
            occupantsByBuilding.merge(record.getHomeBuilding().uuid(), 1, Integer::sum);
         }
      }

      int maxSlots = 0;
      int openJobs = 0;
      int openBeds = 0;
      int buildings = 0;
      for (BuildingInstance building : village.getBuildings()) {
         if (!building.isOperational() || building.isSubBuilding()) {
            continue;
         }
         buildings++;
         BuildingPlanSet planSet = resolvePlanSet(building);
         int capacity = planSet == null ? 0 : planSet.maleResidents().size() + planSet.femaleResidents().size();
         maxSlots += capacity;
         int occupants = occupantsByBuilding.getOrDefault(building.getId().uuid(), 0);
         openJobs += Math.max(0, capacity - occupants);
         if (building.isPlayerReservedHouse()) {
            // King's house beds are for the player only
         } else if (building.hasBedManager()) {
            openBeds += Math.max(0, building.getBedManager().getBedCount() - occupants);
         } else {
            openBeds += Math.max(0, capacity - occupants);
         }
      }

      int threat = computeThreatLevel(level, village);
      String threatLabel = threatLabel(threat);
      CityStats stats = new CityStats(population, maxSlots, openJobs, openBeds, buildings, threat, threatLabel, level.getGameTime());
      village.setCityStatsCache(stats);
      village.clearCityStatsDirty();
      return stats;
   }

   @Nullable
   static BuildingPlanSet resolvePlanSet(BuildingInstance building) {
      if (building.getPlanSetId() != null) {
         return ModCultures.getBuildingPlanSet(building.getPlanSetId());
      }
      return null;
   }

   private static int computeThreatLevel(ServerLevel level, Village village) {
      if (village.isUnderAttack()) {
         return 3;
      }
      VillageType vt = ModCultures.getVillageType(village.getVillageTypeId());
      int radius = MillenaireServerConfig.SERVER.banditRaidRadius.getAsInt();
      if (radius <= 0) {
         return 0;
      }
      long radiusSq = (long)radius * (long)radius;
      int hostileCamps = 0;
      for (Village other : VillageSavedData.get(level).getVillageManager().getAllVillages()) {
         if (other == village) {
            continue;
         }
         VillageType otherType = ModCultures.getVillageType(other.getVillageTypeId());
         if (otherType == null || !isBanditCamp(otherType)) {
            continue;
         }
         if (other.getCenter().distSqr(village.getCenter()) < radiusSq) {
            hostileCamps++;
         }
      }
      if (hostileCamps >= 3) {
         return 2;
      }
      if (hostileCamps >= 1) {
         return 1;
      }
      return village.getRaidTarget() != null ? 1 : 0;
   }

   static boolean isBanditCamp(VillageType vt) {
      return vt.loneBuilding() && (vt.carriesRaid() || "illager_camp".equals(vt.specialType()));
   }

   private static String threatLabel(int threat) {
      return switch (threat) {
         case 3 -> "under_attack";
         case 2 -> "high";
         case 1 -> "elevated";
         default -> "none";
      };
   }

   public static Map<BuildingPurpose, Integer> openSlotsByPurpose(Village village) {
      Map<UUID, Integer> occupantsByBuilding = new HashMap<>();
      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (!record.isKilled() && !record.isRaidingVillage() && record.getHomeBuilding() != null) {
            occupantsByBuilding.merge(record.getHomeBuilding().uuid(), 1, Integer::sum);
         }
      }
      Map<BuildingPurpose, Integer> open = new HashMap<>();
      for (BuildingInstance building : village.getBuildings()) {
         if (!building.isOperational() || building.isSubBuilding()) {
            continue;
         }
         BuildingPlanSet planSet = resolvePlanSet(building);
         int capacity = planSet == null ? 0 : planSet.maleResidents().size() + planSet.femaleResidents().size();
         int free = Math.max(0, capacity - occupantsByBuilding.getOrDefault(building.getId().uuid(), 0));
         if (free > 0) {
            open.merge(building.getPurpose(), free, Integer::sum);
         }
      }
      return open;
   }

   public static Set<String> missingResidentRoles(Village village, BuildingInstance building) {
      BuildingPlanSet planSet = resolvePlanSet(building);
      if (planSet == null) {
         return Set.of();
      }
      Map<String, Integer> needed = new HashMap<>();
      for (String role : planSet.maleResidents()) {
         needed.merge(role, 1, Integer::sum);
      }
      for (String role : planSet.femaleResidents()) {
         needed.merge(role, 1, Integer::sum);
      }
      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (record.isKilled() || record.getHomeBuilding() == null || !record.getHomeBuilding().equals(building.getId())) {
            continue;
         }
         String path = record.getVillagerTypeId().getPath();
         int slash = path.lastIndexOf('/');
         String role = slash >= 0 ? path.substring(slash + 1) : path;
         needed.computeIfPresent(role, (k, v) -> v <= 1 ? null : v - 1);
      }
      return new HashSet<>(needed.keySet());
   }
}
