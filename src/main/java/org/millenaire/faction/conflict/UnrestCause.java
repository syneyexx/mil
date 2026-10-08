package org.millenaire.faction.conflict;

import java.util.Locale;
import javax.annotation.Nullable;

public enum UnrestCause {
   LOW_STABILITY,
   LOW_LEGITIMACY,
   UNPOPULAR_LEADER,
   OPPRESSION,
   HIGH_TAXATION,
   UNPOPULAR_POLICY,
   CULTURAL_TENSION,
   OCCUPATION,
   RESISTANCE,
   ECONOMIC_HARDSHIP,
   SHORTAGE,
   MILITARY_DEFEAT,
   TERRITORIAL_LOSS,
   FORCED_INTEGRATION,
   SUCCESSION_DISPUTE,
   POLITICAL_EXCLUSION,
   HIGH_POLARIZATION,
   BROKEN_CONCESSION,
   DISCRIMINATION;

   @Nullable
   public static UnrestCause tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return UnrestCause.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
