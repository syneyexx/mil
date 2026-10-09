package org.millenaire.living.needs;

import java.util.Locale;

/**
 * Deterministic severity for village needs. Derived from quantities and blocking conditions.
 */
public enum VillageNeedPriority {
   NONE,
   LOW,
   MODERATE,
   HIGH,
   CRITICAL;

   public String i18nKey() {
      return "living.millenaire.severity." + this.name().toLowerCase(Locale.ROOT);
   }

   public int rank() {
      return this.ordinal();
   }

   /**
    * Hysteresis: rising uses the higher threshold; falling requires dropping by one full band.
    */
   public static VillageNeedPriority fromShortageRatio(double missingRatio, VillageNeedPriority previous) {
      VillageNeedPriority raw;
      if (missingRatio <= 0.0) {
         raw = NONE;
      } else if (missingRatio < 0.15) {
         raw = LOW;
      } else if (missingRatio < 0.40) {
         raw = MODERATE;
      } else if (missingRatio < 0.75) {
         raw = HIGH;
      } else {
         raw = CRITICAL;
      }
      if (previous == null || previous == NONE) {
         return raw;
      }
      if (raw.rank() >= previous.rank()) {
         return raw;
      }
      // Require crossing a full band down before demoting.
      if (previous.rank() - raw.rank() >= 2) {
         return raw;
      }
      return previous;
   }
}
