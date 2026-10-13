package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.network.WorldLedgerCityRequestPayload;
import org.millenaire.network.WorldLedgerPayload;
import org.millenaire.village.VillageRelations;

public class WorldLedgerScreen extends AbstractMillenaireScreen {
   private static final int WIDTH = 320;
   private static final int HEIGHT = 240;
   private static final int INK = PanelRenderHelper.TITLE_COLOR;
   private static final int MUTED = PanelRenderHelper.TEXT_COLOR;
   private static final int GREEN = -16733696;
   private static final int RED = -5636096;
   private static final int BLUE = -16777046;
   private static final int GOLD = -3314944;

   private final WorldLedgerPayload data;
   private Tab tab = Tab.OVERVIEW;
   private final List<Button> tabButtons = new ArrayList<>();
   private final List<Button> actionButtons = new ArrayList<>();
   private int rowsPerPage = 8;
   private int listSize;

   public WorldLedgerScreen(WorldLedgerPayload data) {
      super(Component.translatable("gui.millenaire.ledger.title"));
      this.data = data;
   }

   private enum Tab {
      OVERVIEW,
      CULTURES,
      WARS,
      TRADE,
      CITY
   }

   @Override
   protected void init() {
      super.init();
      int x = (this.width - WIDTH) / 2;
      int y = (this.height - HEIGHT) / 2;
      this.rebuildTabs(x, y);
      this.rebuildActions(x, y);
      this.recomputeListPaging(x, y);
   }

   private void rebuildTabs(int x, int y) {
      for (Button b : this.tabButtons) {
         this.removeWidget(b);
      }
      this.tabButtons.clear();
      int tabY = y + 40;
      int tabW = 58;
      int gap = 2;
      int startX = x + 12;
      this.addTab(startX, tabY, tabW, Tab.OVERVIEW, "gui.millenaire.ledger.tab.overview");
      this.addTab(startX + (tabW + gap), tabY, tabW, Tab.CULTURES, "gui.millenaire.ledger.tab.cultures");
      this.addTab(startX + 2 * (tabW + gap), tabY, tabW, Tab.WARS, "gui.millenaire.ledger.tab.wars");
      this.addTab(startX + 3 * (tabW + gap), tabY, tabW, Tab.TRADE, "gui.millenaire.ledger.tab.trade");
      this.addTab(startX + 4 * (tabW + gap), tabY, tabW, Tab.CITY, "gui.millenaire.ledger.tab.city");
   }

   private void addTab(int x, int y, int w, Tab t, String key) {
      Button button = Button.builder(Component.translatable(key), b -> {
            this.tab = t;
            this.currentPage = 0;
            this.rebuildActions((this.width - WIDTH) / 2, (this.height - HEIGHT) / 2);
            this.recomputeListPaging((this.width - WIDTH) / 2, (this.height - HEIGHT) / 2);
         })
         .bounds(x, y, w, 16)
         .build();
      this.tabButtons.add(button);
      this.addRenderableWidget(button);
   }

   private void rebuildActions(int x, int y) {
      for (Button b : this.actionButtons) {
         this.removeWidget(b);
      }
      this.actionButtons.clear();
      if (this.tab == Tab.CITY && this.data.hasOwnedCity() && this.data.ownedVillageUuid() != null) {
         Button openCity = Button.builder(Component.translatable("gui.millenaire.ledger.open_city"), b -> PacketDistributor.sendToServer(
               new WorldLedgerCityRequestPayload(this.data.ownedVillageUuid()),
               new CustomPacketPayload[0]
            ))
            .bounds(x + 16, y + HEIGHT - 36, 140, 16)
            .build();
         this.actionButtons.add(openCity);
         this.addRenderableWidget(openCity);
      }
      if (this.tab == Tab.OVERVIEW) {
         Button travel = Button.builder(Component.translatable("gui.millenaire.info_panel.travel_book"), b -> TravelBookNavHelper.openHome(this))
            .bounds(x + WIDTH - 16 - 100, y + HEIGHT - 36, 100, 16)
            .build();
         this.actionButtons.add(travel);
         this.addRenderableWidget(travel);
      }
   }

