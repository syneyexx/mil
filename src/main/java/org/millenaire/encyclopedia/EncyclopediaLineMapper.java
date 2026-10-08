package org.millenaire.encyclopedia;

import java.util.List;
import java.util.function.UnaryOperator;
import org.millenaire.language.DisplayNameResolver;
import org.millenaire.village.TravelBookLine;
import org.millenaire.village.TravelBookScreenState;

public final class EncyclopediaLineMapper {
   private static final String DESC_NS_PREFIX = "travelbook.millenaire.";
   private static final String DESC_SUFFIX = ".desc";

   private EncyclopediaLineMapper() {
   }

   public static ExportLine map(TravelBookLine line, int lineIndex, UnaryOperator<String> resolve, SlotText slots) {
      if (line.isSeparator()) {
         return ExportLine.builder().style("separator").build();
      }

      ExportLine.Builder b = ExportLine.builder();
      if (line.isColumns()) {
         String leftId = columnSlotId(lineIndex, 0);
         String rightId = columnSlotId(lineIndex, 1);
         slots.put(leftId, strip(line.leftColumn()));
         slots.put(rightId, strip(line.rightColumn()));
         ExportColumn left = line.leftIcon() != null
            ? ExportColumn.withIcon(LocalizedText.id(leftId), line.leftIcon())
            : ExportColumn.text(LocalizedText.id(leftId));
         ExportColumn right = ExportColumn.text(LocalizedText.id(rightId));
         b.columns(List.of(left, right));
         applyNavTarget(b, line.navTarget(), LocalizedText.id(leftId));
         return b.build();
      }

      LocalizedText text;
      if (line.translatable() && line.nativePrefix() == null) {
         text = LocalizedText.key(line.text());
         if (isDescKey(line.text())) {
            b.specialTag("MAIN_DESC");
         }
      } else {
         String slotId = lineSlotId(lineIndex);
         String value;
         if (line.translatable()) {
            value = DisplayNameResolver.resolve(strip(resolve.apply(line.text())), true, strip(line.nativePrefix()), line.text());
         } else {
            value = strip(line.text());
         }

         slots.put(slotId, value);
         text = LocalizedText.id(slotId);
      }

      b.text(text);
      applyNavTarget(b, line.navTarget(), text);
      return b.build();
   }

   private static String lineSlotId(int lineIndex) {
      return "p0.l" + lineIndex;
   }

   private static String columnSlotId(int lineIndex, int colIndex) {
      return "p0.l" + lineIndex + ".c" + colIndex;
   }

   private static void applyNavTarget(ExportLine.Builder b, TravelBookLine.TravelBookNavTarget target, LocalizedText label) {
      if (target != null) {
         String segment = segmentFor(target.targetState());
         if (segment != null) {
            b.referenceButtonCulture(target.cultureKey());
            b.referenceButtonType(segment);
            b.referenceButtonKey(simpleKey(target.itemKey()));
            b.referenceButtonLabel(label);
         }
      }
   }

   private static String segmentFor(TravelBookScreenState state) {
      return switch (state) {
         case CULTURE -> "cultures";
         case VILLAGER_DETAIL -> "villagers";
         case VILLAGE_DETAIL -> "villages";
         case BUILDING_DETAIL -> "buildings";
         case TRADE_GOOD_DETAIL -> "tradegoods";
         default -> null;
      };
   }

   private static String simpleKey(String itemKey) {
      if (itemKey == null) {
         return null;
      }

      int slash = itemKey.lastIndexOf(47);
      return slash >= 0 ? itemKey.substring(slash + 1) : itemKey;
   }

   private static boolean isDescKey(String key) {
      return key != null && key.startsWith("travelbook.millenaire.") && key.endsWith(".desc");
   }

   private static String strip(String text) {
      return text == null ? null : text.replaceAll("(?i)§[0-9A-FK-OR]", "");
   }
}
