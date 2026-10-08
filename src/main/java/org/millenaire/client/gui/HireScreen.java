package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.hire.HireAction;
import org.millenaire.network.HireActionPayload;
import org.millenaire.network.OpenHireScreenPayload;

public class HireScreen extends AbstractMillenaireScreen {
   private static final int PARCHMENT_WIDTH = 256;
   private static final int PARCHMENT_HEIGHT = 200;
   private static final int TEXT_WIDTH = textWidth(256);
   private final OpenHireScreenPayload data;

   public HireScreen(OpenHireScreenPayload data) {
      super(Component.literal(data.name()));
      this.data = data;
   }

   protected void init() {
      super.init();
      int parchX = (this.width - 256) / 2;
      int parchY = (this.height - 200) / 2;
      int btnY = parchY + 200 - 40;
      int btnH = 20;
      if (this.data.hiredByMe()) {
         int x = parchX + 128 - 100;
         if (this.data.canAfford()) {
            this.addRenderableWidget(
               Button.builder(Component.translatable("gui.millenaire.hire.extend"), b -> this.sendAction(HireAction.EXTEND)).bounds(x, btnY, 63, btnH).build()
            );
         }

         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.millenaire.hire.release"), b -> this.sendAction(HireAction.RELEASE))
               .bounds(parchX + 128 - 32, btnY, 64, btnH)
               .build()
         );
         this.addRenderableWidget(
            Button.builder(Component.translatable("gui.millenaire.hire.close"), b -> this.onClose()).bounds(parchX + 128 + 37, btnY, 63, btnH).build()
         );
      } else {
         int x = parchX + 128 - 100;
         if (this.data.hasReputation() && this.data.canAfford()) {
            this.addRenderableWidget(
               Button.builder(Component.translatable("gui.millenaire.hire.hire"), b -> this.sendAction(HireAction.HIRE)).bounds(x, btnY, 95, btnH).build()
            );
            this.addRenderableWidget(
               Button.builder(Component.translatable("gui.millenaire.hire.close"), b -> this.onClose()).bounds(parchX + 128 + 5, btnY, 95, btnH).build()
            );
         } else {
            this.addRenderableWidget(
               Button.builder(Component.translatable("gui.millenaire.hire.close"), b -> this.onClose()).bounds(parchX + 128 - 47, btnY, 95, btnH).build()
            );
         }
      }
   }

   private void sendAction(HireAction action) {
      PacketDistributor.sendToServer(new HireActionPayload(this.data.villagerId(), action), new CustomPacketPayload[0]);
      this.onClose();
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 1610612736);
      int parchX = (this.width - 256) / 2;
      int parchY = (this.height - 200) / 2;
      PanelRenderHelper.renderTexturedBackground(graphics, PanelRenderHelper.VILLAGE_CHIEF_TEXTURE, parchX, parchY, 256, 200);
      int y = this.renderParchmentHeader(graphics, Component.literal(this.data.name()), parchX, parchY, 256);

      for (Component line : this.buildBodyLines()) {
         graphics.drawString(this.font, line, parchX + 16, y, -13421773, false);
         y += 11;
      }

      super.render(graphics, mouseX, mouseY, partialTick);
   }

   private List<Component> buildBodyLines() {
      List<Component> lines = new ArrayList<>();
      if (this.data.hiredByMe()) {
         lines.add(Component.translatable("gui.millenaire.hire.hiredvillager", new Object[]{this.data.hoursLeft()}));
      } else if (this.data.hasReputation()) {
         lines.add(Component.translatable("gui.millenaire.hire.hireablevillager"));
      } else {
         lines.add(Component.translatable("gui.millenaire.hire.hireablevillagernoreputation"));
      }

      lines.add(
         Component.translatable("gui.millenaire.hire.health", new Object[]{String.valueOf(this.data.health() / 2), String.valueOf(this.data.maxHealth() / 2)})
      );
      lines.add(Component.translatable("gui.millenaire.hire.strength", new Object[]{String.valueOf(this.data.attackStrength())}));
      lines.add(Component.translatable("gui.millenaire.hire.cost", new Object[]{String.valueOf(this.data.hireCost())}));
      return lines;
   }
}
