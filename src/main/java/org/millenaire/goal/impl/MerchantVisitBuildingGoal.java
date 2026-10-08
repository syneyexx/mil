package org.millenaire.goal.impl;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.GoodAvailabilityHelper;
import org.millenaire.commerce.TradeGood;
import org.millenaire.commerce.TradeGoodsLoader;
import org.millenaire.culture.ModCultures;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerNavDriver;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TaskLabels;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.item.ItemHelper;
import org.millenaire.village.LocalMerchantHelper;
import org.millenaire.village.Village;
import org.slf4j.Logger;

public class MerchantVisitBuildingGoal implements VillagerGoal {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "visit_building");
   private static final int PRIORITY = 100;
   @Nullable
   private MerchantVisitBuildingGoal.TargetInfo cachedTarget;
   private long cachedTick = -1L;

   @Override
   public ResourceLocation id() {
      return ID;
   }

   @Override
   public int computePriority(GoalContext context) {
      return 100;
   }

   @Override
   public boolean canStart(GoalContext context) {
      this.cachedTarget = this.findDestination(context);
      this.cachedTick = context.level().getServer().getTickCount();
      return this.cachedTarget != null;
   }

   @Override
   public VillagerTask start(GoalContext context) {
      long currentTick = context.level().getServer().getTickCount();
      MerchantVisitBuildingGoal.TargetInfo target = this.cachedTarget != null && this.cachedTick == currentTick
         ? this.cachedTarget
         : this.findDestination(context);
      this.cachedTarget = null;
      return new MerchantVisitBuildingGoal.MerchantVisitBuildingTask(target != null ? target.building() : null, target != null ? target.pos() : null);
   }

   @Nullable
   private MerchantVisitBuildingGoal.TargetInfo findDestination(GoalContext ctx) {
      Village village = ctx.village();
      MillVillager villager = ctx.villager();
      List<TradeGood> goodsList = TradeGoodsLoader.getGoods(village.getCultureId());
      if (goodsList.isEmpty()) {
         return null;
      }

      BuildingInstance townhall = village.getTownhall();
      if (townhall == null) {
         return null;
      }

      for (TradeGood good : goodsList) {
         if (!good.isTag()) {
            Item item = ItemHelper.resolve(good.item());
            if (item != null && villager.getInventory().getCount(item) > 0 && GoodAvailabilityHelper.nbGoodNeeded(townhall, item, ctx.level(), village) > 0) {
               BlockPos sellingPos = townhall.resolveNavigationTarget("sellingPos", "sleepingPos");
               return new MerchantVisitBuildingGoal.TargetInfo(townhall, sellingPos);
            }
         }
      }

      Map<Item, Integer> neededGoods = LocalMerchantHelper.getImportsNeededByOtherVillages(ctx.level(), village);
      if (neededGoods.isEmpty()) {
         return null;
      }

      BuildingInstance home = ctx.resolveHomeBuilding().orElse(null);

      for (BuildingInstance shop : village.getBuildings()) {
         if (shop.isOperational()) {
            BuildingPlan shopPlan = ModCultures.getBuildingPlan(shop.getPlanId());
            if (shopPlan != null && shopPlan.shopId() != null && !shopPlan.hasTag("inn")) {
               BuildingInventory shopInv = shop.getInventory();
               if (shopInv != null) {
                  for (Entry<Item, Integer> entry : neededGoods.entrySet()) {
                     Item item = entry.getKey();
                     int shopAvailable = GoodAvailabilityHelper.nbGoodAvailable(
                        shop, item, ctx.level(), village, village.getCultureId(), false, false, true, null
                     );
                     if (shopAvailable > 0) {
                        int homeStock = home != null && home.getInventory() != null ? home.getInventory().getCount(ctx.level(), item) : 0;
                        int carriedCount = villager.getInventory().getCount(item);
                        if (homeStock + carriedCount < entry.getValue()) {
                           BlockPos shopPos = shop.resolveNavigationTarget("sellingPos", "sleepingPos");
                           return new MerchantVisitBuildingGoal.TargetInfo(shop, shopPos);
                        }
                     }
                  }
               }
            }
         }
      }

      return null;
   }

   static class MerchantVisitBuildingTask implements VillagerTask {
      private static final double ARRIVE_DISTANCE = 3.0;
      private static final double WALK_SPEED = 0.5;
      private static final String GOAL_KEY = "visit_building";
      @Nullable
      private final BuildingInstance targetBuilding;
      @Nullable
      private final BlockPos target;
      private boolean navStarted;
      private boolean arrived;
      private boolean actionDone;

      MerchantVisitBuildingTask(@Nullable BuildingInstance building, @Nullable BlockPos target) {
         this.targetBuilding = building;
         this.target = target;
         if (target == null) {
            this.arrived = true;
            this.actionDone = true;
         }
      }

      @Override
      public ResourceLocation goalId() {
         return MerchantVisitBuildingGoal.ID;
      }

      @Override
      public void tick(GoalContext ctx) {
         if (!this.arrived) {
            VillagerNavDriver nav = ctx.villager().getNavManager();
            if (!this.navStarted) {
               nav.navigateTo(ctx.villager(), this.target, 0.5);
               this.navStarted = true;
            }

            if (nav.isArrived(ctx.villager(), 3.0)) {
               nav.stop(ctx.villager());
               this.arrived = true;
            } else if (nav.isAbandoned()) {
               this.arrived = true;
               this.actionDone = true;
            }
         } else {
            if (!this.actionDone) {
               this.performAction(ctx);
               this.actionDone = true;
            }
         }
      }

      private void performAction(GoalContext ctx) {
         if (this.targetBuilding != null) {
            Village village = ctx.village();
            MillVillager villager = ctx.villager();
            BuildingPlan plan = ModCultures.getBuildingPlan(this.targetBuilding.getPlanId());
            if (plan != null) {
               if (!plan.hasTag("inn")) {
                  List<TradeGood> goodsList = TradeGoodsLoader.getGoods(village.getCultureId());
                  BuildingInventory shopInv = this.targetBuilding.getInventory();
                  if (shopInv != null) {
                     if (plan.hasTag("townhalls") || village.getTownhall() == this.targetBuilding) {
                        for (TradeGood good : goodsList) {
                           if (!good.isTag()) {
                              Item item = ItemHelper.resolve(good.item());
                              if (item != null) {
                                 int nbNeeded = GoodAvailabilityHelper.nbGoodNeeded(this.targetBuilding, item, ctx.level(), village);
                                 if (nbNeeded > 0) {
                                    int carried = villager.getInventory().getCount(item);
                                    if (carried > 0) {
                                       int toDeliver = Math.min(carried, nbNeeded);
                                       int stored = shopInv.add(ctx.level(), item, toDeliver);
                                       if (stored > 0) {
                                          villager.getInventory().remove(item, stored);
                                          MerchantVisitBuildingGoal.LOGGER
                                             .debug(
                                                "[Millenaire] Merchant {} delivered {} {} to TH", new Object[]{villager.getVillagerDisplayName(), stored, item}
                                             );
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }

                     Map<Item, Integer> neededGoods = LocalMerchantHelper.getImportsNeededByOtherVillages(ctx.level(), village);
                     BuildingInstance home = ctx.resolveHomeBuilding().orElse(null);

                     for (TradeGood good : goodsList) {
                        if (!good.isTag()) {
                           Item item = ItemHelper.resolve(good.item());
                           if (item != null && neededGoods.containsKey(item)) {
                              int shopAvailable = GoodAvailabilityHelper.nbGoodAvailable(
                                 this.targetBuilding, item, ctx.level(), village, village.getCultureId(), false, false, true, null
                              );
                              if (shopAvailable > 0) {
                                 int homeStock = home != null && home.getInventory() != null ? home.getInventory().getCount(ctx.level(), item) : 0;
                                 int carried = villager.getInventory().getCount(item);
                                 int needed = neededGoods.get(item);
                                 int toTake = Math.min(shopAvailable, needed - homeStock - carried);
                                 if (toTake > 0) {
                                    int taken = shopInv.remove(ctx.level(), item, toTake);
                                    if (taken > 0) {
                                       villager.getInventory().add(item, taken);
                                       MerchantVisitBuildingGoal.LOGGER
                                          .debug(
                                             "[Millenaire] Merchant {} picked up {} {} from shop", new Object[]{villager.getVillagerDisplayName(), taken, item}
                                          );
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      @Override
      public boolean isFinished() {
         return this.arrived && this.actionDone;
      }

      @Override
      public void stop(GoalContext ctx, StopReason reason) {
      }

      @Override
      public TravelPhase getTravelPhase() {
         return TaskLabels.phaseFor(this.arrived);
      }

      @Nullable
      @Override
      public Component getGoalLabel() {
         return TaskLabels.labelForPhase(this.arrived, "visit_building");
      }
   }

   private record TargetInfo(BuildingInstance building, BlockPos pos) {
   }
}
