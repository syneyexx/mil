package org.millenaire.labyrinth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.client.model.NativeJsonEntityModel;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.entity.DoolhofsoevereinEntity;

/**
 * Renders De Doolhofsoeverein with the 9-block Blockbench native model.
 * Uses {@link EntityRenderer} (not MobRenderer) so humanoid shadows / armor layers
 * do not clip the four-armed monument.
 */
@OnlyIn(Dist.CLIENT)
public class DoolhofsoevereinRenderer extends EntityRenderer<DoolhofsoevereinEntity> {
   private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
      LabyrinthKeys.MODID,
      "textures/entity/doolhofsoeverein/doolhofsoeverein.png"
   );
   private static final String MODEL_PATH = "doolhofsoeverein/doolhofsoeverein";

   private final NativeJsonEntityModel<DoolhofsoevereinEntity> model;

   public DoolhofsoevereinRenderer(EntityRendererProvider.Context context) {
      super(context);
      this.shadowRadius = 1.8F;
      this.model = NativeJsonEntityModel.get(MODEL_PATH);
   }

   @Override
   public ResourceLocation getTextureLocation(DoolhofsoevereinEntity entity) {
      return TEXTURE;
   }

   /**
    * Collision is narrower/shorter than the full 9-block silhouette (arms + halo).
    * Inflate frustum test so looking at the crown does not cull the god.
    */
   @Override
   public boolean shouldRender(DoolhofsoevereinEntity entity, Frustum frustum, double x, double y, double z) {
      return entity.shouldRender(x, y, z)
         && (entity.noCulling || frustum.isVisible(entity.getBoundingBox().inflate(4.0, 3.0, 4.0)));
   }

   @Override
   public void render(
      DoolhofsoevereinEntity entity,
      float entityYaw,
      float partialTicks,
      PoseStack poseStack,
      MultiBufferSource buffer,
      int packedLight
   ) {
      poseStack.pushPose();
      float yaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
      poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - yaw));
      // NativeJsonEntityModel expects feet at y=0 in model space after its internal flip.
      poseStack.scale(-1.0F, -1.0F, 1.0F);
      poseStack.translate(0.0F, -1.501F, 0.0F);

      float limbSwing = 0.0F;
      float limbSwingAmount = 0.0F;
      float age = entity.tickCount + partialTicks;
      float headYaw = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot) - yaw;
      float headPitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
      this.model.setupAnim(entity, limbSwing, limbSwingAmount, age, headYaw, headPitch);

      var consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
      this.model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
      poseStack.popPose();

      super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
   }

   @Override
   protected boolean shouldShowName(DoolhofsoevereinEntity entity) {
      return entity.hasCustomName() || this.entityRenderDispatcher.distanceToSqr(entity) < 64.0 * 64.0;
   }
}
