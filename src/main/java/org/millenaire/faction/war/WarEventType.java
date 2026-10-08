package org.millenaire.faction.war;

import java.util.Locale;
import javax.annotation.Nullable;

public enum WarEventType {
   WAR_DECLARED,
   FACTION_JOINED,
   ALLY_NOTIFIED,
   FACTION_LEFT,
   ARMY_MOBILIZED,
   BATTLE_STARTED,
   BATTLE_ENDED,
   ARMY_DEFEATED,
   ARMY_RETREATED,
   ARMY_DESTROYED,
   SETTLEMENT_DEFENDED,
   SETTLEMENT_CAPTURED,
   WAR_GOAL_PROGRESSED,
   WAR_GOAL_COMPLETED,
   CEASEFIRE_DECLARED,
   CEASEFIRE_ENDED,
   PEACE_NEGOTIATION_STARTED,
   PEACE_ACCEPTED,
   WAR_ENDED,
   REINFORCEMENT_ARRIVED,
   OBJECTIVE_ASSIGNED;

   @Nullable
   public static WarEventType tryParse(@Nullable String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      try {
         return WarEventType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return null;
      }
   }
}
