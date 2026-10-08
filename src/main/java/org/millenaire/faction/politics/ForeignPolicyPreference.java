package org.millenaire.faction.politics;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Group preference toward external affairs. Exposes pressure to diplomacy; does not override it.
 */
public enum ForeignPolicyPreference {
   WAR,
   PEACE,
   TRADE,
   ALLIANCE,
   ISOLATION,
   DIPLOMACY;

   @Nullable
   public static ForeignPolicyPreference tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return ForeignPolicyPreference.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
