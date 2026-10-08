package org.millenaire.faction.conflict;

import java.util.Locale;
import javax.annotation.Nullable;

public enum ConflictEventType {
   UNREST_CREATED,
   UNREST_ESCALATED,
   UNREST_RESOLVED,
   CONFLICT_STARTED,
   SIDE_JOINED,
   SIDE_LEFT,
   STAGE_CHANGED,
   OBJECTIVE_SET,
   MILITARY_SPLIT,
   ARMY_DEFECTED,
   WAR_LINKED,
   TERRITORY_CAPTURED,
   SETTLEMENT_JOINED,
   NEGOTIATION_STARTED,
   NEGOTIATION_FAILED,
   SETTLEMENT_REACHED,
   CONFLICT_RESOLVED,
   FACTION_SPLIT,
   FACTION_MERGED,
   REINTEGRATION,
   RECOVERY_STARTED,
   RECOVERY_COMPLETED,
   REPRESSION,
   CONCESSION,
   FOREIGN_RECOGNITION,
   STALE_RESOLVED;

   @Nullable
   public static ConflictEventType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return ConflictEventType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