   private void recomputeListPaging(int x, int y) {
      this.listSize = switch (this.tab) {
         case CULTURES -> this.data.cultures().size();
         case WARS -> this.data.wars().size();
         case TRADE -> this.data.tradeRanks().size();
         default -> 0;
      };
      int contentTop = y + 62;
      int contentBottom = y + HEIGHT - 40;
      this.rowsPerPage = Math.max(1, (contentBottom - contentTop) / 18);
      this.totalPages = this.listSize == 0 ? 1 : Math.max(1, (this.listSize + this.rowsPerPage - 1) / this.rowsPerPage);
      if (this.prevButton == null || this.nextButton == null) {
         this.initPaginationButtons(x, y, WIDTH, HEIGHT, 16, true);
      }
      this.updatePaginationButtons();
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 0xC0101010);
      int x = (this.width - WIDTH) / 2;
      int y = (this.height - HEIGHT) / 2;
      PanelRenderHelper.renderTexturedBackground(graphics, PanelRenderHelper.QUEST_TEXTURE, x, y, WIDTH, HEIGHT);
      int headerY = this.renderParchmentHeader(graphics, this.title, x, y, WIDTH);

      // tab strip background
      graphics.fill(x + 10, y + 38, x + WIDTH - 10, y + 58, PanelRenderHelper.PARCHMENT_BG_DARK);
      graphics.renderOutline(x + 10, y + 38, WIDTH - 20, 20, PanelRenderHelper.PARCHMENT_BORDER);

      int contentY = y + 62;
      switch (this.tab) {
         case OVERVIEW -> this.renderOverview(graphics, x, contentY);
         case CULTURES -> this.renderCultures(graphics, x, contentY);
         case WARS -> this.renderWars(graphics, x, contentY);
         case TRADE -> this.renderTrade(graphics, x, contentY);
         case CITY -> this.renderCity(graphics, x, contentY);
      }

