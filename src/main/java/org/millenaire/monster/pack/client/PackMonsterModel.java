package org.millenaire.monster.pack.client;

import net.minecraft.resources.ResourceLocation;
import org.millenaire.Millenaire;
import org.millenaire.monster.pack.PackMonster;
import software.bernie.geckolib.model.GeoModel;

public final class PackMonsterModel extends GeoModel<PackMonster> {
   @Override
   public ResourceLocation getModelResource(PackMonster mob) {
      return ResourceLocation.fromNamespaceAndPath(Millenaire.MODID, "geo/" + mob.kind().id + ".geo.json");
   }

   @Override
   public ResourceLocation getTextureResource(PackMonster mob) {
      return ResourceLocation.fromNamespaceAndPath(Millenaire.MODID, "textures/entity/" + mob.kind().id + ".png");
   }

   @Override
   public ResourceLocation getAnimationResource(PackMonster mob) {
      return ResourceLocation.fromNamespaceAndPath(Millenaire.MODID, "animations/" + mob.kind().id + ".animation.json");
   }
}
