package org.millenaire.block;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class PaintedBrickWallBlock extends WallBlock implements IPaintedBlock {
   private final DyeColor color;

   public PaintedBrickWallBlock(DyeColor color, Properties properties) {
      super(properties);
      this.color = color;
   }

   @Override
   public DyeColor getColor() {
      return this.color;
   }

   @Override
   public PaintedBrickBlock.BrickType getBrickType() {
      return PaintedBrickBlock.BrickType.PLAIN;
   }
}
