package org.millenaire.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.client.model.InfernalHarrowModel;
import org.millenaire.client.model.MillModelLayers;
import org.millenaire.entity.InfernalHarrowEntity;

@OnlyIn(Dist.CLIENT)
public class InfernalHarrowRenderer extends MobRenderer<InfernalHarrowEntity, InfernalHarrowModel> {
   private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("millenaire", "textures/entity/infernal_harrow.png");

   public InfernalHarrowRenderer(Context context) {
      super(context, new InfernalHarrowModel(context.bakeLayer(MillModelLayers.INFERNAL_HARROW)), 0.8F);
      this.addLayer(new InfernalHarrowEmissiveLayer(this));
   }

   @Override
   public ResourceLocation getTextureLocation(InfernalHarrowEntity entity) {
      return TEXTURE;
   }
}
