package org.millenaire.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.client.model.AntichristPriestModel;
import org.millenaire.client.model.MillModelLayers;
import org.millenaire.entity.AntichristPriestEntity;

@OnlyIn(Dist.CLIENT)
public class AntichristPriestRenderer extends MobRenderer<AntichristPriestEntity, AntichristPriestModel> {
   private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("millenaire", "textures/entity/antichrist_priest.png");

   public AntichristPriestRenderer(Context context) {
      super(context, new AntichristPriestModel(context.bakeLayer(MillModelLayers.ANTICHRIST_PRIEST)), 0.75F);
      this.addLayer(new AntichristPriestEmissiveLayer(this));
   }

   @Override
   public ResourceLocation getTextureLocation(AntichristPriestEntity entity) {
      return TEXTURE;
   }
}
