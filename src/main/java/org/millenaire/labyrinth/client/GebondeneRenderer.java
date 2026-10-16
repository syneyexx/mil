package org.millenaire.labyrinth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.entity.GebondeneEntity;
import org.millenaire.labyrinth.entity.GebondeneKind;

/**
 * Renders Gebondene with per-kind textures under {@code textures/entity/gebondene/<id>.png}.
 */
@OnlyIn(Dist.CLIENT)
public class GebondeneRenderer extends MobRenderer<GebondeneEntity, GebondeneModel<GebondeneEntity>> {
   private static final float REFERENCE_HEIGHT = 1.95F;

   public GebondeneRenderer(EntityRendererProvider.Context context) {
      super(context, new GebondeneModel<>(context.bakeLayer(GebondeneModel.LAYER_LOCATION)), 0.5F);
   }

   public GebondeneRenderer(EntityRendererProvider.Context context, GebondeneModel<GebondeneEntity> model, float shadow) {
      super(context, model, shadow);
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
}
