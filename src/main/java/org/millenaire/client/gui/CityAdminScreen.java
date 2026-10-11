package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.civilization.presentation.CivilizationActionId;
import org.millenaire.network.CityAdminActionPayload;
import org.millenaire.network.CityAdminPayload;
import org.millenaire.network.CityAdminRenamePayload;

/**
 * Governance hub for owned settlements — tabs map to CivilizationActionService actions.
 */
public class CityAdminScreen extends AbstractMillenaireScreen {
   private static final int PARCHMENT_WIDTH = 248;
   private static final int PARCHMENT_HEIGHT = 220;
   private static final String[] TAB_KEYS = {
      "gui.millenaire.city_admin.tab.overview",
      "gui.millenaire.city_admin.tab.construction",
      "gui.millenaire.city_admin.tab.economy",
      "gui.millenaire.city_admin.tab.people",
      "gui.millenaire.city_admin.tab.diplomacy",
      "gui.millenaire.city_admin.tab.colonies",
      "gui.millenaire.city_admin.tab.settings"
   };

   private CityAdminPayload payload;
   private int tabIndex;
   private EditBox renameField;
   private final List<Button> dynamicButtons = new ArrayList<>();

   public CityAdminScreen(CityAdminPayload payload) {
      super(Component.translatable("gui.millenaire.city_admin.title"));
      this.payload = payload;
      this.tabIndex = Math.max(0, Math.min(payload.tabIndex(), TAB_KEYS.length - 1));
   }

   public void applyUpdate(CityAdminPayload update) {
      this.payload = update;
      this.tabIndex = Math.max(0, Math.min(update.tabIndex(), TAB_KEYS.length - 1));
      this.clearWidgets();
      this.dynamicButtons.clear();
      this.init();
   }

   public boolean sameVillage(CityAdminPayload other) {
      return other != null && this.payload != null && this.payload.villageId().equals(other.villageId());
   }

   @Override
   public boolean isPauseScreen() {
      return true;
   }

   @Override
   protected void init() {
      super.init();
      int parchX = (this.width - PARCHMENT_WIDTH) / 2;
      int parchY = (this.height - PARCHMENT_HEIGHT) / 2;
      int tabY = parchY + 42;
      int tabW = Math.max(28, (PARCHMENT_WIDTH - 32) / TAB_KEYS.length);
      for (int i = 0; i < TAB_KEYS.length; i++) {
         final int idx = i;
         Button tab = Button.builder(Component.literal(String.valueOf(i + 1)), b -> {
               this.tabIndex = idx;
               this.clearWidgets();
               this.dynamicButtons.clear();
               this.init();
            })
            .bounds(parchX + 16 + i * tabW, tabY, tabW - 2, 12)
            .build();
         tab.active = i != this.tabIndex;
         tab.setTooltip(Tooltip.create(Component.translatable(TAB_KEYS[i])));
         this.addRenderableWidget(tab);
      }
      int contentY = tabY + 16;
      this.buildTabContent(parchX, contentY);
   }

   private void buildTabContent(int parchX, int contentY) {
      int x = parchX + 16;
      int y = contentY + 48;
      int width = PARCHMENT_WIDTH - 32;
      switch (this.tabIndex) {
         case 0 -> addActionsFor(x, y, width, CivilizationActionId.SUPPORT_NEED, CivilizationActionId.OPEN_CONTROLLED_PROJECTS);
         case 1 -> addActionsFor(x, y, width, CivilizationActionId.OPEN_CONTROLLED_PROJECTS, CivilizationActionId.PRIORITIZE_HOUSING);
         case 2 -> addActionsFor(x, y, width, CivilizationActionId.SET_TRADE_POLICY, CivilizationActionId.SUPPORT_NEED);
         case 3 -> {
            addActionsFor(x, y, width, CivilizationActionId.SET_IMMIGRATION_POLICY);
            addActionsFor(x, y + 32, width, CivilizationActionId.ASSIGN_GUARD, CivilizationActionId.RELIEVE_GUARD);
         }
         case 4 -> addDiplomacyActions(x, y, width);
         case 5 -> addActionsFor(
            x,
            y,
            width,
            CivilizationActionId.VIEW_COLONIZATION,
            CivilizationActionId.SUPPORT_COLONIZATION_MATERIALS,
            CivilizationActionId.AUTHORIZE_COLONIZATION,
            CivilizationActionId.VIEW_INTERRUPTED_OPERATION
         );
         case 6 -> addSettings(x, y, width);
         default -> {
         }
      }
   }

