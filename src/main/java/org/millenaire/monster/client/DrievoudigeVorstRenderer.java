package org.millenaire.monster.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.millenaire.monster.DrievoudigeVorstEntity;
import org.millenaire.monster.MonsterConstants;

public final class DrievoudigeVorstRenderer extends NativeMonsterRenderer<DrievoudigeVorstEntity> {
   public DrievoudigeVorstRenderer(EntityRendererProvider.Context context) {
      super(context, new NativeMonsterModel<>("drievoudige_vorst"), "drievoudige_vorst", MonsterConstants.DrievoudigeVorst.SHADOW);
   }
}
