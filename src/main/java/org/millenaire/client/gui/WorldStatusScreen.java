package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.civilization.presentation.CivilizationVillageSnapshot;
import org.millenaire.civilization.presentation.WorldStatusSnapshot;
import org.millenaire.network.WorldStatusActionPayload;
import org.millenaire.network.WorldStatusContentPayload;
import org.millenaire.network.WorldStatusRequestPayload;
import org.millenaire.village.panel.PanelLine;

/**
 * Full-world civilization command center: rankings, cultures, wars, diplomacy, player settlements.
 */
public class WorldStatusScreen extends AbstractMillenaireScreen {
   private static final int PARCHMENT_WIDTH = 256;
   private static final int PARCHMENT_HEIGHT = 220;
   private static final int LINES_PER_PAGE = Math.max(1, computeMaxLinesPerPage(220) - 3);
   private static final int TEXT_WIDTH = textWidth(256);
   private static final int COLOR_HEADER = -16777046;
   private static final int COLOR_YOU = -14522659;
   private static final int COLOR_WAR = -5636096;
   private static final int COLOR_MUTED = -7829368;
   private static final String CIV_ACTION = "civ_action";
   private static final String SELECT_FACTION = "select_faction";
   private static final String[] TAB_KEYS = new String[]{
      "gui.millenaire.world_status.tab.overview",
      "gui.millenaire.world_status.tab.cultures",
      "gui.millenaire.world_status.tab.rankings",
      "gui.millenaire.world_status.tab.wars",
      "gui.millenaire.world_status.tab.diplomacy",
      "gui.millenaire.world_status.tab.settlements",
      "gui.millenaire.world_status.tab.activity"
   };

   private WorldStatusContentPayload payload;
   private int currentTab;
   private List<PanelLine> wrappedLines = List.of();
   private final List<Button> tabButtons = new ArrayList<>();
   @Nullable
   private Button refreshButton;
   private boolean busy;
   private String selectedFactionId = "";

   public WorldStatusScreen(WorldStatusContentPayload payload) {
      super(Component.translatable("gui.millenaire.world_status.title"));
      this.payload = payload;
      WorldStatusSnapshot snap = payload.snapshot();
      this.currentTab = Math.max(0, Math.min(TAB_KEYS.length - 1, snap.tabIndex()));
      this.selectedFactionId = snap.selectedFactionId() == null ? "" : snap.selectedFactionId();
   }

   public void applyUpdate(WorldStatusContentPayload next) {
      this.payload = next;
      WorldStatusSnapshot snap = next.snapshot();
      this.currentTab = Math.max(0, Math.min(TAB_KEYS.length - 1, snap.tabIndex()));
      this.selectedFactionId = snap.selectedFactionId() == null ? "" : snap.selectedFactionId();
      this.busy = false;
      this.rebuildWidgets();
   }

   @Override
   protected void init() {
      super.init();
      this.tabButtons.clear();
      int parchX = (this.width - PARCHMENT_WIDTH) / 2;
      int parchY = (this.height - PARCHMENT_HEIGHT) / 2;
      int tabWidth = 32;
      int tabY = parchY + 16 + 20 + 2;
      for (int i = 0; i < TAB_KEYS.length; i++) {
         final int tab = i;
         Button btn = Button.builder(Component.translatable(TAB_KEYS[i]), b -> this.switchTab(tab))
            .bounds(parchX + 16 + i * (tabWidth + 2), tabY, tabWidth, 12)
            .tooltip(Tooltip.create(Component.translatable(TAB_KEYS[i])))
            .build();
         btn.active = i != this.currentTab && !this.busy;
         this.tabButtons.add(btn);
         this.addRenderableWidget(btn);
      }

      this.refreshButton = Button.builder(Component.translatable("gui.millenaire.world_status.refresh"), b -> this.sendRefresh())
         .bounds(parchX + PARCHMENT_WIDTH - 16 - 52, parchY + PARCHMENT_HEIGHT - 28, 52, 12)
         .tooltip(Tooltip.create(Component.translatable("gui.millenaire.world_status.refresh")))
         .build();
      this.refreshButton.active = !this.busy;
      this.addRenderableWidget(this.refreshButton);

      this.buildLines();
      this.totalPages = PanelRenderHelper.computePageCount(this.wrappedLines.size(), LINES_PER_PAGE);
      this.currentPage = Math.min(this.currentPage, Math.max(0, this.totalPages - 1));
      this.initPaginationButtons(parchX, parchY, PARCHMENT_WIDTH, PARCHMENT_HEIGHT, 16, true);
      this.updatePaginationButtons();
   }

