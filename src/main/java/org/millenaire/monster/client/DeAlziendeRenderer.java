package org.millenaire.monster.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.millenaire.monster.DeAlziendeEntity;
import org.millenaire.monster.MonsterConstants;

public final class DeAlziendeRenderer extends NativeMonsterRenderer<DeAlziendeEntity> {
   public DeAlziendeRenderer(EntityRendererProvider.Context context) {
      super(context, new NativeMonsterModel<>("de_alziende"), "de_alziende", MonsterConstants.DeAlziende.SHADOW);
   }
}
