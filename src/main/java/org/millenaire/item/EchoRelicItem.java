package org.millenaire.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Quest relic used in Last Flame relic restoration rituals. */
public class EchoRelicItem extends Item {
   public EchoRelicItem(Properties properties) {
      super(properties);
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("item.millenaire.echo_relic.desc"));
   }
}
