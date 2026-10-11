package org.millenaire.client.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.client.model.NetherWraithModel;
import org.millenaire.entity.NetherWraithEntity;

/**
 * Fullbright overlay for infernal eyes, mouth, chest core, claw tips, and tendril tips.
 * Black pixels are discarded; only the emissive mask glows.
 */
@OnlyIn(Dist.CLIENT)
public class NetherWraithEmissiveLayer extends EyesLayer<NetherWraithEntity, NetherWraithModel> {
   private static final RenderType EMISSIVE = RenderType.eyes(
      ResourceLocation.fromNamespaceAndPath("millenaire", "textures/entity/nether_wraith_emissive.png")
   );

   public NetherWraithEmissiveLayer(RenderLayerParent<NetherWraithEntity, NetherWraithModel> parent) {
      super(parent);
   }

   @Override
   public RenderType renderType() {
      return EMISSIVE;
   }
}
