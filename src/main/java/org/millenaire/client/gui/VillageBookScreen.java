package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.network.VillageBookActionPayload;
import org.millenaire.network.VillageBookPayload;
import org.millenaire.network.VillageBookRefreshRequestPayload;
import org.millenaire.village.panel.PanelContent;
import org.millenaire.village.panel.PanelLine;

public class VillageBookScreen extends AbstractMillenaireScreen {
   private static final int PARCHMENT_WIDTH = 204;
   private static final int PARCHMENT_HEIGHT = 220;
   private static final int TABS_AND_LABEL_HEIGHT = 30;
   private static final int LINES_PER_PAGE = computeMaxLinesPerPage(220) - 2;
   private static final int TEXT_WIDTH = textWidth(204);
   private static final int MAP_HEIGHT = 120;
   public static final int CIVILIZATION_SECTION = 6;
   private static final String CIV_ACTION_STATE = "civ_action";
   private static final String[] SECTION_LABEL_KEYS = new String[]{
      "gui.millenaire.book.section.resume",
      "gui.millenaire.book.section.population",
      "gui.millenaire.book.section.constructions",
      "gui.millenaire.book.section.projects",
      "gui.millenaire.book.section.resources",
      "gui.millenaire.book.section.military",
      "gui.millenaire.book.section.civilization",
      "gui.millenaire.book.section.chronicle"
   };
   private static final String[] SECTION_TAB_KEYS = new String[]{
      "gui.millenaire.book.tab.resume",
      "gui.millenaire.book.tab.population",
      "gui.millenaire.book.tab.constructions",
      "gui.millenaire.book.tab.projects",
      "gui.millenaire.book.tab.resources",
      "gui.millenaire.book.tab.military",
      "gui.millenaire.book.tab.civilization",
      "gui.millenaire.book.tab.chronicle"
   };
   private VillageBookPayload payload;
   private int currentSection = 0;
   private List<List<PanelLine>> wrappedSections = List.of();
   private final List<Button> sectionButtons = new ArrayList<>();
   @Nullable
   private Button refreshButton;
   private boolean busy;
   private String selectedTargetId = "";
   private int pendingSection;
   private int pendingPage;
   @Nullable
   private Component feedbackMessage;
   private boolean feedbackSuccess;
   @Nullable
   private String mapTooltip;

   public VillageBookScreen(VillageBookPayload payload) {
      super(Component.literal(payload.villageName()));
      this.payload = payload;
      this.selectedTargetId = payload.selectedTargetId() != null ? payload.selectedTargetId() : "";
      this.pendingSection = Math.max(0, payload.restoreSection());
      this.pendingPage = Math.max(0, payload.restorePage());
      this.applyFeedbackFromPayload(payload);
   }

   public boolean sameVillage(VillageBookPayload other) {
      if (other == null || other.villageId() == null || other.villageId().isEmpty()) {
         return false;
      }
      String mine = this.payload.villageId();
      return mine != null && !mine.isEmpty() && mine.equals(other.villageId());
   }

   public void applyUpdate(VillageBookPayload update) {
      this.payload = update;
      this.busy = false;
      this.selectedTargetId = update.selectedTargetId() != null ? update.selectedTargetId() : "";
      this.pendingSection = Math.max(0, update.restoreSection());
      this.pendingPage = Math.max(0, update.restorePage());
      this.applyFeedbackFromPayload(update);
      this.rebuildWidgets();
   }

   private void applyFeedbackFromPayload(VillageBookPayload update) {
      if (update.hasFeedback() && update.feedbackKey() != null && !update.feedbackKey().isEmpty()) {
         this.feedbackSuccess = update.feedbackSuccess();
         if (update.feedbackDetail() != null && !update.feedbackDetail().isEmpty()) {
            String detail = update.feedbackDetail();
            if (detail.startsWith("civilization.millenaire.") || detail.startsWith("living.millenaire.")) {
               this.feedbackMessage = Component.translatable(update.feedbackKey(), Component.translatable(detail));
            } else {
               this.feedbackMessage = Component.translatable(update.feedbackKey(), detail);
            }
         } else {
            this.feedbackMessage = Component.translatable(update.feedbackKey());
         }
      } else {
         this.feedbackMessage = null;
      }
   }

