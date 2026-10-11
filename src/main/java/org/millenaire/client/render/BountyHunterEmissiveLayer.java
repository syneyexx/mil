package org.millenaire.client.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.client.model.BountyHunterModel;
import org.millenaire.entity.BountyHunterEntity;

/** Fullbright overlay for eyes and lantern only. */
@OnlyIn(Dist.CLIENT)
public class BountyHunterEmissiveLayer extends EyesLayer<BountyHunterEntity, BountyHunterModel> {
   private static final RenderType EMISSIVE = RenderType.eyes(
      ResourceLocation.fromNamespaceAndPath("millenaire", "textures/entity/bounty_hunter_emissive.png")
   );

   public BountyHunterEmissiveLayer(RenderLayerParent<BountyHunterEntity, BountyHunterModel> parent) {
      super(parent);
   }

   @Override
   public RenderType renderType() {
      return EMISSIVE;
   }
}
