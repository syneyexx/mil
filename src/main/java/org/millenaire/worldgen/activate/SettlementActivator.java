package org.millenaire.worldgen.activate;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.civilization.settlement.ColonizationMaterializationGate;
import org.millenaire.culture.BrickColourTheme;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.faction.FactionId;
import org.millenaire.faction.FactionLifecycle;
import org.millenaire.faction.FactionManager;
import org.millenaire.faction.FactionSavedData;
import org.millenaire.living.events.LivingEventService;
import org.millenaire.living.events.LivingEventSource;
import org.millenaire.living.events.LivingEventType;
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
import org.millenaire.worldgen.materialize.MaterializationTickBudget;
import org.millenaire.worldgen.plan.PlannedBuilding;
import org.millenaire.worldgen.plan.SettlementFailureReason;
import org.millenaire.worldgen.plan.SettlementPlan;
import org.millenaire.worldgen.plan.SettlementPlanSavedData;
import org.millenaire.worldgen.plan.SettlementPlanStatus;
import org.slf4j.Logger;

/**
 * Stage C — server-thread gameplay activation. Resumable across ticks with stable checkpoints
 * so partial runs do not duplicate villagers, goods, or completion effects.
 */
public final class SettlementActivator {
   private static final Logger LOGGER = LogUtils.getLogger();

   private SettlementActivator() {
   }

