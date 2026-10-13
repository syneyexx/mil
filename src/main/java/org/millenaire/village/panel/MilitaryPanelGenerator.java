package org.millenaire.village.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.millenaire.combat.war.WarCampaign;
import org.millenaire.combat.war.WarCampaignService;
import org.millenaire.config.MillenaireServerConfig;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.village.Village;

public final class MilitaryPanelGenerator {
   private MilitaryPanelGenerator() {
   }

   public static PanelContent generateMilitary(Village village, @Nullable ServerLevel level) {
      List<PanelLine> lines = new ArrayList<>();
      String titleKey = "panel.millenaire.title.military";
      String[] titleArgs = new String[]{village.getVillageName()};
      lines.add(raidStatusLine(village));
      appendWarCampaignWarning(lines, village, level);
      lines.add(PanelLine.separator());
      int fighters = 0;
      List<PanelLine> fighterLines = new ArrayList<>();

      for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
         VillagerType vType = ModCultures.getVillagerType(entry.getValue());
         if (vType != null) {
            boolean isDefender = vType.hasTag("helpInAttacks");
            boolean isRaider = vType.hasTag("isRaider");
            if (isDefender || isRaider) {
               fighters++;
               String roleKey = PanelHelper.resolveRoleKey(entry.getValue());
               String statusKey;
               if (isDefender && isRaider) {
                  statusKey = "panel.millenaire.fighter_status_both";
               } else if (isDefender) {
                  statusKey = "panel.millenaire.fighter_status_defender";
               } else {
                  statusKey = "panel.millenaire.fighter_status_raider";
               }

               String displayName = "???";
               List<PanelLine> statsLines = new ArrayList<>();
               if (level != null && level.getEntity(entry.getKey()) instanceof MillVillager mv) {
                  String first = mv.getFirstName();
                  String family = mv.getFamilyName();
                  if (first != null && !first.isEmpty()) {
                     displayName = first + (family != null && !family.isEmpty() ? " " + family : "");
                  }

                  statsLines.add(PanelLine.translatableWithArgsColored("panel.millenaire.health_value", 5592405, String.valueOf(Math.round(mv.getMaxHealth()))));
                  int armorValue = mv.getArmorValue();
                  statsLines.add(PanelLine.translatableWithArgsColored("panel.millenaire.armour_value", 5592405, String.valueOf(armorValue)));
                  ItemStack mainHand = mv.getMainHandItem();
                  if (!mainHand.isEmpty()) {
                     String weaponDescId = mainHand.getItem().getDescriptionId();
                     statsLines.add(PanelLine.translatableWithMixedArgsColored("panel.millenaire.weapons_value", 5592405, 1, weaponDescId));
                  }
               }

               fighterLines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.fighter_name_role", 2, displayName, roleKey));
               fighterLines.add(PanelLine.translatableColored(statusKey, 170));
               fighterLines.addAll(statsLines);
               fighterLines.add(PanelLine.text(""));
            }
         }
      }

      lines.add(PanelLine.translatableWithArgs("panel.millenaire.offensive_value", String.valueOf(village.getVillageRaidingStrength())));
      lines.add(PanelLine.translatableWithArgs("panel.millenaire.defensive_value", String.valueOf(village.getVillageDefendingStrength())));
      lines.add(PanelLine.text(""));
      lines.add(PanelLine.translatableWithArgs("panel.millenaire.defenders_value", String.valueOf(fighters)));
      lines.add(PanelLine.text(""));
      if (fighters == 0) {
         lines.add(PanelLine.translatable("panel.millenaire.no_defenders"));
      } else {
         lines.addAll(fighterLines);
      }

