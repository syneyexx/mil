package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.item.VillageCreateWandItem;
import org.millenaire.network.VillageCreateConfirmPayload;
import org.millenaire.network.VillageCreateWizardPayload;

public class VillageCreateWizardScreen extends AbstractMillenaireScreen {
   private static final int WIDTH = 280;
   private static final int HEIGHT = 220;
   private static final String PLAYER_CUSTOM = "player_custom";

   private final VillageCreateWizardPayload data;
   private int step;
   private String selectedCulture = "";
   private String selectedType = "";
   private String villageName = "";
   private EditBox nameField;
   private final List<Button> stepButtons = new ArrayList<>();

   public VillageCreateWizardScreen(VillageCreateWizardPayload data) {
      super(Component.translatable("gui.millenaire.village_create.wizard.title"));
      this.data = data;
   }

   @Override
   protected void init() {
      super.init();
      this.rebuildStep();
   }

   private void rebuildStep() {
      for (Button b : this.stepButtons) {
         this.removeWidget(b);
      }
      this.stepButtons.clear();
      if (this.nameField != null) {
         this.removeWidget(this.nameField);
         this.nameField = null;
      }
      int x = (this.width - WIDTH) / 2;
      int y = (this.height - HEIGHT) / 2;

      if (this.step > 0) {
         this.addStepButton(
            Button.builder(Component.translatable("gui.millenaire.village_create.back"), b -> {
                  if (this.step == 4 && !PLAYER_CUSTOM.equals(this.selectedCulture)) {
                     this.step = 2;
                  } else {
                     this.step--;
                  }
                  this.rebuildStep();
               })
               .bounds(x + 16, y + HEIGHT - 28, 70, 18)
               .build()
         );
      }

      switch (this.step) {
         case 0 -> this.addStepButton(
            Button.builder(Component.translatable("gui.millenaire.village_create.next"), b -> {
                  this.step = 1;
                  this.rebuildStep();
               })
               .bounds(x + WIDTH - 86, y + HEIGHT - 28, 70, 18)
               .build()
         );
         case 1 -> this.addStepButton(
            Button.builder(Component.translatable("gui.millenaire.village_create.next"), b -> {
                  this.step = 2;
                  this.rebuildStep();
               })
               .bounds(x + WIDTH - 86, y + HEIGHT - 28, 70, 18)
               .build()
         );
         case 2 -> {
            int by = y + 50;
            for (VillageCreateWizardPayload.CultureOption c : this.data.cultures()) {
               String key = c.cultureKey();
               this.addStepButton(
                  Button.builder(Component.literal(c.displayName()), b -> {
                        this.selectedCulture = key;
                        this.selectedType = "";
                        this.villageName = "";
                        if (PLAYER_CUSTOM.equals(key)) {
                           this.step = 3;
                        } else {
                           this.selectedType = this.resolveTypeKeyForCulture(key);
                           this.step = 4;
                        }
                        this.rebuildStep();
                     })
                     .bounds(x + 40, by, 200, 16)
                     .build()
               );
               by += 18;
               if (by > y + HEIGHT - 50) {
                  break;
               }
            }
         }
         case 3 -> {
            int by = y + 50;
            for (VillageCreateWizardPayload.TypeOption t : this.data.types()) {
               if (!PLAYER_CUSTOM.equals(t.cultureKey())) {
                  continue;
               }
               this.addStepButton(
                  Button.builder(Component.literal(t.displayName()), b -> {
                        this.selectedType = t.typeKey();
                        this.villageName = "";
                        this.step = 4;
                        this.rebuildStep();
                     })
                     .bounds(x + 40, by, 200, 16)
                     .build()
               );
               by += 18;
               if (by > y + HEIGHT - 50) {
                  break;
               }
            }
         }
         case 4 -> {
            this.nameField = new EditBox(
               this.font, x + 40, y + 70, 200, 18, Component.translatable("gui.millenaire.village_create.pick_name")
            );
            this.nameField.setMaxLength(VillageCreateWandItem.MAX_VILLAGE_NAME_LENGTH);
            this.nameField.setValue(this.villageName);
            this.addRenderableWidget(this.nameField);
            this.setInitialFocus(this.nameField);

            this.addStepButton(
               Button.builder(Component.translatable("gui.millenaire.village_create.found"), b -> {
                     String name = VillageCreateWandItem.sanitizeVillageName(this.villageName);
                     if (name.isEmpty() || this.selectedType.isEmpty()) {
                        return;
                     }
                     PacketDistributor.sendToServer(
                        new VillageCreateConfirmPayload(this.data.targetPos(), this.selectedCulture, this.selectedType, name),
                        new CustomPacketPayload[0]
                     );
                     this.onClose();
                  })
                  .bounds(x + WIDTH - 100, y + HEIGHT - 28, 84, 18)
                  .build()
            );
            this.nameField.setResponder(s -> {
               this.villageName = s;
               this.rebuildFoundButtonState();
            });
            this.rebuildFoundButtonState();
         }
         default -> {
         }
      }
   }

