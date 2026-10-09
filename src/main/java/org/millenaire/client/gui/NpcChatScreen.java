package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.chat.NpcChatClosedPayload;
import org.millenaire.chat.NpcChatLinePayload;
import org.millenaire.chat.NpcChatOpenPayload;
import org.millenaire.chat.NpcChatService;
import org.millenaire.network.NpcChatCloseRequestPayload;
import org.millenaire.network.NpcChatSendPayload;
import org.millenaire.village.panel.PanelLine;

public class NpcChatScreen extends AbstractMillenaireScreen {
   private static final int PARCHMENT_WIDTH = 280;
   private static final int PARCHMENT_HEIGHT = 220;
   private static final int MAX_LINES = 100;
   private static final int LINE_HEIGHT = 11;
   private static NpcChatScreen active;

   private final int entityId;
   private final String villagerName;
   private final String roleLine;
   private final String reputationLabel;
   private final List<Line> lines = new ArrayList<>();
   private EditBox input;
   private Button sendButton;
   private boolean awaitingReply;
   private int scroll;
   /** Visual wrapped line count for scroll bounds (not entry count). */
   private int wrappedLineCount;

   public NpcChatScreen(NpcChatOpenPayload payload) {
      super(Component.literal(payload.villagerName()));
      this.entityId = payload.entityId();
      this.villagerName = payload.villagerName();
      this.roleLine = payload.roleLine();
      this.reputationLabel = payload.reputationLabel();
      for (NpcChatOpenPayload.HistoryLine line : payload.history()) {
         this.lines.add(new Line(line.speaker(), line.text(), false));
      }
      while (this.lines.size() > MAX_LINES) {
         this.lines.remove(0);
      }
   }

   public static void open(NpcChatOpenPayload payload) {
      NpcChatScreen screen = new NpcChatScreen(payload);
      active = screen;
      Minecraft.getInstance().setScreen(screen);
   }

   public static void handleLine(NpcChatLinePayload payload) {
      if (active == null || active.entityId != payload.entityId()) {
         return;
      }
      active.appendLine(payload.speaker(), payload.text(), payload.pending());
   }

