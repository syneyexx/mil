package org.millenaire.monster.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.monster.NativeMonster;

public class NativeMonsterRenderer<T extends NativeMonster> extends MobRenderer<T, NativeMonsterModel<T>> {
   private final ResourceLocation texture;

   public NativeMonsterRenderer(EntityRendererProvider.Context context, NativeMonsterModel<T> model, String id, float shadow) {
      super(context, model, shadow);
      texture = ResourceLocation.fromNamespaceAndPath("millenaire", "textures/entity/monster/" + id + ".png");
   }

   @Override
   public ResourceLocation getTextureLocation(T entity) {
      return texture;
   }

   @Override
   public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z) {
      return entity.shouldRender(x, y, z) && (entity.noCulling || frustum.isVisible(entity.getBoundingBox().inflate(12)));
   }

   @Override
   protected float getFlipDegrees(T entity) {
      return 0;
   }
}
