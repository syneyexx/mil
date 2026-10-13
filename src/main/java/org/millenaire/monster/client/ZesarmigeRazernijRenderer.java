package org.millenaire.monster.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.millenaire.monster.MonsterConstants;
import org.millenaire.monster.ZesarmigeRazernijEntity;

public final class ZesarmigeRazernijRenderer extends NativeMonsterRenderer<ZesarmigeRazernijEntity> {
   public ZesarmigeRazernijRenderer(EntityRendererProvider.Context context) {
      super(context, new NativeMonsterModel<>("zesarmige_razernij"), "zesarmige_razernij", MonsterConstants.ZesarmigeRazernij.SHADOW);
   }
}
