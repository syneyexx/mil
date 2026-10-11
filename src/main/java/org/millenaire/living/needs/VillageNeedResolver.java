package org.millenaire.living.needs;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.millenaire.building.AnywoodHelper;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.ConstructionTask;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;
import org.millenaire.item.ItemHelper;
import org.millenaire.village.ControlledQueuedProject;
import org.millenaire.village.Village;
import org.millenaire.village.Village.PendingProject;

/**
 * Resolves shortages from authoritative construction / inventory state.
 */
public final class VillageNeedResolver {
   private VillageNeedResolver() {
   }

   @Nullable
   public static PendingProject resolveDisplayProject(Village village) {
      ControlledQueuedProject head = village.controlledQueueHead();
      if (head != null) {
         return new PendingProject(head.planSetId(), head.variant(), head.level(), false, null, head.plannedLocation());
      }
      return village.getPendingProject();
   }

   @Nullable
   public static ConstructionShortage resolveConstructionShortage(ServerLevel level, Village village) {
      PendingProject pending = resolveDisplayProject(village);
      BuildingInstance activeBuild = null;
      String projectName;
      String projectRef;
      Map<ResourceLocation, Integer> required;

      if (pending != null) {
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(pending.planSetId());
         projectName = planSet != null ? planSet.nativeName() : pending.planSetId().getPath();
         projectRef = "pending:" + pending.planSetId() + ":" + pending.variant() + ":" + pending.level();
         BuildingPlanSet.LevelDef levelDef = planSet != null ? planSet.getLevel(pending.variant(), pending.level()) : null;
         required = levelDef != null && levelDef.requiredResources() != null
            ? new LinkedHashMap<>(levelDef.requiredResources())
            : new LinkedHashMap<>();
      } else {
         for (BuildingInstance b : village.getBuildings()) {
            if (b.isBeingBuilt() && !b.isWallSegment() && b.getPlanSetId() != null) {
               activeBuild = b;
               break;
            }
         }
         if (activeBuild == null) {
            return null;
         }
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(activeBuild.getPlanSetId());
         projectName = planSet != null ? planSet.nativeName() : activeBuild.getPlanSetId().getPath();
         projectRef = "building:" + activeBuild.getId();
         BuildingPlanSet.LevelDef levelDef = planSet != null
            ? planSet.getLevel(activeBuild.getVariant(), activeBuild.getLevel())
            : null;
         required = levelDef != null && levelDef.requiredResources() != null
            ? new LinkedHashMap<>(levelDef.requiredResources())
            : new LinkedHashMap<>();
      }

      BuildingInstance townhall = village.getTownhall();
      if (townhall == null || townhall.getInventory() == null) {
         return new ConstructionShortage(projectRef, projectName, required, Map.of(), Map.of(), true, stallReason(activeBuild));
      }

      BuildingInventory inv = townhall.getInventory();
      inv.invalidateCacheOncePerTick(level.getGameTime());

      Map<ResourceLocation, Integer> available = new LinkedHashMap<>();
      Map<ResourceLocation, Integer> shortage = new LinkedHashMap<>();

      for (Entry<ResourceLocation, Integer> entry : required.entrySet()) {
         ResourceLocation key = entry.getKey();
         int need = Math.max(0, entry.getValue());
         int have;
         if (AnywoodHelper.isAnywood(key)) {
            have = inv.getCountByTag(level, AnywoodHelper.LOGS_TAG);
         } else {
            Item item = ItemHelper.resolve(key);
            have = item == null ? 0 : inv.getCount(level, item);
         }
         available.put(key, have);
         if (have < need) {
            shortage.put(key, need - have);
         }
      }

      String stall = stallReason(activeBuild);
      return new ConstructionShortage(projectRef, projectName, required, available, shortage, !shortage.isEmpty(), stall);
   }

   @Nullable
   private static String stallReason(@Nullable BuildingInstance building) {
      if (building == null) {
         return null;
      }
      ConstructionTask task = building.getConstructionTask();
      if (task == null) {
         return "no_task";
      }
      if (task.isComplete()) {
         return null;
      }
      if (!task.isReserved()) {
         return "no_builder";
      }
      if (task.getFailedAttempts() >= 3) {
         return "access_or_path";
      }
      return null;
   }

   public record ConstructionShortage(
      String projectRef,
      String projectName,
      Map<ResourceLocation, Integer> required,
      Map<ResourceLocation, Integer> available,
      Map<ResourceLocation, Integer> shortage,
      boolean hasShortage,
      @Nullable String stallReason
   ) {
   }
}
