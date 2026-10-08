package org.millenaire.faction.politics;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Government archetypes. Behavioral differences live in {@link GovernmentTypeDefinition}.
 */
public enum GovernmentType {
   CHIEFDOM,
   TRIBAL_COUNCIL,
   MONARCHY,
   COUNCIL,
   REPUBLIC,
   MILITARY_RULE;

   @Nullable
   public static GovernmentType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return GovernmentType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   public LeadershipRole defaultLeaderRole() {
      return switch (this) {
         case CHIEFDOM -> LeadershipRole.CHIEF;
         case TRIBAL_COUNCIL, COUNCIL -> LeadershipRole.COUNCIL_HEAD;
         case MONARCHY -> LeadershipRole.KING;
         case REPUBLIC -> LeadershipRole.PRESIDENT;
         case MILITARY_RULE -> LeadershipRole.WAR_LEADER;
      };
   }

   public SuccessionMechanism defaultSuccession() {
      return switch (this) {
         case CHIEFDOM, MONARCHY -> SuccessionMechanism.HEREDITARY;
         case TRIBAL_COUNCIL, COUNCIL -> SuccessionMechanism.COUNCIL_SELECTION;
         case REPUBLIC -> SuccessionMechanism.ELECTIVE;
         case MILITARY_RULE -> SuccessionMechanism.MILITARY_SELECTION;
      };
   }
}
