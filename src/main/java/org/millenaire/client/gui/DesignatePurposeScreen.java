package org.millenaire.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.millenaire.building.BuildingPurpose;
import org.millenaire.client.DesignateModeController;
import org.millenaire.network.BuildingProjectListPayload;

public class DesignatePurposeScreen extends AbstractMillenaireScreen {
   private static final int PARCHMENT_WIDTH = 220;
   private static final int PARCHMENT_HEIGHT = 220;
   private static final int BUTTON_WIDTH = 186;
   private static final int BUTTON_HEIGHT = 18;
   private static final int BUTTON_GAP = 2;
   private static final int VISIBLE_ROWS = 7;
   private final BuildingProjectListPayload data;
   private int scroll;
   private int listTop;
   private int listLeft;

   public DesignatePurposeScreen(BuildingProjectListPayload data) {
      super(Component.translatable("gui.millenaire.designate.title"));
      this.data = data;
   }

   protected void init() {
      super.init();
      this.rebuildButtons();
   }

   private void rebuildButtons() {
      this.clearWidgets();
      int parchX = (this.width - PARCHMENT_WIDTH) / 2;
      int parchY = (this.height - PARCHMENT_HEIGHT) / 2;
      this.listLeft = parchX + 17;
      this.listTop = parchY + 48;
      BuildingPurpose[] purposes = BuildingPurpose.values();
      int maxScroll = Math.max(0, purposes.length - VISIBLE_ROWS);
      this.scroll = Math.max(0, Math.min(this.scroll, maxScroll));

      int row = 0;
      for (int i = this.scroll; i < purposes.length && row < VISIBLE_ROWS; i++) {
         BuildingPurpose purpose = purposes[i];
         int y = this.listTop + row * (BUTTON_HEIGHT + BUTTON_GAP);
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.millenaire.designate.purpose." + purpose.id()), button -> {
                  DesignateModeController.arm(this.data, purpose);
                  this.onClose();
               })
               .bounds(this.listLeft, y, BUTTON_WIDTH, BUTTON_HEIGHT)
               .build()
         );
         row++;
      }

      int backY = parchY + PARCHMENT_HEIGHT - 28;
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.millenaire.designate.back"), button -> {
               if (this.minecraft != null) {
                  this.minecraft.setScreen(new NewBuildingProjectScreen(this.data));
               }
            })
            .bounds(this.listLeft, backY, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build()
      );
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      BuildingPurpose[] purposes = BuildingPurpose.values();
      int maxScroll = Math.max(0, purposes.length - VISIBLE_ROWS);
      if (maxScroll > 0 && scrollY != 0.0) {
         int before = this.scroll;
         this.scroll = Math.max(0, Math.min(maxScroll, this.scroll - (int)Math.signum(scrollY)));
         if (this.scroll != before) {
            this.rebuildButtons();
         }
         return true;
      }
      return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 1610612736);
      int parchX = (this.width - PARCHMENT_WIDTH) / 2;
      int parchY = (this.height - PARCHMENT_HEIGHT) / 2;
      PanelRenderHelper.renderParchmentBackground(graphics, parchX, parchY, PARCHMENT_WIDTH, PARCHMENT_HEIGHT);
      this.renderParchmentHeader(graphics, this.getTitle(), parchX, parchY, PARCHMENT_WIDTH);
      graphics.drawString(
         this.font, Component.translatable("gui.millenaire.designate.subtitle"), parchX + 16, parchY + 34, 5914656, false
      );
      BuildingPurpose[] purposes = BuildingPurpose.values();
      if (purposes.length > VISIBLE_ROWS) {
         graphics.drawString(
            this.font,
            Component.translatable("gui.millenaire.designate.scroll_hint"),
            parchX + 16,
            parchY + PARCHMENT_HEIGHT - 42,
            9079434,
            false
         );
      }
      super.render(graphics, mouseX, mouseY, partialTick);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }
}
