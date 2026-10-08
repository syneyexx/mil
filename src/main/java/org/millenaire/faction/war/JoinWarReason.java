package org.millenaire.faction.war;

import java.util.Locale;
import javax.annotation.Nullable;

public enum JoinWarReason {
   INITIATOR,
   TARGET,
   ALLIANCE,
   DEFENSIVE_PACT,
   DIPLOMATIC_INTERVENTION,
   VOLUNTARY;

   @Nullable
   public static JoinWarReason tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return JoinWarReason.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
