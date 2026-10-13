package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.millenaire.network.CultureWarActionPayload;
import org.millenaire.network.DesignateBuildingPayload;
import org.millenaire.network.InfoPanelRequestPayload;
import org.millenaire.network.VillageCreateHubPayload;
import org.millenaire.village.PlayerBuildingRole;

public class VillageCreateHubScreen extends AbstractMillenaireScreen {
   private static final int WIDTH = 340;
   private static final int HEIGHT = 300;
   private final VillageCreateHubPayload data;
   private Tab tab = Tab.OVERVIEW;
   private final List<Button> dynamic = new ArrayList<>();

   private enum Tab {
      OVERVIEW,
      DIPLOMACY,
      LOG,
      ACTIONS
   }

   public VillageCreateHubScreen(VillageCreateHubPayload data) {
      super(Component.translatable("gui.millenaire.village_create.hub.title"));
      this.data = data;
   }

   @Override
   protected void init() {
      super.init();
      int x = (this.width - WIDTH) / 2;
      int y = (this.height - HEIGHT) / 2;
      this.addRenderableWidget(tabButton(x + 12, y + 40, Tab.OVERVIEW, "gui.millenaire.village_create.hub.overview"));
      this.addRenderableWidget(tabButton(x + 70, y + 40, Tab.DIPLOMACY, "gui.millenaire.village_create.hub.diplomacy"));
      this.addRenderableWidget(tabButton(x + 140, y + 40, Tab.LOG, "gui.millenaire.village_create.hub.log"));
      this.addRenderableWidget(tabButton(x + 200, y + 40, Tab.ACTIONS, "gui.millenaire.village_create.hub.actions"));
      this.rebuildDynamic();
   }

   private Button tabButton(int x, int y, Tab t, String key) {
      return Button.builder(Component.translatable(key), b -> {
            this.tab = t;
            this.rebuildDynamic();
         })
         .bounds(x, y, 56, 16)
         .build();
   }

   private void rebuildDynamic() {
      for (Button b : this.dynamic) {
         this.removeWidget(b);
      }
      this.dynamic.clear();
      int x = (this.width - WIDTH) / 2;
      int y = (this.height - HEIGHT) / 2;
      if (this.tab == Tab.ACTIONS) {
         this.addDyn(
            Button.builder(Component.translatable("gui.millenaire.ledger.open_short"), b -> {
                  PacketDistributor.sendToServer(new InfoPanelRequestPayload(), new CustomPacketPayload[0]);
               })
               .bounds(x + 16, y + 72, 140, 16)
               .build()
         );
         int col = 0;
         int row = 0;
         for (PlayerBuildingRole role : PlayerBuildingRole.values()) {
            int bx = x + 16 + col * 156;
            int by = y + 96 + row * 18;
            String roleId = role.id();
            this.addDyn(
               Button.builder(Component.translatable("gui.millenaire.designate.role." + roleId), b -> {
                     PacketDistributor.sendToServer(
                        new DesignateBuildingPayload(this.data.villageUuid(), roleId),
                        new CustomPacketPayload[0]
                     );
                  })
                  .bounds(bx, by, 150, 16)
                  .build()
            );
            col++;
            if (col >= 2) {
               col = 0;
               row++;
            }
         }
      } else if (this.tab == Tab.DIPLOMACY) {
         int by = y + 70;
         for (VillageCreateHubPayload.CultureStandingRow row : this.data.standings()) {
            if (by > y + HEIGHT - 40) {
               break;
            }
            if (row.atWar()) {
               String cultureId = row.cultureId();
               this.addDyn(
                  Button.builder(Component.translatable("gui.millenaire.village_create.hub.end_war", row.displayName()), b -> {
                        PacketDistributor.sendToServer(
                           new CultureWarActionPayload(this.data.villageUuid(), CultureWarActionPayload.ACTION_END_WAR, cultureId),
                           new CustomPacketPayload[0]
                        );
                     })
                     .bounds(x + 220, by, 100, 14)
                     .build()
               );
            } else {
               String cultureId = row.cultureId();
               this.addDyn(
                  Button.builder(Component.translatable("gui.millenaire.village_create.hub.declare_war"), b -> {
                        PacketDistributor.sendToServer(
                           new CultureWarActionPayload(this.data.villageUuid(), CultureWarActionPayload.ACTION_DECLARE_WAR, cultureId),
                           new CustomPacketPayload[0]
                        );
                     })
                     .bounds(x + 220, by, 100, 14)
                     .build()
               );
            }
            by += 16;
         }
      }
   }

   private void addDyn(Button b) {
      this.addRenderableWidget(b);
      this.dynamic.add(b);
   }

   @Override
   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(graphics, mouseX, mouseY, partialTick);
      int x = (this.width - WIDTH) / 2;
      int y = (this.height - HEIGHT) / 2;
      graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF2A2218);
      graphics.fill(x + 2, y + 2, x + WIDTH - 2, y + HEIGHT - 2, 0xFFD8C9A0);
      Component title = this.data.villageName().isEmpty()
         ? this.getTitle()
         : Component.literal(this.data.villageName());
      this.renderParchmentHeader(graphics, title, x, y, WIDTH);
      int ink = 0xFF2A2218;
      int textY = y + 62;

      switch (this.tab) {
         case OVERVIEW -> {
            graphics.drawString(this.font, Component.translatable("gui.millenaire.village_create.hub.pop", this.data.population()), x + 16, textY, ink, false);
            textY += 12;
            graphics.drawString(this.font, Component.translatable("gui.millenaire.village_create.hub.buildings", this.data.buildings()), x + 16, textY, ink, false);
            textY += 12;
            graphics.drawString(this.font, Component.translatable("gui.millenaire.village_create.hub.jobs", this.data.openJobs(), this.data.openBeds()), x + 16, textY, ink, false);
            textY += 12;
            graphics.drawString(this.font, Component.translatable("gui.millenaire.village_create.hub.threat", this.data.threatLevel()), x + 16, textY, ink, false);
         }
         case DIPLOMACY -> {
            for (VillageCreateHubPayload.CultureStandingRow row : this.data.standings()) {
               int color = row.atWar() ? 0xFF8B0000 : ink;
               graphics.drawString(
                  this.font,
                  Component.literal(row.displayName() + ": " + row.standing() + (row.atWar() ? " WAR" : "")),
                  x + 16,
                  textY,
                  color,
                  false
               );
               textY += 16;
               if (textY > y + HEIGHT - 30) {
                  break;
               }
            }
         }
         case LOG -> {
            List<VillageCreateHubPayload.LogRow> rows = this.data.logRows();
            for (int i = Math.max(0, rows.size() - 12); i < rows.size(); i++) {
               VillageCreateHubPayload.LogRow r = rows.get(i);
               graphics.drawString(
                  this.font,
                  Component.literal("D" + r.day() + " " + r.action() + " " + r.cultureA() + "/" + r.cultureB() + " (" + r.standingAfter() + ")"),
                  x + 12,
                  textY,
                  ink,
                  false
               );
               textY += 11;
            }
         }
         case ACTIONS -> graphics.drawString(
            this.font,
            Component.translatable("gui.millenaire.designate.hint"),
            x + 16,
            textY,
            ink,
            false
         );
      }
      super.render(graphics, mouseX, mouseY, partialTick);
   }
}
