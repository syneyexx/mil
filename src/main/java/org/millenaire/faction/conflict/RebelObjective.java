package org.millenaire.faction.conflict;

import java.util.Locale;
import javax.annotation.Nullable;

public enum RebelObjective {
   REPLACE_LEADER,
   CHANGE_GOVERNMENT,
   CHANGE_POLICY,
   GAIN_AUTONOMY,
   SECEDE,
   OVERTHROW_GOVERNMENT,
   RESTORE_PREVIOUS_RULER,
   ESTABLISH_NEW_GOVERNMENT,
   INDEPENDENCE,
   LIBERATION;

   @Nullable
   public static RebelObjective tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return RebelObjective.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public boolean requiresFactionSplit() {
      return this == SECEDE || this == INDEPENDENCE;
   }

   public boolean requiresGovernmentChange() {
      return this == CHANGE_GOVERNMENT
         || this == OVERTHROW_GOVERNMENT
         || this == ESTABLISH_NEW_GOVERNMENT
         || this == RESTORE_PREVIOUS_RULER;
   }
}