   private void switchTab(int tab) {
      if (this.busy || tab == this.currentTab) {
         return;
      }
      this.currentTab = tab;
      this.currentPage = 0;
      this.rebuildWidgets();
   }

   private void sendRefresh() {
      if (this.busy) {
         return;
      }
      this.busy = true;
      if (this.refreshButton != null) {
         this.refreshButton.active = false;
      }
      for (Button btn : this.tabButtons) {
         btn.active = false;
      }
      PacketDistributor.sendToServer(
         new WorldStatusRequestPayload(this.currentTab, this.selectedFactionId),
         new CustomPacketPayload[0]
      );
   }

   private void sendAction(String villageId, String actionId, String targetId) {
      if (this.busy || villageId == null || villageId.isBlank() || actionId == null || actionId.isBlank()) {
         return;
      }
      this.busy = true;
      if (this.refreshButton != null) {
         this.refreshButton.active = false;
      }
      for (Button btn : this.tabButtons) {
         btn.active = false;
      }
      PacketDistributor.sendToServer(
         new WorldStatusActionPayload(
            villageId,
            actionId,
            targetId == null ? "" : targetId,
            UUID.randomUUID().toString(),
            this.currentTab,
            this.selectedFactionId
         ),
         new CustomPacketPayload[0]
      );
   }

   private void buildLines() {
      WorldStatusSnapshot snap = this.payload.snapshot();
      List<PanelLine> lines = new ArrayList<>();
      switch (this.currentTab) {
         case 0 -> buildOverview(lines, snap);
         case 1 -> buildCultures(lines, snap);
         case 2 -> buildRankings(lines, snap);
         case 3 -> buildWars(lines, snap);
         case 4 -> buildDiplomacy(lines, snap);
         case 5 -> buildSettlements(lines, snap);
         case 6 -> buildActivity(lines, snap);
         default -> buildOverview(lines, snap);
      }
      this.wrappedLines = this.wrapLines(lines, PARCHMENT_WIDTH);
   }

   private void buildOverview(List<PanelLine> lines, WorldStatusSnapshot snap) {
      WorldStatusSnapshot.Overview o = snap.overview();
      lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.overview.leaders"), COLOR_HEADER));
      lines.add(PanelLine.text(I18n.get(
         "gui.millenaire.world_status.overview.trade_leader",
         o.topTradeFactionName().isBlank() ? "-" : o.topTradeFactionName(),
         String.valueOf(o.topTradeScore())
      )));
      lines.add(PanelLine.text(I18n.get(
         "gui.millenaire.world_status.overview.military_leader",
         o.topMilitaryFactionName().isBlank() ? "-" : o.topMilitaryFactionName(),
         String.valueOf(o.topMilitaryPower())
      )));
      lines.add(PanelLine.text(I18n.get(
         "gui.millenaire.world_status.overview.population_leader",
         o.topPopulationFactionName().isBlank() ? "-" : o.topPopulationFactionName(),
         String.valueOf(o.topPopulation())
      )));
      lines.add(PanelLine.empty());
      lines.add(PanelLine.text(I18n.get("gui.millenaire.world_status.overview.active_wars", o.activeWarCount())));
      lines.add(PanelLine.text(I18n.get("gui.millenaire.world_status.overview.threats", o.threatCount())));
      lines.add(PanelLine.separator());
      if (o.hasPlayerSettlement()) {
         lines.add(PanelLine.colored(I18n.get(
            "gui.millenaire.world_status.overview.your_faction",
            o.playerFactionName().isBlank() ? "-" : o.playerFactionName()
         ), COLOR_YOU));
         if (o.playerAtWar()) {
            lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.overview.you_at_war"), COLOR_WAR));
         } else {
            lines.add(PanelLine.text(I18n.get("gui.millenaire.world_status.overview.you_at_peace")));
         }
      } else {
         lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.overview.no_settlement"), COLOR_MUTED));
      }
      lines.add(PanelLine.separator());
      lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.overview.hot_threats"), COLOR_HEADER));
      if (snap.threats().isEmpty()) {
         lines.add(PanelLine.text(I18n.get("gui.millenaire.world_status.threats.empty")));
      } else {
         int shown = 0;
         for (WorldStatusSnapshot.ThreatLine threat : snap.threats()) {
            if (shown++ >= 8) {
               break;
            }
            lines.add(PanelLine.colored(formatThreat(threat), threat.severity() >= 70 ? COLOR_WAR : COLOR_MUTED));
         }
      }
   }

