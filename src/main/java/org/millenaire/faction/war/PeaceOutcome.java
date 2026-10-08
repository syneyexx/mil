package org.millenaire.faction.war;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Diplomatic/military outcomes only. No territorial transfer terms in Phase 6.
 */
public enum PeaceOutcome {
   WHITE_PEACE,
   ATTACKER_VICTORY,
   DEFENDER_VICTORY,
   STATUS_QUO,
   CAPITULATION_ATTACKER,
   CAPITULATION_DEFENDER,
   EXHAUSTION;

   @Nullable
   public static PeaceOutcome tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return PeaceOutcome.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
