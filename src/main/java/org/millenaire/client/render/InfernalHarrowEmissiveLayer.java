package org.millenaire.client.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.client.model.InfernalHarrowModel;
import org.millenaire.entity.InfernalHarrowEntity;

/**
 * Fullbright overlay for infernal eyes, mouth, chest core, claw tips,
 * wing tear edges, and tail tip. Black pixels are discarded; only the emissive mask glows.
 */
@OnlyIn(Dist.CLIENT)
public class InfernalHarrowEmissiveLayer extends EyesLayer<InfernalHarrowEntity, InfernalHarrowModel> {
   private static final RenderType EMISSIVE = RenderType.eyes(
      ResourceLocation.fromNamespaceAndPath("millenaire", "textures/entity/infernal_harrow_emissive.png")
   );

   public InfernalHarrowEmissiveLayer(RenderLayerParent<InfernalHarrowEntity, InfernalHarrowModel> parent) {
      super(parent);
   }

   @Override
   public RenderType renderType() {
      return EMISSIVE;
   }
}