   private void buildCultures(List<PanelLine> lines, WorldStatusSnapshot snap) {
      lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.cultures.header"), COLOR_HEADER));
      if (snap.cultures().isEmpty()) {
         lines.add(PanelLine.text(I18n.get("gui.millenaire.world_status.cultures.empty")));
         return;
      }
      for (WorldStatusSnapshot.CultureLine culture : snap.cultures()) {
         String name = I18n.get(culture.cultureNameKey());
         lines.add(PanelLine.colored(name, COLOR_HEADER));
         lines.add(PanelLine.text(I18n.get(
            "gui.millenaire.world_status.cultures.summary",
            culture.factionCount(),
            culture.settlementCount(),
            culture.residents()
         )));
         if (culture.temperamentPrimary() != null && !culture.temperamentPrimary().isBlank()) {
            lines.add(PanelLine.text(I18n.get(
               "gui.millenaire.world_status.cultures.temperament",
               I18n.get("civilization.millenaire.temperament." + culture.temperamentPrimary()),
               I18n.get("civilization.millenaire.temperament." + culture.temperamentSecondary())
            )));
         }
         if (culture.anyAtWar()) {
            lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.cultures.at_war"), COLOR_WAR));
         }
         for (WorldStatusSnapshot.FactionLine faction : snap.factions()) {
            if (!faction.cultureId().equals(culture.cultureId())) {
               continue;
            }
            String label = faction.displayName()
               + " — "
               + faction.settlementCount()
               + " / "
               + faction.residents()
               + (faction.viewerOwned() ? " " + I18n.get("gui.millenaire.world_status.you_marker") : "")
               + (faction.atWar() ? " [" + I18n.get("gui.millenaire.world_status.diplomacy.flag_war") + "]" : "");
            lines.add(PanelLine.clickableText(
               "  " + label,
               new PanelLine.PanelNavTarget(SELECT_FACTION, faction.factionId(), "", "")
            ));
         }
         for (WorldStatusSnapshot.VillageLine village : snap.villages()) {
            if (!village.cultureId().equals(culture.cultureId())) {
               continue;
            }
            String marker = village.controlledByViewer() || village.viewerSovereign()
               ? " " + I18n.get("gui.millenaire.world_status.you_marker")
               : "";
            lines.add(PanelLine.text("    • "
               + village.villageName()
               + " ("
               + village.factionName()
               + ")"
               + marker
               + " ["
               + village.x()
               + ", "
               + village.z()
               + "]"));
         }
         lines.add(PanelLine.empty());
      }
   }

   private void buildRankings(List<PanelLine> lines, WorldStatusSnapshot snap) {
      lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.rankings.trade"), COLOR_HEADER));
      appendRanks(lines, snap.tradeRanks());
      lines.add(PanelLine.empty());
      lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.rankings.military"), COLOR_HEADER));
      appendRanks(lines, snap.militaryRanks());
      lines.add(PanelLine.empty());
      lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.rankings.population"), COLOR_HEADER));
      appendRanks(lines, snap.populationRanks());
   }

   private void appendRanks(List<PanelLine> lines, List<WorldStatusSnapshot.RankLine> ranks) {
      if (ranks.isEmpty()) {
         lines.add(PanelLine.text(I18n.get("gui.millenaire.world_status.rankings.empty")));
         return;
      }
      for (WorldStatusSnapshot.RankLine rank : ranks) {
         String text = I18n.get(
            "gui.millenaire.world_status.rankings.row",
            rank.rank(),
            rank.displayName(),
            String.valueOf(rank.score())
         );
         if (rank.viewerOwned()) {
            text = text + " " + I18n.get("gui.millenaire.world_status.you_marker");
            lines.add(PanelLine.colored(text, COLOR_YOU));
         } else {
            lines.add(PanelLine.clickableText(
               text,
               new PanelLine.PanelNavTarget(SELECT_FACTION, rank.factionId(), "", "")
            ));
         }
      }
   }

