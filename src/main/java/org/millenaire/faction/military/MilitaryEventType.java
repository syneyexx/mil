package org.millenaire.faction.military;

import java.util.Locale;
import javax.annotation.Nullable;

public enum MilitaryEventType {
   ARMY_CREATED,
   ARMY_DISBANDED,
   ALLEGIANCE_TRANSFERRED,
   UNIT_CREATED,
   UNIT_TRANSFERRED,
   COMMANDER_ASSIGNED,
   COMMANDER_REMOVED,
   RECRUITMENT_STARTED,
   RECRUITMENT_COMPLETED,
   MOBILIZATION_CHANGED,
   READINESS_CHANGED,
   GARRISON_CHANGED,
   ALERT_CHANGED;

   @Nullable
   public static MilitaryEventType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return MilitaryEventType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
