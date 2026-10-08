package org.millenaire.faction.politics;

import java.util.Locale;
import javax.annotation.Nullable;

public enum InterestType {
   MILITARY_SPENDING,
   TRADE,
   TAXATION,
   EXPANSION,
   PEACE,
   TRADITION,
   REFORM,
   LOCAL_AUTONOMY,
   CENTRALIZATION,
   ECONOMIC_GROWTH,
   CULTURAL_PRESERVATION;

   @Nullable
   public static InterestType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return InterestType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
