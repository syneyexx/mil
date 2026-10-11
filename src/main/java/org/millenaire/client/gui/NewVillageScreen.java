package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.item.ModItems;
import org.millenaire.network.SettlementFoundingRequestPayload;
import org.millenaire.network.VillageCreationRequestPayload;
import org.millenaire.network.VillageTypeListPayload;

public class NewVillageScreen extends AbstractMillenaireScreen {
   private static final int PARCHMENT_WIDTH = 204;
   private static final int PARCHMENT_HEIGHT = 220;
   private static final int TEXT_WIDTH = textWidth(204);
   private final VillageTypeListPayload data;
   private final Map<String, List<VillageTypeListPayload.VillageTypeEntry>> groupedByCulture;
   private List<NewVillageScreen.DisplayRow> displayRows = List.of();
   private final List<Button> pageButtons = new ArrayList<>();
   private int rowsPerPage;
   private boolean sovereignMode;
   private String preservedName = "";
   private EditBox nameField;
   private Button modeToggleButton;
   private Button claimButton;
   private VillageTypeListPayload.VillageTypeEntry selectedEntry;
   private Component statusMessage;

   public NewVillageScreen(VillageTypeListPayload data) {
      super(Component.translatable("gui.millenaire.new_village.title"));
      this.data = data;
      this.groupedByCulture = new LinkedHashMap<>();

      for (VillageTypeListPayload.VillageTypeEntry entry : data.entries()) {
         this.groupedByCulture.computeIfAbsent(entry.cultureKey(), k -> new ArrayList<>()).add(entry);
      }
   }

   @Override
   public boolean isPauseScreen() {
      return true;
   }

   protected void init() {
      super.init();
      this.rebuildDisplayRows();
      int parchY = (this.height - 220) / 2;
      int parchX = (this.width - 204) / 2;
      int contentStartY = parchY + 42;
      if (this.data.sovereignFoundingAvailable()) {
         this.modeToggleButton = Button.builder(
               this.sovereignMode
                  ? Component.translatable("gui.millenaire.new_village.mode_found")
                  : Component.translatable("gui.millenaire.new_village.mode_summon"),
               button -> {
                  if (this.nameField != null) {
                     this.preservedName = this.nameField.getValue();
                  }
                  this.sovereignMode = !this.sovereignMode;
                  if (!this.sovereignMode) {
                     this.selectedEntry = null;
                  }
                  this.statusMessage = null;
                  this.clearWidgets();
                  this.pageButtons.clear();
                  this.init();
               }
            )
            .bounds(parchX + 16, contentStartY, TEXT_WIDTH, 14)
            .build();
         this.addRenderableWidget(this.modeToggleButton);
         contentStartY += 16;
         this.nameField = new EditBox(
            this.font, parchX + 16, contentStartY, TEXT_WIDTH, 16, Component.translatable("gui.millenaire.new_village.name_hint")
         );
         this.nameField.setMaxLength(40);
         this.nameField.setHint(Component.translatable("gui.millenaire.new_village.name_hint"));
         this.nameField.setValue(this.preservedName);
         this.nameField.setEditable(this.sovereignMode);
         this.nameField.setVisible(this.sovereignMode);
         this.nameField.setCanLoseFocus(true);
         this.addRenderableWidget(this.nameField);
         if (this.sovereignMode) {
            contentStartY += 18;
            this.claimButton = Button.builder(
                  Component.translatable("gui.millenaire.new_village.claim"),
                  button -> this.confirmFound()
               )
               .bounds(parchX + 16, contentStartY, TEXT_WIDTH, 14)
               .build();
            this.addRenderableWidget(this.claimButton);
            this.pageButtons.add(this.claimButton);
            contentStartY += 16;
            this.setInitialFocus(this.nameField);
            this.nameField.setFocused(true);
         }
      }

      int contentEndY = parchY + 220 - 15;
      int availableHeight = contentEndY - contentStartY;
      this.rowsPerPage = Math.max(1, availableHeight / 16);
      this.totalPages = Math.max(1, (this.displayRows.size() + this.rowsPerPage - 1) / this.rowsPerPage);
      this.currentPage = Math.min(this.currentPage, this.totalPages - 1);
      this.initPaginationButtons(parchX, parchY, 204, 220, 16, true);
      this.rebuildPageButtons(contentStartY);
      this.updatePaginationButtons();
      this.updateClaimButton();
   }