   protected void init() {
      super.init();
      this.sectionButtons.clear();
      this.refreshButton = null;
      List<List<PanelLine>> wrapped = new ArrayList<>();
      for (PanelContent panelSection : this.payload.sections()) {
         wrapped.add(PanelRenderHelper.wrapLines(panelSection.lines(), this.font, TEXT_WIDTH));
      }
      this.wrappedSections = wrapped;
      int section = this.pendingSection;
      int page = this.pendingPage;
      this.currentSection = Math.min(section, Math.max(0, this.wrappedSections.size() - 1));
      this.currentPage = Math.max(0, page);

      int parchX = (this.width - 204) / 2;
      int parchY = (this.height - 220) / 2;
      this.sectionButtons.clear();
      int sectionCount = Math.min(this.payload.sections().size(), SECTION_LABEL_KEYS.length);
      int tabY = parchY + 16 + 20 + 4;
      int tabTotalWidth = 172;
      int tabWidth = sectionCount > 0 ? tabTotalWidth / sectionCount : tabTotalWidth;

      for (int i = 0; i < sectionCount; i++) {
         int sectionIndex = i;
         Component shortLabel = i < SECTION_TAB_KEYS.length ? Component.translatable(SECTION_TAB_KEYS[i]) : Component.literal("§" + i);
         Component fullLabel = i < SECTION_LABEL_KEYS.length
            ? Component.translatable(SECTION_LABEL_KEYS[i])
            : Component.translatable("gui.millenaire.book.section_fallback", new Object[]{i});
         Button btn = Button.builder(shortLabel, button -> {
            this.currentSection = sectionIndex;
            this.currentPage = 0;
            this.feedbackMessage = null;
            this.recalcPages();
            this.updatePaginationButtons();
            this.updateRefreshButton();
         }).bounds(parchX + 16 + i * tabWidth, tabY, tabWidth, 14).tooltip(Tooltip.create(fullLabel)).build();
         this.sectionButtons.add(btn);
         this.addRenderableWidget(btn);
      }

      this.refreshButton = Button.builder(Component.translatable("gui.millenaire.book.refresh"), button -> this.sendRefresh())
         .bounds(parchX + 16, parchY + 220 - 28, 52, 12)
         .tooltip(Tooltip.create(Component.translatable("gui.millenaire.book.refresh")))
         .build();
      this.addRenderableWidget(this.refreshButton);

      this.initPaginationButtons(parchX, parchY, 204, 220, 16, true);
      this.recalcPages();
      if (this.currentPage >= this.totalPages) {
         this.currentPage = Math.max(0, this.totalPages - 1);
      }
      this.updatePaginationButtons();
      this.updateRefreshButton();
   }

   private void updateRefreshButton() {
      if (this.refreshButton != null) {
         boolean onCiv = this.currentSection == CIVILIZATION_SECTION;
         this.refreshButton.visible = onCiv;
         this.refreshButton.active = onCiv && !this.busy && this.hasVillageId();
      }
   }

   private boolean hasVillageId() {
      return this.payload.villageId() != null && !this.payload.villageId().isEmpty();
   }

   private void sendRefresh() {
      if (this.busy || !this.hasVillageId()) {
         return;
      }
      this.busy = true;
      this.updateRefreshButton();
      PacketDistributor.sendToServer(
         new VillageBookRefreshRequestPayload(
            this.payload.villageId(),
            this.currentSection,
            this.currentPage,
            this.selectedTargetId == null ? "" : this.selectedTargetId
         ),
         new CustomPacketPayload[0]
      );
   }

   private void sendCivAction(PanelLine.PanelNavTarget target) {
      if (this.busy || !this.hasVillageId() || target == null) {
         return;
      }
      String actionId = target.cultureKey() == null ? "" : target.cultureKey();
      if (actionId.isEmpty()) {
         return;
      }
      String targetId = target.categoryKey() == null ? "" : target.categoryKey();
      this.selectedTargetId = targetId;
      this.busy = true;
      this.updateRefreshButton();
      String requestId = UUID.randomUUID().toString();
      PacketDistributor.sendToServer(
         new VillageBookActionPayload(
            this.payload.villageId(), actionId, targetId, requestId, this.currentSection, this.currentPage
         ),
         new CustomPacketPayload[0]
      );
   }

   private void recalcPages() {
      if (this.wrappedSections.isEmpty()) {
         this.totalPages = 1;
      } else {
         int sectionIdx = Math.min(this.currentSection, this.wrappedSections.size() - 1);
         List<PanelLine> lines = this.wrappedSections.get(sectionIdx);
         int textPages = PanelRenderHelper.computePageCount(lines.size(), LINES_PER_PAGE);
         boolean hasMap = this.currentSection == 0 && this.payload.hasMapData();
         this.totalPages = hasMap ? 1 + textPages : textPages;
      }
   }

   @Override
   protected void updatePaginationButtons() {
      super.updatePaginationButtons();

      for (int i = 0; i < this.sectionButtons.size(); i++) {
         this.sectionButtons.get(i).active = i != this.currentSection && !this.busy;
      }
      this.updateRefreshButton();
   }

