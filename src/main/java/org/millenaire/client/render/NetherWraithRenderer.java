package org.millenaire.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.client.model.MillModelLayers;
import org.millenaire.client.model.NetherWraithModel;
import org.millenaire.entity.NetherWraithEntity;

@OnlyIn(Dist.CLIENT)
public class NetherWraithRenderer extends MobRenderer<NetherWraithEntity, NetherWraithModel> {
   private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("millenaire", "textures/entity/nether_wraith.png");

   public NetherWraithRenderer(Context context) {
      super(context, new NetherWraithModel(context.bakeLayer(MillModelLayers.NETHER_WRAITH)), 0.9F);
      this.addLayer(new NetherWraithEmissiveLayer(this));
   }

   @Override
   public ResourceLocation getTextureLocation(NetherWraithEntity entity) {
      return TEXTURE;
   }
}
