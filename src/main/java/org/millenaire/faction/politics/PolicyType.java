package org.millenaire.faction.politics;

import java.util.Locale;
import javax.annotation.Nullable;

public enum PolicyType {
   MILITARY_SPENDING,
   TRADE_POLICY,
   TAX_POLICY,
   CENTRALIZATION,
   LOCAL_AUTONOMY,
   OCCUPATION_POLICY,
   DIPLOMATIC_POSTURE,
   CONSCRIPTION,
   CULTURAL_POLICY;

   @Nullable
   public static PolicyType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return PolicyType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
