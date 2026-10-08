package org.millenaire.faction.politics;

import java.util.Locale;
import javax.annotation.Nullable;

public enum SuccessionMechanism {
   HEREDITARY,
   ELECTIVE,
   COUNCIL_SELECTION,
   MILITARY_SELECTION,
   APPOINTMENT,
   CUSTOM;

   @Nullable
   public static SuccessionMechanism tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return SuccessionMechanism.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
