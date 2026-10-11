package org.millenaire.client.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.client.model.InfernalHellhoundModel;
import org.millenaire.entity.InfernalHellhoundEntity;

/**
 * Fullbright overlay for hellhound eyes, mouth, rib core, spike tips, claws, and tail fissures.
 * Black pixels are discarded; only the emissive mask glows.
 */
@OnlyIn(Dist.CLIENT)
public class InfernalHellhoundEmissiveLayer extends EyesLayer<InfernalHellhoundEntity, InfernalHellhoundModel> {
   private static final RenderType EMISSIVE = RenderType.eyes(
      ResourceLocation.fromNamespaceAndPath("millenaire", "textures/entity/infernal_hellhound_emissive.png")
   );

   public InfernalHellhoundEmissiveLayer(RenderLayerParent<InfernalHellhoundEntity, InfernalHellhoundModel> parent) {
      super(parent);
   }

   @Override
   public RenderType renderType() {
      return EMISSIVE;
   }
}
