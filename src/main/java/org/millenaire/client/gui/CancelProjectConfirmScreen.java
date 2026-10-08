package org.millenaire.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.network.BuildingProjectCancelRequestPayload;
import org.millenaire.network.ProjectCancelPromptPayload;

public class CancelProjectConfirmScreen extends AbstractMillenaireScreen {
   private static final int PARCHMENT_WIDTH = 204;
   private static final int PARCHMENT_HEIGHT = 130;
   private final ProjectCancelPromptPayload payload;

   public CancelProjectConfirmScreen(ProjectCancelPromptPayload payload) {
      super(Component.translatable("gui.millenaire.building_project.cancel_title"));
      this.payload = payload;
   }

   protected void init() {
      super.init();
      int parchX = (this.width - 204) / 2;
      int parchY = (this.height - 130) / 2;
      int buttonY = parchY + 130 - 15 - 25;
      int centerX = parchX + 102;
      int buttonWidth = 80;
      int buttonGap = 10;
      this.addRenderableWidget(
         Button.builder(
               Component.translatable("gui.millenaire.building_project.cancel_confirm"),
               btn -> {
                  PacketDistributor.sendToServer(
                     new BuildingProjectCancelRequestPayload(
                        this.payload.villageUuid(),
                        this.payload.queueIndex(),
                        this.payload.planSetId(),
                        this.payload.plannedX(),
                        this.payload.plannedY(),
                        this.payload.plannedZ(),
                        false
                     ),
                     new CustomPacketPayload[0]
                  );
                  this.onClose();
               }
            )
            .bounds(centerX - buttonWidth - buttonGap / 2, buttonY, buttonWidth, 20)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.millenaire.building_project.cancel_keep"), btn -> this.onClose())
            .bounds(centerX + buttonGap / 2, buttonY, buttonWidth, 20)
            .build()
      );
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 1610612736);
      int parchX = (this.width - 204) / 2;
      int parchY = (this.height - 130) / 2;
      PanelRenderHelper.renderParchmentBackground(graphics, parchX, parchY, 204, 130);
      int y = this.renderParchmentHeader(graphics, this.getTitle(), parchX, parchY, 204);
      Component message = Component.translatable(
         "gui.millenaire.building_project.cancel_message", new Object[]{Component.translatable(this.payload.buildingName())}
      );
      int textW = 172;
      int textX = parchX + 16;

      for (FormattedCharSequence line : this.font.split(message, textW)) {
         graphics.drawString(this.font, line, textX, y, 4141088, false);
         y += 9 + 2;
      }

      super.render(graphics, mouseX, mouseY, partialTick);
   }
}
