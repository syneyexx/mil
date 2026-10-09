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
   REPORT_WORLD_EVENT;

   public String i18nKey() {
      return "living.millenaire.action." + this.name().toLowerCase(Locale.ROOT);
   }

   public boolean mutatesWorld() {
      return switch (this) {
         case ACCEPT_REQUEST, DECLINE_REQUEST, DELIVER_RESOURCES, COMPLETE_REQUEST, OPEN_EXISTING_QUEST_INTERACTION, OPEN_EXISTING_TRADE_INTERACTION -> true;
         default -> false;
      };
   }
}
