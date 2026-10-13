package org.millenaire.monster.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.millenaire.monster.MonsterConstants;
import org.millenaire.monster.ScharlakenKoninginEntity;

public final class ScharlakenKoninginRenderer extends NativeMonsterRenderer<ScharlakenKoninginEntity> {
   public ScharlakenKoninginRenderer(EntityRendererProvider.Context context) {
      super(context, new NativeMonsterModel<>("scharlaken_koningin"), "scharlaken_koningin", MonsterConstants.ScharlakenKoningin.SHADOW);
   }
}