   private String resolveTypeKeyForCulture(String cultureKey) {
      for (VillageCreateWizardPayload.TypeOption t : this.data.types()) {
         if (t.cultureKey().equals(cultureKey)) {
            return t.typeKey();
         }
      }
      return "";
   }

   private void addStepButton(Button button) {
      this.addRenderableWidget(button);
      this.stepButtons.add(button);
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
      int ink = 0xFF2A2218;
      int red = 0xFF8B0000;

      switch (this.step) {
         case 0 -> {
            for (var line : this.font.split(Component.translatable("gui.millenaire.village_create.intro"), WIDTH - 32)) {
               graphics.drawString(this.font, line, x + 16, textY, ink, false);
               textY += 11;
            }
         }
         case 1 -> {
            graphics.drawString(
               this.font,
               Component.translatable("gui.millenaire.village_create.location", this.data.closestDistance(), this.data.warDistance()),
               x + 16,
               textY,
               ink,
               false
            );
            textY += 14;
            if (this.data.territoryConflict()) {
               for (var line : this.font.split(Component.translatable("gui.millenaire.village_create.war_warning"), WIDTH - 32)) {
                  graphics.drawString(this.font, line, x + 16, textY, red, false);
                  textY += 11;
               }
            }
            for (VillageCreateWizardPayload.NearbyVillage n : this.data.nearby()) {
               graphics.drawString(
                  this.font,
                  Component.literal(n.name() + " — " + n.distance() + "m" + (n.conflict() ? " !" : "")),
                  x + 16,
                  textY,
                  n.conflict() ? red : ink,
                  false
               );
               textY += 11;
            }
         }
         case 2 -> {
            graphics.drawString(this.font, Component.translatable("gui.millenaire.village_create.pick_culture"), x + 16, textY, ink, false);
            if (this.data.cultures().isEmpty()) {
               textY += 14;
               for (var line : this.font.split(Component.translatable("gui.millenaire.village_create.no_control"), WIDTH - 32)) {
                  graphics.drawString(this.font, line, x + 16, textY, red, false);
                  textY += 11;
               }
            }
         }
         case 3 -> graphics.drawString(
            this.font, Component.translatable("gui.millenaire.village_create.pick_layout_culture"), x + 16, textY, ink, false
         );
         case 4 -> {
            graphics.drawString(this.font, Component.translatable("gui.millenaire.village_create.pick_name"), x + 16, textY, ink, false);
            textY = y + 96;
            String preview = VillageCreateWandItem.sanitizeVillageName(this.villageName);
            if (!preview.isEmpty()) {
               graphics.drawString(
                  this.font, Component.translatable("gui.millenaire.village_create.confirm", preview), x + 16, textY, ink, false
               );
               textY += 14;
            }
            if (this.data.territoryConflict()) {
               for (var line : this.font.split(Component.translatable("gui.millenaire.village_create.war_inevitable"), WIDTH - 32)) {
                  graphics.drawString(this.font, line, x + 16, textY, red, false);
                  textY += 11;
               }
            }
            if (this.data.overlapsRadius()) {
               graphics.drawString(this.font, Component.translatable("gui.millenaire.village_create.overlaps"), x + 16, textY, red, false);
            }
         }
         default -> {
         }
      }
      super.render(graphics, mouseX, mouseY, partialTick);
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.step == 4 && this.nameField != null && this.nameField.isFocused()) {
         this.rebuildFoundButtonState();
      }
      return super.keyPressed(keyCode, scanCode, modifiers);
   }

   @Override
   public boolean charTyped(char codePoint, int modifiers) {
      boolean handled = super.charTyped(codePoint, modifiers);
      if (this.step == 4) {
         this.rebuildFoundButtonState();
      }
      return handled;
   }

   private void rebuildFoundButtonState() {
      if (this.stepButtons.isEmpty()) {
         return;
      }
      Button found = this.stepButtons.getLast();
      found.active = !this.data.overlapsRadius()
         && !this.selectedType.isEmpty()
         && !VillageCreateWandItem.sanitizeVillageName(this.villageName).isEmpty();
   }
}
