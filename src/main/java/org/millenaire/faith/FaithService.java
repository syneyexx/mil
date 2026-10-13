package org.millenaire.faith;

import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.village.ControlledQueuedProject;
import org.millenaire.village.PlacementSignHelper;
import org.millenaire.village.Village;
import org.millenaire.village.VillageGrowthManager;
import org.millenaire.world.PlacedLocation;

/**
 * Assigns default faiths and queues a shrine/church project for player-founded faiths.
 */
public final class FaithService {
   private FaithService() {
   }

   public static void applyDefaultFaith(Village village) {
      if (village.getFaithId() != null) {
         return;
      }
      ResourceLocation faithId = FaithRegistry.defaultFaithIdForCulture(village.getCultureId());
      if (faithId != null) {
         village.setFaithId(faithId);
      }
   }

   public static void startPlayerFaith(Village village, String customName) {
      village.setFaithId(FaithRegistry.playerCustomId());
      village.setCustomFaithName(customName != null && !customName.isBlank() ? customName.trim() : "Faith");
      enqueuePreferredShrine(village);
   }

   /**
    * Prefer {@link ReligionService#startPlayerFaithInstance} when a ServerLevel is available
    * so custom faiths get a unique UUID identity.
    */
   public static void startPlayerFaith(ServerLevel level, Village village, String customName) {
      ReligionService.startPlayerFaithInstance(level, village, customName);
   }

   public static String resolveDisplayName(Village village) {
      if (village.getCustomFaithName() != null && !village.getCustomFaithName().isBlank()) {
         return village.getCustomFaithName().trim();
      }
      FaithDefinition def = FaithRegistry.get(village.getFaithId());
      if (def != null) {
         return def.displayName();
      }
      return "Faith";
   }

   public static String resolveDisplayName(ServerLevel level, Village village) {
      if (village.getFaithInstanceId() != null) {
         ReligionSavedData data = ReligionSavedData.get(level);
         FaithInstance inst = data.getFaith(village.getFaithInstanceId());
         if (inst != null) {
            return inst.resolveDisplayName();
         }
      }
      return resolveDisplayName(village);
   }

   @Nullable
   public static FaithDefinition resolve(Village village) {
      FaithDefinition def = FaithRegistry.get(village.getFaithId());
      if (def != null) {
         return def;
      }
      return FaithRegistry.getDefaultForCulture(village.getCultureId());
   }

   /**
    * One-shot per session: if an ordinary village has no place of worship, queue a shrine/church.
    * Lone / illager / bandit settlements are skipped. Player-controlled villages (auto-core blocked)
    * receive a controlled-queue entry with a planned location when possible.
    */
   public static void ensureWorshipInfrastructure(ServerLevel level, Village village) {
      if (village.isWorshipInfrastructureEnsured()) {
         return;
      }
      village.setWorshipInfrastructureEnsured(true);

      VillageType type = ModCultures.getVillageType(village.getVillageTypeId());
      if (type == null || !type.isRegularVillage()) {
         return;
      }
      if (type.culture() != null && "illagers".equals(type.culture().getPath())) {
         return;
      }
      if (hasWorshipBuilding(village) || isWorshipAlreadyQueued(village)) {
         return;
      }

      ResourceLocation planId = findShrinePlan(village);
      if (planId == null) {
         return;
      }

      if (type.playerControlled()) {
         enqueueControlledShrine(level, village, planId);
      } else {
         enqueuePreferredShrine(village);
      }
   }