      appendRaidHistory(
         lines,
         village.getRaidsPerformed(),
         "panel.millenaire.raids_performed",
         170,
         "panel.millenaire.raid_history_success",
         30464,
         "panel.millenaire.raid_history_failure",
         11141120
      );
      appendRaidHistory(
         lines,
         village.getRaidsSuffered(),
         "panel.millenaire.raids_suffered",
         11141120,
         "panel.millenaire.raid_history_raided",
         11141120,
         "panel.millenaire.raid_history_defended",
         30464
      );
      return new PanelContent(PanelType.MILITARY, titleKey, lines, true, titleArgs);
   }

   private static void appendRaidHistory(
      List<PanelLine> lines, List<String> history, String titleKey, int titleColor, String successKey, int successColor, String failureKey, int failureColor
   ) {
      if (history != null && !history.isEmpty()) {
         lines.add(PanelLine.separator());
         lines.add(PanelLine.translatableColored(titleKey, titleColor));
         lines.add(PanelLine.text(""));

         for (int i = history.size() - 1; i >= 0; i--) {
            String[] parts = history.get(i).split(";");
            if (parts.length >= 2) {
               boolean success = "success".equals(parts[0]);
               String otherName = parts[1];
               if (!success) {
                  lines.add(PanelLine.translatableWithArgsColored(failureKey, failureColor, otherName));
                  lines.add(PanelLine.text(""));
               } else {
                  lines.add(PanelLine.translatableWithArgsColored(successKey, successColor, otherName));
                  boolean anyLoot = false;

                  for (int j = 2; j < parts.length; j++) {
                     int slash = parts[j].lastIndexOf(47);
                     if (slash > 0 && slash != parts[j].length() - 1) {
                        String id = parts[j].substring(0, slash);
                        String qty = parts[j].substring(slash + 1);
                        Item item = (Item)BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElse(null);
                        if (item != null) {
                           lines.add(PanelLine.withIconTranslatable(item.getDescriptionId(), "x" + qty, id, 5592405));
                           anyLoot = true;
                        }
                     }
                  }

                  if (!anyLoot) {
                     lines.add(PanelLine.translatableColored("panel.millenaire.raid_nothing", 5592405));
                  }

                  lines.add(PanelLine.text(""));
               }
            }
         }
      }
   }

   static void addMilitary3D(PanelContentGenerator.DisplayData data, Village village) {
      data.addCenteredWithIcons(PanelHelper.t("panel.millenaire.military_title"), "minecraft:iron_sword", "minecraft:iron_sword");
      data.addCentered("");
      data.addCentered(PanelHelper.t(raidStatusKey(village)));
      data.addCenteredWithIcons(
         PanelHelper.t("panel.millenaire.offensive_strength") + ": " + village.getVillageRaidingStrength(), "minecraft:iron_axe", "minecraft:iron_axe"
      );
      data.addCenteredWithIcons(
         PanelHelper.t("panel.millenaire.defensive_strength") + ": " + village.getVillageDefendingStrength(),
         "minecraft:iron_chestplate",
         "minecraft:iron_chestplate"
      );
   }

   private static String raidStatusKey(Village village) {
      if (village.isUnderAttack()) {
         return "panel.millenaire.village_under_attack";
      } else if (village.getRaidTarget() != null) {
         return village.getRaidStart() > 0L ? "panel.millenaire.village_raiding" : "panel.millenaire.village_raid_planned";
      } else {
         return "panel.millenaire.village_at_peace";
      }
   }

   private static PanelLine raidStatusLine(Village village) {
      return PanelLine.translatable(raidStatusKey(village));
   }

   private static void appendWarCampaignWarning(List<PanelLine> lines, Village village, @Nullable ServerLevel level) {
      if (!MillenaireServerConfig.SERVER.warCampaignsEnabled.getAsBoolean() || level == null) {
         return;
      }
      WarCampaign campaign = WarCampaignService.activeCampaignForVillage(level, village);
      if (campaign == null || !campaign.isOpen()) {
         return;
      }
      lines.add(
         PanelLine.translatableWithArgs(
            "panel.millenaire.war.campaign_waves",
            String.valueOf(campaign.waveIndex()),
            String.valueOf(campaign.wavesTotal()),
            campaign.phase().name().toLowerCase()
         )
      );
      lines.add(PanelLine.translatable("panel.millenaire.war.wave_warning"));
   }
}
