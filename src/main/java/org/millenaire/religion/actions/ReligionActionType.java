package org.millenaire.religion.actions;

import java.util.Locale;

public enum ReligionActionType {
   VIEW_RELIGION_STATUS,
   HELP_PREPARE_CEREMONY,
   DELIVER_RITUAL_SUPPLIES,
   JOIN_CEREMONY,
   ESCORT_PILGRIM,
   SUPPORT_AFTER_ATTACK,
   SET_RELIGION_POLICY_OPEN,
   SET_RELIGION_POLICY_HOST_PREFERRED,
   SET_RELIGION_POLICY_HOST_ONLY,
   DESIGNATE_SHRINE;

   public String i18nKey() {
      return "religion.millenaire.action." + this.name().toLowerCase(Locale.ROOT);
   }

   public boolean mutatesWorld() {
      return switch (this) {
         case HELP_PREPARE_CEREMONY,
            DELIVER_RITUAL_SUPPLIES,
            JOIN_CEREMONY,
            ESCORT_PILGRIM,
            SUPPORT_AFTER_ATTACK,
            SET_RELIGION_POLICY_OPEN,
            SET_RELIGION_POLICY_HOST_PREFERRED,
            SET_RELIGION_POLICY_HOST_ONLY,
            DESIGNATE_SHRINE -> true;
         default -> false;
      };
   }
}
