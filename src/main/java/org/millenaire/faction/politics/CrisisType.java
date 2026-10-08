package org.millenaire.faction.politics;

import java.util.Locale;
import javax.annotation.Nullable;

public enum CrisisType {
   SUCCESSION_CRISIS,
   LEGITIMACY_CRISIS,
   ECONOMIC_CRISIS,
   MILITARY_CRISIS,
   COALITION_CRISIS,
   REFORM_CRISIS,
   REGIONAL_CRISIS;

   @Nullable
   public static CrisisType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return CrisisType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
