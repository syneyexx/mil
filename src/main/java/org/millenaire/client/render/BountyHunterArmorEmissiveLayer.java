package org.millenaire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.millenaire.client.model.BountyHunterArmorModel;
import org.millenaire.client.model.MillModelLayers;
import org.millenaire.item.BountyHunterArmorItem;

/** Fullbright eyes (helmet) and lantern (chest) only — not the whole outfit. */
@OnlyIn(Dist.CLIENT)
public class BountyHunterArmorEmissiveLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
   private static final ResourceLocation EMISSIVE =
      ResourceLocation.fromNamespaceAndPath("millenaire", "textures/models/armor/bounty_hunter_emissive.png");

   private BountyHunterArmorModel classic;
   private BountyHunterArmorModel slim;

   public BountyHunterArmorEmissiveLayer(RenderLayerParent<T, M> parent) {
      super(parent);
   }

   private BountyHunterArmorModel modelFor(LivingEntity entity) {
      Minecraft mc = Minecraft.getInstance();
      if (this.classic == null) {
         this.classic = new BountyHunterArmorModel(mc.getEntityModels().bakeLayer(MillModelLayers.BOUNTY_HUNTER_ARMOR), false);
         this.slim = new BountyHunterArmorModel(mc.getEntityModels().bakeLayer(MillModelLayers.BOUNTY_HUNTER_ARMOR_SLIM), true);
      }
      boolean useSlim = entity instanceof AbstractClientPlayer player && player.getSkin().model() == PlayerSkin.Model.SLIM;
      return useSlim ? this.slim : this.classic;
   }

   @Override
   public void render(
      PoseStack poseStack,
      MultiBufferSource buffer,
      int packedLight,
      T entity,
      float limbSwing,
      float limbSwingAmount,
      float partialTick,
      float ageInTicks,
      float netHeadYaw,
      float headPitch
   ) {
      ItemStack helmet = entity.getItemBySlot(EquipmentSlot.HEAD);
      ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
      boolean hasHelmet = helmet.getItem() instanceof BountyHunterArmorItem;
      boolean hasChest = chest.getItem() instanceof BountyHunterArmorItem;
      if (!hasHelmet && !hasChest) {
         return;
      }
      if (entity.isInvisible()) {
         return;
      }

      BountyHunterArmorModel model = modelFor(entity);
      @SuppressWarnings({"unchecked", "rawtypes"})
      HumanoidModel parent = (HumanoidModel) this.getParentModel();
      parent.copyPropertiesTo(model);

      VertexConsumer consumer = buffer.getBuffer(RenderType.eyes(EMISSIVE));
      if (hasHelmet) {
         model.prepareForSlot(EquipmentSlot.HEAD, entity);
         model.renderToBuffer(poseStack, consumer, 15728880, OverlayTexture.NO_OVERLAY);
      }
      if (hasChest) {
         model.prepareForSlot(EquipmentSlot.CHEST, entity);
         model.animateCoat(entity, limbSwing, limbSwingAmount, ageInTicks);
         model.renderToBuffer(poseStack, consumer, 15728880, OverlayTexture.NO_OVERLAY);
      }
   }
}
