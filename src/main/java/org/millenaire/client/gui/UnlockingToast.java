package org.millenaire.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.components.toasts.Toast.Visibility;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class UnlockingToast implements Toast {
   private static final ResourceLocation BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("toast/tutorial");
   private static final long DISPLAY_DURATION = 2000L;
   private final Component title;
   private final Component message;
   private final ItemStack icon;

   public UnlockingToast(Component title, Component message, ItemStack icon) {
      this.title = title;
      this.message = message;
      this.icon = icon;
   }

   public Visibility render(GuiGraphics graphics, ToastComponent toastComponent, long timeSinceLastVisible) {
      graphics.blitSprite(BACKGROUND_SPRITE, 0, 0, this.width(), this.height());
      Font font = toastComponent.getMinecraft().font;
      graphics.drawString(font, this.title, 30, 7, -11534256, false);
      graphics.drawString(font, this.message, 30, 18, -16777216, false);
      if (!this.icon.isEmpty()) {
         graphics.renderItem(this.icon, 8, 8);
      }

      return timeSinceLastVisible >= 2000L ? Visibility.HIDE : Visibility.SHOW;
   }
}
