package org.millenaire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.millenaire.block.FirePitAlignment;
import org.millenaire.block.FirePitBlock;
import org.millenaire.block.FirePitBlockEntity;

public class FirePitRenderer implements BlockEntityRenderer<FirePitBlockEntity> {
   private static final float ITEM_SCALE = 0.35F;
   private static final float[][] COOKING_POSITIONS = new float[][]{
      {0.5F, 1.0F, 0.4F, 25.0F, 180.0F, -45.0F}, {0.5F, 0.9F, 0.5F, 0.0F, 45.0F, -45.0F}, {0.5F, 1.0F, 0.6F, -25.0F, 180.0F, -45.0F}
   };
   private static final float[][] COOKED_POSITIONS = new float[][]{
      {0.5F, 0.9F, 0.4F, 25.0F, 180.0F, -45.0F}, {0.5F, 0.9F, 0.5F, 0.0F, -45.0F, -45.0F}, {0.5F, 0.9F, 0.6F, -25.0F, 180.0F, -45.0F}
   };
   private final ItemRenderer itemRenderer;

   public FirePitRenderer(Context context) {
      this.itemRenderer = context.getItemRenderer();
   }

   public void render(
      FirePitBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay
   ) {
      Level level = blockEntity.getLevel();
      BlockPos pos = blockEntity.getBlockPos();
      BlockState state = blockEntity.getBlockState();
      if (state.getBlock() instanceof FirePitBlock) {
         double alignment = ((FirePitAlignment)state.getValue(FirePitBlock.ALIGNMENT)).angle;
         poseStack.pushPose();
         poseStack.translate(0.5, 0.5, 0.5);
         poseStack.mulPose(Axis.YP.rotationDegrees((float)alignment));
         poseStack.translate(-0.5, -0.5, -0.5);
         ItemStack fuel = blockEntity.getFuelItem();
         if (!fuel.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5, 0.2, 0.5);
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            int posTotal = pos.getX() + pos.getY() + pos.getZ() & 3;
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F * posTotal));
            poseStack.scale(0.5F, 0.5F, 0.5F);
            this.renderItem(fuel, level, pos, poseStack, bufferSource, packedLight, packedOverlay);
            poseStack.popPose();
         }

         for (int i = 0; i < 3; i++) {
            this.renderPlacedItem(blockEntity.getInputItem(i), COOKING_POSITIONS[i], level, pos, poseStack, bufferSource, packedLight, packedOverlay);
            this.renderPlacedItem(blockEntity.getOutputItem(i), COOKED_POSITIONS[i], level, pos, poseStack, bufferSource, packedLight, packedOverlay);
         }

         poseStack.popPose();
      }
   }

   private void renderPlacedItem(
      ItemStack stack, float[] transform, Level level, BlockPos pos, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay
   ) {
      if (!stack.isEmpty()) {
         poseStack.pushPose();
         poseStack.translate(transform[0], transform[1], transform[2]);
         poseStack.mulPose(Axis.XP.rotationDegrees(transform[3]));
         poseStack.mulPose(Axis.YP.rotationDegrees(transform[4]));
         poseStack.mulPose(Axis.ZP.rotationDegrees(transform[5]));
         poseStack.scale(0.35F, 0.35F, 0.35F);
         this.renderItem(stack, level, pos, poseStack, bufferSource, packedLight, packedOverlay);
         poseStack.popPose();
      }
   }

   private void renderItem(ItemStack stack, Level level, BlockPos pos, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
      int seed = pos == null ? 0 : (int)pos.asLong();
      this.itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, bufferSource, level, seed);
   }
}
