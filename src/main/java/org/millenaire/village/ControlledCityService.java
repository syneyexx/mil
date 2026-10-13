package org.millenaire.village;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.language.BuildingNameHelper;
import org.millenaire.network.ControlledCityPayload;

public final class ControlledCityService {
   private ControlledCityService() {
   }

   public static ControlledCityPayload buildPayload(Village village, ServerLevel level) {
      CityStats stats = CityStatsService.getOrRefresh(level, village);
      Map<UUID, Integer> occupantsByBuilding = new HashMap<>();

      for (VillagerRecord record : village.getVillagerRecords().values()) {
         if (!record.isKilled() && !record.isRaidingVillage() && record.getHomeBuilding() != null) {
            occupantsByBuilding.merge(record.getHomeBuilding().uuid(), 1, Integer::sum);
         }
      }

      List<ControlledCityPayload.BuildingEntry> entries = new ArrayList<>();

      for (BuildingInstance building : village.getBuildings()) {
         if (!building.isOperational() || building.isSubBuilding()) {
            continue;
         }

         BuildingPlanSet planSet = building.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(building.getPlanSetId()) : null;
         int capacity = planSet == null ? 0 : planSet.maleResidents().size() + planSet.femaleResidents().size();
         int occupants = occupantsByBuilding.getOrDefault(building.getId().uuid(), 0);
         int openSlots = Math.max(0, capacity - occupants);
         String planName = planSet != null ? BuildingNameHelper.getTranslationKey(planSet) : building.getPlanId().getPath();
         entries.add(
            new ControlledCityPayload.BuildingEntry(
               building.getId().uuid().toString(),
               planName,
               building.getPurpose().serializationName(),
               openSlots,
               occupants
            )
         );
      }

      String villageName = village.getVillageName() != null ? village.getVillageName() : "";
      String jobPriority = village.getJobPriorityRole();
      return new ControlledCityPayload(
         village.getId().uuid().toString(),
         villageName,
         stats.population(),
         stats.maxResidentSlots(),
         stats.openJobSlots(),
         stats.openBeds(),
         stats.buildings(),
         stats.threatLevel(),
         stats.threatLabel(),
         jobPriority,
         village.getImmigrantAutoToday(),
         village.getImmigrantManualToday(),
         MillenaireServerConfig.SERVER.immigrantAutoPerDay.getAsInt(),
         MillenaireServerConfig.SERVER.immigrantManualPerDay.getAsInt(),
         MillenaireServerConfig.SERVER.immigrantDenierCost.getAsInt(),
         entries
      );
   }

   public static void sendRefresh(ServerPlayer player, ServerLevel level, Village village) {
      PacketDistributor.sendToPlayer(player, buildPayload(village, level), new CustomPacketPayload[0]);
   }
}