      this.renderPageCounter(graphics, x, y, WIDTH, HEIGHT);
      super.render(graphics, mouseX, mouseY, partialTick);
      // suppress unused
      if (headerY < 0) {
         return;
      }
   }

   private void drawCard(GuiGraphics g, int x, int y, int w, int h) {
      g.fill(x, y, x + w, y + h, PanelRenderHelper.PARCHMENT_BG_DARK);
      g.renderOutline(x, y, w, h, PanelRenderHelper.PARCHMENT_BORDER);
   }

   private void renderOverview(GuiGraphics g, int x, int y) {
      int cardW = 92;
      int cardH = 44;
      int gap = 6;
      this.drawCard(g, x + 14, y, cardW, cardH);
      g.drawString(this.font, Component.translatable("gui.millenaire.ledger.stat.villages"), x + 20, y + 6, BLUE, false);
      g.drawString(this.font, Component.literal(String.valueOf(this.data.villageCount())), x + 20, y + 22, INK, false);

      this.drawCard(g, x + 14 + cardW + gap, y, cardW, cardH);
      g.drawString(this.font, Component.translatable("gui.millenaire.ledger.stat.camps"), x + 20 + cardW + gap, y + 6, BLUE, false);
      g.drawString(this.font, Component.literal(String.valueOf(this.data.loneBuildingCount())), x + 20 + cardW + gap, y + 22, INK, false);

      this.drawCard(g, x + 14 + 2 * (cardW + gap), y, cardW, cardH);
      g.drawString(this.font, Component.translatable("gui.millenaire.ledger.stat.raids"), x + 20 + 2 * (cardW + gap), y + 6, RED, false);
      g.drawString(this.font, Component.literal(String.valueOf(this.data.activeRaidCount())), x + 20 + 2 * (cardW + gap), y + 22, INK, false);

      int y2 = y + cardH + 10;
      this.drawCard(g, x + 14, y2, WIDTH - 28, 54);
      g.drawString(this.font, Component.translatable("gui.millenaire.ledger.overview.wars", this.data.activeWarCount()), x + 22, y2 + 8, this.data.activeWarCount() > 0 ? RED : GREEN, false);
      String allegiance = this.data.playerCulturePath() == null
         ? I18n.get("gui.millenaire.ledger.overview.no_allegiance")
         : I18n.get("gui.millenaire.ledger.overview.allegiance", allegianceLabel());
      g.drawString(this.font, allegiance, x + 22, y2 + 22, MUTED, false);
      int totalBounty = 0;
      int bountyCultures = 0;
      for (WorldLedgerPayload.CultureRow c : this.data.cultures()) {
         if (c.bounty() > 0) {
            totalBounty += c.bounty();
            bountyCultures++;
         }
      }
      g.drawString(
         this.font,
         Component.translatable("gui.millenaire.ledger.overview.bounty", totalBounty, bountyCultures),
         x + 22,
         y2 + 36,
         totalBounty > 0 ? RED : GOLD,
         false
      );

      int y3 = y2 + 62;
      this.drawCard(g, x + 14, y3, WIDTH - 28, 56);
      g.drawString(this.font, Component.translatable("gui.millenaire.ledger.overview.top_cultures"), x + 22, y3 + 6, BLUE, false);
      int row = 0;
      for (WorldLedgerPayload.CultureRow c : this.data.cultures()) {
         if (row >= 3) {
            break;
         }
         String name = resolveDisplay(c.displayNameKey());
         String rep = MillenaireScreenUtils.resolveReputationLabel(c.reputationLabel());
         int color = c.reputation() > 0 ? GREEN : (c.reputation() < 0 ? RED : MUTED);
         g.drawString(this.font, (row + 1) + ". " + name + " — " + rep, x + 22, y3 + 20 + row * 11, color, false);
         row++;
      }
   }

   private void renderCultures(GuiGraphics g, int x, int y) {
      int start = this.currentPage * this.rowsPerPage;
      int end = Math.min(start + this.rowsPerPage, this.data.cultures().size());
      for (int i = start; i < end; i++) {
         WorldLedgerPayload.CultureRow c = this.data.cultures().get(i);
         int rowY = y + (i - start) * 18;
         this.drawCard(g, x + 14, rowY, WIDTH - 28, 16);
         String name = resolveDisplay(c.displayNameKey());
         if (c.controlledByPlayer()) {
            name = name + " ★";
         }
         int color = c.reputation() > 0 ? GREEN : (c.reputation() < 0 ? RED : INK);
         g.drawString(this.font, name, x + 20, rowY + 4, color, false);
         String meta = I18n.get(
            "gui.millenaire.ledger.culture.meta_bounty",
            MillenaireScreenUtils.resolveReputationLabel(c.reputationLabel()),
            languageLabel(c.languageScore()),
            c.villageCount(),
            c.bounty()
         );
         g.drawString(this.font, meta, x + 140, rowY + 4, c.bounty() > 0 ? RED : MUTED, false);
      }
   }

   private void renderWars(GuiGraphics g, int x, int y) {
      if (this.data.wars().isEmpty()) {
         this.drawCard(g, x + 14, y, WIDTH - 28, 28);
         g.drawString(this.font, Component.translatable("gui.millenaire.ledger.wars.empty"), x + 22, y + 10, GREEN, false);
         return;
      }
      int start = this.currentPage * this.rowsPerPage;
      int end = Math.min(start + this.rowsPerPage, this.data.wars().size());
      for (int i = start; i < end; i++) {
         WorldLedgerPayload.WarRow w = this.data.wars().get(i);
         int rowY = y + (i - start) * 18;
         this.drawCard(g, x + 14, rowY, WIDTH - 28, 16);
         String pair = resolveDisplay(w.displayAKey()) + "  ↔  " + resolveDisplay(w.displayBKey());
         Integer formattingColor = VillageRelations.getRelationColor(w.standing()).getColor();
         int color = w.atWar() ? RED : (formattingColor != null ? formattingColor : MUTED);
         g.drawString(this.font, pair, x + 20, rowY + 4, color, false);
         String status = w.atWar()
            ? I18n.get("gui.millenaire.ledger.wars.at_war")
            : I18n.get(VillageRelations.getRelationKey(w.standing())) + " (" + w.standing() + ")";
         g.drawString(this.font, status, x + 210, rowY + 4, MUTED, false);
      }
   }

   private void renderTrade(GuiGraphics g, int x, int y) {
      g.drawString(this.font, Component.translatable("gui.millenaire.ledger.trade.subtitle"), x + 16, y - 2, BLUE, false);
      int start = this.currentPage * this.rowsPerPage;
      int end = Math.min(start + this.rowsPerPage, this.data.tradeRanks().size());
      int listY = y + 10;
      for (int i = start; i < end; i++) {
         WorldLedgerPayload.TradeRow t = this.data.tradeRanks().get(i);
         int rowY = listY + (i - start) * 18;
         this.drawCard(g, x + 14, rowY, WIDTH - 28, 16);
         String name = "#" + (i + 1) + "  " + resolveDisplay(t.displayNameKey());
         g.drawString(this.font, name, x + 20, rowY + 4, INK, false);
         String chip = I18n.get("gui.millenaire.ledger.trade.hint." + t.tradeHint());
         int chipColor = switch (t.tradeHint()) {
            case "allied" -> GREEN;
            case "war" -> RED;
            case "poor" -> GOLD;
            case "same" -> BLUE;
            default -> MUTED;
         };
         g.drawString(this.font, chip + "  (" + t.rankScore() + ")", x + 180, rowY + 4, chipColor, false);
      }
   }

   private void renderCity(GuiGraphics g, int x, int y) {
      if (!this.data.hasOwnedCity()) {
         this.drawCard(g, x + 14, y, WIDTH - 28, 48);
         g.drawString(this.font, Component.translatable("gui.millenaire.ledger.city.empty"), x + 22, y + 18, MUTED, false);
         return;
      }
      this.drawCard(g, x + 14, y, WIDTH - 28, 90);
      String title = this.data.ownedVillageName() != null && !this.data.ownedVillageName().isEmpty()
         ? this.data.ownedVillageName()
         : I18n.get("gui.millenaire.ledger.city.unnamed");
      g.drawString(this.font, title, x + 22, y + 10, INK, false);
      if (this.data.ownedCulturePath() != null) {
         g.drawString(this.font, allegianceLabel(), x + 22, y + 24, BLUE, false);
      }
      g.drawString(
         this.font,
         Component.translatable(
            "gui.millenaire.city.population",
            this.data.ownedPopulation(),
            this.data.ownedMaxSlots(),
            this.data.ownedOpenJobs(),
            this.data.ownedOpenBeds()
         ),
         x + 22,
         y + 40,
         MUTED,
         false
      );
      g.drawString(
         this.font,
         Component.translatable("gui.millenaire.city.threat." + (this.data.ownedThreatLabel() == null ? "none" : this.data.ownedThreatLabel())),
         x + 22,
         y + 54,
         RED,
         false
      );
      g.drawString(this.font, Component.translatable("gui.millenaire.ledger.city.hint"), x + 22, y + 70, GOLD, false);
   }

   private static String languageLabel(int score) {
      if (score >= 500) {
         return I18n.get("gui.millenaire.info_panel.lang_fluent");
      }
      if (score >= 200) {
         return I18n.get("gui.millenaire.info_panel.lang_moderate");
      }
      if (score >= 100) {
         return I18n.get("gui.millenaire.info_panel.lang_beginner");
      }
      return I18n.get("gui.millenaire.info_panel.lang_minimal");
   }

   private String allegianceLabel() {
      if (this.data.ownedVillageName() != null && !this.data.ownedVillageName().isEmpty()) {
         return this.data.ownedVillageName();
      }
      if (this.data.playerCulturePath() == null) {
         return "";
      }
      return resolveDisplay("culture.millenaire." + this.data.playerCulturePath());
   }

   /** Prefer translated culture keys; otherwise treat as an already-resolved display name. */
   private static String resolveDisplay(String keyOrName) {
      if (keyOrName == null || keyOrName.isEmpty()) {
         return "";
      }
      if (keyOrName.startsWith("culture.millenaire.")) {
         String translated = I18n.get(keyOrName);
         if (!translated.equals(keyOrName)) {
            return translated;
         }
         return keyOrName.substring("culture.millenaire.".length());
      }
      return keyOrName;
   }
}
