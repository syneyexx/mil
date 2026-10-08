package org.millenaire.goal.gathering.handler;

import com.mojang.logging.LogUtils;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.goal.GoalContext;
import org.millenaire.goal.gathering.GatheringTarget;
import org.millenaire.goal.gathering.GatheringType;
import org.slf4j.Logger;

public class CocoaHarvestingHandler extends AbstractGatheringHandler {
   private static final Logger LOGGER = LogUtils.getLogger();

   @Override
   public String id() {
      return "cocoa_harvesting";
   }

   @Override
   public boolean canStart(GoalContext ctx, GatheringType type) {
      for (BlockPos soil : this.collectSoilPositions(ctx, type)) {
         if (ctx.level().isLoaded(soil)) {
            BlockState state = ctx.level().getBlockState(soil);
            if (state.is(Blocks.COCOA) && (Integer)state.getValue(CocoaBlock.AGE) >= 2) {
               return true;
            }
         }
      }

      return false;
   }

   @Nullable
   @Override
   public GatheringTarget findTarget(GoalContext ctx, GatheringType type, @Nullable GatheringTarget lastTarget) {
      ServerLevel level = ctx.level();
      List<BlockPos> soilPositions = this.collectSoilPositions(ctx, type);
      BlockPos reference = lastTarget != null ? lastTarget.navigationPos() : ctx.villager().blockPosition();
      BlockPos best = findClosestBlock(
         soilPositions, pos -> level.isLoaded(pos) && isMatureCocoa(level.getBlockState(pos)), reference, lastTarget, type.batchRadius()
      );
      return best != null ? new GatheringTarget.BlockTarget(best) : null;
   }

   private static boolean isMatureCocoa(BlockState state) {
      return state.is(Blocks.COCOA) && (Integer)state.getValue(CocoaBlock.AGE) >= 2;
   }

   @Override
   public boolean performAction(GoalContext ctx, GatheringType type, GatheringTarget target) {
      if (target instanceof GatheringTarget.BlockTarget blockTarget) {
         BlockPos pos = blockTarget.pos();
         ServerLevel level = ctx.level();
         BlockState state = level.getBlockState(pos);
         if (state.is(Blocks.COCOA) && (Integer)state.getValue(CocoaBlock.AGE) >= 2) {
            level.destroyBlock(pos, false);
            int nbCrop = 2;
            int irrigation = ctx.village().getVillageIrrigation();
            if (irrigation > 0 && Math.random() < irrigation / 100.0) {
               nbCrop++;
            }

            ctx.villager().getInventory().add(Items.COCOA_BEANS, nbCrop);
            return true;
         } else {
            return true;
         }
      } else {
         return true;
      }
   }
}
