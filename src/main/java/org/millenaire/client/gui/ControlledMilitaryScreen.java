package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.combat.raid.RaidButtonState;
import org.millenaire.network.ControlledMilitaryPayload;
import org.millenaire.network.ControlledRelationPayload;
import org.millenaire.network.CultureWarActionPayload;
import org.millenaire.network.RaidActionPayload;
import org.millenaire.village.CultureStandingSavedData;
import org.millenaire.village.VillageRelations;
import org.millenaire.village.panel.PanelLine;

public class ControlledMilitaryScreen extends AbstractMillenaireScreen {
   private static final int PARCHMENT_WIDTH = 204;
   private static final int PARCHMENT_HEIGHT = 220;
   private static final int ROW_HEIGHT = 58;
   private static final int REL_BUTTON_WIDTH = 52;
   private static final int RAID_BUTTON_WIDTH = 90;
   private static final int BUTTON_HEIGHT = 14;
   private static final int BUTTON_GAP = 2;
   private final ControlledMilitaryPayload data;
   private final List<Button> pageButtons = new ArrayList<>();
   private List<PanelLine> wrappedMilitaryLines = List.of();
   private int rowsPerPage;
   private int relationPageCount;
   private int culturePageCount;
   private int culturePageOffset;

   public ControlledMilitaryScreen(ControlledMilitaryPayload data) {
      super(Component.translatable("panel.millenaire.panel_type.controlled_military"));
      this.data = data;
   }

   private ControlledMilitaryPayload data() {
      return this.data;
   }

   protected void init() {
      super.init();
      int parchY = (this.height - 220) / 2;
      int parchX = (this.width - 204) / 2;
      int contentStartY = parchY + 42 + this.subtitleOffset();
      int contentEndY = parchY + 220 - 15;
      int availableHeight = contentEndY - contentStartY;
      this.rowsPerPage = Math.max(1, availableHeight / 58);
      this.wrappedMilitaryLines = this.wrapLines(this.data().militaryLines(), 204);
      int relationRows = this.data().relations().size();
      this.relationPageCount = relationRows == 0 ? 0 : (relationRows + this.rowsPerPage - 1) / this.rowsPerPage;
      int cultureRows = this.data().cultureStandings().size();
      this.culturePageCount = cultureRows == 0 ? 0 : (cultureRows + this.rowsPerPage - 1) / this.rowsPerPage;
      this.culturePageOffset = this.relationPageCount;
      this.totalPages = Math.max(1, this.relationPageCount + this.culturePageCount + 1);
      this.currentPage = 0;
      this.initPaginationButtons(parchX, parchY, 204, 220, 16, true);
      this.rebuildPageButtons();
      this.updatePaginationButtons();
   }

   private int subtitleOffset() {
      return this.data().villageName().isEmpty() ? 0 : 9 + 4;
   }

   private int militaryPageIndex() {
      return this.totalPages - 1;
   }

   private boolean isCulturePage() {
      return this.currentPage >= this.culturePageOffset && this.currentPage < this.militaryPageIndex();
   }

