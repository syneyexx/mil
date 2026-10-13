package org.millenaire.monster.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.millenaire.monster.DrakenkoningEntity;
import org.millenaire.monster.MonsterConstants;

public final class DrakenkoningRenderer extends NativeMonsterRenderer<DrakenkoningEntity> {
   public DrakenkoningRenderer(EntityRendererProvider.Context context) {
      super(context, new NativeMonsterModel<>("drakenkoning"), "drakenkoning", MonsterConstants.Drakenkoning.SHADOW);
   }
}