   private void rebuildDisplayRows() {
      List<NewVillageScreen.DisplayRow> rows = new ArrayList<>();
      if (!this.sovereignMode) {
         rows.add(new NewVillageScreen.RandomRow());
      }

      int foundingEligible = 0;
      for (Entry<String, List<VillageTypeListPayload.VillageTypeEntry>> group : this.groupedByCulture.entrySet()) {
         List<VillageTypeListPayload.VillageTypeEntry> entries = group.getValue();
         if (entries.isEmpty()) {
            continue;
         }
         List<VillageTypeListPayload.VillageTypeEntry> filtered = new ArrayList<>();
         for (VillageTypeListPayload.VillageTypeEntry entry : entries) {
            if (this.sovereignMode) {
               if (entry.canFound()) {
                  filtered.add(entry);
                  foundingEligible++;
               }
            } else {
               filtered.add(entry);
            }
         }
         if (filtered.isEmpty()) {
            continue;
         }
         rows.add(new NewVillageScreen.CultureHeaderRow(filtered.getFirst().cultureName(), group.getKey()));
         for (VillageTypeListPayload.VillageTypeEntry entry : filtered) {
            rows.add(new NewVillageScreen.VillageTypeRow(entry));
         }
      }

      if (this.sovereignMode && foundingEligible == 0) {
         rows.add(new NewVillageScreen.EmptyHintRow());
      }

      this.displayRows = rows;
   }

   private int computeContentStartY(int parchY) {
      int contentStartY = parchY + 42;
      if (this.data.sovereignFoundingAvailable()) {
         contentStartY += 16;
         if (this.sovereignMode) {
            contentStartY += 18 + 16;
         }
      }
      return contentStartY;
   }

   @Override
   protected void onPageChanged() {
      super.onPageChanged();
      int parchY = (this.height - 220) / 2;
      this.rebuildPageButtons(this.computeContentStartY(parchY));
   }

   private void rebuildPageButtons(int contentStartY) {
      for (Button btn : this.pageButtons) {
         if (btn != this.claimButton) {
            this.removeWidget(btn);
         }
      }

      this.pageButtons.removeIf(btn -> btn != this.claimButton);
      int parchX = (this.width - 204) / 2;
      int contentX = parchX + 16;
      int y = contentStartY;
      int startIdx = this.currentPage * this.rowsPerPage;
      int endIdx = Math.min(startIdx + this.rowsPerPage, this.displayRows.size());
      int mainBtnWidth = 136;

      for (int i = startIdx; i < endIdx; i++) {
         NewVillageScreen.DisplayRow row = this.displayRows.get(i);
         if (row instanceof NewVillageScreen.RandomRow) {
            int btnX = contentX + (TEXT_WIDTH - 160) / 2;
            Button randomBtn = Button.builder(Component.translatable("gui.millenaire.new_village.random"), button -> {
               PacketDistributor.sendToServer(new VillageCreationRequestPayload(this.data.targetPos(), "", "", true), new CustomPacketPayload[0]);
               this.onClose();
            }).bounds(btnX, y, 160, 14).build();
            this.addRenderableWidget(randomBtn);
            this.pageButtons.add(randomBtn);
         } else if (row instanceof NewVillageScreen.VillageTypeRow vtRow) {
            VillageTypeListPayload.VillageTypeEntry entry = vtRow.entry();
            String label = entry.requiresControl()
               ? entry.displayName() + " " + Component.translatable("gui.millenaire.new_village.controlled_suffix").getString()
               : entry.displayName();
            if (this.sovereignMode && this.selectedEntry != null
               && Objects.equals(this.selectedEntry.cultureKey(), entry.cultureKey())
               && Objects.equals(this.selectedEntry.typeKey(), entry.typeKey())) {
               label = "> " + label;
            }
            boolean enabled = this.sovereignMode || (!entry.requiresControl() || entry.hasControl());
            int btnX = contentX + (TEXT_WIDTH - 160) / 2;
            Button btn = Button.builder(Component.literal(label), button -> this.selectType(entry))
               .bounds(btnX, y, mainBtnWidth, 14)
               .build();
            btn.active = enabled;
            this.addRenderableWidget(btn);
            this.pageButtons.add(btn);
            ItemStack bookIcon = new ItemStack((ItemLike) ModItems.TRAVEL_BOOK.get());
            IconButton infoBtn = new IconButton(
               btnX + mainBtnWidth + 4,
               y,
               20,
               14,
               bookIcon,
               Component.translatable("gui.millenaire.new_village.info_tooltip"),
               button -> TravelBookNavHelper.openVillageType(this, entry.cultureKey(), entry.typeKey())
            );
            this.addRenderableWidget(infoBtn);
            this.pageButtons.add(infoBtn);
         }

         y += 16;
      }
      this.updateClaimButton();
   }

   private void selectType(VillageTypeListPayload.VillageTypeEntry entry) {
      if (this.sovereignMode) {
         this.selectedEntry = entry;
         this.statusMessage = null;
         this.preservedName = this.nameField != null ? this.nameField.getValue() : this.preservedName;
         this.clearWidgets();
         this.pageButtons.clear();
         this.init();
         return;
      }
      PacketDistributor.sendToServer(
         new VillageCreationRequestPayload(this.data.targetPos(), entry.cultureKey(), entry.typeKey(), false),
         new CustomPacketPayload[0]
      );
      this.onClose();
   }