   private boolean isRelationPage() {
      return this.currentPage < this.culturePageOffset;
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
      if (this.currentPage == this.militaryPageIndex()) {
         return;
      }

      int parchX = (this.width - 204) / 2;
      int parchY = (this.height - 220) / 2;
      int contentX = parchX + 16;
      int rowY = parchY + 42 + this.subtitleOffset();

      if (this.isCulturePage()) {
         int pageWithinCulture = this.currentPage - this.culturePageOffset;
         int startIdx = pageWithinCulture * this.rowsPerPage;
         int endIdx = Math.min(startIdx + this.rowsPerPage, this.data().cultureStandings().size());

         for (int i = startIdx; i < endIdx; i++) {
            ControlledMilitaryPayload.CultureStandingEntry ce = this.data().cultureStandings().get(i);
            int btnY = rowY + 2 * (9 + 1) + 2;
            if (ce.atWar()) {
               this.addPageButton(
                  Button.builder(
                        Component.translatable("gui.millenaire.controlled_military.culture_peace"),
                        b -> PacketDistributor.sendToServer(
                           new CultureWarActionPayload(this.data().villageUuid(), CultureWarActionPayload.ACTION_END_WAR, ce.cultureId()),
                           new CustomPacketPayload[0]
                        )
                     )
                     .bounds(contentX, btnY, 120, 14)
                     .build()
               );
            } else {
               this.addPageButton(
                  Button.builder(
                        Component.translatable("gui.millenaire.controlled_military.culture_war"),
                        b -> PacketDistributor.sendToServer(
                           new CultureWarActionPayload(this.data().villageUuid(), CultureWarActionPayload.ACTION_DECLARE_WAR, ce.cultureId()),
                           new CustomPacketPayload[0]
                        )
                     )
                     .bounds(contentX, btnY, 120, 14)
                     .build()
               );
            }

            rowY += 58;
         }
      } else if (this.isRelationPage()) {
         int startIdx = this.currentPage * this.rowsPerPage;
         int endIdx = Math.min(startIdx + this.rowsPerPage, this.data().relations().size());
         String currentRaidTarget = this.data().currentRaidTargetVillageId().isEmpty() ? null : this.data().currentRaidTargetVillageId();

         for (int i = startIdx; i < endIdx; i++) {
            ControlledMilitaryPayload.RelationEntry re = this.data().relations().get(i);
            int btnRow1Y = rowY + 2 * (9 + 1) + 2;
            int gx = contentX;
            this.addPageButton(
               Button.builder(
                     Component.translatable("gui.millenaire.controlled_military.relgood"),
                     b -> PacketDistributor.sendToServer(
                        new ControlledRelationPayload(this.data().villageUuid(), re.villageId(), 100), new CustomPacketPayload[0]
                     )
                  )
                  .bounds(gx, btnRow1Y, 52, 14)
                  .build()
            );
            gx += 54;
            this.addPageButton(
               Button.builder(
                     Component.translatable("gui.millenaire.controlled_military.relneutral"),
                     b -> PacketDistributor.sendToServer(
                        new ControlledRelationPayload(this.data().villageUuid(), re.villageId(), 0), new CustomPacketPayload[0]
                     )
                  )
                  .bounds(gx, btnRow1Y, 52, 14)
                  .build()
            );
            gx += 54;
            this.addPageButton(
               Button.builder(
                     Component.translatable("gui.millenaire.controlled_military.relbad"),
                     b -> PacketDistributor.sendToServer(
                        new ControlledRelationPayload(this.data().villageUuid(), re.villageId(), -100), new CustomPacketPayload[0]
                     )
                  )
                  .bounds(gx, btnRow1Y, 52, 14)
                  .build()
            );
            int btnRow2Y = btnRow1Y + 14 + 2;
            RaidButtonState state = RaidButtonState.compute(currentRaidTarget, this.data().raidInProgress(), re.villageId());
            switch (state) {
               case PLAN:
               case PLANNED_OTHER:
                  this.addPageButton(
                     Button.builder(
                           Component.translatable("gui.millenaire.controlled_military.raid"),
                           b -> PacketDistributor.sendToServer(
                              new RaidActionPayload(this.data().villageUuid(), re.villageId(), 0, true), new CustomPacketPayload[0]
                           )
                        )
                        .bounds(contentX, btnRow2Y, 90, 14)
                        .build()
                  );
                  break;
               case CANCEL:
                  this.addPageButton(
                     Button.builder(
                           Component.translatable("gui.millenaire.controlled_military.raidcancel"),
                           b -> PacketDistributor.sendToServer(
                              new RaidActionPayload(this.data().villageUuid(), re.villageId(), 1, true), new CustomPacketPayload[0]
                           )
                        )
                        .bounds(contentX, btnRow2Y, 90, 14)
                        .build()
                  );
               case IN_PROGRESS_HERE:
               case IN_PROGRESS_OTHER:
            }

            rowY += 58;
         }
      }
   }

   private void addPageButton(Button btn) {
      this.addRenderableWidget(btn);
      this.pageButtons.add(btn);
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 1610612736);
      int parchX = (this.width - 204) / 2;
      int parchY = (this.height - 220) / 2;
      PanelRenderHelper.renderTexturedBackground(graphics, PanelRenderHelper.PANEL_TEXTURE, parchX, parchY, 204, 220);
      int y = this.renderParchmentHeader(graphics, this.getTitle(), parchX, parchY, 204);
      if (!this.data().villageName().isEmpty()) {
         graphics.drawString(this.font, Component.literal(this.data().villageName()), parchX + 16, y, 5914656, false);
         y += 9 + 4;
      }

      if (this.currentPage == this.militaryPageIndex()) {
         this.renderMilitaryPage(graphics, parchX, parchY, y);
      } else if (this.isCulturePage()) {
         this.renderCultureRows(graphics, parchX, y);
      } else if (this.data().relations().isEmpty()) {
         graphics.drawString(this.font, Component.translatable("gui.millenaire.chief.diplomacy_none"), parchX + 16, y, 5914656, false);
      } else {
         this.renderRelationRows(graphics, parchX, y);
      }

