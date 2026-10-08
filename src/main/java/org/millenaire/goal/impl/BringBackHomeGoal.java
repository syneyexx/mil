package org.millenaire.goal.impl;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingId;
import org.millenaire.building.BuildingInstance;
import org.millenaire.building.BuildingInventory;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.VillagerInventory;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.TaskLabels;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.slf4j.Logger;

public class BringBackHomeGoal implements VillagerGoal {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "bring_back_home");
   private static final int STANDARD_DELAY = 2000;
   private static final int IMMEDIATE_THRESHOLD = 16;
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
      int nbGoods = countBringBackGoods(context);
      return 10 + nbGoods * 3;
   }

   @Override
   public boolean canStart(GoalContext context) {
      VillagerType vtype = ModCultures.getVillagerType(context.villager().getVillagerTypeId());
      if (vtype == null || vtype.bringBackHomeGoods().isEmpty()) {
         return false;
      } else if (context.villager().getHomeBuilding() == null) {
         return false;
      } else {
         BuildingInstance home = context.village().getBuilding(context.villager().getHomeBuilding());
         if (home != null && home.getInventory() != null) {
            int nbGoods = countBringBackGoods(context);
            return nbGoods <= 0 ? false : this.throttle.allowStart(context.villager().getUUID(), context.gameTime(), nbGoods, 16);
         } else {
            return false;
         }
      }
   }

   @Override
   public VillagerTask start(GoalContext context) {
      this.throttle.recordStart(context.villager().getUUID(), context.gameTime());
      return new BringBackHomeGoal.BringBackHomeTask(this.throttle);
   }

   private static int countBringBackGoods(GoalContext context) {
      VillagerType vtype = ModCultures.getVillagerType(context.villager().getVillagerTypeId());
      if (vtype == null) {
         return 0;
      }

      VillagerInventory inv = context.villager().getInventory();
      int total = 0;

      for (Entry<Item, Integer> entry : inv.getAll().entrySet()) {
         if (isBringBackGood(entry.getKey(), vtype)) {
            total += entry.getValue();
         }
      }

      return total;
   }

   static boolean isBringBackGood(Item item, VillagerType vtype) {
      return vtype.resolvedBringBackHomeGoods().contains(item);
   }

   static class BringBackHomeTask extends WalkThenActTask {
      private final TransportThrottle throttle;

      BringBackHomeTask(TransportThrottle throttle) {
         this.throttle = throttle;
      }

      @Override
      public ResourceLocation goalId() {
         return BringBackHomeGoal.ID;
      }

      @Nullable
      @Override
      protected BlockPos resolveTarget(GoalContext ctx) {
         BuildingInstance home = this.resolveHomeBuilding(ctx);
         return home != null ? home.getPathStartPos() : null;
      }

      @Override
      protected void performAction(GoalContext ctx) {
         BuildingInstance home = this.resolveHomeBuilding(ctx);
         if (home != null && home.getInventory() != null) {
            VillagerType vtype = ModCultures.getVillagerType(ctx.villager().getVillagerTypeId());
            if (vtype != null) {
               VillagerInventory villagerInv = ctx.villager().getInventory();
               BuildingInventory buildingInv = home.getInventory();
               int transferred = 0;
               boolean hadGoodsToDeliver = false;

               for (Entry<Item, Integer> entry : new ArrayList<>(villagerInv.getAll().entrySet())) {
                  if (BringBackHomeGoal.isBringBackGood(entry.getKey(), vtype)) {
                     int count = entry.getValue();
                     hadGoodsToDeliver = true;
                     int added = buildingInv.add(ctx.level(), entry.getKey(), count);
                     if (added > 0) {
                        villagerInv.remove(entry.getKey(), added);
                        transferred += added;
                     }
                  }
               }

               if (transferred > 0) {
                  this.reportProgress();
                  BringBackHomeGoal.LOGGER.debug("[Millenaire] {} delivered {} items to home", ctx.villager().getVillagerTypeId(), transferred);
               } else if (hadGoodsToDeliver) {
                  this.throttle.recordFailedTransfer(ctx.villager().getUUID(), ctx.gameTime());
                  BringBackHomeGoal.LOGGER.debug("[Millenaire] {} could not deliver to home (chests full) — throttled", ctx.villager().getVillagerTypeId());
               }
            }
         } else {
            BringBackHomeGoal.LOGGER.debug("[Millenaire] BringBackHome — no inventory in building, abandoning");
         }
      }

      @Nullable
      private BuildingInstance resolveHomeBuilding(GoalContext ctx) {
         BuildingId homeId = ctx.villager().getHomeBuilding();
         return homeId == null ? null : ctx.village().getBuilding(homeId);
      }

      @Nullable
      @Override
      public Component getGoalLabel() {
         return this.phase == WalkThenActTask.Phase.DONE ? null : TaskLabels.labelForPhase(this.phase != WalkThenActTask.Phase.WALKING, "bring_back_home");
      }
   }
}
