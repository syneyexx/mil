package org.millenaire.civilization.presentation;

/**
 * How completely village inventory observations are known for a calculation.
 * Partial observation must never be presented as a proven total shortage.
 */
public enum InventoryCertainty {
   /** All relevant registered containers were readable. */
   KNOWN,
   /** Some relevant containers were readable; others were unavailable. */
   PARTIAL,
   /** No relevant containers could be observed. */
   UNKNOWN;

   public boolean isFullyKnown() {
      return this == KNOWN;
   }

   public boolean hasAnyObservation() {
      return this == KNOWN || this == PARTIAL;
   }

   public String labelKey() {
      return switch (this) {
         case KNOWN -> "civilization.millenaire.inventory.known";
         case PARTIAL -> "civilization.millenaire.inventory.partial";
         case UNKNOWN -> "civilization.millenaire.inventory.unknown";
      };
   }
}
