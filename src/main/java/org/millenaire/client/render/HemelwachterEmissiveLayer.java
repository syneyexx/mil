package org.millenaire.client.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.client.model.HemelwachterModel;
import org.millenaire.entity.HemelwachterEntity;

/**
 * Fullbright overlay for eye iris, cyan gems, and runes only (black discarded).
 */
@OnlyIn(Dist.CLIENT)
public class HemelwachterEmissiveLayer extends EyesLayer<HemelwachterEntity, HemelwachterModel> {
   private static final RenderType EMISSIVE = RenderType.eyes(
      ResourceLocation.fromNamespaceAndPath("millenaire", "textures/entity/hemelwachter_emissive.png")
   );

   public HemelwachterEmissiveLayer(RenderLayerParent<HemelwachterEntity, HemelwachterModel> parent) {
      super(parent);
   }

   @Override
   public RenderType renderType() {
      return EMISSIVE;
   }
}
