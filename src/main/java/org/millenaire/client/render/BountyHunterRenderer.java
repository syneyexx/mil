package org.millenaire.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.client.model.BountyHunterModel;
import org.millenaire.client.model.MillModelLayers;
import org.millenaire.entity.BountyHunterEntity;

@OnlyIn(Dist.CLIENT)
public class BountyHunterRenderer extends MobRenderer<BountyHunterEntity, BountyHunterModel> {
   private static final ResourceLocation TEXTURE =
      ResourceLocation.fromNamespaceAndPath("millenaire", "textures/entity/bounty_hunter.png");

   public BountyHunterRenderer(Context context) {
      super(context, new BountyHunterModel(context.bakeLayer(MillModelLayers.BOUNTY_HUNTER)), 0.55F);
      this.addLayer(new BountyHunterEmissiveLayer(this));
   }

   @Override
   public ResourceLocation getTextureLocation(BountyHunterEntity entity) {
      return TEXTURE;
   }
}
