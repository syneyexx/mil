package org.millenaire.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.client.model.InfernalCultistModel;
import org.millenaire.client.model.MillModelLayers;
import org.millenaire.entity.InfernalCultistEntity;

@OnlyIn(Dist.CLIENT)
public class InfernalCultistRenderer extends MobRenderer<InfernalCultistEntity, InfernalCultistModel> {
   private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("millenaire", "textures/entity/infernal_cultist.png");

   public InfernalCultistRenderer(Context context) {
      super(context, new InfernalCultistModel(context.bakeLayer(MillModelLayers.INFERNAL_CULTIST)), 0.45F);
      this.addLayer(new InfernalCultistEmissiveLayer(this));
   }

   @Override
   public ResourceLocation getTextureLocation(InfernalCultistEntity entity) {
      return TEXTURE;
   }
}
