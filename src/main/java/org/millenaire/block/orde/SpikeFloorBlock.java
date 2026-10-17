package org.millenaire.block.orde;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Orde spike floor — mild contact damage (catalog Tier C). */
public class SpikeFloorBlock extends Block {
   public SpikeFloorBlock(Properties properties) {
      super(properties);
   }

   @Override
   public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
      if (!level.isClientSide && entity instanceof LivingEntity living && !living.isInvulnerable()) {
         living.hurt(level.damageSources().cactus(), 1.0F);
      }
      super.stepOn(level, pos, state, entity);
   }
}
