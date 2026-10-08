package org.millenaire.goal.gathering.handler;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.GoodAvailabilityHelper;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.millenaire.item.ItemHelper;
import org.slf4j.Logger;

public class TakeFromBuildingHandler extends AbstractGatheringHandler {
   private static final Logger LOGGER = LogUtils.getLogger();

   @Override
   public String id() {
      return "take_from_building";
   }

   @Override
   public List<String> validate(GatheringType type) {
      List<String> errors = new ArrayList<>();
      if (this.getBuildingTag(type) == null) {
         errors.add("missing required handlerParam 'buildingTag'");
      }

      if (this.resolveCollectGood(type) == null) {
         String raw = GsonHelper.getAsString(type.handlerParams(), "collectGood", null);
         if (raw == null) {
            errors.add("missing required handlerParam 'collectGood'");
         } else {
            errors.add("unresolvable item in 'collectGood': " + raw);
         }
      }

      return errors;
   }

   @Override
   public boolean canStart(GoalContext ctx, GatheringType type) {
      String buildingTag = this.getBuildingTag(type);
      Item collectItem = this.resolveCollectGood(type);
      int minimumPickup = GsonHelper.getAsInt(type.handlerParams(), "minimumPickup", 4);
      int maxCollect = GsonHelper.getAsInt(type.handlerParams(), "maxCollect", 64);
      if (buildingTag != null && collectItem != null) {
         int currentlyHeld = ctx.villager().getInventory().getCount(collectItem);

         for (BuildingInstance building : this.findBuildingsWithTag(ctx.village(), buildingTag)) {
            if (this.pickupCount(ctx, building, collectItem, maxCollect, currentlyHeld) >= minimumPickup) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   @Nullable
   @Override
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      String buildingTag = this.getBuildingTag(type);
      Item collectItem = this.resolveCollectGood(type);
      int minimumPickup = GsonHelper.getAsInt(type.handlerParams(), "minimumPickup", 4);
      int maxCollect = GsonHelper.getAsInt(type.handlerParams(), "maxCollect", 64);
      if (buildingTag != null && collectItem != null) {
         BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : ctx.villager().blockPosition();
         BlockPos bestPos = null;
         double bestDistSq = Double.MAX_VALUE;
         int currentlyHeld = ctx.villager().getInventory().getCount(collectItem);

         for (BuildingInstance building : this.findBuildingsWithTag(ctx.village(), buildingTag)) {
            if (this.pickupCount(ctx, building, collectItem, maxCollect, currentlyHeld) >= minimumPickup) {
               BlockPos targetPos = this.resolveNavPos(building);
               if (targetPos != null) {
                  double distSq = reference.distSqr(targetPos);
                  if (distSq < bestDistSq) {
                     bestDistSq = distSq;
                     bestPos = targetPos;
                  }
               }
            }
         }

         return bestPos != null ? new GatheringTarget.BlockTarget(bestPos) : null;
      } else {
         return null;
      }
   }

   @Override
   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      String buildingTag = this.getBuildingTag(type);
      Item collectItem = this.resolveCollectGood(type);
      int maxCollect = GsonHelper.getAsInt(type.handlerParams(), "maxCollect", 64);
      if (buildingTag != null && collectItem != null) {
         BuildingInstance targetBuilding = null;

         for (BuildingInstance building : this.findBuildingsWithTag(ctx.village(), buildingTag)) {
            BlockPos navPos = this.resolveNavPos(building);
            if (navPos != null && navPos.equals(target.navigationPos())) {
               targetBuilding = building;
               break;
            }
         }

         if (targetBuilding == null) {
            return true;
         }

         BuildingInventory inv = targetBuilding.getInventory();
         if (inv == null) {
            return true;
         }

         ServerLevel level = ctx.level();
         int currentlyHeld = ctx.villager().getInventory().getCount(collectItem);
         int canTake = this.pickupCount(ctx, targetBuilding, collectItem, maxCollect, currentlyHeld);
         if (canTake <= 0) {
            return true;
         }

         int taken = inv.remove(level, collectItem, canTake);
         if (taken > 0) {
            ctx.villager().getInventory().add(collectItem, taken);
            ctx.villager().swing(InteractionHand.MAIN_HAND);
            LOGGER.debug("Took {} {} from building {} at {}", new Object[]{taken, collectItem, targetBuilding.getPlanId(), target.navigationPos()});
         }

         return true;
      } else {
         return true;
      }
   }

   @Nullable
   @Override
   public BuildingInstance resolveBuildingLimitTarget(GoalContext ctx, GatheringType type) {
      String buildingTag = this.getBuildingTag(type);
      if (buildingTag == null) {
         return null;
      }

      List<BuildingInstance> buildings = this.findBuildingsWithTag(ctx.village(), buildingTag);
      return buildings.isEmpty() ? null : buildings.getFirst();
   }

   private int pickupCount(GoalContext ctx, BuildingInstance building, Item collectItem, int maxCollect, int currentlyHeld) {
      int available = GoodAvailabilityHelper.nbGoodAvailable(building, collectItem, ctx.level(), ctx.village(), ctx.village().getCultureId(), false);
      return pickupCount(maxCollect, currentlyHeld, available);
   }

   static int pickupCount(int maxCollect, int currentlyHeld, int available) {
      int quantityNeeded = maxCollect - currentlyHeld;
      return quantityNeeded <= 0 ? 0 : Math.min(quantityNeeded, Math.max(available, 0));
   }

   @Nullable
   private Item resolveCollectGood(GatheringType type) {
      String itemId = GsonHelper.getAsString(type.handlerParams(), "collectGood", null);
      return itemId == null ? null : ItemHelper.resolve(itemId);
   }

   @Nullable
   private BlockPos resolveNavPos(BuildingInstance building) {
      BlockPos pos = building.getFirstPointPos("sellingPos");
      if (pos != null) {
         return pos;
      }

      pos = building.getFirstPointPos("craftingPos");
      return pos != null ? pos : building.getOrigin();
   }
}
