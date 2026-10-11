package org.millenaire.client.gui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.millenaire.client.BountyHudClientState;
import org.millenaire.network.BountyHudPayload;

/** Top-right bounty HUD for wanted cultures. */
public final class BountyHudOverlay {
   private static final int MAX_VISIBLE = 4;
   private static final int COLOR_TITLE = 0xFFFF5555;
   private static final int COLOR_LINE = 0xFFFFFFFF;

   private BountyHudOverlay() {
   }

   public static void onRenderGui(RenderGuiEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.options.hideGui || mc.player == null) {
         return;
      }
      List<BountyHudPayload.Entry> entries = BountyHudClientState.entries();
      if (entries.isEmpty()) {
         return;
      }
      GuiGraphics graphics = event.getGuiGraphics();
      Font font = mc.font;
      int screenWidth = mc.getWindow().getGuiScaledWidth();
      int xRight = screenWidth - 6;
      int y = 6;
      Component title = Component.translatable("hud.millenaire.bounty.title");
      int titleW = font.width(title);
      graphics.drawString(font, title, xRight - titleW, y, COLOR_TITLE, true);
      y += font.lineHeight + 2;
      int shown = 0;
      for (BountyHudPayload.Entry entry : entries) {
         if (shown >= MAX_VISIBLE) {
            int more = entries.size() - MAX_VISIBLE;
            Component moreText = Component.translatable("hud.millenaire.bounty.more", more);
            int w = font.width(moreText);
            graphics.drawString(font, moreText, xRight - w, y, COLOR_LINE, true);
            break;
         }
         Component line = Component.translatable(
            "hud.millenaire.bounty.line",
            entry.displayName() == null || entry.displayName().isBlank() ? entry.cultureId() : entry.displayName(),
            entry.score()
         );
         int w = font.width(line);
         graphics.drawString(font, line, xRight - w, y, COLOR_LINE, true);
         y += font.lineHeight + 1;
         shown++;
      }
   }
}
