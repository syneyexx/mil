package org.millenaire.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Wearable Bounty Hunter armor piece with custom 3D geometry (client extensions).
 */
public class BountyHunterArmorItem extends ArmorItem {
   public BountyHunterArmorItem(Type type, Properties properties) {
      super(BountyHunterMaterials.BOUNTY_HUNTER_ARMOR, type, properties.durability(BountyHunterMaterials.durabilityFor(type)));
   }

   public static boolean isBountyHunterArmor(ItemStack stack) {
      return stack != null && !stack.isEmpty() && stack.getItem() instanceof BountyHunterArmorItem;
   }

   public static boolean isFullSet(LivingEntity entity) {
      if (entity == null) {
         return false;
      }
      return isBountyHunterArmor(entity.getItemBySlot(EquipmentSlot.HEAD))
         && isBountyHunterArmor(entity.getItemBySlot(EquipmentSlot.CHEST))
         && isBountyHunterArmor(entity.getItemBySlot(EquipmentSlot.LEGS))
         && isBountyHunterArmor(entity.getItemBySlot(EquipmentSlot.FEET));
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("item.millenaire.bounty_hunter_armor.tooltip").withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.translatable("item.millenaire.bounty_hunter_armor.set_bonus").withStyle(ChatFormatting.GOLD));
      super.appendHoverText(stack, context, tooltip, flag);
   }
}
