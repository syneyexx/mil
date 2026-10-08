package org.millenaire.goal.impl;

import com.mojang.logging.LogUtils;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.entity.VillagerNavDriver;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.GoalUtils;
import org.millenaire.goal.ProgressAwareTask;
import org.millenaire.goal.StopReason;
import org.millenaire.goal.TravelPhase;
import org.millenaire.goal.VillagerGoal;
import org.millenaire.goal.VillagerTask;
import org.millenaire.tool.ToolCategory;
import org.millenaire.tool.ToolCategoryRegistry;
import org.millenaire.village.path.VillagePathManager;
import org.slf4j.Logger;

public class ClearOldPathGoal implements VillagerGoal {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("millenaire", "clear_old_path");
   private static final Logger LOGGER = LogUtils.getLogger();
   static final int NAVIGATE_STUCK_THRESHOLD = 100;
   private static final int REMOTE_CLEAR_WARN_THRESHOLD = 20;

   static ClearOldPathGoal.WalkOutcome decideWalk(boolean clearable, boolean arrived, int navigateStuckTicks) {
      if (!clearable) {
         return ClearOldPathGoal.WalkOutcome.SKIP_STALE;
      } else if (arrived) {
         return ClearOldPathGoal.WalkOutcome.START_CLEARING;
      } else {
         return navigateStuckTicks > 100 ? ClearOldPathGoal.WalkOutcome.REMOTE_CLEAR : ClearOldPathGoal.WalkOutcome.KEEP_WALKING;
      }
   }

   @Override
   public ResourceLocation id() {
      return ID;
   }

   @Override
   public int computePriority(GoalContext context) {
      return GoalUtils.countSimultaneous(context, ID) > 0 ? 0 : 40;
   }

   @Override
   public boolean canStart(GoalContext context) {
      if (!(Boolean)MillenaireServerConfig.SERVER.buildPaths.get()) {
         return false;
      } else {
         return GoalUtils.countSimultaneous(context, ID) > 0 ? false : context.village().getPathManager().hasPathsToClear();
      }
   }

   @Override
   public VillagerTask start(GoalContext context) {
      return new ClearOldPathGoal.Task();
   }

   private class Task extends ProgressAwareTask {
      private ClearOldPathGoal.Task.State state = ClearOldPathGoal.Task.State.WALKING;
      @Nullable
      private BlockPos target;
      private int clearTimer;
      @Nullable
      private BlockPos lastClearPos;
      private int navigateStuckTicks;
      private int remoteClearCount;
      private boolean warnedRemoteClear;
      private float cachedShovelEfficiency = 2.0F;
      private boolean efficiencyComputed = false;
      private List<ItemStack> heldShovel = List.of(new ItemStack(Items.WOODEN_SHOVEL));

      @Override
      public ResourceLocation goalId() {
         return ClearOldPathGoal.ID;
      }

      @Override
      public List<ItemStack> getHeldItems(TravelPhase phase) {
         return this.heldShovel;
      }

      @Override
      public void tick(GoalContext ctx) {
         if (!this.efficiencyComputed) {
            this.efficiencyComputed = true;
            ToolCategory category = ToolCategoryRegistry.get("toolsshovel");
            if (category != null) {
               this.cachedShovelEfficiency = category.getBestDestroySpeed(
                  item -> ctx.villager().getInventory().getCount(item) > 0, Blocks.DIRT.defaultBlockState(), Items.WOODEN_SHOVEL
               );
               ToolCategory.ToolEntry best = category.getBestOwned(item -> ctx.villager().getInventory().getCount(item) > 0);
               if (best != null && best.item() != null) {
                  this.heldShovel = List.of(new ItemStack(best.item()));
               }
            }
         }

         VillagePathManager pm = ctx.village().getPathManager();
         VillagerNavDriver nav = ctx.villager().getNavManager();
         ServerLevel level = ctx.level();
         switch (this.state) {
            case WALKING:
               this.target = pm.getNextClearPos();
               if (this.target == null) {
                  this.state = ClearOldPathGoal.Task.State.DONE;
                  return;
               }

               if (!this.target.equals(this.lastClearPos)) {
                  this.navigateStuckTicks = 0;
                  this.lastClearPos = this.target;
               }

               boolean clearable = VillagePathManager.isClearablePathAt(level, this.target);
               boolean arrived = false;
               if (clearable) {
                  nav.navigateTo(ctx.villager(), this.target, 0.5);
                  arrived = nav.isArrivedHorizontal(ctx.villager(), 2.0);
                  if (!arrived) {
                     this.navigateStuckTicks++;
                  }
               }

               switch (ClearOldPathGoal.decideWalk(clearable, arrived, this.navigateStuckTicks)) {
                  case SKIP_STALE:
                     this.advanceAndReport(ctx, pm);
                     return;
                  case START_CLEARING:
                     nav.stop(ctx.villager());
                     this.state = ClearOldPathGoal.Task.State.CLEARING;
                     this.clearTimer = 10 - (int)this.cachedShovelEfficiency;
                     return;
                  case REMOTE_CLEAR:
                     this.remoteClearCount++;
                     ClearOldPathGoal.LOGGER
                        .debug(
                           "Path clearer remote clear #{} (stuck {} ticks) at {}",
                           new Object[]{this.remoteClearCount, this.navigateStuckTicks, this.target.toShortString()}
                        );
                     if (this.remoteClearCount == 20 && !this.warnedRemoteClear) {
                        this.warnedRemoteClear = true;
                        ClearOldPathGoal.LOGGER.warn("Path clearer reached {} remote clears — clear queue may be broken", 20);
                     }

                     this.clearPathBlock(ctx, level, this.target);
                     this.advanceAndReport(ctx, pm);
                     return;
                  case KEEP_WALKING:
                  default:
                     return;
               }
            case CLEARING:
               this.clearTimer--;
               if (this.clearTimer <= 0) {
                  this.clearPathBlock(ctx, level, this.target);
                  this.advanceAndReport(ctx, pm);
                  if (this.state != ClearOldPathGoal.Task.State.DONE) {
                     this.state = ClearOldPathGoal.Task.State.WALKING;
                  }
               }
            case DONE:
         }
      }

      private void clearPathBlock(GoalContext ctx, ServerLevel level, BlockPos pos) {
         if (VillagePathManager.isClearablePathAt(level, pos)) {
            BlockState current = level.getBlockState(pos);
            BlockState replacement = VillagePathManager.pathClearReplacement(current, level.getBlockState(pos.below()));
            if (level.setBlock(pos, replacement, 3)) {
               ctx.village().markWaypointGraphDirty();
            }
         }
      }

      private void advanceAndReport(GoalContext ctx, VillagePathManager pm) {
         pm.advanceClear();
         ctx.village().markDirty();
         this.reportProgress();
         this.lastClearPos = null;
         if (!pm.hasPathsToClear()) {
            this.state = ClearOldPathGoal.Task.State.DONE;
         }
      }

      @Override
      public boolean isFinished() {
         return this.state == ClearOldPathGoal.Task.State.DONE;
      }

      @Override
      public void stop(GoalContext context, StopReason reason) {
         if (context != null) {
            context.villager().getNavManager().stop(context.villager());
         }
      }

      private enum State {
         WALKING,
         CLEARING,
         DONE;
      }
   }

   enum WalkOutcome {
      SKIP_STALE,
      START_CLEARING,
      REMOTE_CLEAR,
      KEEP_WALKING;
   }
}