   public static void enqueuePreferredShrine(Village village) {
      if (village.getPendingProject() != null) {
         return;
      }
      ResourceLocation planId = findShrinePlan(village);
      if (planId == null) {
         return;
      }
      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planId);
      if (planSet == null) {
         return;
      }
      String variant = planSet.variants().isEmpty() ? "a" : planSet.variants().keySet().iterator().next();
      village.setPendingProject(new Village.PendingProject(planId, variant, 0, false, null, null));
   }

   private static void enqueueControlledShrine(ServerLevel level, Village village, ResourceLocation planId) {
      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planId);
      if (planSet == null) {
         return;
      }
      VillageType type = ModCultures.getVillageType(village.getVillageTypeId());
      VillageType.LayoutSlot slot = type != null ? findLayoutSlot(type, planId) : null;
      // Controlled auto-growth excludes core/secondary; queue the offered shrine instead.
      if (slot == null || "centre".equals(slot.role()) || "townhall".equals(slot.role())) {
         return;
      }
      String variant = planSet.pickRandomVariant(ThreadLocalRandom.current());
      BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, 0);
      BuildingPlan plan = levelDef != null ? ModCultures.getBuildingPlan(levelDef.planId()) : null;
      if (plan == null) {
         return;
      }
      PlacedLocation planned = VillageGrowthManager.findLocationForNewBuilding(level, village, planSet, plan, slot);
      if (planned == null) {
         return;
      }
      boolean enqueued = village.enqueueControlledProject(new ControlledQueuedProject(planId, variant, 0, planned));
      if (enqueued) {
         village.setNoProjectsLeftUntil(0L);
         PlacementSignHelper.placeCornerSigns(level, plan, planned, planSet.nativeName());
      }
   }

   public static boolean hasWorshipBuilding(Village village) {
      FaithDefinition def = resolve(village);
      for (BuildingInstance building : village.getBuildings()) {
         if (building.getPlanSetId() == null) {
            continue;
         }
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(building.getPlanSetId());
         if (planSet != null && hasReligiousTag(planSet, def)) {
            return true;
         }
      }
      return false;
   }

   public static boolean isWorshipAlreadyQueued(Village village) {
      FaithDefinition def = resolve(village);
      Village.PendingProject pending = village.getPendingProject();
      if (pending != null && isWorshipPlan(pending.planSetId(), def)) {
         return true;
      }
      for (ControlledQueuedProject q : village.getControlledQueue()) {
         if (isWorshipPlan(q.planSetId(), def)) {
            return true;
         }
      }
      return false;
   }

   private static boolean isWorshipPlan(ResourceLocation planId, @Nullable FaithDefinition def) {
      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planId);
      return planSet != null && hasReligiousTag(planSet, def);
   }

   @Nullable
   public static ResourceLocation findShrinePlan(Village village) {
      FaithDefinition def = resolve(village);
      if (def != null && def.preferredShrinePlan() != null) {
         BuildingPlanSet preferred = ModCultures.getBuildingPlanSet(def.preferredShrinePlan());
         if (preferred != null) {
            return def.preferredShrinePlan();
         }
      }
      VillageType type = ModCultures.getVillageType(village.getVillageTypeId());
      if (type == null) {
         return null;
      }
      for (VillageType.LayoutSlot slot : type.layout()) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(slot.plan());
         if (planSet == null) {
            continue;
         }
         if (hasReligiousTag(planSet, def)) {
            return slot.plan();
         }
      }
      return null;
   }

   @Nullable
   private static VillageType.LayoutSlot findLayoutSlot(VillageType type, ResourceLocation planId) {
      for (VillageType.LayoutSlot slot : type.layout()) {
         if (planId.equals(slot.plan())) {
            return slot;
         }
      }
      return null;
   }

   static boolean hasReligiousTag(BuildingPlanSet planSet, @Nullable FaithDefinition def) {
      for (String tag : planSet.tags()) {
         if (def != null && def.churchBuildingTags().contains(tag)) {
            return true;
         }
         if ("praying".equals(tag) || "mosque".equals(tag) || "pujas".equals(tag)
            || "sacrifices".equals(tag) || "religious".equals(tag)) {
            return true;
         }
      }
      return false;
   }

   /** No-op level param kept for call-site clarity at spawn. */
   public static void onVillageSpawned(ServerLevel level, Village village) {
      applyDefaultFaith(village);
      ensureWorshipInfrastructure(level, village);
   }
}
