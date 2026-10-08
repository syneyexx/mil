package org.millenaire.encyclopedia;

import java.util.List;

public record IndexEntry(
   String itemRef,
   String cultureSlug,
   String type,
   String itemKey,
   LocalizedText label,
   String nativePrefix,
   String iconKey,
   String category,
   LocalizedText categoryLabel,
   int displayOrder,
   List<String> residents,
   Boolean signature,
   String signatureFamily,
   Boolean signaturePrimary
) {
}
