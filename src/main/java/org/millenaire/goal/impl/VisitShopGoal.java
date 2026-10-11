package org.millenaire.goal.impl;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingPurpose;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.village.PlayerShopService;
import org.millenaire.village.Village;

/** Residents / merchants visit a designated SHOP and buy one item. */
public class VisitShopGoal implements VillagerGoal {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "visit_shop");

   @Override
   public ResourceLocation id() {
      return ID;
   }

   @Override
   public int computePriority(GoalContext context) {
      return 40;
   }

   @Override
   public boolean canStart(GoalContext context) {
      if (!PlayerShopService.isEnabled()) {
         return false;
      }
      Village village = context.village();
      MillVillager v = context.villager();
      if (village == null || v.isRaiderEntity()) {
         return false;
      }
      VillagerType type = ModCultures.getVillagerType(v.getVillagerTypeId());
      if (type != null && (type.isHostile() || type.isChild())) {
         return false;
      }
      // Throttle: ~every ~2 minutes of goal evaluation chance
      if (v.tickCount % 2400 > 40) {
         return false;
      }
      BuildingInstance shop = PlayerShopService.findShop(village);
      return shop != null && PlayerShopService.shopPos(shop) != null;
   }

   @Override
   public VillagerTask start(GoalContext context) {
      BuildingInstance shop = PlayerShopService.findShop(context.village());
      return new VisitShopTask(shop, PlayerShopService.shopPos(shop));
   }

   static class VisitShopTask implements VillagerTask {
      @Nullable
      private final BuildingInstance shop;
      @Nullable
      private final BlockPos pos;
      private boolean finished;
      private boolean purchased;

      VisitShopTask(@Nullable BuildingInstance shop, @Nullable BlockPos pos) {
         this.shop = shop;
         this.pos = pos;
      }

      @Override
      public ResourceLocation goalId() {
         return VisitShopGoal.ID;
      }

      @Override
      public void tick(GoalContext ctx) {
         MillVillager v = ctx.villager();
         if (this.shop == null || this.pos == null || this.shop.getPurpose() != BuildingPurpose.SHOP) {
            this.finished = true;
            return;
         }
         if (v.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) > 9.0) {
            v.getNavManager().navigateTo(v, this.pos, 0.55);
            return;
         }
         v.getNavManager().stop(v);
         if (!this.purchased && ctx.level() instanceof ServerLevel level) {
            PlayerShopService.tryPurchase(level, ctx.village(), v);
            this.purchased = true;
            if (v.isRoadShopTraveler()) {
               v.discard();
            }
         }
         this.finished = true;
      }

      @Override
      public boolean isFinished() {
         return this.finished;
      }

      @Override
      public void stop(GoalContext ctx, StopReason reason) {
         if (ctx != null) {
            ctx.villager().getNavManager().stop(ctx.villager());
         }
      }
   }
}
