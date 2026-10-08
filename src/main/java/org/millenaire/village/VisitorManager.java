package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerSpawnFactory;
import org.slf4j.Logger;

public final class VisitorManager {
   private static final Logger LOGGER = LogUtils.getLogger();

   private VisitorManager() {
   }

   public static void updateVisitors(ServerLevel level, Village village, BuildingInstance building, boolean isDaytime) {
      if (!isDaytime) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(building.getPlanSetId());
         if (planSet != null && !planSet.visitors().isEmpty()) {
            long currentDay = level.getDayTime() / 24000L;
            if (building.getLastMarketNightDay() > currentDay) {
               building.setLastMarketNightDay(-1L);
            }

            if (building.getLastMarketNightDay() < currentDay) {
               Map<String, Integer> targetCount = new LinkedHashMap<>();

               for (String visitorType : planSet.visitors()) {
                  targetCount.merge(visitorType, 1, Integer::sum);
               }

               String culturePath = village.getCultureId().getPath();
               BuildingId buildingId = building.getId();
               RandomSource random = level.getRandom();

               for (Entry<String, Integer> entry : targetCount.entrySet()) {
                  String visitorType = entry.getKey();
                  int target = entry.getValue();
                  ResourceLocation visitorTypeId = ResourceLocation.fromNamespaceAndPath("millenaire", culturePath + "/" + visitorType);
                  int currentCount = countVisitors(village, buildingId, visitorTypeId);

                  for (int i = currentCount; i < target; i++) {
                     if (random.nextInt(2) == 0) {
                        spawnVisitor(level, village, building, visitorTypeId);
                     }
                  }
               }

               building.setLastMarketNightDay(currentDay);
            }
         }
      }
   }

   private static int countVisitors(Village village, BuildingId buildingId, ResourceLocation visitorTypeId) {
      int count = 0;

      for (VillagerRecord record : village.getVillagerRecords().values()) {
         BuildingId home = record.getHomeBuilding();
         if (home != null && home.equals(buildingId) && visitorTypeId.equals(record.getVillagerTypeId())) {
            count++;
         }
      }

      return count;
   }

   private static void spawnVisitor(ServerLevel level, Village village, BuildingInstance building, ResourceLocation visitorTypeId) {
      BlockPos spawnPos = building.getSleepingPos();
      MillVillager visitor = VillagerSpawnFactory.spawnInVillage(level, village, visitorTypeId, spawnPos, building.getId());
      if (visitor == null) {
         LOGGER.warn("[Millenaire] Failed to spawn visitor {} at {}", visitorTypeId, spawnPos.toShortString());
      } else {
         LOGGER.debug("[Millenaire] Spawned visitor {} at building {}", visitorTypeId, building.getPlanSetId());
      }
   }
}
