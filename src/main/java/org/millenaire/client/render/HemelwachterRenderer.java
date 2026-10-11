package org.millenaire.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.client.model.HemelwachterModel;
import org.millenaire.client.model.MillModelLayers;
import org.millenaire.entity.HemelwachterEntity;

@OnlyIn(Dist.CLIENT)
public class HemelwachterRenderer extends MobRenderer<HemelwachterEntity, HemelwachterModel> {
   private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("millenaire", "textures/entity/hemelwachter.png");

   public HemelwachterRenderer(Context context) {
      super(context, new HemelwachterModel(context.bakeLayer(MillModelLayers.HEMELWACHTER)), 1.6F);
      this.addLayer(new HemelwachterEmissiveLayer(this));
   }

   @Override
   public ResourceLocation getTextureLocation(HemelwachterEntity entity) {
      return TEXTURE;
   }
}
