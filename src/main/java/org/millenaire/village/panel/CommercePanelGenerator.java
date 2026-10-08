package org.millenaire.village.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingInstance;
import org.millenaire.culture.Culture;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.item.ItemHelper;
import org.millenaire.language.BuildingNameHelper;
import org.millenaire.village.Village;

public final class CommercePanelGenerator {
   private CommercePanelGenerator() {
   }

   public static PanelContent generateInnVisitors(Village village, @Nullable BuildingInstance building) {
      List<PanelLine> lines = new ArrayList<>();
      String buildingTitle = building != null ? BuildingNameHelper.getServerFallbackName(building) : village.getVillageName();
      lines.add(PanelLine.translatableWithArgsColored("panel.millenaire.inn_visitors_title", 170, buildingTitle));
      lines.add(PanelLine.text(""));
      if (building != null && !building.getVisitorLog().isEmpty()) {
         List<String> log = building.getVisitorLog();

         for (int i = log.size() - 1; i >= 0; i--) {
            String entry = log.get(i);
            String[] parts = entry.split(";");
            if (parts.length <= 2 || !parts[0].equals("storedexports") && !parts[0].equals("broughtimport")) {
               if (parts.length <= 1) {
                  lines.add(PanelLine.text("  " + entry));
               } else {
                  String[] args = new String[parts.length - 1];
                  int argMask = 0;

                  for (int j = 0; j < args.length; j++) {
                     args[j] = parts[j + 1];
                     if (args[j].startsWith("role.millenaire.")) {
                        argMask |= 1 << j;
                     }
                  }

                  if (argMask != 0) {
                     lines.add(PanelLine.translatableWithMixedArgs(parts[0], argMask, args));
                  } else {
                     lines.add(PanelLine.translatableWithArgs(parts[0], args));
                  }
               }
            } else {
               StringBuilder goods = new StringBuilder();

               for (int j = 2; j < parts.length; j++) {
                  if (goods.length() > 0) {
                     goods.append(", ");
                  }

                  goods.append(describeLoggedGood(parts[j]));
               }

               lines.add(PanelLine.translatableWithArgs("panels." + parts[0], parts[1], goods.toString()));
            }

            lines.add(PanelLine.text(""));
         }
      } else {
         lines.add(PanelLine.translatable("panel.millenaire.no_visitors_currently"));
      }

      return new PanelContent(PanelType.INN_VISITORS, buildingTitle, lines, false, null);
   }

   public static PanelContent generateInnGoods(Village village, @Nullable BuildingInstance building) {
      List<PanelLine> lines = new ArrayList<>();
      String buildingTitle = building != null ? BuildingNameHelper.getServerFallbackName(building) : village.getVillageName();
      lines.add(PanelLine.colored(buildingTitle, 170));
      lines.add(PanelLine.text(""));
      lines.add(PanelLine.translatable("panel.millenaire.goods_traded"));
      lines.add(PanelLine.text(""));
      lines.add(PanelLine.translatable("panel.millenaire.goods_imported"));
      lines.add(PanelLine.text(""));
      addTallyLines(lines, building == null ? Map.of() : building.getImported());
      lines.add(PanelLine.text(""));
      lines.add(PanelLine.translatable("panel.millenaire.goods_exported"));
      lines.add(PanelLine.text(""));
      addTallyLines(lines, building == null ? Map.of() : building.getExported());
      return new PanelContent(PanelType.INN_TRADE_GOODS, buildingTitle, lines, false, null);
   }