   private int computeContentStartY(int parchY) {
      int y = parchY + 16;
      y += 20;
      y += 18;
      if (this.payload.degraded()) {
         y += 12;
      }
      if (this.currentSection < SECTION_LABEL_KEYS.length) {
         y += 12;
      }
      y += 6;
      return y;
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0 && !this.busy && !this.wrappedSections.isEmpty()) {
         boolean hasMap = this.currentSection == 0 && this.payload.hasMapData();
         boolean isMapPage = hasMap && this.currentPage == 0;
         if (!isMapPage) {
            int parchX = (this.width - 204) / 2;
            int parchY = (this.height - 220) / 2;
            int contentX = parchX + 16;
            int contentY = this.computeContentStartY(parchY);
            int sectionIdx = Math.min(this.currentSection, this.wrappedSections.size() - 1);
            int textPage = hasMap ? this.currentPage - 1 : this.currentPage;
            int startLine = textPage * LINES_PER_PAGE;
            List<PanelLine> lines = this.wrappedSections.get(sectionIdx);
            PanelLine.PanelNavTarget target = PanelRenderHelper.getClickedNavTarget(
               lines, contentX, contentY, TEXT_WIDTH, startLine, LINES_PER_PAGE, mouseX, mouseY
            );
            if (target != null && CIV_ACTION_STATE.equals(target.targetStateName())) {
               this.sendCivAction(target);
               return true;
            }
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 1610612736);
      int parchX = (this.width - 204) / 2;
      int parchY = (this.height - 220) / 2;
      PanelRenderHelper.renderTexturedBackground(graphics, PanelRenderHelper.PARCHMENT_TEXTURE, parchX, parchY, 204, 220);
      int contentX = parchX + 16;
      int y = parchY + 16;
      Component bookTitle = Component.translatable("gui.millenaire.book.title", new Object[]{this.payload.villageName()}).withStyle(s -> s.withBold(true));
      int titleX = parchX + 102 - this.font.width(bookTitle) / 2;
      graphics.drawString(this.font, bookTitle, titleX, y, -14018560, false);
      y += 20;
      y += 18;
      if (this.payload.degraded()) {
         graphics.drawCenteredString(this.font, Component.translatable("gui.millenaire.book.out_of_range"), parchX + 102, y, -3381760);
         y += 12;
      }

      if (this.currentSection < SECTION_LABEL_KEYS.length) {
         graphics.drawCenteredString(this.font, Component.translatable(SECTION_LABEL_KEYS[this.currentSection]), parchX + 102, y, -13421773);
         y += 12;
      }

      graphics.fill(contentX, y, parchX + 204 - 16, y + 1, -5601178);
      y += 6;
      this.mapTooltip = null;
      if (!this.wrappedSections.isEmpty()) {
         int sectionIdx = Math.min(this.currentSection, this.wrappedSections.size() - 1);
         boolean hasMap = this.currentSection == 0 && this.payload.hasMapData();
         if (hasMap && this.currentPage == 0) {
            float playerYaw = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getYRot() : 0.0F;
            this.mapTooltip = PanelRenderHelper.renderVillageMap(
               graphics,
               this.font,
               this.payload.mapBuildings(),
               this.payload.mapVillagers(),
               this.payload.mapPlayerX(),
               this.payload.mapPlayerZ(),
               playerYaw,
               this.payload.mapCenterX(),
               this.payload.mapCenterZ(),
               this.payload.mapTerrain(),
               this.payload.mapPaths(),
               contentX,
               y,
               172,
               120,
               mouseX,
               mouseY
            );
         } else if (this.currentSection == 0 && !this.payload.hasMapData() && this.currentPage == 0) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.millenaire.book.map_unavailable"), parchX + 102, y + 60 - 4, -13421773);
         }

         if (!hasMap || this.currentPage != 0) {
            int textPage = hasMap ? this.currentPage - 1 : this.currentPage;
            List<PanelLine> lines = this.wrappedSections.get(sectionIdx);
            int startLine = textPage * LINES_PER_PAGE;
            PanelRenderHelper.renderPanelLines(graphics, this.font, lines, contentX, y, TEXT_WIDTH, startLine, LINES_PER_PAGE, mouseX, mouseY);
         }
      }

      if (this.feedbackMessage != null) {
         int feedbackColor = this.feedbackSuccess ? -14522659 : -3381760;
         graphics.drawCenteredString(this.font, this.feedbackMessage, parchX + 102, parchY + 220 - 40, feedbackColor);
      }

      this.renderPageCounter(graphics, parchX, parchY, 204, 220);
      super.render(graphics, mouseX, mouseY, partialTick);
      if (this.mapTooltip != null) {
         graphics.renderTooltip(this.font, Component.literal(this.mapTooltip), mouseX, mouseY);
      }
   }
}