   private void buildWars(List<PanelLine> lines, WorldStatusSnapshot snap) {
      lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.wars.header"), COLOR_HEADER));
      if (snap.wars().isEmpty()) {
         lines.add(PanelLine.text(I18n.get("gui.millenaire.world_status.wars.empty")));
      } else {
         for (WorldStatusSnapshot.WarLine war : snap.wars()) {
            String text = I18n.get(
               "gui.millenaire.world_status.wars.row",
               war.attackerName(),
               war.defenderName(),
               war.stateCode(),
               war.warScore(),
               war.primaryGoalCode()
            );
            lines.add(PanelLine.colored(text, war.involvesViewer() ? COLOR_WAR : -13421773));
         }
      }
      lines.add(PanelLine.separator());
      lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.threats.header"), COLOR_HEADER));
      if (snap.threats().isEmpty()) {
         lines.add(PanelLine.text(I18n.get("gui.millenaire.world_status.threats.empty")));
      } else {
         for (WorldStatusSnapshot.ThreatLine threat : snap.threats()) {
            lines.add(PanelLine.text(formatThreat(threat)));
         }
      }
   }

   private void buildDiplomacy(List<PanelLine> lines, WorldStatusSnapshot snap) {
      String focus = this.selectedFactionId.isBlank() ? snap.overview().playerFactionId() : this.selectedFactionId;
      String focusName = focus;
      for (WorldStatusSnapshot.FactionLine faction : snap.factions()) {
         if (faction.factionId().equals(focus)) {
            focusName = faction.displayName();
            break;
         }
      }
      lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.diplomacy.focus", focusName), COLOR_HEADER));
      lines.add(PanelLine.text(I18n.get("gui.millenaire.world_status.diplomacy.hint")));
      lines.add(PanelLine.empty());
      if (snap.standings().isEmpty()) {
         lines.add(PanelLine.text(I18n.get("gui.millenaire.world_status.diplomacy.empty")));
      } else {
         for (WorldStatusSnapshot.StandingLine standing : snap.standings()) {
            String flags = "";
            if (standing.atWar()) {
               flags += " " + I18n.get("gui.millenaire.world_status.diplomacy.flag_war");
            }
            if (standing.hasEmbargo()) {
               flags += " " + I18n.get("gui.millenaire.world_status.diplomacy.flag_embargo");
            }
            String text = I18n.get(
               "gui.millenaire.world_status.diplomacy.row",
               standing.displayName(),
               standing.categoryCode(),
               standing.score()
            ) + flags;
            lines.add(PanelLine.clickableText(
               text,
               new PanelLine.PanelNavTarget(SELECT_FACTION, standing.factionId(), "", "")
            ));
         }
      }
      lines.add(PanelLine.separator());
      lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.diplomacy.actions"), COLOR_HEADER));
      String actorVillage = snap.actorVillageId();
      if (actorVillage == null || actorVillage.isBlank()) {
         lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.overview.no_settlement"), COLOR_MUTED));
         return;
      }
      if (snap.diplomacyActions().isEmpty()) {
         lines.add(PanelLine.text(I18n.get("gui.millenaire.world_status.diplomacy.no_actions")));
         return;
      }
      for (WorldStatusSnapshot.DiplomacyActionLine action : snap.diplomacyActions()) {
         String label = I18n.get(action.labelKey());
         if (action.targetFactionName() != null && !action.targetFactionName().isBlank()
            && !action.targetFactionName().startsWith("immigration:")
            && !action.targetFactionName().startsWith("trade:")) {
            label = label + " → " + action.targetFactionName();
         } else if (action.targetFactionName() != null && !action.targetFactionName().isBlank()) {
            label = label + " (" + action.targetFactionName() + ")";
         }
         if (action.enabled()) {
            lines.add(PanelLine.clickableText(
               label,
               new PanelLine.PanelNavTarget(CIV_ACTION, action.actionId(), action.targetFactionId(), actorVillage)
            ));
         } else {
            String blocked = action.blockedReasonKey() == null || action.blockedReasonKey().isBlank()
               ? ""
               : " — " + I18n.get(action.blockedReasonKey());
            lines.add(PanelLine.colored(label + blocked, COLOR_MUTED));
         }
      }
   }

