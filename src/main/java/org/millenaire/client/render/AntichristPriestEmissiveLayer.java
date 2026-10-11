package org.millenaire.client.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.client.model.AntichristPriestModel;
import org.millenaire.entity.AntichristPriestEntity;

/**
 * Fullbright overlay for priest eyes, forehead/robe runes, chest core,
 * claw tips, staff floating core, shaft cracks, and chain glow tips.
 * Black pixels are discarded; only the emissive mask glows.
 */
@OnlyIn(Dist.CLIENT)
public class AntichristPriestEmissiveLayer extends EyesLayer<AntichristPriestEntity, AntichristPriestModel> {
   private static final RenderType EMISSIVE = RenderType.eyes(
      ResourceLocation.fromNamespaceAndPath("millenaire", "textures/entity/antichrist_priest_emissive.png")
   );

   public AntichristPriestEmissiveLayer(RenderLayerParent<AntichristPriestEntity, AntichristPriestModel> parent) {
      super(parent);
   }

   @Override
   public RenderType renderType() {
      return EMISSIVE;
   }
}
