package org.millenaire.goal.impl;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.building.BuildingPlan;
import org.millenaire.building.GoodAvailabilityHelper;
import org.millenaire.commerce.ShopProfile;
import org.millenaire.commerce.ShopProfileLoader;
import org.millenaire.culture.ModCultures;
import org.millenaire.entity.VillagerInventory;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.item.ItemHelper;
import org.slf4j.Logger;

public class DeliverResourcesToShopGoal implements VillagerGoal {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "deliver_resources_shop");
   private static final int STANDARD_DELAY = 2000;
   private static final int IMMEDIATE_THRESHOLD = 16;
   private final TransportThrottle throttle = new TransportThrottle(2000);

   @Override
   public ResourceLocation id() {
      return ID;
   }

   @Override
   public int computePriority(GoalContext context) {
      VillagerInventory inv = context.villager().getInventory();
      int priority = 0;
      ResourceLocation cultureId = context.village().getCultureId();

      for (BuildingInstance shop : context.village().getBuildings()) {
         if (shop.isOperational()) {
            BuildingPlan plan = ModCultures.getBuildingPlan(shop.getPlanId());
            if (plan != null && plan.shopId() != null) {
               ShopProfile profile = ShopProfileLoader.getProfile(cultureId, plan.shopId());
               if (profile != null && !profile.deliverTo().isEmpty()) {
                  for (String itemName : profile.deliverTo()) {
                     Item item = ItemHelper.resolve(itemName);
                     if (item != null) {
                        priority += inv.getCount(item) * 10;
                     }
                  }
               }
            }
         }
      }

      return priority;
   }

   @Override
   public boolean canStart(GoalContext context) {
      if (context.resolveHomeBuilding().isEmpty()) {
         return false;
      }

      DeliverResourcesToShopGoal.DeliveryTarget target = this.findDeliveryTarget(context);
      return target == null ? false : this.throttle.allowStart(context.villager().getUUID(), context.gameTime(), target.totalCount, 16);
   }

   @Override
   public VillagerTask start(GoalContext context) {
      this.throttle.recordStart(context.villager().getUUID(), context.gameTime());
      DeliverResourcesToShopGoal.DeliveryTarget target = this.findDeliveryTarget(context);
      return target == null
         ? new DeliverResourcesToShopGoal.DeliverTask(null, List.of(), this.throttle)
         : new DeliverResourcesToShopGoal.DeliverTask(target.shopId, target.items, this.throttle);
   }

   @Nullable
   private DeliverResourcesToShopGoal.DeliveryTarget findDeliveryTarget(GoalContext context) {
      VillagerInventory inv = context.villager().getInventory();
      ResourceLocation cultureId = context.village().getCultureId();
      BuildingInstance townhall = context.village().getTownhall();
      BuildingId townhallId = townhall != null ? townhall.getId() : null;
      List<DeliverResourcesToShopGoal.DeliveryTarget> candidates = new ArrayList<>();

      for (BuildingInstance shop : context.village().getBuildings()) {
         if (shop.isOperational()) {
            BuildingPlan plan = ModCultures.getBuildingPlan(shop.getPlanId());
            if (plan != null && plan.shopId() != null) {
               ShopProfile profile = ShopProfileLoader.getProfile(cultureId, plan.shopId());
               if (profile != null && !profile.deliverTo().isEmpty()) {
                  List<DeliverResourcesToShopGoal.ItemCount> carriedItems = new ArrayList<>();
                  int totalCount = 0;

                  for (String itemName : profile.deliverTo()) {
                     Item item = ItemHelper.resolve(itemName);
                     if (item != null) {
                        int count = inv.getCount(item);
                        if (count > 0) {
                           carriedItems.add(new DeliverResourcesToShopGoal.ItemCount(item, count));
                           totalCount += count;
                        }
                     }
                  }

                  if (!carriedItems.isEmpty()) {
                     candidates.add(new DeliverResourcesToShopGoal.DeliveryTarget(shop.getId(), carriedItems, totalCount));
                  }
               }
            }
         }
      }

      return preferNonTownhall(candidates, t -> townhallId != null && townhallId.equals(t.shopId()));
   }

   @Nullable
   static <T> T preferNonTownhall(List<T> candidates, Predicate<T> isTownhall) {
      T townhallFallback = null;

      for (T c : candidates) {
         if (!isTownhall.test(c)) {
            return c;
         }

         if (townhallFallback == null) {
            townhallFallback = c;
         }
      }

      return townhallFallback;
   }

   static class DeliverTask extends WalkThenActTask {
      @Nullable
      private final BuildingId shopId;
      private final List<DeliverResourcesToShopGoal.ItemCount> itemsToDeliver;
      private final TransportThrottle throttle;

      DeliverTask(@Nullable BuildingId shopId, List<DeliverResourcesToShopGoal.ItemCount> itemsToDeliver, TransportThrottle throttle) {
         this.shopId = shopId;
         this.itemsToDeliver = itemsToDeliver;
         this.throttle = throttle;
         if (shopId == null || itemsToDeliver.isEmpty()) {
            this.phase = WalkThenActTask.Phase.DONE;
         }
      }

      @Override
      public ResourceLocation goalId() {
         return DeliverResourcesToShopGoal.ID;
      }

      @Nullable
      @Override
      protected BlockPos resolveTarget(GoalContext ctx) {
         BuildingInstance shop = this.shopId != null ? ctx.village().getBuilding(this.shopId) : null;
         return shop != null ? shop.getSellingPos() : null;
      }

      @Override
      protected void performAction(GoalContext ctx) {
         BuildingInstance shop = this.shopId != null ? ctx.village().getBuilding(this.shopId) : null;
         if (shop != null && shop.getInventory() != null) {
            VillagerInventory villagerInv = ctx.villager().getInventory();
            BuildingInventory shopInv = shop.getInventory();
            int delivered = 0;
            boolean hadGoodsToDeliver = false;

            for (DeliverResourcesToShopGoal.ItemCount ic : this.itemsToDeliver) {
               int has = villagerInv.getCount(ic.item);
               if (has > 0) {
                  hadGoodsToDeliver = true;
                  int demand = GoodAvailabilityHelper.getSinkRequiredGoods(shop, ic.item, ctx.village());
                  int currentStock = shopInv.getCount(ctx.level(), ic.item);
                  int cap = GoodAvailabilityHelper.remainingSinkDemand(demand, currentStock, 256);
                  int toDeliver = Math.min(has, cap);
                  if (toDeliver > 0) {
                     int added = shopInv.add(ctx.level(), ic.item, toDeliver);
                     if (added > 0) {
                        villagerInv.remove(ic.item, added);
                        delivered += added;
                     }
                  }
               }
            }

            if (delivered > 0) {
               DeliverResourcesToShopGoal.LOGGER.debug("[Millenaire] {} delivered {} items to shop", ctx.villager().getVillagerTypeId(), delivered);
               this.reportProgress();
            } else if (hadGoodsToDeliver) {
               this.throttle.recordFailedTransfer(ctx.villager().getUUID(), ctx.gameTime());
               DeliverResourcesToShopGoal.LOGGER
                  .debug("[Millenaire] {} could not deliver to shop (chests full) — throttled", ctx.villager().getVillagerTypeId());
            }
         }
      }

      @Override
      public List<ItemStack> getHeldItems(TravelPhase phase) {
         if (phase != TravelPhase.TRAVELLING) {
            return List.of();
         }

         List<ItemStack> items = new ArrayList<>();

         for (DeliverResourcesToShopGoal.ItemCount ic : this.itemsToDeliver) {
            items.add(new ItemStack(ic.item, 1));
         }

         return items;
      }

      @Nullable
      @Override
      public Component getGoalLabel() {
         return switch (this.phase) {
            case WALKING -> Component.translatable("goal.millenaire.transport.to_dest");
            case ACTING -> Component.translatable("goal.millenaire.transport.delivering");
            case DONE -> null;
         };
      }
   }

   private record DeliveryTarget(BuildingId shopId, List<DeliverResourcesToShopGoal.ItemCount> items, int totalCount) {
   }

   private record ItemCount(Item item, int count) {
   }
}
