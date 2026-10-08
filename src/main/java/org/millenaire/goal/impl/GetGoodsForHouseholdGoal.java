package org.millenaire.goal.impl;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.GoodAvailabilityHelper;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.VillagerInventory;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.village.VillagerRecord;
import org.slf4j.Logger;

public class GetGoodsForHouseholdGoal implements VillagerGoal {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "get_goods_for_household");
   private static final int STANDARD_DELAY = 2000;
   private static final int IMMEDIATE_THRESHOLD = 16;
   private static final double BLOCKS_PER_DEFICIT_UNIT = 16.0;
   private final TransportThrottle throttle = new TransportThrottle(2000);

   @Override
   public boolean showInTravelBook() {
      return false;
   }

   @Override
   public ResourceLocation id() {
      return ID;
   }

   @Override
   public int computePriority(GoalContext context) {
      Map<Item, Integer> allGoods = collectHouseholdRequiredGoods(context);
      int nbMissing = this.countMissingGoods(context, allGoods);
      return nbMissing * 20;
   }

   @Override
   public boolean canStart(GoalContext context) {
      if (context.villager().getHomeBuilding() == null) {
         return false;
      } else {
         Map<Item, Integer> allRequiredGoods = collectHouseholdRequiredGoods(context);
         if (allRequiredGoods.isEmpty()) {
            return false;
         } else {
            int nbMissing = this.countMissingGoods(context, allRequiredGoods);
            if (!this.throttle.allowStart(context.villager().getUUID(), context.gameTime(), nbMissing, 16)) {
               return false;
            } else {
               return nbMissing > 0 ? true : this.isCarryingNeededHouseholdGoods(context, allRequiredGoods);
            }
         }
      }
   }

   private boolean isCarryingNeededHouseholdGoods(GoalContext context, Map<Item, Integer> requiredGoods) {
      BuildingId homeId = context.villager().getHomeBuilding();
      BuildingInstance home = homeId != null ? context.village().getBuilding(homeId) : null;
      if (home != null && home.getInventory() != null) {
         VillagerInventory villagerInv = context.villager().getInventory();
         BuildingInventory homeInv = home.getInventory();

         for (Entry<Item, Integer> entry : requiredGoods.entrySet()) {
            int inHome = homeInv.getCount(context.level(), entry.getKey());
            if (inHome < entry.getValue() && villagerInv.getCount(entry.getKey()) > 0) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   static Map<Item, Integer> collectHouseholdRequiredGoods(GoalContext context) {
      BuildingId homeId = context.villager().getHomeBuilding();
      if (homeId == null) {
         return Map.of();
      }

      Map<Item, Integer> allGoods = new HashMap<>();

      for (VillagerRecord record : context.village().getVillagerRecords().values()) {
         BuildingId vHome = record.getHomeBuilding();
         if (vHome != null && vHome.equals(homeId)) {
            VillagerType vtype = ModCultures.getVillagerType(record.getVillagerTypeId());
            if (vtype != null) {
               for (Entry<Item, Integer> entry : vtype.resolvedRequiredGoods().entrySet()) {
                  allGoods.merge(entry.getKey(), entry.getValue(), Math::max);
               }
            }
         }
      }

      return allGoods;
   }

   @Override
   public VillagerTask start(GoalContext context) {
      this.throttle.recordStart(context.villager().getUUID(), context.gameTime());
      List<GetGoodsForHouseholdGoal.ItemAmount> itemsToGet = this.computeItemsToGet(context);
      return new GetGoodsForHouseholdGoal.GetGoodsForHouseholdTask(itemsToGet, this.throttle);
   }

   private int countMissingGoods(GoalContext context, Map<Item, Integer> requiredGoods) {
      BuildingId homeId = context.villager().getHomeBuilding();
      BuildingInstance home = homeId != null ? context.village().getBuilding(homeId) : null;
      if (home != null && home.getInventory() != null) {
         BuildingInventory homeInv = home.getInventory();
         int missing = 0;

         for (Entry<Item, Integer> entry : requiredGoods.entrySet()) {
            Item item = entry.getKey();
            int required = entry.getValue();
            int inHome = homeInv.getCount(context.level(), item);
            if (inHome < required / 2) {
               for (BuildingInstance building : context.village().getBuildings()) {
                  if (building.isOperational() && !building.getId().equals(homeId) && building.getInventory() != null) {
                     int available = GoodAvailabilityHelper.nbGoodAvailable(
                        building, item, context.level(), context.village(), context.village().getCultureId(), false
                     );
                     if (available > 0) {
                        missing += Math.min(required - inHome, available);
                        break;
                     }
                  }
               }
            }
         }

         return missing;
      } else {
         return 0;
      }
   }

   private List<GetGoodsForHouseholdGoal.ItemAmount> computeItemsToGet(GoalContext context) {
      Map<Item, Integer> allGoods = collectHouseholdRequiredGoods(context);
      if (allGoods.isEmpty()) {
         return List.of();
      }

      BuildingId homeId = context.villager().getHomeBuilding();
      BuildingInstance home = homeId != null ? context.village().getBuilding(homeId) : null;
      if (home != null && home.getInventory() != null) {
         BuildingInventory homeInv = home.getInventory();
         VillagerInventory villagerInv = context.villager().getInventory();
         Map<Item, Integer> needed = new LinkedHashMap<>();

         for (Entry<Item, Integer> entry : allGoods.entrySet()) {
            Item item = entry.getKey();
            int required = entry.getValue();
            int inHome = homeInv.getCount(context.level(), item);
            if (inHome < required / 2) {
               int deficit = required - inHome - villagerInv.getCount(item);
               if (deficit > 0) {
                  needed.put(item, deficit);
               }
            }
         }

         if (needed.isEmpty()) {
            return List.of();
         }

         List<GetGoodsForHouseholdGoal.ItemAmount> bestFromBuilding = List.of();
         double bestScore = Double.NEGATIVE_INFINITY;

         for (BuildingInstance building : context.village().getBuildings()) {
            if (building.isOperational() && !building.getId().equals(homeId) && building.getInventory() != null) {
               List<GetGoodsForHouseholdGoal.ItemAmount> fromThisBuilding = new ArrayList<>();
               int value = 0;

               for (Entry<Item, Integer> ne : needed.entrySet()) {
                  int available = GoodAvailabilityHelper.nbGoodAvailable(
                     building, ne.getKey(), context.level(), context.village(), context.village().getCultureId(), false
                  );
                  if (available > 0) {
                     int toTake = Math.min(ne.getValue(), available);
                     fromThisBuilding.add(new GetGoodsForHouseholdGoal.ItemAmount(ne.getKey(), toTake, building.getId()));
                     value += toTake;
                  }
               }

               if (value != 0) {
                  double score = value - travelPenalty(building, home);
                  if (score > bestScore) {
                     bestScore = score;
                     bestFromBuilding = fromThisBuilding;
                  }
               }
            }
         }

         return bestFromBuilding;
      } else {
         return List.of();
      }
   }

   private static double travelPenalty(BuildingInstance building, BuildingInstance home) {
      return Math.sqrt(building.getSellingPos().distSqr(home.getSellingPos())) / 16.0;
   }

   static class GetGoodsForHouseholdTask extends AbstractTransportTask {
      private static final int HOUSEHOLD_ACTION_DURATION = 10;
      private final List<GetGoodsForHouseholdGoal.ItemAmount> itemsToGet;
      private final TransportThrottle throttle;
      @Nullable
      private final BuildingId sourceBuildingId;

      GetGoodsForHouseholdTask(List<GetGoodsForHouseholdGoal.ItemAmount> itemsToGet, TransportThrottle throttle) {
         this.itemsToGet = itemsToGet;
         this.throttle = throttle;
         this.sourceBuildingId = itemsToGet.isEmpty() ? null : itemsToGet.getFirst().sourceBuilding();
         if (itemsToGet.isEmpty()) {
            this.state = AbstractTransportTask.State.WALKING_TO_DEST;
         }
      }

      @Override
      public ResourceLocation goalId() {
         return GetGoodsForHouseholdGoal.ID;
      }

      @Nullable
      @Override
      protected BuildingInstance resolveSourceBuilding(GoalContext ctx) {
         return this.sourceBuildingId != null ? ctx.village().getBuilding(this.sourceBuildingId) : null;
      }

      @Nullable
      @Override
      protected BuildingInstance resolveDestBuilding(GoalContext ctx) {
         BuildingId homeId = ctx.villager().getHomeBuilding();
         return homeId != null ? ctx.village().getBuilding(homeId) : null;
      }

      @Override
      protected List<GetGoodsForHouseholdGoal.ItemAmount> getItemsToTransfer() {
         return this.itemsToGet;
      }

      @Override
      protected String pickupLogLabel() {
         return "for household";
      }

      @Override
      protected String deliveryLogLabel() {
         return "to household";
      }

      @Override
      protected void tickPickingUp(GoalContext ctx) {
         this.actionTicks++;
         if (this.actionTicks >= 10) {
            VillagerInventory villagerInv = ctx.villager().getInventory();
            int picked = 0;

            for (GetGoodsForHouseholdGoal.ItemAmount ia : this.itemsToGet) {
               BuildingInstance source = ctx.village().getBuilding(ia.sourceBuilding());
               if (source != null && source.getInventory() != null) {
                  int available = GoodAvailabilityHelper.nbGoodAvailable(source, ia.item, ctx.level(), ctx.village(), ctx.village().getCultureId(), false);
                  int toTake = Math.min(ia.count, available);
                  if (toTake > 0) {
                     int removed = source.getInventory().remove(ctx.level(), ia.item, toTake);
                     if (removed > 0) {
                        villagerInv.add(ia.item, removed);
                        picked += removed;
                     }
                  }
               }
            }

            if (picked > 0) {
               this.hasPickedUpGoods = true;
               this.reportProgress();
            }

            this.state = AbstractTransportTask.State.WALKING_TO_DEST;
            this.actionTicks = 0;
         }
      }

      @Override
      protected void tickDelivering(GoalContext ctx) {
         this.actionTicks++;
         if (this.actionTicks >= 10) {
            BuildingInstance dest = this.resolveDestBuilding(ctx);
            if (dest != null && dest.getInventory() != null) {
               VillagerInventory villagerInv = ctx.villager().getInventory();
               BuildingInventory destInv = dest.getInventory();
               int delivered = 0;
               boolean hadGoodsToDeliver = false;

               for (GetGoodsForHouseholdGoal.ItemAmount ia : this.itemsToGet) {
                  int has = villagerInv.getCount(ia.item);
                  if (has > 0) {
                     hadGoodsToDeliver = true;
                     int added = destInv.add(ctx.level(), ia.item, has);
                     if (added > 0) {
                        villagerInv.remove(ia.item, added);
                        delivered += added;
                     }
                  }
               }

               Map<Item, Integer> allGoods = this.collectHouseholdRequiredGoods(ctx);

               for (Entry<Item, Integer> entry : allGoods.entrySet()) {
                  Item item = entry.getKey();
                  boolean alreadyHandled = false;

                  for (GetGoodsForHouseholdGoal.ItemAmount ia : this.itemsToGet) {
                     if (ia.item.equals(item)) {
                        alreadyHandled = true;
                        break;
                     }
                  }

                  if (!alreadyHandled) {
                     int has = villagerInv.getCount(item);
                     if (has > 0) {
                        hadGoodsToDeliver = true;
                        int added = destInv.add(ctx.level(), item, has);
                        if (added > 0) {
                           villagerInv.remove(item, added);
                           delivered += added;
                        }
                     }
                  }
               }

               if (delivered > 0) {
                  GetGoodsForHouseholdGoal.LOGGER.debug("[Millenaire] {} delivered {} items to household", ctx.villager().getVillagerTypeId(), delivered);
                  this.reportProgress();
               } else if (hadGoodsToDeliver) {
                  this.throttle.recordFailedTransfer(ctx.villager().getUUID(), ctx.gameTime());
                  GetGoodsForHouseholdGoal.LOGGER
                     .debug("[Millenaire] {} could not deliver to household (chests full) — throttled", ctx.villager().getVillagerTypeId());
               }

               if (!stillCarryingAny(villagerInv, this.itemsToGet)) {
                  this.hasPickedUpGoods = false;
               }

               this.state = AbstractTransportTask.State.DONE;
            } else {
               this.state = AbstractTransportTask.State.DONE;
            }
         }
      }

      private Map<Item, Integer> collectHouseholdRequiredGoods(GoalContext context) {
         return GetGoodsForHouseholdGoal.collectHouseholdRequiredGoods(context);
      }

      @Nullable
      @Override
      public Component getGoalLabel() {
         return switch (this.state) {
            case WALKING_TO_SOURCE -> Component.translatable("goal.millenaire.get_goods_household.to_townhall");
            case PICKING_UP -> Component.translatable("goal.millenaire.get_goods_household.picking_up");
            case WALKING_TO_DEST -> Component.translatable("goal.millenaire.get_goods_household.to_home");
            case DELIVERING -> Component.translatable("goal.millenaire.get_goods_household.delivering");
            case DONE -> null;
         };
      }
   }

   record ItemAmount(Item item, int count, BuildingId sourceBuilding) implements AbstractTransportTask.ItemRef {
   }
}
