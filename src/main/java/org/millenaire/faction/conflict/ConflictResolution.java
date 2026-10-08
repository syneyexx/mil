package org.millenaire.faction.conflict;

import java.util.Locale;
import javax.annotation.Nullable;

public enum ConflictResolution {
   GOVERNMENT_VICTORY,
   REBEL_VICTORY,
   NEGOTIATED_SETTLEMENT,
   PARTIAL_AUTONOMY,
   LEADERSHIP_CHANGE,
   GOVERNMENT_REFORM,
   SECESSION,
   REINTEGRATION,
   FACTION_SPLIT,
   FACTION_MERGE,
   SURRENDER,
   STALE_RESOLVED;

   @Nullable
   public static ConflictResolution tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return ConflictResolution.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
