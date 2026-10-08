package org.millenaire.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import org.millenaire.entity.MillVillager;

public final class VillagerPreviewPose {
   private static final float PRESENT_X = -1.5F;
   private static final float PRESENT_X_OFF = -1.0F;
   private static final float PRESENT_Z = 0.0F;

   private VillagerPreviewPose() {
   }

   public static void apply(HumanoidModel<MillVillager> model, MillVillager entity) {
      if (entity.isGuiPreviewMode()) {
         if (isFlatItem(entity.getItemInHand(InteractionHand.MAIN_HAND))) {
            model.rightArm.xRot = -1.5F;
            model.rightArm.yRot = 0.0F;
            model.rightArm.zRot = 0.0F;
         }

         if (isFlatItem(entity.getItemInHand(InteractionHand.OFF_HAND))) {
            model.leftArm.xRot = -1.0F;
            model.leftArm.yRot = 0.0F;
            model.leftArm.zRot = -0.0F;
         }
      }
   }

   private static boolean isFlatItem(ItemStack stack) {
      return stack.isEmpty() ? false : !(stack.getItem() instanceof TieredItem) && !(stack.getItem() instanceof BlockItem);
   }
}
