package org.millenaire.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.network.DiplomacyEventPopupPayload;
import org.millenaire.network.DiplomacyEventResponsePayload;

public class DiplomacyEventScreen extends AbstractMillenaireScreen {
   private static final int WIDTH = 260;
   private static final int HEIGHT = 180;
   private final DiplomacyEventPopupPayload data;

   public DiplomacyEventScreen(DiplomacyEventPopupPayload data) {
      super(Component.translatable("gui.millenaire.diplomacy.event." + data.typeName().toLowerCase() + ".title"));
      this.data = data;
   }

   @Override
   protected void init() {
      super.init();
      int x = (this.width - WIDTH) / 2;
      int y = (this.height - HEIGHT) / 2;
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.millenaire.diplomacy.accept"), b -> {
               PacketDistributor.sendToServer(new DiplomacyEventResponsePayload(this.data.eventId(), true), new CustomPacketPayload[0]);
               this.onClose();
            })
            .bounds(x + 20, y + HEIGHT - 36, 100, 20)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.millenaire.diplomacy.refuse"), b -> {
               PacketDistributor.sendToServer(new DiplomacyEventResponsePayload(this.data.eventId(), false), new CustomPacketPayload[0]);
               this.onClose();
            })
            .bounds(x + WIDTH - 120, y + HEIGHT - 36, 100, 20)
            .build()
      );
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(graphics, mouseX, mouseY, partialTick);
      int x = (this.width - WIDTH) / 2;
      int y = (this.height - HEIGHT) / 2;
      graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF2A2218);
      graphics.fill(x + 2, y + 2, x + WIDTH - 2, y + HEIGHT - 2, 0xFFD8C9A0);
      this.renderParchmentHeader(graphics, this.getTitle(), x, y, WIDTH);
      int textY = y + 48;
      Component body = Component.translatable(
         "gui.millenaire.diplomacy.event." + this.data.typeName().toLowerCase() + ".body",
         this.data.cultureName(),
         this.data.amount(),
         this.data.secondaryCultureName()
      );
      for (var line : this.font.split(body, WIDTH - 32)) {
         graphics.drawString(this.font, line, x + 16, textY, 0xFF2A2218, false);
         textY += 12;
      }
      super.render(graphics, mouseX, mouseY, partialTick);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }
}