   private void confirmFound() {
      if (!this.sovereignMode) {
         return;
      }
      String name = this.nameField != null ? this.nameField.getValue().trim() : "";
      if (name.isEmpty()) {
         this.statusMessage = Component.translatable("ui.settlement.found.error.bad_name");
         return;
      }
      if (this.selectedEntry == null) {
         this.statusMessage = Component.translatable("gui.millenaire.new_village.select_type");
         return;
      }
      PacketDistributor.sendToServer(
         new SettlementFoundingRequestPayload(
            this.data.targetPos(), this.selectedEntry.cultureKey(), this.selectedEntry.typeKey(), name
         ),
         new CustomPacketPayload[0]
      );
      this.onClose();
   }

   private void updateClaimButton() {
      if (this.claimButton != null) {
         this.claimButton.active = this.sovereignMode && this.selectedEntry != null;
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.sovereignMode && this.nameField != null && this.nameField.isVisible()) {
         if (keyCode == 257 || keyCode == 335) {
            this.confirmFound();
            return true;
         }
         if (this.nameField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
         }
      }
      return super.keyPressed(keyCode, scanCode, modifiers);
   }

   @Override
   public boolean charTyped(char codePoint, int modifiers) {
      if (this.sovereignMode && this.nameField != null && this.nameField.isVisible() && this.nameField.charTyped(codePoint, modifiers)) {
         return true;
      }
      return super.charTyped(codePoint, modifiers);
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 1610612736);
      int parchX = (this.width - 204) / 2;
      int parchY = (this.height - 220) / 2;
      PanelRenderHelper.renderTexturedBackground(graphics, PanelRenderHelper.PANEL_TEXTURE, parchX, parchY, 204, 220);
      Component title = this.sovereignMode
         ? Component.translatable("gui.millenaire.new_village.found_title")
         : Component.translatable("gui.millenaire.new_village.title");
      this.renderParchmentHeader(graphics, title, parchX, parchY, 204);
      int contentX = parchX + 16;
      int contentStartY = this.computeContentStartY(parchY);
      int startIdx = this.currentPage * this.rowsPerPage;
      int endIdx = Math.min(startIdx + this.rowsPerPage, this.displayRows.size());
      int rowY = contentStartY;

      for (int i = startIdx; i < endIdx; i++) {
         NewVillageScreen.DisplayRow row = this.displayRows.get(i);
         if (row instanceof NewVillageScreen.CultureHeaderRow header) {
            graphics.drawString(this.font, Component.literal(header.cultureName()).withStyle(s -> s.withBold(true)), contentX, rowY + 3, -14018560, false);
         } else if (row instanceof NewVillageScreen.EmptyHintRow) {
            graphics.drawWordWrap(
               this.font,
               Component.translatable("gui.millenaire.new_village.no_foundable_types"),
               contentX,
               rowY + 2,
               TEXT_WIDTH,
               -65536
            );
         }

         rowY += 16;
      }

      if (this.statusMessage != null) {
         graphics.drawWordWrap(this.font, this.statusMessage, contentX, parchY + 220 - 28, TEXT_WIDTH, -65536);
      }

      this.renderPageCounter(graphics, parchX, parchY, 204, 220);
      super.render(graphics, mouseX, mouseY, partialTick);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0) {
         int parchX = (this.width - 204) / 2;
         int parchY = (this.height - 220) / 2;
         int contentX = parchX + 16;
         int y = this.computeContentStartY(parchY);
         int startIdx = this.currentPage * this.rowsPerPage;
         int endIdx = Math.min(startIdx + this.rowsPerPage, this.displayRows.size());

         for (int i = startIdx; i < endIdx; i++) {
            NewVillageScreen.DisplayRow row = this.displayRows.get(i);
            if (row instanceof NewVillageScreen.CultureHeaderRow header
               && mouseY >= y
               && mouseY < y + 16
               && mouseX >= contentX
               && mouseX < contentX + TEXT_WIDTH) {
               TravelBookNavHelper.openCulture(this, header.cultureKey());
               return true;
            }

            y += 16;
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   private record CultureHeaderRow(String cultureName, String cultureKey) implements NewVillageScreen.DisplayRow {
   }

   private sealed interface DisplayRow
      permits NewVillageScreen.CultureHeaderRow,
         NewVillageScreen.VillageTypeRow,
         NewVillageScreen.RandomRow,
         NewVillageScreen.EmptyHintRow {
   }

   private record RandomRow() implements NewVillageScreen.DisplayRow {
   }

   private record EmptyHintRow() implements NewVillageScreen.DisplayRow {
   }

   private record VillageTypeRow(VillageTypeListPayload.VillageTypeEntry entry) implements NewVillageScreen.DisplayRow {
   }
}
