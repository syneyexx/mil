package org.millenaire.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.millenaire.network.BountyHudPayload;

public final class BountyHudClient {
   private static final int TITLE_COLOR = 0xE0C060;
   private static final int LINE_COLOR = 0xFF5555;
   private static final int ZERO_COLOR = 0xAAAAAA;
   private static final int PADDING = 4;
   private static final int LINE_HEIGHT = 10;
   private static List<BountyHudPayload.Entry> entries = List.of();

   private BountyHudClient() {
   }

   public static void update(BountyHudPayload payload) {
      entries = payload.entries() == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(payload.entries()));
   }

   public static void clear() {
      entries = List.of();
   }

   public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.options.hideGui || mc.screen != null) {
         return;
      }

      Font font = mc.font;
      int screenWidth = mc.getWindow().getGuiScaledWidth();
      int xRight = screenWidth - PADDING;
      int y = PADDING;

      Component title = Component.translatable("gui.millenaire.bounty_hud.title");
      int titleWidth = font.width(title);
      graphics.drawString(font, title, xRight - titleWidth, y, TITLE_COLOR, true);
      y += LINE_HEIGHT + 1;

      if (entries.isEmpty()) {
         Component zero = Component.translatable("gui.millenaire.bounty_hud.zero");
         graphics.drawString(font, zero, xRight - font.width(zero), y, ZERO_COLOR, true);
         return;
      }

      for (BountyHudPayload.Entry entry : entries) {
         Component line = Component.translatable("gui.millenaire.bounty_hud.line", entry.displayName(), entry.amount());
         graphics.drawString(font, line, xRight - font.width(line), y, LINE_COLOR, true);
         y += LINE_HEIGHT;
      }
   }
}
