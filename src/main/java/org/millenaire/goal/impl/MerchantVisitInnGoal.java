package org.millenaire.goal.impl;

import com.mojang.logging.LogUtils;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.GoodAvailabilityHelper;
import org.millenaire.commerce.TradeGood;
import org.millenaire.commerce.TradeGoodsLoader;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.VillagerNavDriver;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.PerVillagerThrottle;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TaskLabels;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.item.ItemHelper;
import org.millenaire.village.Village;
import org.slf4j.Logger;

public class MerchantVisitInnGoal implements VillagerGoal {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "visit_inn");
   private static final int PRIORITY = 100;
   private static final int STANDARD_DELAY = 2000;
   private final PerVillagerThrottle throttle = new PerVillagerThrottle(2000, 24000L, 6000L);

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
      long currentTick = context.level().getServer().getTickCount();
      boolean delayOver = !this.throttle.isThrottled(context.villager().getUUID(), currentTick);
      Village village = context.village();
      MillVillager villager = context.villager();
      List<TradeGood> goodsList = TradeGoodsLoader.getGoods(village.getCultureId());
      if (goodsList.isEmpty()) {
         return false;
      }

      if (village.getTownhall() == null) {
         return false;
      }

      BuildingInstance home = context.resolveHomeBuilding().orElse(null);
      if (home == null) {
         return false;
      }

      int totalExportGoods = 0;

      for (Item item : villager.getInventory().getAll().keySet()) {
         int carried = villager.getInventory().getCount(item);
         if (carried > 0 && nbGoodNeeded(context.level(), village, item) == 0) {
            totalExportGoods += carried;
            if (delayOver) {
               return true;
            }

            if (totalExportGoods > 64) {
               return true;
            }
         }
      }

      BuildingInventory innInv = home.getInventory();
      if (innInv != null) {
         for (TradeGood good : goodsList) {
            if (!good.isTag()) {
               Item item = ItemHelper.resolve(good.item());
               if (item != null) {
                  int innStock = innInv.getCount(context.level(), item);
                  if (innStock > 0) {
                     int carried = villager.getInventory().getCount(item);
                     if (carried < nbGoodNeeded(context.level(), village, item)) {
                        return true;
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   @Override
   public VillagerTask start(GoalContext context) {
      this.throttle.record(context.villager().getUUID(), context.level().getServer().getTickCount());
      BuildingInstance home = context.resolveHomeBuilding().orElse(null);
      BlockPos target = home != null ? home.resolveNavigationTarget("sellingPos", "sleepingPos") : null;
      return new MerchantVisitInnGoal.MerchantVisitInnTask(home, target);
   }

   private static int nbGoodNeeded(ServerLevel level, Village village, Item item) {
      return GoodAvailabilityHelper.nbGoodNeeded(level, village, item);
   }

   static class MerchantVisitInnTask implements VillagerTask {
      private static final double ARRIVE_DISTANCE = 3.0;
      private static final double WALK_SPEED = 0.5;
      private static final String GOAL_KEY = "visit_inn";
      @Nullable
      private final BuildingInstance innBuilding;
      @Nullable
      private final BlockPos target;
      private boolean navStarted;
      private boolean arrived;
      private boolean actionDone;

      MerchantVisitInnTask(@Nullable BuildingInstance inn, @Nullable BlockPos target) {
         this.innBuilding = inn;
         this.target = target;
         if (target == null) {
            this.arrived = true;
            this.actionDone = true;
         }
      }

      @Override
      public ResourceLocation goalId() {
         return MerchantVisitInnGoal.ID;
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
         if (this.innBuilding != null) {
            Village village = ctx.village();
            MillVillager villager = ctx.villager();
            BuildingInventory innInv = this.innBuilding.getInventory();
            if (innInv != null) {
               StringBuilder exportLog = new StringBuilder();

               for (Item item : villager.getInventory().getAll().keySet().toArray(new Item[0])) {
                  int carried = villager.getInventory().getCount(item);
                  if (carried > 0 && MerchantVisitInnGoal.nbGoodNeeded(ctx.level(), village, item) == 0) {
                     int stored = innInv.add(ctx.level(), item, carried);
                     if (stored > 0) {
                        villager.getInventory().remove(item, stored);
                        if (!exportLog.isEmpty()) {
                           exportLog.append(";");
                        }

                        exportLog.append(BuiltInRegistries.ITEM.getKey(item)).append("/").append(stored);
                     }
                  }
               }

               if (!exportLog.isEmpty()) {
                  String merchantName = villager.getVillagerDisplayName();
                  this.innBuilding.addVisitorLog("storedexports;" + merchantName + ";" + exportLog);
                  MerchantVisitInnGoal.LOGGER.debug("[Millenaire] Merchant {} stored exports at inn: {}", merchantName, exportLog);
               }

               StringBuilder importLog = new StringBuilder();

               for (TradeGood good : TradeGoodsLoader.getGoods(village.getCultureId())) {
                  if (!good.isTag()) {
                     Item item = ItemHelper.resolve(good.item());
                     if (item != null) {
                        int needed = MerchantVisitInnGoal.nbGoodNeeded(ctx.level(), village, item);
                        int carried = villager.getInventory().getCount(item);
                        if (carried < needed) {
                           int toTake = needed - carried;
                           int taken = innInv.remove(ctx.level(), item, toTake);
                           if (taken > 0) {
                              villager.getInventory().add(item, taken);
                              if (!importLog.isEmpty()) {
                                 importLog.append(";");
                              }

                              importLog.append(BuiltInRegistries.ITEM.getKey(item)).append("/").append(taken);
                           }
                        }
                     }
                  }
               }

               if (!importLog.isEmpty()) {
                  String merchantName = villager.getVillagerDisplayName();
                  this.innBuilding.addVisitorLog("broughtimport;" + merchantName + ";" + importLog);
                  MerchantVisitInnGoal.LOGGER.debug("[Millenaire] Merchant {} took imports from inn: {}", merchantName, importLog);
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
         return TaskLabels.labelForPhase(this.arrived, "visit_inn");
      }
   }
}
