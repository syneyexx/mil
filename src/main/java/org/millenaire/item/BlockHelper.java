package org.millenaire.item;

import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockHelper {
   private BlockHelper() {
   }

   public static boolean isDecorativeFlower(BlockState state) {
      return state.is(BlockTags.FLOWERS) && !(state.getBlock() instanceof LeavesBlock);
   }

   @Nullable
   public static Block resolve(String blockId) {
      return blockId != null && !blockId.isEmpty() ? (Block)BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(blockId)).orElse(null) : null;
   }
}
