package org.millenaire.client.gui;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.millenaire.village.panel.PanelLine;

public abstract class AbstractMillenaireScreen extends Screen {
   protected static final int PARCHMENT_PADDING = 16;
   protected static final int PARCHMENT_TITLE_HEIGHT = 20;
   protected static final int SEPARATOR_GAP = 6;
   protected static final int HEADER_HEIGHT = 42;
   protected static final int BOTTOM_RESERVE = 15;
   protected int currentPage = 0;
   protected int totalPages = 1;
   protected AbstractWidget prevButton;
   protected AbstractWidget nextButton;
   private static final int ARROW_WIDTH = 32;
   private static final int ARROW_HEIGHT = 13;
   private static final int ARROW_LEFT_X = 1;
   private static final int ARROW_INSET_RIGHT = 32;
   private static final int ARROW_INSET_BOTTOM = 13;

   protected AbstractMillenaireScreen(Component title) {
      super(title);
   }

   protected static int computeMaxLinesPerPage(int parchmentHeight) {
      return (parchmentHeight - 42 - 15) / 11;
   }

   protected static int textWidth(int parchmentWidth) {
      return parchmentWidth - 32;
   }

   protected int renderParchmentHeader(GuiGraphics graphics, Component title, int parchX, int parchY, int parchWidth) {
      int y = parchY + 16;
      int maxWidth = parchWidth - 32;
      Component styledTitle = title.copy().withStyle(s -> s.withBold(true));
      if (this.font.width(styledTitle) > maxWidth) {
         String raw = styledTitle.getString();
         String ellipsis = "...";
         int ellipsisWidth = this.font.width(ellipsis);
         StringBuilder truncated = new StringBuilder();

         for (int i = 0; i < raw.length() && this.font.width(truncated.toString() + raw.charAt(i)) + ellipsisWidth <= maxWidth; i++) {
            truncated.append(raw.charAt(i));
         }

         styledTitle = Component.literal(truncated + ellipsis).withStyle(s -> s.withBold(true));
      }

      int titleX = parchX + parchWidth / 2 - this.font.width(styledTitle) / 2;
      graphics.drawString(this.font, styledTitle, titleX, y, -14018560, false);
      y += 20;
      int contentX = parchX + 16;
      graphics.fill(contentX, y, parchX + parchWidth - 16, y + 1, -5601178);
      return y + 6;
   }

   protected void addTravelBookButton(int parchX, int parchY, int parchWidth, OnPress action) {
      int tbButtonSize = 20;
      int tbButtonX = parchX + parchWidth - 16 - tbButtonSize;
      int tbButtonY = parchY + 16 - 2;
      this.addRenderableWidget(
         new IconButton(
            tbButtonX,
            tbButtonY,
            tbButtonSize,
            tbButtonSize,
            new ItemStack(Items.WRITABLE_BOOK),
            Component.translatable("gui.millenaire.travel_book.open"),
            action
         )
      );
   }

   protected List<PanelLine> wrapLines(List<PanelLine> lines, int parchmentWidth) {
      return PanelRenderHelper.wrapLines(lines, this.font, textWidth(parchmentWidth));
   }

   public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
   }

   public boolean isPauseScreen() {
      return false;
   }

   protected void initPaginationButtons(int parchX, int parchY, int parchWidth, int parchHeight, int padding, boolean texturedArrows) {
      int arrowY = parchY + parchHeight - 13;
      this.prevButton = new AbstractMillenaireScreen.ArrowZone(parchX + 1, arrowY, 32, 13, true, !texturedArrows, () -> {
         if (this.currentPage > 0) {
            this.currentPage--;
            this.onPageChanged();
         }
      });
      this.addRenderableWidget(this.prevButton);
      this.nextButton = new AbstractMillenaireScreen.ArrowZone(parchX + parchWidth - 32, arrowY, 32, 13, false, !texturedArrows, () -> {
         if (this.currentPage < this.totalPages - 1) {
            this.currentPage++;
            this.onPageChanged();
         }
      });
      this.addRenderableWidget(this.nextButton);
   }

   protected void updatePaginationButtons() {
      this.prevButton.visible = this.totalPages > 1;
      this.nextButton.visible = this.totalPages > 1;
      this.prevButton.active = this.currentPage > 0;
      this.nextButton.active = this.currentPage < this.totalPages - 1;
   }

   protected void onPageChanged() {
      this.updatePaginationButtons();
   }

   protected void renderPageCounter(GuiGraphics graphics, int parchX, int parchY, int parchWidth, int parchHeight) {
      if (this.totalPages > 1) {
         String pageStr = this.currentPage + 1 + "/" + this.totalPages;
         graphics.drawCenteredString(this.font, pageStr, parchX + parchWidth / 2, parchY + parchHeight - 10, -14018560);
      }
   }

   protected static class ArrowZone extends AbstractWidget {
      private final Runnable onClick;
      private final boolean pointsLeft;
      private final boolean drawTriangle;

      ArrowZone(int x, int y, int width, int height, boolean pointsLeft, boolean drawTriangle, Runnable onClick) {
         super(x, y, width, height, Component.empty());
         this.pointsLeft = pointsLeft;
         this.drawTriangle = drawTriangle;
         this.onClick = onClick;
      }

      public void onClick(double mouseX, double mouseY) {
         this.onClick.run();
      }

      protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
         if (this.drawTriangle) {
            int color = !this.active ? 1431646976 : (this.isHovered() ? -8762112 : -14018560);
            int aw = 7;
            int ah = 11;
            int baseX = this.getX() + (this.getWidth() - aw) / 2;
            int cy = this.getY() + this.getHeight() / 2;

            for (int i = 0; i < aw; i++) {
               int half = Math.round(ah / 2.0F * ((float)i / (aw - 1)));
               int colX = this.pointsLeft ? baseX + i : baseX + (aw - 1 - i);
               graphics.fill(colX, cy - half, colX + 1, cy + half + 1, color);
            }
         }
      }

      protected void updateWidgetNarration(NarrationElementOutput output) {
      }
   }
}
