package org.millenaire.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Ceremonial relic used to open Labyrinth relic gates with the Configuratie. */
public class LabyrinthRelicItem extends Item {
   private final String descKey;

   public LabyrinthRelicItem(Properties properties, String descKey) {
      super(properties);
      this.descKey = descKey;
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable(this.descKey));
   }

   @Override
   public boolean isFoil(ItemStack stack) {
      return true;
   }
}