   /**
    * @return true when activation fully completed this call
    */
   public static boolean tryActivate(ServerLevel level, SettlementPlan plan) {
      if (plan.activated() || plan.status() == SettlementPlanStatus.ACTIVE) {
         return true;
      }
      if (plan.status() != SettlementPlanStatus.PHYSICAL_COMPLETE && plan.status() != SettlementPlanStatus.ACTIVATING) {
         return false;
      }
      if (!plan.isPhysicallyComplete()) {
         return false;
      }
      if (!ColonizationMaterializationGate.allowActivation(level, plan)) {
         return false;
      }
      if (!plan.hasMandatoryCentre() && !plan.loneBuilding()) {
         plan.markFailed(SettlementFailureReason.MISSING_MANDATORY_BUILDING, "activation_no_centre");
         SettlementPlanSavedData.get(level).markDirtyPlan(plan.planId());
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
         Village village = manager.getVillage(villageId);
         if (village != null && plan.activationBuildingIndex() >= plan.buildings().size() && !plan.activationPathsPending()) {
            finishActive(level, plan);
            return true;
         }

         VillageType villageType = ModCultures.getVillageType(plan.villageTypeId());
         if (villageType == null) {
            plan.markFailed(SettlementFailureReason.ACTIVATION_FAILURE, "missing_village_type");
            SettlementPlanSavedData.get(level).markDirtyPlan(plan.planId());
            return false;
         }

         if (village == null) {
            village = new Village(villageId, plan.cultureId(), plan.villageTypeId(), plan.center());
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
            manager.addVillage(village);
            if (plan.isEmptyFounding() && plan.foundingFactionId() != null) {
               assignFoundingFaction(level, village, plan.foundingFactionId());
            } else {
               FactionLifecycle.onVillageRegistered(level, village);
            }
            if (plan.loneBuilding()) {
               String culturePath = plan.cultureId() != null ? plan.cultureId().getPath() : "";
               savedData.registerLoneBuilding(plan.center(), plan.villageTypeId(), culturePath, null);
            }
            if (plan.parentPlanId() != null) {
               SettlementPlan parent = SettlementPlanSavedData.get(level).getPlan(plan.parentPlanId());
               if (parent != null) {
                  village.setParentVillageId(new VillageId(parent.villageUuid()));
               }
            }
            savedData.setDirty();
            SettlementPlanSavedData.get(level).markDirtyPlan(plan.planId());
         }

         // Register buildings in small steps under the shared time budget.
         int index = plan.activationBuildingIndex();
         while (index < plan.buildings().size()) {
            if (!MaterializationTickBudget.hasTime()) {
               plan.setActivationBuildingIndex(index);
               SettlementPlanSavedData.get(level).markDirtyPlan(plan.planId());
               return false;
            }
            PlannedBuilding pb = plan.buildings().get(index);
            BuildingId bid = new BuildingId(pb.buildingId());
            if (village.getBuilding(bid) == null) {
               BuildingPlan bPlan = ModCultures.getBuildingPlan(pb.planId());
               if (bPlan != null) {
                  BuildingInstance instance = new BuildingInstance(
                     bid,
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
                        if (!plan.isEmptyFounding()) {
                           VillageGrowthManager.spawnBuildingOccupants(level, village, planSet, instance);
                        }
                     }
                  }
               }
            }
            index++;
            plan.setActivationBuildingIndex(index);
            SettlementPlanSavedData.get(level).markDirtyPlan(plan.planId());
         }

         if (!plan.inventoriesInitialized()) {
            BuildingInstance centre = village.getBuildings().isEmpty() ? null : village.getBuildings().getFirst();
            if (centre != null && centre.getPlanSetId() != null) {
               BuildingPlanSet centrePlanSet = ModCultures.getBuildingPlanSet(centre.getPlanSetId());
               if (!plan.isEmptyFounding()
                  && centrePlanSet != null
                  && !centrePlanSet.startingGoods().isEmpty()) {
                  village.fillStartingGoods(level, centre, centrePlanSet, true);
                  village.setLastGoodsRefresh(level.getGameTime());
               }
               ItemStack scroll = VillageBookService.createScrollForVillage(village);
               if (centre.getInventory() != null) {
                  centre.getInventory().addStack(level, scroll);
               }
            }
            plan.setInventoriesInitialized(true);
            SettlementPlanSavedData.get(level).markDirtyPlan(plan.planId());
         }

         if (!plan.loneBuilding() && Boolean.TRUE.equals(org.millenaire.config.MillenaireServerConfig.SERVER.buildPaths.get())) {
            // Mark dirty so VillagePathManager.startRecalcIfDirty drains A* across later ticks
            // instead of blocking activation with a one-shot full recalculate.
            try {
               VillagePathManager pathManager = village.getPathManager();
               if (pathManager != null) {
                  pathManager.markPathsDirty();
               }
            } catch (Exception pathEx) {
               LOGGER.warn("[Millenaire] Path activation mark deferred for {}: {}", plan.planId(), pathEx.getMessage());
            }
            plan.setActivationPathsPending(false);
            SettlementPlanSavedData.get(level).markDirtyPlan(plan.planId());
         }

         if (!plan.isEmptyFounding()) {
            emitOptionalPhase3Event(level, village);
            if (!plan.loneBuilding()) {
               org.millenaire.compat.WaystoneVillagePlacer.tryPlace(level, village);
            }
            org.millenaire.worldgen.highway.HighwayDiscoveryService.onVillageActivated(level, village);
         }

         finishActive(level, plan);
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
         SettlementPlanSavedData.get(level).markDirtyPlan(plan.planId());
         return false;
      }
   }

   private static void finishActive(ServerLevel level, SettlementPlan plan) {
      plan.setActivated(true);
      plan.setActivationPathsPending(false);
      plan.forceStatus(SettlementPlanStatus.ACTIVE);
      SettlementPlanSavedData.get(level).markDirtyPlan(plan.planId());
      WorldgenMetrics.ACTIVATIONS.incrementAndGet();
   }

   private static void assignFoundingFaction(ServerLevel level, Village village, String factionIdRaw) {
      try {
         FactionId factionId = FactionId.tryParse(factionIdRaw);
         FactionManager factions = FactionSavedData.get(level).getFactionManager();
         if (factionId != null && factions.getFaction(factionId) != null) {
            factions.assignVillage(village, factionId);
            return;
         }
         LOGGER.warn(
            "[Millenaire] Colonization founding faction {} missing — falling back to culture default for {}",
            factionIdRaw,
            village.getId().uuid()
         );
      } catch (Exception e) {
         LOGGER.warn("[Millenaire] Colonization faction assign failed: {}", e.toString());
      }
      FactionLifecycle.onVillageRegistered(level, village);
   }

   private static void emitOptionalPhase3Event(ServerLevel level, Village village) {
      try {
         LivingEventService.emit(
            level,
            LivingEventType.VILLAGE_FOUNDED,
            LivingEventSource.SYSTEM,
            village,
            null,
            null,
            "worldgen_activation",
            "public",
            "founded:" + village.getId().uuid()
         );
      } catch (Exception e) {
         LOGGER.debug("[Millenaire] Living event settlement-founded hook failed: {}", e.toString());
      }
   }
}