   private void addDiplomacyActions(int x, int y, int width) {
      int row = 0;
      for (CityAdminPayload.ActionEntry action : this.payload.actions()) {
         if (!isDiplomacyAction(action.actionId())) {
            continue;
         }
         if (row >= 10) {
            break;
         }
         addActionButton(x, y + row * 16, width, action);
         row++;
      }
      if (row == 0) {
         addActionsFor(
            x,
            y,
            width,
            CivilizationActionId.DECLARE_WAR,
            CivilizationActionId.OFFER_PEACE,
            CivilizationActionId.SET_EMBARGO,
            CivilizationActionId.CLEAR_EMBARGO
         );
      }
   }

   private static boolean isDiplomacyAction(String actionId) {
      return CivilizationActionId.DECLARE_WAR.name().equals(actionId)
         || CivilizationActionId.OFFER_PEACE.name().equals(actionId)
         || CivilizationActionId.SET_EMBARGO.name().equals(actionId)
         || CivilizationActionId.CLEAR_EMBARGO.name().equals(actionId);
   }

   private void addActionsFor(int x, int y, int width, CivilizationActionId... ids) {
      int row = 0;
      for (CivilizationActionId id : ids) {
         List<CityAdminPayload.ActionEntry> matches = new ArrayList<>();
         for (CityAdminPayload.ActionEntry action : this.payload.actions()) {
            if (id.name().equals(action.actionId())) {
               matches.add(action);
            }
         }
         if (matches.isEmpty()) {
            matches.add(new CityAdminPayload.ActionEntry(id.name(), false, id.i18nKey(), "civilization.millenaire.action.blocked.unavailable", ""));
         }
         for (CityAdminPayload.ActionEntry match : matches) {
            if (row >= 10) {
               return;
            }
            addActionButton(x, y + row * 16, width, match);
            row++;
         }
      }
   }

   private void addActionButton(int x, int y, int width, CityAdminPayload.ActionEntry action) {
      Component label = resolveActionLabel(action);
      Button btn = Button.builder(label, b -> {
            PacketDistributor.sendToServer(
               new CityAdminActionPayload(this.payload.villageId(), action.actionId(), action.targetId(), this.tabIndex),
               new CustomPacketPayload[0]
            );
         })
         .bounds(x, y, width, 14)
         .build();
      btn.active = action.enabled();
      if (!action.enabled() && action.blockedReasonKey() != null && !action.blockedReasonKey().isBlank()) {
         btn.setTooltip(Tooltip.create(Component.translatable(action.blockedReasonKey())));
      } else if (action.targetId() != null && !action.targetId().isBlank()) {
         btn.setTooltip(Tooltip.create(Component.literal(action.targetId())));
      }
      this.addRenderableWidget(btn);
      this.dynamicButtons.add(btn);
   }

   private static Component resolveActionLabel(CityAdminPayload.ActionEntry action) {
      String key = action.labelKey() == null || action.labelKey().isBlank()
         ? "civilization.millenaire.action." + action.actionId().toLowerCase(Locale.ROOT)
         : action.labelKey();
      if (key.contains("|")) {
         String base = key.substring(0, key.indexOf('|'));
         String suffix = key.substring(key.indexOf('|') + 1);
         return Component.translatable(base).append(Component.literal(" — " + suffix));
      }
      return Component.translatable(key);
   }

