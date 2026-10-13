package org.millenaire.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerSpawnFactory;
import org.millenaire.item.ModItems;
import org.millenaire.perf.StaggeredVillageJob;
import org.millenaire.perf.TickPerfCounters;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Controlled-village immigration with Phase 0 stagger + hard caps.
 */
public final class ImmigrantService {
   private static final Logger LOGGER = LogUtils.getLogger();

   private ImmigrantService() {
   }

   public static void tickAuto(ServerLevel level, Village village) {
      if (!village.isPlayerControlled() || !village.isActive()) {
         return;
      }
      int period = MillenaireServerConfig.SERVER.immigrantAutoPeriodTicks.getAsInt();
      if (!StaggeredVillageJob.shouldRun(level, village, period)) {
         return;
      }
      TickPerfCounters.IMMIGRANT_CHECKS.incrementAndGet();
      tryArrive(level, village, false, null);
   }

   public static boolean callImmigrants(ServerLevel level, Village village, ServerPlayer player) {
      if (!village.isControlledBy(player.getUUID())) {
         return false;
      }
      return tryArrive(level, village, true, player);
   }

   private static boolean tryArrive(ServerLevel level, Village village, boolean manual, @Nullable ServerPlayer player) {
      long day = level.getDayTime() / 24000L;
      village.rollImmigrantDay(day);

      int autoCap = MillenaireServerConfig.SERVER.immigrantAutoPerDay.getAsInt();
      int manualCap = MillenaireServerConfig.SERVER.immigrantManualPerDay.getAsInt();
      int maxQueue = MillenaireServerConfig.SERVER.immigrantMaxQueue.getAsInt();

      if (manual) {
         if (village.getImmigrantManualToday() >= manualCap) {
            notify(player, Component.translatable("gui.millenaire.city.immigrant_manual_cap"));
            return false;
         }
      } else if (village.getImmigrantAutoToday() >= autoCap) {
         return false;
      }

      CityStats stats = CityStatsService.getOrRefresh(level, village);
      if (stats.openJobSlots() <= 0) {
         if (manual) {
            notify(player, Component.translatable("gui.millenaire.city.immigrant_no_jobs"));
         }
         return false;
      }
      if (stats.openBeds() <= 0) {
         if (manual) {
            notify(player, Component.translatable("gui.millenaire.city.immigrant_no_beds"));
         }
         return false;
      }
      if (countPendingish(village) >= maxQueue) {
         if (manual) {
            notify(player, Component.translatable("gui.millenaire.city.immigrant_queue_full"));
         }
         return false;
      }

      ImmigrantTarget target = pickTarget(village);
      if (target == null) {
         if (manual) {
            notify(player, Component.translatable("gui.millenaire.city.immigrant_no_target"));
         }
         return false;
      }

      int cost = MillenaireServerConfig.SERVER.immigrantDenierCost.getAsInt();
      if (cost > 0) {
         if (!VillageTreasuryService.canAfford(level, village, cost)) {
            if (manual) {
               notify(player, Component.translatable("gui.millenaire.city.immigrant_no_funds"));
            }
            return false;
         }
         if (!VillageTreasuryService.withdraw(level, village, cost, VillageTreasuryService.REASON_IMMIGRANT)) {
            if (manual) {
               notify(player, Component.translatable("gui.millenaire.city.immigrant_no_funds"));
            }
            return false;
         }
      }

      MillVillager spawned = VillagerSpawnFactory.spawnInVillage(
         level, village, target.typeId(), target.spawnPos(), target.building().getId()
      );
      if (spawned == null) {
         if (cost > 0) {
            VillageTreasuryService.deposit(level, village, cost, VillageTreasuryService.REASON_IMMIGRANT);
         }
         if (manual) {
            notify(player, Component.translatable("gui.millenaire.city.immigrant_spawn_failed"));
         }
         return false;
      }

      if (manual) {
         village.incrementImmigrantManualToday();
      } else {
         village.incrementImmigrantAutoToday();
      }
      TickPerfCounters.IMMIGRANT_SPAWNS.incrementAndGet();
      village.markCityStatsDirty();
      village.markDirty();
      CityStatsService.refresh(level, village);
      LOGGER.info(
         "[Millenaire] Immigrant {} arrived in controlled village {} (manual={})",
         target.typeId().getPath(),
         village.getVillageName(),
         manual
      );
      if (manual) {
         notify(player, Component.translatable("gui.millenaire.city.immigrant_arrived", spawned.getVillagerDisplayName()));
      }
      return true;
   }

   private static int countPendingish(Village village) {
      // Soft budget: open jobs already gate arrivals; use recent spawns today as queue proxy.
      return village.getImmigrantAutoToday() + village.getImmigrantManualToday();
   }

   @Nullable
   private static ImmigrantTarget pickTarget(Village village) {
      List<BuildingInstance> buildings = new ArrayList<>(village.getBuildings());
      buildings.sort(
         Comparator
            .<BuildingInstance>comparingInt(b -> priorityScore(village, b))
            .reversed()
            .thenComparing(b -> b.getId().uuid())
      );
      String culturePath = village.getCultureId().getPath();
      for (BuildingInstance building : buildings) {
         if (!building.isOperational() || building.isSubBuilding()) {
            continue;
         }
         Set<String> missing = CityStatsService.missingResidentRoles(village, building);
         if (missing.isEmpty()) {
            continue;
         }
         String preferred = preferredRole(village, building, missing);
         if (preferred == null) {
            preferred = missing.iterator().next();
         }
         ResourceLocation typeId = ResourceLocation.fromNamespaceAndPath("millenaire", culturePath + "/" + preferred);
         VillagerType type = ModCultures.getVillagerType(typeId);
         if (type == null) {
            continue;
         }
         BlockPos spawn = building.getPathStartPos();
         if (spawn == null) {
            spawn = building.getOrigin();
         }
         return new ImmigrantTarget(building, typeId, spawn);
      }
      return null;
   }

   private static int priorityScore(Village village, BuildingInstance building) {
      int base = switch (building.getPurpose()) {
         case HOUSING -> 50;
         case PRODUCTION -> 40;
         case MILITARY -> 35;
         case TRADE -> 30;
         case STORAGE -> 20;
         case CULTURE -> 25;
         case AUTO -> 10;
      };
      String preferred = village.getJobPriorityRole();
      if (preferred != null && !preferred.isEmpty()) {
         Set<String> missing = CityStatsService.missingResidentRoles(village, building);
         if (missing.contains(preferred)) {
            base += 100;
         }
      }
      return base;
   }

   @Nullable
   private static String preferredRole(Village village, BuildingInstance building, Set<String> missing) {
      String preferred = village.getJobPriorityRole();
      if (preferred != null && missing.contains(preferred)) {
         return preferred;
      }
      return null;
   }

   private static void notify(@Nullable ServerPlayer player, Component message) {
      if (player != null) {
         player.sendSystemMessage(message);
      }
   }

   private record ImmigrantTarget(BuildingInstance building, ResourceLocation typeId, BlockPos spawnPos) {
   }
}
