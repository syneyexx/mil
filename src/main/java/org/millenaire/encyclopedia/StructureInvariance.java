package org.millenaire.encyclopedia;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;

public final class StructureInvariance {
   public static final String REFERENCE_LOCALE = "en_us";

   private StructureInvariance() {
   }

   public static List<ExportLine> validate(String itemRef, Map<String, List<ExportLine>> perLocaleSkeletons) {
      List<ExportLine> reference = perLocaleSkeletons.get("en_us");
      if (reference == null) {
         throw new IllegalStateException("Structure invariance check for item '" + itemRef + "' is missing the reference locale 'en_us'");
      }

      List<StructureInvariance.LineSignature> referenceSignature = signature(reference);

      for (Entry<String, List<ExportLine>> entry : perLocaleSkeletons.entrySet()) {
         String locale = entry.getKey();
         if (!"en_us".equals(locale)) {
            List<StructureInvariance.LineSignature> candidate = signature(entry.getValue());
            if (!candidate.equals(referenceSignature)) {
               throw new IllegalStateException(
                  "Structure invariance violated for item '"
                     + itemRef
                     + "': locale '"
                     + locale
                     + "' skeleton diverges from reference locale 'en_us' ("
                     + describeDivergence(referenceSignature, candidate)
                     + ")"
               );
            }
         }
      }

      return reference;
   }

   private static List<StructureInvariance.LineSignature> signature(List<ExportLine> lines) {
      return lines.stream().map(StructureInvariance::canonical).toList();
   }

   private static String describeDivergence(List<StructureInvariance.LineSignature> reference, List<StructureInvariance.LineSignature> candidate) {
      if (reference.size() != candidate.size()) {
         return "line count " + candidate.size() + " != reference " + reference.size();
      }

      for (int i = 0; i < reference.size(); i++) {
         if (!reference.get(i).equals(candidate.get(i))) {
            return "first divergence at line index " + i + ": reference=" + reference.get(i) + " candidate=" + candidate.get(i);
         }
      }

      return "structures differ";
   }

   private static StructureInvariance.LineSignature canonical(ExportLine line) {
      List<StructureInvariance.ColumnSignature> columns = line.columns() == null ? null : line.columns().stream().map(StructureInvariance::canonical).toList();
      return new StructureInvariance.LineSignature(
         line.style(),
         signatureOf(line.text()),
         line.iconKey(),
         line.iconLabel(),
         line.specialTag(),
         columns,
         line.referenceButtonCulture(),
         line.referenceButtonType(),
         line.referenceButtonKey(),
         signatureOf(line.referenceButtonLabel()),
         line.referenceButtonIconKey()
      );
   }

   private static StructureInvariance.ColumnSignature canonical(ExportColumn column) {
      return new StructureInvariance.ColumnSignature(
         column.style(),
         signatureOf(column.text()),
         column.iconKey(),
         column.iconLabel(),
         column.referenceButtonCulture(),
         column.referenceButtonType(),
         column.referenceButtonKey(),
         signatureOf(column.referenceButtonLabel()),
         column.referenceButtonIconKey()
      );
   }

   private static StructureInvariance.TextSignature signatureOf(LocalizedText text) {
      if (text == null) {
         return null;
      } else if (text.key() != null) {
         return new StructureInvariance.TextSignature(StructureInvariance.Mode.KEY, text.key());
      } else if (text.id() != null) {
         return new StructureInvariance.TextSignature(StructureInvariance.Mode.ID, null);
      } else {
         return text.text() != null
            ? new StructureInvariance.TextSignature(StructureInvariance.Mode.TEXT, text.text())
            : new StructureInvariance.TextSignature(StructureInvariance.Mode.EMPTY, null);
      }
   }

   private record ColumnSignature(
      String style,
      StructureInvariance.TextSignature text,
      String iconKey,
      String iconLabel,
      String referenceButtonCulture,
      String referenceButtonType,
      String referenceButtonKey,
      StructureInvariance.TextSignature referenceButtonLabel,
      String referenceButtonIconKey
   ) {
   }

   private record LineSignature(
      String style,
      StructureInvariance.TextSignature text,
      String iconKey,
      String iconLabel,
      String specialTag,
      List<StructureInvariance.ColumnSignature> columns,
      String referenceButtonCulture,
      String referenceButtonType,
      String referenceButtonKey,
      StructureInvariance.TextSignature referenceButtonLabel,
      String referenceButtonIconKey
   ) {
   }

   private enum Mode {
      KEY,
      ID,
      TEXT,
      EMPTY;
   }

   private record TextSignature(StructureInvariance.Mode mode, String value) {
      private TextSignature {
         Objects.requireNonNull(mode, "mode");
      }
   }
}
