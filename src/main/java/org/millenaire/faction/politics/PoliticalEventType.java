package org.millenaire.faction.politics;

import java.util.Locale;
import javax.annotation.Nullable;

public enum PoliticalEventType {
   LEADER_APPOINTED,
   LEADER_REMOVED,
   SUCCESSION,
   GOVERNMENT_FORMED,
   GOVERNMENT_CHANGED,
   COALITION_FORMED,
   COALITION_COLLAPSED,
   POLICY_CHANGED,
   REFORM_PASSED,
   CONCESSION_GRANTED,
   POLITICAL_CRISIS_STARTED,
   POLITICAL_CRISIS_ESCALATED,
   POLITICAL_CRISIS_RESOLVED,
   GROUP_GAINED_POWER,
   GROUP_LOST_POWER,
   COUP_ATTEMPTED,
   COUP_SUCCEEDED,
   COUP_FAILED,
   INTERNAL_UNREST,
   INTERNAL_CONFLICT_STARTED,
   INTERNAL_CONFLICT_RESOLVED,
   GOVERNMENT_OVERTHROWN,
   AUTONOMY_GRANTED,
   DECISION_TAKEN,
   INTERIM_STARTED,
   INTERIM_ENDED,
   POWER_REDISTRIBUTED,
   LOCAL_ADMIN_APPOINTED;

   @Nullable
   public static PoliticalEventType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return PoliticalEventType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