   public static PanelContent generateVisitors(Village village, @Nullable BuildingInstance building, boolean isMarket) {
      List<PanelLine> lines = new ArrayList<>();
      String buildingTitle = building != null ? BuildingNameHelper.getServerFallbackName(building) : village.getVillageName();
      lines.add(PanelLine.colored(buildingTitle, 170));
      lines.add(PanelLine.text(""));
      if (isMarket) {
         int stallCount = 0;
         if (building != null) {
            stallCount = building.getPointsByType("stall").size();
            if (stallCount == 0) {
               stallCount = building.getPointsByType("sellingPos").size();
            }
         }

         int merchantCount = 0;
         if (building != null) {
            for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
               UUID uuid = entry.getKey();
               if (building.getId().equals(village.getVillagerHome(uuid))) {
                  VillagerType vType = ModCultures.getVillagerType(entry.getValue());
                  if (vType != null && vType.hasTag("foreignmerchant")) {
                     merchantCount++;
                  }
               }
            }
         }

         lines.add(PanelLine.translatableWithArgs("panel.millenaire.merchant_count", String.valueOf(merchantCount), String.valueOf(stallCount)));
         lines.add(PanelLine.text(""));
         if (building != null && merchantCount > 0) {
            for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
               UUID uuid = entry.getKey();
               if (building.getId().equals(village.getVillagerHome(uuid))) {
                  VillagerType vType = ModCultures.getVillagerType(entry.getValue());
                  if (vType != null && vType.hasTag("foreignmerchant")) {
                     String roleKey = PanelHelper.resolveRoleKey(entry.getValue());
                     Culture merchantCulture = ModCultures.getCulture(vType.culture());
                     String cultureStr = merchantCulture != null ? merchantCulture.displayName() : vType.culture().getPath();
                     lines.add(PanelLine.translatableWithMixedArgs("panel.millenaire.merchant_line", 1, roleKey, cultureStr));
                  }
               }
            }
         } else {
            lines.add(PanelLine.translatable("panel.millenaire.no_merchants_currently"));
         }
      } else {
         int visitorCount = 0;
         if (building != null) {
            for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
               UUID uuid = entry.getKey();
               if (building.getId().equals(village.getVillagerHome(uuid))) {
                  VillagerType vType = ModCultures.getVillagerType(entry.getValue());
                  if (vType != null && vType.hasTag("visitor")) {
                     visitorCount++;
                  }
               }
            }
         }

         lines.add(PanelLine.translatableWithArgs("panel.millenaire.visitor_count", String.valueOf(visitorCount)));
         lines.add(PanelLine.text(""));
         if (visitorCount == 0) {
            lines.add(PanelLine.translatable("panel.millenaire.no_visitors_currently"));
         }
      }

      PanelType resultType = isMarket ? PanelType.MARKET_MERCHANTS : PanelType.VISITORS;
      return new PanelContent(resultType, buildingTitle, lines, false, null);
   }

   private static void addTallyLines(List<PanelLine> lines, Map<Item, Integer> tally) {
      if (tally.isEmpty()) {
         lines.add(PanelLine.text("  §8-"));
      } else {
         for (Entry<Item, Integer> entry : tally.entrySet()) {
            Item item = entry.getKey();
            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
            lines.add(PanelLine.withIconTranslatable(item.getDescriptionId(), "§8" + entry.getValue(), itemId, 0));
         }
      }
   }

   private static String describeLoggedGood(String token) {
      int slash = token.lastIndexOf(47);
      if (slash < 0) {
         return token;
      }

      String itemId = token.substring(0, slash);
      String qty = token.substring(slash + 1);
      Item item = ItemHelper.resolve(itemId);
      String name = item != null ? item.getDescription().getString() : itemId.substring(itemId.indexOf(58) + 1).replace('_', ' ');
      return name + ": " + qty;
   }

   static void addCommerce3D(PanelContentGenerator.DisplayData data, PanelType type, Village village, @Nullable BuildingInstance building) {
      if (building != null) {
         String bldIcon = PanelHelper.resolveBuildingIcon(building);
         data.addCenteredBuildingNameWithIcons(
            PanelHelper.getBuildingTranslationKey(building), bldIcon, bldIcon, BuildingNameHelper.getServerFallbackName(building)
         );
         data.addCentered("");
         if (type == PanelType.INN_TRADE_GOODS) {
            int importTotal = building.getImported().values().stream().mapToInt(Integer::intValue).sum();
            int exportTotal = building.getExported().values().stream().mapToInt(Integer::intValue).sum();
            data.addCentered(PanelHelper.t("panel.millenaire.goods_imported") + ": " + importTotal);
            data.addCentered(PanelHelper.t("panel.millenaire.goods_exported") + ": " + exportTotal);
            return;
         }

         String tag = type == PanelType.MARKET_MERCHANTS ? "foreignmerchant" : "visitor";
         int count = 0;

         for (Entry<UUID, ResourceLocation> entry : village.getVillagerTypes().entrySet()) {
            UUID uuid = entry.getKey();
            if (building.getId().equals(village.getVillagerHome(uuid))) {
               VillagerType vType = ModCultures.getVillagerType(entry.getValue());
               if (vType != null && vType.hasTag(tag)) {
                  count++;
               }
            }
         }

         if (count > 0) {
            String label = type == PanelType.MARKET_MERCHANTS
               ? PanelHelper.t("panel.millenaire.merchant_list")
               : PanelHelper.t("panel.millenaire.visitor_list");
            data.addCentered(label + ": " + count);
         } else {
            data.addCentered(PanelHelper.t("panel.millenaire.no_visitors_currently"));
         }
      } else {
         data.addCentered(PanelHelper.t("panel.millenaire.panel_type." + type.name().toLowerCase(Locale.ROOT)));
      }
   }
}
