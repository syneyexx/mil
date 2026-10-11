package org.millenaire.client.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.client.model.InfernalCultistModel;
import org.millenaire.entity.InfernalCultistEntity;

/**
 * Fullbright overlay for cultist eyes, forehead rune, chest ritual mark,
 * claw tips, book runes, and dagger edge glow.
 * Black pixels are discarded; only the emissive mask glows.
 */
@OnlyIn(Dist.CLIENT)
public class InfernalCultistEmissiveLayer extends EyesLayer<InfernalCultistEntity, InfernalCultistModel> {
   private static final RenderType EMISSIVE = RenderType.eyes(
      ResourceLocation.fromNamespaceAndPath("millenaire", "textures/entity/infernal_cultist_emissive.png")
   );

   public InfernalCultistEmissiveLayer(RenderLayerParent<InfernalCultistEntity, InfernalCultistModel> parent) {
      super(parent);
   }

   @Override
   public RenderType renderType() {
      return EMISSIVE;
   }
}
