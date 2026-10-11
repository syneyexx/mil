package org.millenaire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.entity.InfernalHexBoltEntity;

@OnlyIn(Dist.CLIENT)
public class InfernalHexBoltRenderer extends EntityRenderer<InfernalHexBoltEntity> {
   private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon_fireball.png");

   public InfernalHexBoltRenderer(Context context) {
      super(context);
   }

   @Override
   public void render(InfernalHexBoltEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
      poseStack.pushPose();
      poseStack.scale(0.95F, 0.95F, 0.95F);
      poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
      poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
      PoseStack.Pose pose = poseStack.last();
      VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
      vertex(consumer, pose, packedLight, 0.0F, 0.0F, 0, 1);
      vertex(consumer, pose, packedLight, 1.0F, 0.0F, 1, 1);
      vertex(consumer, pose, packedLight, 1.0F, 1.0F, 1, 0);
      vertex(consumer, pose, packedLight, 0.0F, 1.0F, 0, 0);
      poseStack.popPose();
      super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
   }

   private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int light, float x, float y, int u, int v) {
      // Deep crimson hex bolt tint
      consumer.addVertex(pose, x - 0.5F, y - 0.25F, 0.0F)
         .setColor(200, 24, 36, 255)
         .setUv(u, v)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(light)
         .setNormal(pose, 0.0F, 1.0F, 0.0F);
   }

   @Override
   protected int getBlockLightLevel(InfernalHexBoltEntity entity, BlockPos pos) {
      return 15;
   }

   @Override
   public ResourceLocation getTextureLocation(InfernalHexBoltEntity entity) {
      return TEXTURE;
   }
}
