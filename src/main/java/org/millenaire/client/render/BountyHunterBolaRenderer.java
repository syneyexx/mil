package org.millenaire.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.entity.BountyHunterBolaEntity;

@OnlyIn(Dist.CLIENT)
public class BountyHunterBolaRenderer extends ThrownItemRenderer<BountyHunterBolaEntity> {
   public BountyHunterBolaRenderer(Context context) {
      super(context, 0.75F, true);
   }
}
