package org.millenaire.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.client.model.InfernalHellhoundModel;
import org.millenaire.client.model.MillModelLayers;
import org.millenaire.entity.InfernalHellhoundEntity;

@OnlyIn(Dist.CLIENT)
public class InfernalHellhoundRenderer extends MobRenderer<InfernalHellhoundEntity, InfernalHellhoundModel> {
   private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
      "millenaire", "textures/entity/infernal_hellhound.png"
   );

   public InfernalHellhoundRenderer(Context context) {
      super(context, new InfernalHellhoundModel(context.bakeLayer(MillModelLayers.INFERNAL_HELLHOUND)), 0.85F);
      this.addLayer(new InfernalHellhoundEmissiveLayer(this));
   }

   @Override
   public ResourceLocation getTextureLocation(InfernalHellhoundEntity entity) {
      return TEXTURE;
   }
}