   private void buildSettlements(List<PanelLine> lines, WorldStatusSnapshot snap) {
      lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.settlements.header"), COLOR_HEADER));
      if (snap.playerSettlements().isEmpty()) {
         lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.overview.no_settlement"), COLOR_MUTED));
         return;
      }
      for (WorldStatusSnapshot.PlayerSettlementLine settlement : snap.playerSettlements()) {
         lines.add(PanelLine.colored(settlement.villageName(), COLOR_YOU));
         lines.add(PanelLine.text(I18n.get(
            "gui.millenaire.world_status.settlements.summary",
            settlement.residents(),
            settlement.housingCapacity(),
            I18n.get(settlement.developmentLabelKey())
         )));
         if (settlement.overcrowded()) {
            lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.settlements.overcrowded"), COLOR_WAR));
         }
         if (settlement.inventoryCertaintyKey() != null
            && !settlement.inventoryCertaintyKey().isBlank()
            && !"civilization.millenaire.inventory.known".equals(settlement.inventoryCertaintyKey())) {
            lines.add(PanelLine.colored(I18n.get(settlement.inventoryCertaintyKey()), COLOR_MUTED));
         }
         if (settlement.topNeedSummary() != null && !settlement.topNeedSummary().isBlank()) {
            String need = settlement.topNeedSummary().contains(".")
               ? I18n.get(settlement.topNeedSummary())
               : settlement.topNeedSummary();
            if (settlement.topNeedProven()) {
               lines.add(PanelLine.text(I18n.get("gui.millenaire.world_status.settlements.need", need)));
            } else {
               lines.add(PanelLine.colored(
                  I18n.get("gui.millenaire.world_status.settlements.need_unproven", need),
                  COLOR_MUTED
               ));
            }
         }
         if (settlement.projectName() != null && !settlement.projectName().isBlank()) {
            String project = settlement.projectName().contains(".")
               ? I18n.get(settlement.projectName())
               : settlement.projectName();
            lines.add(PanelLine.text(I18n.get("gui.millenaire.world_status.settlements.project", project)));
         }
         if (settlement.colonizationStatusKey() != null && !settlement.colonizationStatusKey().isBlank()) {
            lines.add(PanelLine.text(I18n.get(
               "gui.millenaire.world_status.settlements.colonization",
               I18n.get(settlement.colonizationStatusKey())
            )));
         }
         lines.add(PanelLine.text(I18n.get("gui.millenaire.world_status.settlements.actions")));
         for (CivilizationVillageSnapshot.AllowedAction action : settlement.actions()) {
            // Hide target-less diplomacy stubs; those live on Diplomacy tab with real targets.
            if (!action.enabled()
               && ("DECLARE_WAR".equals(action.actionId().name())
                  || "OFFER_PEACE".equals(action.actionId().name())
                  || "SET_EMBARGO".equals(action.actionId().name())
                  || "CLEAR_EMBARGO".equals(action.actionId().name()))) {
               continue;
            }
            if (action.enabled()) {
               lines.add(PanelLine.clickableTranslatable(
                  action.labelKey(),
                  new PanelLine.PanelNavTarget(
                     CIV_ACTION,
                     action.actionId().name(),
                     action.targetId() == null ? "" : action.targetId(),
                     settlement.villageId()
                  )
               ));
            } else {
               String blocked = action.blockedReasonKey() == null || action.blockedReasonKey().isBlank()
                  ? ""
                  : " — " + I18n.get(action.blockedReasonKey());
               lines.add(PanelLine.colored(I18n.get(action.labelKey()) + blocked, COLOR_MUTED));
            }
         }
         lines.add(PanelLine.separator());
      }
   }

