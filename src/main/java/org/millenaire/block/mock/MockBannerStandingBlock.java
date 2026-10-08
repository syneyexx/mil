package org.millenaire.block.mock;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.millenaire.building.SpecialPoint;

public class MockBannerStandingBlock extends MockBlock {
   public static final EnumProperty<BannerSubtype> SUBTYPE = EnumProperty.create("subtype", BannerSubtype.class);
   public static final IntegerProperty ROTATION = BannerBlock.ROTATION;
   private static final VoxelShape SHAPE_NS = Block.box(3.0, 0.0, 7.0, 13.0, 16.0, 9.0);
   private static final VoxelShape SHAPE_EW = Block.box(7.0, 0.0, 3.0, 9.0, 16.0, 13.0);

   public MockBannerStandingBlock(Properties properties) {
      super(properties);
      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(SUBTYPE, BannerSubtype.VILLAGE)).setValue(ROTATION, 0)
      );
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      int bucket = ((Integer)state.getValue(ROTATION) + 2) / 4 % 4;
      return bucket % 2 == 0 ? SHAPE_NS : SHAPE_EW;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{SUBTYPE, ROTATION});
   }

   public BlockState rotate(BlockState state, Rotation rotation) {
      return (BlockState)state.setValue(ROTATION, rotation.rotate((Integer)state.getValue(ROTATION), 16));
   }

   public BlockState mirror(BlockState state, Mirror mirror) {
      return (BlockState)state.setValue(ROTATION, mirror.mirror((Integer)state.getValue(ROTATION), 16));
   }

   @Override
   protected Property<? extends Comparable<?>> variantProperty() {
      return SUBTYPE;
   }

   @Override
   protected String translationKeyPrefix() {
      return "block.millenaire.mock_banner_standing.";
   }

   @Override
   public SpecialPoint toSpecialPoint(BlockState state, BlockPos pos) {
      BannerSubtype subtype = (BannerSubtype)state.getValue(SUBTYPE);
      int rotation = (Integer)state.getValue(ROTATION);
      String placement = "standing_" + rotation;
      return new SpecialPoint("banner", subtype.specialPointSubtype(), String.valueOf(rotation), placement, pos);
   }

   @Nullable
   @Override
   public BlockState getReplacementState(BlockState mockState) {
      int rotation = (Integer)mockState.getValue(ROTATION);
      return (BlockState)Blocks.WHITE_BANNER.defaultBlockState().setValue(BannerBlock.ROTATION, rotation);
   }
}
