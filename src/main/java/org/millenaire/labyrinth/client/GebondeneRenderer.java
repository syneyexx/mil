package org.millenaire.labyrinth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.entity.GebondeneAttitude;
import org.millenaire.labyrinth.entity.GebondeneEntity;
import org.millenaire.labyrinth.entity.GebondeneKind;

/**
 * Renders Gebondene with per-kind textures and hostility tinting.
 */
@OnlyIn(Dist.CLIENT)
public class GebondeneRenderer extends MobRenderer<GebondeneEntity, GebondeneModel<GebondeneEntity>> {
   private static final float REFERENCE_HEIGHT = 1.95F;

   public GebondeneRenderer(EntityRendererProvider.Context context) {
      super(context, new GebondeneModel<>(context.bakeLayer(GebondeneModel.LAYER_LOCATION)), 0.5F);
   }

   @Override
   public ResourceLocation getTextureLocation(GebondeneEntity entity) {
      GebondeneKind kind = entity.getKind();
      return ResourceLocation.fromNamespaceAndPath(
         LabyrinthKeys.MODID,
         "textures/entity/gebondene/" + kind.id() + ".png"
      );
   }

   @Override
   protected void scale(GebondeneEntity entity, PoseStack poseStack, float partialTick) {
      float height = entity.getKind().height();
      float scale = height / REFERENCE_HEIGHT;
      poseStack.scale(scale, scale, scale);
   }

   @Override
   public void render(
      GebondeneEntity entity,
      float entityYaw,
      float partialTicks,
      PoseStack poseStack,
      MultiBufferSource buffer,
      int packedLight
   ) {
      this.shadowRadius = 0.4F + entity.getKind().width() * 0.35F;
      super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
   }

   @Override
   protected float getWhiteOverlayProgress(GebondeneEntity livingEntity, float partialTicks) {
      if (livingEntity.getAttitude() == GebondeneAttitude.JACHT) {
         return 0.08F + Mth.sin((livingEntity.tickCount + partialTicks) * 0.4F) * 0.04F;
      }
      if (livingEntity.getAttitude() == GebondeneAttitude.GEALARMEERD) {
         return 0.04F;
      }
      return super.getWhiteOverlayProgress(livingEntity, partialTicks);
   }
}