   private void buildActivity(List<PanelLine> lines, WorldStatusSnapshot snap) {
      lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.activity.header"), COLOR_HEADER));
      if (!snap.theater().isEmpty()) {
         lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.theater.header"), COLOR_HEADER));
         for (WorldStatusSnapshot.TheaterLine story : snap.theater()) {
            lines.add(PanelLine.text(I18n.get(
               "gui.millenaire.world_status.theater.row",
               story.type().toUpperCase(Locale.ROOT),
               I18n.get(story.summaryKey()),
               story.factionA()
            )));
         }
      }
      if (!snap.corridors().isEmpty()) {
         lines.add(PanelLine.colored(I18n.get("gui.millenaire.world_status.corridors.header"), COLOR_HEADER));
         for (WorldStatusSnapshot.CorridorLine corridor : snap.corridors()) {
            String state = corridor.disrupted()
               ? I18n.get("gui.millenaire.world_status.corridors.disrupted")
               : corridor.recognized()
                  ? I18n.get("gui.millenaire.world_status.corridors.recognized")
                  : I18n.get("gui.millenaire.world_status.corridors.forming");
            lines.add(PanelLine.text(I18n.get(
               "gui.millenaire.world_status.corridors.row",
               corridor.sourceFaction(),
               corridor.targetFaction(),
               corridor.deliveries(),
               state
            )));
         }
      }
      if (snap.activity().isEmpty() && snap.theater().isEmpty() && snap.corridors().isEmpty()) {
         lines.add(PanelLine.text(I18n.get("gui.millenaire.world_status.activity.empty")));
         return;
      }
      for (WorldStatusSnapshot.ActivityLine activity : snap.activity()) {
         lines.add(PanelLine.text(I18n.get(
            "gui.millenaire.world_status.activity.row",
            activity.typeKey().toUpperCase(Locale.ROOT),
            activity.summary()
         )));
      }
   }

   private static String formatThreat(WorldStatusSnapshot.ThreatLine threat) {
      return I18n.get(
         "gui.millenaire.world_status.threats.row",
         threat.kindCode(),
         threat.sourceName(),
         threat.targetName().isBlank() ? "-" : threat.targetName(),
         threat.detail()
      );
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0 && !this.busy && !this.wrappedLines.isEmpty()) {
         int parchX = (this.width - PARCHMENT_WIDTH) / 2;
         int parchY = (this.height - PARCHMENT_HEIGHT) / 2;
         int contentX = parchX + 16;
         int contentY = parchY + 16 + 20 + 16 + 6;
         int startLine = this.currentPage * LINES_PER_PAGE;
         PanelLine.PanelNavTarget target = PanelRenderHelper.getClickedNavTarget(
            this.wrappedLines, contentX, contentY, TEXT_WIDTH, startLine, LINES_PER_PAGE, mouseX, mouseY
         );
         if (target != null) {
            if (SELECT_FACTION.equals(target.targetStateName())) {
               this.selectedFactionId = target.cultureKey() == null ? "" : target.cultureKey();
               this.currentTab = 4;
               this.sendRefresh();
               return true;
            }
            if (CIV_ACTION.equals(target.targetStateName())) {
               String actionId = target.cultureKey() == null ? "" : target.cultureKey();
               String targetId = target.categoryKey() == null ? "" : target.categoryKey();
               String villageId = target.itemKey() == null || target.itemKey().isBlank()
                  ? this.payload.snapshot().actorVillageId()
                  : target.itemKey();
               this.sendAction(villageId, actionId, targetId);
               return true;
            }
         }
      }
      return super.mouseClicked(mouseX, mouseY, button);
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 1610612736);
      int parchX = (this.width - PARCHMENT_WIDTH) / 2;
      int parchY = (this.height - PARCHMENT_HEIGHT) / 2;
      PanelRenderHelper.renderTexturedBackground(graphics, PanelRenderHelper.QUEST_TEXTURE, parchX, parchY, PARCHMENT_WIDTH, PARCHMENT_HEIGHT);
      int y = this.renderParchmentHeader(graphics, Component.translatable("gui.millenaire.world_status.title"), parchX, parchY, PARCHMENT_WIDTH);
      y += 14;
      graphics.fill(parchX + 16, y, parchX + PARCHMENT_WIDTH - 16, y + 1, -5601178);
      y += 6;
      if (!this.wrappedLines.isEmpty()) {
         int startLine = this.currentPage * LINES_PER_PAGE;
         PanelRenderHelper.renderPanelLines(
            graphics, this.font, this.wrappedLines, parchX + 16, y, TEXT_WIDTH, startLine, LINES_PER_PAGE, mouseX, mouseY
         );
      }
      this.renderPageCounter(graphics, parchX, parchY, PARCHMENT_WIDTH, PARCHMENT_HEIGHT);
      super.render(graphics, mouseX, mouseY, partialTick);
   }
}
