package org.millenaire.faction.politics;

import java.util.Locale;
import javax.annotation.Nullable;

public enum LeadershipRole {
   RULER,
   CHIEF,
   KING,
   QUEEN,
   PRESIDENT,
   COUNCIL_HEAD,
   WAR_LEADER,
   GOVERNOR,
   ADVISOR,
   REGENT;

   @Nullable
   public static LeadershipRole tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return LeadershipRole.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public boolean isPrimaryRuler() {
      return this != ADVISOR && this != GOVERNOR;
   }
}
