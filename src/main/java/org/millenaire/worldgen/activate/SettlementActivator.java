package org.millenaire.worldgen.activate;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.BrickColourTheme;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.faction.FactionLifecycle;
import org.millenaire.worldgen.WorldgenMetrics;
import org.millenaire.village.BuildingFinalizer;
import org.millenaire.village.SubBuildingHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillageBookService;
import org.millenaire.village.VillageGrowthManager;
import org.millenaire.village.VillageId;
import org.millenaire.village.VillageManager;
import org.millenaire.village.VillageSavedData;
import org.millenaire.village.path.VillagePathManager;
import org.millenaire.worldgen.plan.PlannedBuilding;
import org.millenaire.worldgen.plan.SettlementFailureReason;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;
import org.millenaire.worldgen.plan.SettlementPlanStatus;
import org.slf4j.Logger;

/**
 * Stage C — server-thread gameplay activation. Idempotent.
 * Physical blocks must already be present (PHYSICAL_COMPLETE).
 */
public final class SettlementActivator {
   private static final Logger LOGGER = LogUtils.getLogger();

   private SettlementActivator() {
   }

   public static boolean tryActivate(ServerLevel level, SettlementPlan plan) {
      if (plan.activated() || plan.status() == SettlementPlanStatus.ACTIVE) {
         return true;
      }
      if (plan.status() != SettlementPlanStatus.PHYSICAL_COMPLETE && plan.status() != SettlementPlanStatus.ACTIVATING) {
         return false;
      }
      if (!plan.hasMandatoryCentre() && !plan.loneBuilding()) {
         plan.markFailed(SettlementFailureReason.MISSING_MANDATORY_BUILDING, "activation_no_centre");
         SettlementPlanSavedData.get(level).markDirtyPlan();
         return false;
      }

      if (!plan.trySetStatus(SettlementPlanStatus.ACTIVATING)) {
         if (plan.status() != SettlementPlanStatus.ACTIVATING) {
            plan.forceStatus(SettlementPlanStatus.ACTIVATING);
         }
      }

      try {
         VillageSavedData savedData = VillageSavedData.get(level);
         VillageManager manager = savedData.getVillageManager();
         VillageId villageId = new VillageId(plan.villageUuid());
         Village existing = manager.getVillage(villageId);
         if (existing != null) {
            plan.setActivated(true);
            plan.forceStatus(SettlementPlanStatus.ACTIVE);
            SettlementPlanSavedData.get(level).markDirtyPlan();
            return true;
         }

         VillageType villageType = ModCultures.getVillageType(plan.villageTypeId());
         if (villageType == null) {
            plan.markFailed(SettlementFailureReason.ACTIVATION_FAILURE, "missing_village_type");
            SettlementPlanSavedData.get(level).markDirtyPlan();
            return false;
         }

         Village village = new Village(villageId, plan.cultureId(), plan.villageTypeId(), plan.center());
         String name = plan.plannedName();
         if (name == null || name.isBlank()) {
            name = villageType.name() != null ? villageType.name() : plan.villageTypeId().getPath();
         }
         village.setVillageName(name);

         if (!villageType.brickColourThemes().isEmpty()) {
            BrickColourTheme theme = villageType.brickColourThemes().getFirst();
            village.setBrickTheme(theme);
         }
         if (!villageType.bannerJsons().isEmpty()) {
            village.setBannerNbt(villageType.bannerJsons().getFirst());
         }

         for (PlannedBuilding pb : plan.buildings()) {
            BuildingPlan bPlan = ModCultures.getBuildingPlan(pb.planId());
            if (bPlan == null) {
               continue;
            }
            BuildingInstance instance = new BuildingInstance(
               new BuildingId(pb.buildingId()),
               pb.planId(),
               pb.origin(),
               pb.rotation(),
               BuildingInstance.Status.COMPLETE,
               pb.planSetId(),
               pb.variant(),
               pb.level()
            );
            if (village.getBrickTheme() != null) {
               instance.initBrickColours(village.getBrickTheme(), level.getRandom());
            }
            BuildingFinalizer.applyPostPlacement(level, village, instance, bPlan);
            village.addBuilding(instance);
            BuildingFinalizer.applyCompletionEffects(level, village, instance);
            if (pb.planSetId() != null) {
               BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(pb.planSetId());
               if (planSet != null) {
                  SubBuildingHelper.spawnStartingSubBuildings(level, village, planSet, instance, true);
                  BuildingPlanSet.LevelDef level0 = planSet.getLevel(pb.variant(), 0);
                  if (level0 != null) {
                     SubBuildingHelper.spawnUpgradeSubBuildings(level, village, planSet, level0, instance, false);
                  }
                  VillageGrowthManager.spawnBuildingOccupants(level, village, planSet, instance);
               }
            }
         }

         if (!plan.inventoriesInitialized()) {
            BuildingInstance centre = village.getBuildings().isEmpty() ? null : village.getBuildings().getFirst();
            if (centre != null && centre.getPlanSetId() != null) {
               BuildingPlanSet centrePlanSet = ModCultures.getBuildingPlanSet(centre.getPlanSetId());
               if (centrePlanSet != null && !centrePlanSet.startingGoods().isEmpty()) {
                  village.fillStartingGoods(level, centre, centrePlanSet, true);
                  village.setLastGoodsRefresh(level.getGameTime());
               }
               ItemStack scroll = VillageBookService.createScrollForVillage(village);
               if (centre.getInventory() != null) {
                  centre.getInventory().addStack(level, scroll);
               }
            }
            plan.setInventoriesInitialized(true);
         }

         if (plan.parentPlanId() != null) {
            SettlementPlan parent = SettlementPlanSavedData.get(level).getPlan(plan.parentPlanId());
            if (parent != null) {
               village.setParentVillageId(new VillageId(parent.villageUuid()));
            }
         }

         manager.addVillage(village);
         FactionLifecycle.onVillageRegistered(level, village);
         savedData.setDirty();

         if (!plan.loneBuilding() && Boolean.TRUE.equals(org.millenaire.config.MillenaireServerConfig.SERVER.buildPaths.get())) {
            try {
               VillagePathManager pathManager = village.getPathManager();
               if (pathManager != null) {
                  pathManager.recalculatePaths(level, village, true);
               }
            } catch (Exception pathEx) {
               LOGGER.warn("[Millenaire] Path activation deferred for {}: {}", plan.planId(), pathEx.getMessage());
            }
         }

         emitOptionalPhase3Event(level, village);

         plan.setActivated(true);
         plan.forceStatus(SettlementPlanStatus.ACTIVE);
         SettlementPlanSavedData.get(level).markDirtyPlan();
         WorldgenMetrics.ACTIVATIONS.incrementAndGet();
         LOGGER.info(
            "[Millenaire] Activated worldgen settlement {} '{}' type={} at {}",
            plan.planId(),
            village.getVillageName(),
            plan.villageTypeId(),
            plan.center().toShortString()
         );
         return true;
      } catch (Exception e) {
         LOGGER.error("[Millenaire] Activation failed for plan {}", plan.planId(), e);
         WorldgenMetrics.ACTIVATION_FAILURES.incrementAndGet();
         plan.markFailed(SettlementFailureReason.ACTIVATION_FAILURE, e.getClass().getSimpleName() + ": " + e.getMessage());
         SettlementPlanSavedData.get(level).markDirtyPlan();
         return false;
      }
   }

   private static void emitOptionalPhase3Event(ServerLevel level, Village village) {
      try {
         Class<?> events = Class.forName("org.millenaire.living.VillageEventService");
         Object service = events.getMethod("get", ServerLevel.class).invoke(null, level);
         if (service != null) {
            events.getMethod("emitSettlementFounded", Village.class).invoke(service, village);
         }
      } catch (ClassNotFoundException ignored) {
         // Phase 3 not present on this branch
      } catch (ReflectiveOperationException e) {
         LOGGER.debug("[Millenaire] Phase 3 settlement-founded hook unavailable: {}", e.getMessage());
      }
   }
}
