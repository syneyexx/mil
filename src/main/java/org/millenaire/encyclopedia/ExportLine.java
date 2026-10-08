package org.millenaire.encyclopedia;

import java.util.List;

public record ExportLine(
   String style,
   LocalizedText text,
   String iconKey,
   String iconLabel,
   String referenceButtonCulture,
   String referenceButtonType,
   String referenceButtonKey,
   LocalizedText referenceButtonLabel,
   String referenceButtonIconKey,
   String specialTag,
   List<ExportColumn> columns
) {
   public static final String STYLE_SEPARATOR = "separator";

   public static ExportLine.Builder builder() {
      return new ExportLine.Builder();
   }

   public static final class Builder {
      private String style;
      private LocalizedText text;
      private String iconKey;
      private String iconLabel;
      private String referenceButtonCulture;
      private String referenceButtonType;
      private String referenceButtonKey;
      private LocalizedText referenceButtonLabel;
      private String referenceButtonIconKey;
      private String specialTag;
      private List<ExportColumn> columns;

      public ExportLine.Builder style(String v) {
         this.style = v;
         return this;
      }

      public ExportLine.Builder text(LocalizedText v) {
         this.text = v;
         return this;
      }

      public ExportLine.Builder iconKey(String v) {
         this.iconKey = v;
         return this;
      }

      public ExportLine.Builder iconLabel(String v) {
         this.iconLabel = v;
         return this;
      }

      public ExportLine.Builder referenceButtonCulture(String v) {
         this.referenceButtonCulture = v;
         return this;
      }

      public ExportLine.Builder referenceButtonType(String v) {
         this.referenceButtonType = v;
         return this;
      }

      public ExportLine.Builder referenceButtonKey(String v) {
         this.referenceButtonKey = v;
         return this;
      }

      public ExportLine.Builder referenceButtonLabel(LocalizedText v) {
         this.referenceButtonLabel = v;
         return this;
      }

      public ExportLine.Builder referenceButtonIconKey(String v) {
         this.referenceButtonIconKey = v;
         return this;
      }

      public ExportLine.Builder specialTag(String v) {
         this.specialTag = v;
         return this;
      }

      public ExportLine.Builder columns(List<ExportColumn> v) {
         this.columns = v;
         return this;
      }

      public ExportLine build() {
         return new ExportLine(
            this.style,
            this.text,
            this.iconKey,
            this.iconLabel,
            this.referenceButtonCulture,
            this.referenceButtonType,
            this.referenceButtonKey,
            this.referenceButtonLabel,
            this.referenceButtonIconKey,
            this.specialTag,
            this.columns
         );
      }
   }
}
