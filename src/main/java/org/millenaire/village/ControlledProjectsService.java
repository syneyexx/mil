package org.millenaire.village;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.BuildingPlanSet;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillageType;
import org.millenaire.language.BuildingNameHelper;
import org.millenaire.network.ControlledProjectsPayload;
import org.millenaire.village.panel.PanelContentGenerator;
import org.millenaire.world.PlacedLocation;

public final class ControlledProjectsService {
   private ControlledProjectsService() {
   }

   public static ControlledProjectsPayload buildPayload(Village village, ServerLevel level) {
      BlockPos center = village.getCenter();
      List<ControlledProjectsPayload.ProjectEntry> entries = new ArrayList<>();
      BuildingInstance townhall = village.getTownhall();

      for (BuildingInstance building : village.getBuildings()) {
         if (!building.isSubBuilding()) {
            BuildingPlanSet planSet = building.getPlanSetId() != null ? ModCultures.getBuildingPlanSet(building.getPlanSetId()) : null;
            if (planSet != null) {
               String variant = building.getVariant() != null ? building.getVariant() : "0";
               int maxLevel = planSet.getLevelCount(variant);
               if (maxLevel <= 0) {
                  maxLevel = 1;
               }

               String displayName = BuildingNameHelper.getTranslationKey(planSet);
               String distance = formatDistanceDirection(center, building.getOrigin());
               boolean isTownHall = townhall != null && townhall.getId().equals(building.getId());
               entries.add(
                  new ControlledProjectsPayload.ProjectEntry(
                     building.getId().uuid().toString(),
                     displayName,
                     planSet.id().toString(),
                     building.getLevel(),
                     maxLevel,
                     building.isUpgradesAllowed(),
                     distance,
                     isTownHall
                  )
               );
            }
         }
      }

      List<ControlledProjectsPayload.QueueEntry> queue = new ArrayList<>();
      List<ControlledQueuedProject> queued = village.getControlledQueue();

      for (int i = 0; i < queued.size(); i++) {
         ControlledQueuedProject project = queued.get(i);
         BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(project.planSetId());
         if (planSet != null) {
            String displayName = BuildingNameHelper.getTranslationKey(planSet);
            String distance = project.plannedLocation() != null ? formatDistanceDirection(center, project.plannedLocation().position()) : "";
            List<ControlledProjectsPayload.ResourceLine> resources = new ArrayList<>();
            BuildingPlanSet.LevelDef levelDef = planSet.getLevel(project.variant(), project.level());
            if (levelDef != null && levelDef.requiredResources() != null) {
               for (PanelContentGenerator.ResourceLineInfo info : PanelContentGenerator.computeResourceLines(
                  levelDef.requiredResources(), village, level, false
               )) {
                  String itemId = info.itemId() != null ? info.itemId() : "";
                  resources.add(
                     info.descId() != null
                        ? new ControlledProjectsPayload.ResourceLine(info.descId(), true, itemId, info.have(), info.need())
                        : new ControlledProjectsPayload.ResourceLine(info.name(), false, itemId, info.have(), info.need())
                  );
               }
            }

            BlockPos planned = project.plannedLocation() != null ? project.plannedLocation().position() : BlockPos.ZERO;
            queue.add(
               new ControlledProjectsPayload.QueueEntry(
                  i, displayName, planSet.id().toString(), planned.getX(), planned.getY(), planned.getZ(), distance, resources
               )
            );
         }
      }

      String villageName = village.getVillageName() != null ? village.getVillageName() : "";
      return new ControlledProjectsPayload(village.getId().uuid().toString(), villageName, entries, queue);
   }

   @Nullable
   public static VillageType.LayoutSlot findOfferedSlot(@Nullable VillageType vt, ResourceLocation planSetLoc, BuildingPlanSet planSet) {
      if (vt != null && !planSet.isTownHall()) {
         for (VillageType.LayoutSlot s : vt.layout()) {
            if (planSetLoc.equals(s.plan()) && !"centre".equals(s.role()) && !"start".equals(s.role())) {
               return s;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   @Nullable
   public static ControlledProjectsService.PlannedFootprint resolveBuiltFootprint(BuildingInstance building) {
      if (!building.isSubBuilding() && building.getEffectiveWidth() > 0) {
         int ox = building.getOrigin().getX();
         int oz = building.getOrigin().getZ();
         BuildingPlan plan = building.getPlanId() != null ? ModCultures.getBuildingPlan(building.getPlanId()) : null;
         int surfaceY = plan != null ? building.getOrigin().getY() - plan.groundLevel() : building.getOrigin().getY();
         return new ControlledProjectsService.PlannedFootprint(
            new PlacedLocation.Bounds(
               ox + building.getCachedMinX(), oz + building.getCachedMinZ(), ox + building.getCachedMaxX() + 1, oz + building.getCachedMaxZ() + 1
            ),
            surfaceY
         );
      } else {
         return null;
      }
   }

   @Nullable
   public static ControlledProjectsService.PlannedFootprint resolvePlannedFootprint(
      ResourceLocation planSetId, String variant, int level, @Nullable PlacedLocation planned
   ) {
      if (planned == null) {
         return null;
      }

      BuildingPlanSet planSet = ModCultures.getBuildingPlanSet(planSetId);
      if (planSet == null) {
         return null;
      }

      BuildingPlanSet.LevelDef levelDef = planSet.getLevel(variant, level);
      BuildingPlan plan = levelDef != null ? ModCultures.getBuildingPlan(levelDef.planId()) : null;
      if (plan == null) {
         return null;
      }

      PlacedLocation.Bounds bounds = PlacedLocation.worldBoundsForPivot(
         planned.position().getX(), planned.position().getZ(), plan.width(), plan.depth(), planned.rotation()
      );
      return new ControlledProjectsService.PlannedFootprint(bounds, planned.position().getY() - plan.groundLevel());
   }

   public static String formatDistanceDirection(BlockPos origin, BlockPos target) {
      int dx = target.getX() - origin.getX();
      int dz = target.getZ() - origin.getZ();
      int dist = (int)Math.sqrt(dx * dx + dz * dz);
      String dir;
      if (dx == 0 && dz == 0) {
         dir = "";
      } else {
         double angle = Math.atan2(-dx, -dz);
         int sector = (int)Math.round(angle / (Math.PI / 4));

         dir = switch (Math.floorMod(sector, 8)) {
            case 0 -> "N";
            case 1 -> "NE";
            case 2 -> "E";
            case 3 -> "SE";
            case 4 -> "S";
            case 5 -> "SW";
            case 6 -> "W";
            case 7 -> "NW";
            default -> "";
         };
      }

      return dir.isEmpty() ? dist + "m" : dist + "m " + dir;
   }

   public record PlannedFootprint(PlacedLocation.Bounds bounds, int surfaceY) {
   }
}
