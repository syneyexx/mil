package org.millenaire.faction.politics;

import java.util.Locale;
import javax.annotation.Nullable;

public enum DecisionType {
   APPOINT_LEADER,
   CHANGE_POLICY,
   CHANGE_GOVERNMENT,
   GRANT_AUTONOMY,
   REDUCE_AUTONOMY,
   MOBILIZE,
   DEMOBILIZE,
   NEGOTIATE,
   REFORM,
   CONCESSION;

   @Nullable
   public static DecisionType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return DecisionType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
