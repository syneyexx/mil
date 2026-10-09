package org.millenaire.civilization.presentation;

/**
 * Stable identifiers for server-validated civilization player actions.
 * Client sends these intents; server re-authorizes at commit.
 */
public enum CivilizationActionId {
   SUPPORT_NEED,
   PRIORITIZE_HOUSING,
   VIEW_COLONIZATION,
   SUPPORT_COLONIZATION_MATERIALS,
   AUTHORIZE_COLONIZATION,
   VIEW_INTERRUPTED_OPERATION;

   public String i18nKey() {
      return "civilization.millenaire.action." + name().toLowerCase();
   }

   public boolean mutatesWorld() {
      return switch (this) {
         case SUPPORT_NEED, PRIORITIZE_HOUSING, SUPPORT_COLONIZATION_MATERIALS, AUTHORIZE_COLONIZATION -> true;
         default -> false;
      };
   }

   public boolean requiresVillageControl() {
      return switch (this) {
         case PRIORITIZE_HOUSING, AUTHORIZE_COLONIZATION -> true;
         default -> false;
      };
   }
}
