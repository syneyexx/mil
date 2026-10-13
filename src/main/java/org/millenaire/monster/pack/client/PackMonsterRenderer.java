package org.millenaire.monster.pack.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.millenaire.monster.pack.PackMonster;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class PackMonsterRenderer extends GeoEntityRenderer<PackMonster> {
   public PackMonsterRenderer(EntityRendererProvider.Context context) {
      super(context, new PackMonsterModel());
      this.shadowRadius = 0.5f;
   }

   @Override
   protected float getDeathMaxRotation(PackMonster entity) {
      return 0;
   }
}
