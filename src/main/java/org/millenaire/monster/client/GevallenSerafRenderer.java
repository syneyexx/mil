package org.millenaire.monster.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.millenaire.monster.GevallenSerafEntity;
import org.millenaire.monster.MonsterConstants;

public final class GevallenSerafRenderer extends NativeMonsterRenderer<GevallenSerafEntity> {
   public GevallenSerafRenderer(EntityRendererProvider.Context context) {
      super(context, new NativeMonsterModel<>("gevallen_seraf"), "gevallen_seraf", MonsterConstants.GevallenSeraf.SHADOW);
   }
}