   private void addSettings(int x, int y, int width) {
      this.renameField = new EditBox(this.font, x, y, width, 16, Component.translatable("gui.millenaire.city_admin.rename_hint"));
      this.renameField.setMaxLength(40);
      this.renameField.setValue(this.payload.villageName());
      this.renameField.setHint(Component.translatable("gui.millenaire.city_admin.rename_hint"));
      this.addRenderableWidget(this.renameField);
      Button rename = Button.builder(Component.translatable("gui.millenaire.city_admin.rename"), b -> {
            String name = this.renameField.getValue().trim();
            PacketDistributor.sendToServer(
               new CityAdminRenamePayload(this.payload.villageId(), name, this.tabIndex),
               new CustomPacketPayload[0]
            );
         })
         .bounds(x, y + 20, width, 14)
         .build();
      this.addRenderableWidget(rename);
      this.dynamicButtons.add(rename);
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.tabIndex == 6 && this.renameField != null && this.renameField.isFocused()) {
         if (this.renameField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
         }
      }
      return super.keyPressed(keyCode, scanCode, modifiers);
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 1610612736);
      int parchX = (this.width - PARCHMENT_WIDTH) / 2;
      int parchY = (this.height - PARCHMENT_HEIGHT) / 2;
      PanelRenderHelper.renderTexturedBackground(graphics, PanelRenderHelper.PANEL_TEXTURE, parchX, parchY, PARCHMENT_WIDTH, PARCHMENT_HEIGHT);
      this.renderParchmentHeader(graphics, Component.translatable("gui.millenaire.city_admin.title"), parchX, parchY, PARCHMENT_WIDTH);
      int x = parchX + 16;
      int y = parchY + 58;
      graphics.drawString(this.font, Component.translatable(TAB_KEYS[this.tabIndex]).withStyle(s -> s.withBold(true)), x, y, -14018560, false);
      y += 12;
      if (this.tabIndex == 0) {
         y = renderOverview(graphics, x, y, parchY);
         if (this.payload.colonizationHint() != null && this.payload.colonizationHint().startsWith("wanted:")) {
            String[] parts = this.payload.colonizationHint().split(":");
            if (parts.length >= 2) {
               graphics.drawString(
                  this.font,
                  Component.translatable("gui.millenaire.city_admin.wanted", parts[1], parts.length >= 3 ? parts[2] : ""),
                  x,
                  y,
                  -65536,
                  false
               );
            }
         }
      } else if (this.tabIndex == 2) {
         if (this.payload.tradeHint() != null && !this.payload.tradeHint().isBlank()) {
            graphics.drawString(
               this.font,
               Component.translatable("gui.millenaire.city_admin.trade_policy", this.payload.tradeHint()),
               x,
               y,
               -14018560,
               false
            );
            y += 12;
         }
         for (String need : this.payload.needLines()) {
            if (need != null && need.startsWith("shopstats|")) {
               String[] parts = need.split("\\|", -1);
               String visits = parts.length > 1 ? parts[1] : "0";
               String deniers = parts.length > 2 ? parts[2] : "0";
               graphics.drawString(
                  this.font,
                  Component.translatable("gui.millenaire.city_admin.shop_stats", visits, deniers),
                  x,
                  y,
                  -14018560,
                  false
               );
               break;
            }
         }
      } else if (this.tabIndex == 3) {
         y = renderPeopleHints(graphics, x, y);
      } else if (this.tabIndex == 5 && this.payload.colonizationHint() != null && !this.payload.colonizationHint().isBlank()) {
         graphics.drawWordWrap(this.font, Component.literal(this.payload.colonizationHint()), x, y, PARCHMENT_WIDTH - 32, -14018560);
      } else if (this.tabIndex == 6) {
         if (this.payload.factionDisplay() != null && !this.payload.factionDisplay().isBlank()) {
            graphics.drawString(
               this.font,
               Component.translatable("gui.millenaire.city_admin.faction", this.payload.factionDisplay()),
               x,
               y + 40,
               -14018560,
               false
            );
         }
         graphics.drawString(this.font, Component.translatable("gui.millenaire.city_admin.banner_hint"), x, y + 52, -10066330, false);
      }
      if (this.payload.feedbackKey() != null && !this.payload.feedbackKey().isBlank()) {
         graphics.drawWordWrap(
            this.font,
            Component.translatable(this.payload.feedbackKey()),
            x,
            parchY + PARCHMENT_HEIGHT - 28,
            PARCHMENT_WIDTH - 32,
            -16711936
         );
      }
      super.render(graphics, mouseX, mouseY, partialTick);
   }

   private int renderOverview(GuiGraphics graphics, int x, int y, int parchY) {
      graphics.drawString(this.font, this.payload.villageName(), x, y, -14018560, false);
      y += 11;
      if (this.payload.factionDisplay() != null && !this.payload.factionDisplay().isBlank()) {
         graphics.drawString(
            this.font,
            Component.translatable("gui.millenaire.city_admin.faction", this.payload.factionDisplay()),
            x,
            y,
            -14018560,
            false
         );
         y += 11;
      }
      graphics.drawString(this.font, Component.translatable(this.payload.developmentKey()), x, y, -14018560, false);
      y += 11;
      graphics.drawString(
         this.font,
         Component.translatable("gui.millenaire.city_admin.pop", this.payload.residents(), this.payload.housingCapacity()),
         x,
         y,
         -14018560,
         false
      );
      y += 11;
      if (this.payload.overcrowded()) {
         graphics.drawString(this.font, Component.translatable("gui.millenaire.city_admin.overcrowded"), x, y, -65536, false);
         y += 11;
      }
      if (this.payload.inventoryCertaintyKey() != null
         && !this.payload.inventoryCertaintyKey().isBlank()
         && !"civilization.millenaire.inventory.known".equals(this.payload.inventoryCertaintyKey())) {
         graphics.drawString(this.font, Component.translatable(this.payload.inventoryCertaintyKey()), x, y, -10066330, false);
         y += 11;
      }
      if (this.payload.projectLabel() != null && !this.payload.projectLabel().isBlank()) {
         graphics.drawString(this.font, Component.literal(this.payload.projectLabel()), x, y, -14018560, false);
         y += 11;
      }
      for (String need : this.payload.needLines()) {
         if (y > parchY + PARCHMENT_HEIGHT - 80) {
            break;
         }
         if (need != null && need.startsWith("shopstats|")) {
            continue;
         }
         String[] parts = need.split("\\|", -1);
         String label = parts.length > 0 ? parts[0] : need;
         boolean proven = parts.length < 4 || "1".equals(parts[3]);
         if (proven) {
            graphics.drawString(this.font, Component.translatable(label), x, y, -14018560, false);
         } else {
            String observed = parts.length > 4 ? parts[4] : "?";
            graphics.drawString(
               this.font,
               Component.translatable("civilization.millenaire.overview.shortage_unproven", observed),
               x,
               y,
               -10066330,
               false
            );
         }
         y += 10;
      }
      return y;
   }

   private int renderPeopleHints(GuiGraphics graphics, int x, int y) {
      String hint = this.payload.immigrationHint();
      if (hint != null && !hint.isBlank()) {
         String policy = hint;
         String vacancies = "0";
         if (hint.contains("|")) {
            policy = hint.substring(0, hint.indexOf('|'));
            vacancies = hint.substring(hint.indexOf('|') + 1);
         }
         graphics.drawString(
            this.font,
            Component.translatable("gui.millenaire.city_admin.immigration_policy", policy),
            x,
            y,
            -14018560,
            false
         );
         y += 11;
         graphics.drawString(
            this.font,
            Component.translatable("gui.millenaire.city_admin.housing_demand", vacancies),
            x,
            y,
            -14018560,
            false
         );
         y += 11;
      }
      graphics.drawString(
         this.font,
         Component.translatable("gui.millenaire.city_admin.diplomacy_hint"),
         x,
         y,
         -10066330,
         false
      );
      return y;
   }
}
