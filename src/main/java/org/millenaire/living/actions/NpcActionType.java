package org.millenaire.living.actions;

import java.util.Locale;

public enum NpcActionType {
   VIEW_VILLAGE_NEEDS,
   OFFER_ASSISTANCE,
   ACCEPT_REQUEST,
   DECLINE_REQUEST,
   DELIVER_RESOURCES,
   REVIEW_REQUEST_PROGRESS,
   COMPLETE_REQUEST,
   DISCUSS_EXISTING_QUEST,
   OPEN_EXISTING_QUEST_INTERACTION,
   OPEN_EXISTING_TRADE_INTERACTION,
   VIEW_VILLAGE_STATUS,
   REPORT_WORLD_EVENT,
   SUPPORT_VILLAGE_NEED,
   PRIORITIZE_HOUSING_PROJECT,
   VIEW_COLONIZATION_PROPOSAL,
   SUPPORT_COLONIZATION_MATERIALS,
   AUTHORIZE_COLONIZATION_PROPOSAL,
   VIEW_INTERRUPTED_OPERATION,
   VIEW_RELIGION_STATUS,
   HELP_PREPARE_CEREMONY,
   DELIVER_RITUAL_SUPPLIES,
   JOIN_CEREMONY,
   SUPPORT_AFTER_ATTACK;

   public String i18nKey() {
      // Legacy enum name kept for stored proposals; player-facing meaning is resume.
      if (this == AUTHORIZE_COLONIZATION_PROPOSAL) {
         return "living.millenaire.action.resume_colonization";
      }
      if (this == VIEW_RELIGION_STATUS
         || this == HELP_PREPARE_CEREMONY
         || this == DELIVER_RITUAL_SUPPLIES
         || this == JOIN_CEREMONY
         || this == SUPPORT_AFTER_ATTACK) {
         return "religion.millenaire.action." + this.name().toLowerCase(Locale.ROOT);
      }
      return "living.millenaire.action." + this.name().toLowerCase(Locale.ROOT);
   }

   public boolean mutatesWorld() {
      return switch (this) {
         case ACCEPT_REQUEST,
            DECLINE_REQUEST,
            DELIVER_RESOURCES,
            COMPLETE_REQUEST,
            OPEN_EXISTING_QUEST_INTERACTION,
            OPEN_EXISTING_TRADE_INTERACTION,
            SUPPORT_VILLAGE_NEED,
            PRIORITIZE_HOUSING_PROJECT,
            SUPPORT_COLONIZATION_MATERIALS,
            AUTHORIZE_COLONIZATION_PROPOSAL,
            HELP_PREPARE_CEREMONY,
            DELIVER_RITUAL_SUPPLIES,
            JOIN_CEREMONY,
            SUPPORT_AFTER_ATTACK -> true;
         default -> false;
      };
   }
}
