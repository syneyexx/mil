package org.millenaire.labyrinth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.entity.GebondeneAttitude;
import org.millenaire.labyrinth.entity.GebondeneEntity;
import org.millenaire.labyrinth.entity.GebondeneKind;

/**
 * Renders each Gebondene kind with its Blockbench/Native JSON model and texture.
 * Main's {@link GebondeneModel} humanoid layer stays registered in ClientEvents;
 * humanoid textures are preserved under textures/entity/gebondene/humanoid/.
 */
@OnlyIn(Dist.CLIENT)
public class GebondeneRenderer extends MobRenderer<GebondeneEntity, GebondeneNativeModel> {
   private final Map<GebondeneKind, GebondeneNativeModel> models = new EnumMap<>(GebondeneKind.class);

   public GebondeneRenderer(EntityRendererProvider.Context context) {
      super(context, load(GebondeneKind.GASTHEER), 0.5F);
      for (GebondeneKind kind : GebondeneKind.values()) {
         models.put(kind, load(kind));
      }
      this.model = models.get(GebondeneKind.GASTHEER);
   }

   private static GebondeneNativeModel load(GebondeneKind kind) {
      return new GebondeneNativeModel(kind.id());
   }

   @Override
   public ResourceLocation getTextureLocation(GebondeneEntity entity) {
      return ResourceLocation.fromNamespaceAndPath(
         LabyrinthKeys.MODID,
         "textures/entity/gebondene/" + entity.getKind().id() + ".png"
      );
   }

   @Override
   public void render(
      GebondeneEntity entity,
      float entityYaw,
      float partialTicks,
      PoseStack poseStack,
      MultiBufferSource buffer,
      int packedLight
   ) {
      GebondeneNativeModel kindModel = models.get(entity.getKind());
      if (kindModel != null) {
         this.model = kindModel;
      }
      this.shadowRadius = 0.35F + entity.getKind().width() * 0.4F;
      super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
   }

   @Override
   protected float getFlipDegrees(GebondeneEntity entity) {
      return 0;
   }

   @Override
   protected float getWhiteOverlayProgress(GebondeneEntity livingEntity, float partialTicks) {
      if (livingEntity.getAttitude() == GebondeneAttitude.JACHT) {
         return 0.08F + Mth.sin((livingEntity.tickCount + partialTicks) * 0.4F) * 0.04F;
      }
      if (livingEntity.getAttitude() == GebondeneAttitude.GEALARMEERD) {
         return 0.04F;
      }
      return super.getWhiteOverlayProgress(livingEntity, partialTicks);
   }
}