      this.renderPageCounter(graphics, parchX, parchY, 204, 220);
      super.render(graphics, mouseX, mouseY, partialTick);
   }

   private void renderCultureRows(GuiGraphics graphics, int parchX, int startY) {
      graphics.drawString(this.font, Component.translatable("gui.millenaire.controlled_military.culture_title"), parchX + 16, startY, -14018560, false);
      int rowY = startY + 9 + 4;
      int contentX = parchX + 16;
      int pageWithinCulture = this.currentPage - this.culturePageOffset;
      int startIdx = pageWithinCulture * this.rowsPerPage;
      int endIdx = Math.min(startIdx + this.rowsPerPage, this.data().cultureStandings().size());

      if (startIdx >= endIdx) {
         graphics.drawString(this.font, Component.translatable("gui.millenaire.controlled_military.culture_none"), contentX, rowY, 5914656, false);
      } else {
         for (int i = startIdx; i < endIdx; i++) {
            ControlledMilitaryPayload.CultureStandingEntry ce = this.data().cultureStandings().get(i);
            graphics.drawString(this.font, Component.literal(ce.displayName()), contentX, rowY, 4202512, false);
            int color = ce.atWar() ? 11141120 : ce.standing() >= CultureStandingSavedData.ALLIED_THRESHOLD ? 5635925 : 6963232;
            String statusKey = ce.atWar()
               ? "gui.millenaire.controlled_military.culture_at_war"
               : ce.standing() >= CultureStandingSavedData.ALLIED_THRESHOLD
                  ? "gui.millenaire.controlled_military.culture_allied"
                  : "gui.millenaire.controlled_military.culture_standing";
            graphics.drawString(
               this.font,
               Component.translatable(statusKey, ce.standing()),
               contentX,
               rowY + 9 + 1,
               0xFF000000 | color,
               false
            );
            rowY += 58;
         }
      }
   }

   private void renderRelationRows(GuiGraphics graphics, int parchX, int startY) {
      int contentX = parchX + 16;
      int rowY = startY;
      int startIdx = this.currentPage * this.rowsPerPage;
      int endIdx = Math.min(startIdx + this.rowsPerPage, this.data().relations().size());
      String currentRaidTarget = this.data().currentRaidTargetVillageId().isEmpty() ? null : this.data().currentRaidTargetVillageId();

      for (int i = startIdx; i < endIdx; i++) {
         ControlledMilitaryPayload.RelationEntry re = this.data().relations().get(i);
         graphics.drawString(this.font, Component.literal(re.villageName() + " (" + re.cultureName() + ")"), contentX, rowY, 4202512, false);
         ChatFormatting cf = VillageRelations.getRelationColor(re.relation());
         int relColor = cf.getColor() != null ? 0xFF000000 | cf.getColor() : 6963232;
         String relLabel = Component.translatable(re.relationLabel()).getString();
         graphics.drawString(this.font, Component.literal(relLabel + " (" + re.relation() + ")"), contentX, rowY + 9 + 1, relColor, false);
         RaidButtonState state = RaidButtonState.compute(currentRaidTarget, this.data().raidInProgress(), re.villageId());
         int btnRow2Y = rowY + 2 * (9 + 1) + 2 + 14 + 2;
         switch (state) {
            case PLAN:
            default:
               break;
            case PLANNED_OTHER:
               this.drawStatus(graphics, contentX, btnRow2Y, "gui.millenaire.chief.other_raid_planned", 16733525);
               break;
            case CANCEL:
               this.drawStatus(graphics, contentX, btnRow2Y, "gui.millenaire.chief.raid_planned", 16733525);
               break;
            case IN_PROGRESS_HERE:
               this.drawStatus(graphics, contentX, btnRow2Y, "gui.millenaire.chief.raid_in_progress", 11141120);
               break;
            case IN_PROGRESS_OTHER:
               this.drawStatus(graphics, contentX, btnRow2Y, "gui.millenaire.chief.other_raid_in_progress", 11141120);
         }

         rowY += 58;
      }
   }

   private void drawStatus(GuiGraphics graphics, int x, int y, String key, int color) {
      graphics.drawString(this.font, Component.translatable(key), x + 90 + 6, y + 3, 0xFF000000 | color, false);
   }

   private void renderMilitaryPage(GuiGraphics graphics, int parchX, int parchY, int startY) {
      graphics.drawString(this.font, Component.translatable("gui.millenaire.chief.military_title"), parchX + 16, startY, -14018560, false);
      int linesStartY = startY + 9 + 4;
      int contentEndY = parchY + 220 - 15;
      int maxLines = Math.max(0, (contentEndY - linesStartY) / 11);
      PanelRenderHelper.renderPanelLines(graphics, this.font, this.wrappedMilitaryLines, parchX + 16, linesStartY, textWidth(204), 0, maxLines);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }
}