   public static void handleClosed(NpcChatClosedPayload payload) {
      if (active != null && active.entityId == payload.entityId()) {
         if (payload.reasonKey() != null && !payload.reasonKey().isBlank()) {
            active.appendLine(NpcChatService.SPEAKER_SYSTEM, Component.translatable(payload.reasonKey()).getString(), false);
         }
         active.awaitingReply = false;
         active.updateInputState();
         Minecraft mc = Minecraft.getInstance();
         if (mc.screen == active) {
            mc.setScreen(null);
         }
         active = null;
      } else if (active == null && payload.reasonKey() != null && !payload.reasonKey().isBlank()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(payload.reasonKey()), true);
         }
      }
   }

   protected void init() {
      super.init();
      int parchX = (this.width - PARCHMENT_WIDTH) / 2;
      int parchY = (this.height - PARCHMENT_HEIGHT) / 2;
      int fieldY = parchY + PARCHMENT_HEIGHT - 40;
      this.input = new EditBox(this.font, parchX + 16, fieldY, PARCHMENT_WIDTH - 90, 20, Component.translatable("gui.millenaire.npcchat.placeholder"));
      this.input.setMaxLength(400);
      this.input.setHint(Component.translatable("gui.millenaire.npcchat.placeholder"));
      this.addRenderableWidget(this.input);
      this.sendButton = Button.builder(Component.translatable("gui.millenaire.npcchat.send"), btn -> this.send())
         .bounds(parchX + PARCHMENT_WIDTH - 70, fieldY, 54, 20)
         .build();
      this.addRenderableWidget(this.sendButton);
      this.setInitialFocus(this.input);
      this.updateInputState();
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 1610612736);
      int parchX = (this.width - PARCHMENT_WIDTH) / 2;
      int parchY = (this.height - PARCHMENT_HEIGHT) / 2;
      PanelRenderHelper.renderTexturedBackground(graphics, PanelRenderHelper.VILLAGE_CHIEF_TEXTURE, parchX, parchY, PARCHMENT_WIDTH, PARCHMENT_HEIGHT);
      int y = this.renderParchmentHeader(graphics, Component.literal(this.villagerName), parchX, parchY, PARCHMENT_WIDTH);
      String subtitle = this.roleLine;
      if (this.reputationLabel != null && !this.reputationLabel.isBlank()) {
         subtitle = subtitle + " · " + MillenaireScreenUtils.resolveReputationLabel(this.reputationLabel);
      }
      graphics.drawString(this.font, subtitle, parchX + 16, y, -10066330, false);
      y += 14;
      graphics.fill(parchX + 16, y, parchX + PARCHMENT_WIDTH - 16, y + 1, -5601178);
      y += 6;
      int transcriptTop = y;
      int transcriptBottom = parchY + PARCHMENT_HEIGHT - 48;
      int maxVisible = Math.max(1, (transcriptBottom - transcriptTop) / LINE_HEIGHT);

      List<RenderedLine> rendered = this.buildRenderedLines();
      this.wrappedLineCount = rendered.size();
      int maxScroll = Math.max(0, rendered.size() - maxVisible);
      this.scroll = Math.min(this.scroll, maxScroll);
      int start = Math.max(0, rendered.size() - maxVisible - this.scroll);
      int end = Math.min(rendered.size(), start + maxVisible);
      int drawY = transcriptTop;
      for (int i = start; i < end; i++) {
         RenderedLine rl = rendered.get(i);
         graphics.drawString(this.font, rl.text, parchX + 16, drawY, rl.color, false);
         drawY += LINE_HEIGHT;
         if (drawY + LINE_HEIGHT > transcriptBottom) {
            break;
         }
      }
      super.render(graphics, mouseX, mouseY, partialTick);
   }

   private List<RenderedLine> buildRenderedLines() {
      List<RenderedLine> out = new ArrayList<>();
      int width = textWidth(PARCHMENT_WIDTH);
      for (Line line : this.lines) {
         int color = switch (line.speaker) {
            case NpcChatService.SPEAKER_PLAYER -> -14018560;
            case NpcChatService.SPEAKER_SYSTEM -> -10066330;
            default -> -12254976;
         };
         String prefix = switch (line.speaker) {
            case NpcChatService.SPEAKER_PLAYER -> Component.translatable("gui.millenaire.npcchat.you_prefix").getString();
            case NpcChatService.SPEAKER_SYSTEM -> "";
            default -> this.villagerName + ": ";
         };
         String text = prefix + line.text;
         if (line.pending) {
            text = "§o" + text;
         }
         List<PanelLine> wrapped = PanelRenderHelper.wrapLines(List.of(PanelLine.text(text)), this.font, width);
         for (PanelLine pl : wrapped) {
            out.add(new RenderedLine(pl.text(), color));
         }
      }
      return out;
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 257 || keyCode == 335) {
         this.send();
         return true;
      }
      if (keyCode == 256) {
         this.onClose();
         return true;
      }
      return this.input.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      int maxScroll = Math.max(0, this.wrappedLineCount - 1);
      if (scrollY > 0) {
         this.scroll = Math.min(this.scroll + 1, maxScroll);
      } else if (scrollY < 0) {
         this.scroll = Math.max(0, this.scroll - 1);
      }
      return true;
   }

   public void onClose() {
      PacketDistributor.sendToServer(new NpcChatCloseRequestPayload(this.entityId), new CustomPacketPayload[0]);
      this.awaitingReply = false;
      if (active == this) {
         active = null;
      }
      super.onClose();
   }

   private void send() {
      if (this.awaitingReply || this.input == null) {
         return;
      }
      String text = this.input.getValue().trim();
      if (text.isEmpty()) {
         return;
      }
      String lang = Minecraft.getInstance().options.languageCode;
      if (lang == null || lang.isBlank()) {
         lang = "en_us";
      }
      lang = lang.toLowerCase().replace('-', '_');
      PacketDistributor.sendToServer(new NpcChatSendPayload(this.entityId, text, lang), new CustomPacketPayload[0]);
      this.input.setValue("");
      this.awaitingReply = true;
      this.updateInputState();
   }

   private void appendLine(byte speaker, String text, boolean pending) {
      this.lines.removeIf(l -> l.pending);
      if (pending) {
         this.awaitingReply = true;
      } else if (speaker == NpcChatService.SPEAKER_NPC || speaker == NpcChatService.SPEAKER_SYSTEM) {
         // NPC replies and system rejects/errors unlock input; player echo must not.
         this.awaitingReply = false;
      }
      this.lines.add(new Line(speaker, text, pending));
      while (this.lines.size() > MAX_LINES) {
         this.lines.remove(0);
      }
      this.scroll = 0;
      this.updateInputState();
   }

   private void updateInputState() {
      if (this.input != null) {
         this.input.setEditable(!this.awaitingReply);
      }
      if (this.sendButton != null) {
         this.sendButton.active = !this.awaitingReply;
      }
   }

   private static final class Line {
      private final byte speaker;
      private final String text;
      private final boolean pending;

      private Line(byte speaker, String text, boolean pending) {
         this.speaker = speaker;
         this.text = text;
         this.pending = pending;
      }
   }

   private static final class RenderedLine {
      private final String text;
      private final int color;

      private RenderedLine(String text, int color) {
         this.text = text;
         this.color = color;
      }
   }
}
