package org.millenaire.faction.diplomacy;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Why an agreement ended. Historical records remain; only active status changes.
 */
public enum AgreementTerminationReason {
   NONE,
   EXPIRED,
   EXPLICIT,
   SUPERSEDED,
   CONFLICT,
   FACTION_REMOVED,
   ADMIN;

   @Nullable
   public static AgreementTerminationReason tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return AgreementTerminationReason.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
