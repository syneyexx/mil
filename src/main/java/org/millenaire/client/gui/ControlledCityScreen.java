package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.network.ControlledCityActionPayload;
import org.millenaire.network.ControlledCityPayload;
import org.millenaire.network.InfoPanelRequestPayload;
import org.millenaire.village.BuildingPurpose;

public class ControlledCityScreen extends AbstractMillenaireScreen {
   private static final int PARCHMENT_WIDTH = 280;
   private static final int PARCHMENT_HEIGHT = 230;
   private static final int ROW_HEIGHT = 34;
   private static final int PURPOSE_BUTTON_WIDTH = 78;
   private static final int BUTTON_HEIGHT = 14;
   private static final int INK = PanelRenderHelper.TITLE_COLOR;
   private static final int MUTED = PanelRenderHelper.TEXT_COLOR;
   private static final int BLUE = -16777046;
   private final ControlledCityPayload data;
   private final List<Button> pageButtons = new ArrayList<>();
   private int rowsPerPage;
   private int rowCount;
   private int listStartY;

   public ControlledCityScreen(ControlledCityPayload data) {
      super(Component.translatable("panel.millenaire.panel_type.controlled_city"));
      this.data = data;
   }

   protected void init() {
      super.init();
      int parchY = (this.height - PARCHMENT_HEIGHT) / 2;
      int parchX = (this.width - PARCHMENT_WIDTH) / 2;
      int headerExtra = this.data.villageName().isEmpty() ? 0 : 13;
      int statusBlock = 78;
      this.listStartY = parchY + 42 + headerExtra + statusBlock;
      int contentEndY = parchY + PARCHMENT_HEIGHT - 18;
      this.rowsPerPage = Math.max(1, (contentEndY - this.listStartY) / ROW_HEIGHT);
      this.rowCount = this.data.buildingEntries().size();
      this.totalPages = Math.max(1, (this.rowCount + this.rowsPerPage - 1) / this.rowsPerPage);
      this.currentPage = 0;
      this.initPaginationButtons(parchX, parchY, PARCHMENT_WIDTH, PARCHMENT_HEIGHT, 16, true);

      int btnY = parchY + 42 + headerExtra + 52;
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.millenaire.city.call_immigrants"), b -> PacketDistributor.sendToServer(
               new ControlledCityActionPayload(this.data.villageUuid(), ControlledCityActionPayload.ACTION_CALL_IMMIGRANTS, null, null),
               new CustomPacketPayload[0]
            ))
            .bounds(parchX + 16, btnY, 118, BUTTON_HEIGHT)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.millenaire.ledger.open_short"), b -> PacketDistributor.sendToServer(
               new InfoPanelRequestPayload(),
               new CustomPacketPayload[0]
            ))
            .bounds(parchX + 140, btnY, 118, BUTTON_HEIGHT)
            .build()
      );
      this.rebuildPageButtons();
      this.updatePaginationButtons();
   }

   @Override
   protected void onPageChanged() {
      super.onPageChanged();
      this.rebuildPageButtons();
   }

   private void rebuildPageButtons() {
      for (Button btn : this.pageButtons) {
         this.removeWidget(btn);
      }

      this.pageButtons.clear();
      int parchX = (this.width - PARCHMENT_WIDTH) / 2;
      int startIdx = this.currentPage * this.rowsPerPage;
      int endIdx = Math.min(startIdx + this.rowsPerPage, this.rowCount);
      int contentX = parchX + 16;

      for (int i = startIdx; i < endIdx; i++) {
         ControlledCityPayload.BuildingEntry entry = this.data.buildingEntries().get(i);
         int rowY = this.listStartY + (i - startIdx) * ROW_HEIGHT;
         BuildingPurpose current = BuildingPurpose.fromName(entry.purpose());
         BuildingPurpose next = nextPurpose(current);
         Button button = Button.builder(
               Component.translatable("gui.millenaire.city.purpose." + current.serializationName()),
               b -> PacketDistributor.sendToServer(
                  new ControlledCityActionPayload(
                     this.data.villageUuid(),
                     ControlledCityActionPayload.ACTION_SET_PURPOSE,
                     entry.buildingUuid(),
                     next.serializationName()
                  ),
                  new CustomPacketPayload[0]
               )
            )
            .bounds(contentX + PARCHMENT_WIDTH - 32 - PURPOSE_BUTTON_WIDTH, rowY + 14, PURPOSE_BUTTON_WIDTH, BUTTON_HEIGHT)
            .build();
         this.pageButtons.add(button);
         this.addRenderableWidget(button);
      }
   }

   private static BuildingPurpose nextPurpose(BuildingPurpose current) {
      BuildingPurpose[] values = BuildingPurpose.values();
      return values[(current.ordinal() + 1) % values.length];
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 0xC0101010);
      int parchX = (this.width - PARCHMENT_WIDTH) / 2;
      int parchY = (this.height - PARCHMENT_HEIGHT) / 2;
      PanelRenderHelper.renderTexturedBackground(graphics, PanelRenderHelper.QUEST_TEXTURE, parchX, parchY, PARCHMENT_WIDTH, PARCHMENT_HEIGHT);
      int y = this.renderParchmentHeader(graphics, this.title, parchX, parchY, PARCHMENT_WIDTH);
      if (!this.data.villageName().isEmpty()) {
         graphics.drawString(this.font, this.data.villageName(), parchX + 16, y, INK, false);
         y += 13;
      }

      graphics.fill(parchX + 14, y, parchX + PARCHMENT_WIDTH - 14, y + 48, PanelRenderHelper.PARCHMENT_BG_DARK);
      graphics.renderOutline(parchX + 14, y, PARCHMENT_WIDTH - 28, 48, PanelRenderHelper.PARCHMENT_BORDER);
      graphics.drawString(this.font, Component.translatable("gui.millenaire.city.section.status"), parchX + 20, y + 4, BLUE, false);
      graphics.drawString(
         this.font,
         Component.translatable(
            "gui.millenaire.city.population",
            this.data.population(),
            this.data.maxSlots(),
            this.data.openJobs(),
            this.data.openBeds()
         ),
         parchX + 20,
         y + 16,
         MUTED,
         false
      );
      graphics.drawString(
         this.font,
         Component.translatable("gui.millenaire.city.threat." + this.data.threatLabel())
            .append("  ·  ")
            .append(Component.translatable("gui.millenaire.city.buildings", this.data.buildings())),
         parchX + 20,
         y + 28,
         MUTED,
         false
      );
      String priority = this.data.jobPriorityRole() != null ? this.data.jobPriorityRole() : "-";
      graphics.drawString(
         this.font,
         Component.translatable("gui.millenaire.city.job_priority", priority)
            .append("  ·  ")
            .append(
               Component.translatable(
                  "gui.millenaire.city.immigrants_today",
                  this.data.immigrantAutoToday(),
                  this.data.autoCap(),
                  this.data.immigrantManualToday(),
                  this.data.manualCap()
               )
            ),
         parchX + 20,
         y + 38,
         MUTED,
         false
      );

      int listHeaderY = this.listStartY - 12;
      graphics.drawString(this.font, Component.translatable("gui.millenaire.city.section.buildings"), parchX + 16, listHeaderY, BLUE, false);

      int startIdx = this.currentPage * this.rowsPerPage;
      int endIdx = Math.min(startIdx + this.rowsPerPage, this.rowCount);
      for (int i = startIdx; i < endIdx; i++) {
         ControlledCityPayload.BuildingEntry entry = this.data.buildingEntries().get(i);
         int rowY = this.listStartY + (i - startIdx) * ROW_HEIGHT;
         graphics.fill(parchX + 14, rowY, parchX + PARCHMENT_WIDTH - 14, rowY + ROW_HEIGHT - 2, PanelRenderHelper.PARCHMENT_BG_DARK);
         graphics.renderOutline(parchX + 14, rowY, PARCHMENT_WIDTH - 28, ROW_HEIGHT - 2, PanelRenderHelper.PARCHMENT_BORDER);
         graphics.drawString(this.font, Component.translatable(entry.planName()), parchX + 20, rowY + 4, INK, false);
         graphics.drawString(
            this.font,
            Component.translatable("gui.millenaire.city.building_slots", entry.occupants(), entry.openSlots()),
            parchX + 20,
            rowY + 16,
            MUTED,
            false
         );
      }

      this.renderPageCounter(graphics, parchX, parchY, PARCHMENT_WIDTH, PARCHMENT_HEIGHT);
      super.render(graphics, mouseX, mouseY, partialTick);
   }
}
