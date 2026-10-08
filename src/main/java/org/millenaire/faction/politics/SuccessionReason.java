package org.millenaire.faction.politics;

import java.util.Locale;
import javax.annotation.Nullable;

public enum SuccessionReason {
   DEATH,
   RESIGNATION,
   REMOVAL,
   ELECTION,
   HEREDITARY_SUCCESSION,
   COUP,
   COUNCIL_DECISION,
   MILITARY_TAKEOVER,
   APPOINTMENT,
   GOVERNMENT_CHANGE,
   INITIALIZATION;

   @Nullable
   public static SuccessionReason tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return SuccessionReason.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
