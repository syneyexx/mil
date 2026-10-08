package org.millenaire.encyclopedia;

public record ExportColumn(
   String style,
   LocalizedText text,
   String iconKey,
   String iconLabel,
   String referenceButtonCulture,
   String referenceButtonType,
   String referenceButtonKey,
   LocalizedText referenceButtonLabel,
   String referenceButtonIconKey
) {
   public static ExportColumn text(LocalizedText text) {
      return new ExportColumn(null, text, null, null, null, null, null, null, null);
   }

   public static ExportColumn withIcon(LocalizedText text, String iconKey) {
      return new ExportColumn(null, text, iconKey, null, null, null, null, null, null);
   }
}
