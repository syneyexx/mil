package org.millenaire.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.millenaire.client.model.BountyHunterArmorModel;
import org.millenaire.client.model.MillModelLayers;

@OnlyIn(Dist.CLIENT)
public enum BountyHunterArmorClientExtensions implements IClientItemExtensions {
   INSTANCE;

   private BountyHunterArmorModel classic;
   private BountyHunterArmorModel slim;

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
   public HumanoidModel<?> getHumanoidArmorModel(
      LivingEntity livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, HumanoidModel<?> original
   ) {
      BountyHunterArmorModel model = modelFor(livingEntity);
      model.prepareForSlot(equipmentSlot, livingEntity);
      return model;
   }

   @Override
   public void setupModelAnimations(
      LivingEntity livingEntity,
      ItemStack itemStack,
      EquipmentSlot equipmentSlot,
      Model model,
      float limbSwing,
      float limbSwingAmount,
      float partialTick,
      float ageInTicks,
      float netHeadYaw,
      float headPitch
   ) {
      if (model instanceof BountyHunterArmorModel armorModel && equipmentSlot == EquipmentSlot.CHEST) {
         armorModel.animateCoat(livingEntity, limbSwing, limbSwingAmount, ageInTicks);
      }
   }
}
