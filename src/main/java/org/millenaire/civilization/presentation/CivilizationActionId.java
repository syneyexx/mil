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
   VIEW_INTERRUPTED_OPERATION,
   SET_IMMIGRATION_POLICY,
   SET_TRADE_POLICY,
   DECLARE_WAR,
   OFFER_PEACE,
   SET_EMBARGO,
   CLEAR_EMBARGO,
   OPEN_CONTROLLED_PROJECTS,
   ASSIGN_GUARD,
   RELIEVE_GUARD,
   OFFER_CARAVAN_CHARTER,
   EMERGENCY_RELIEF,
   PAY_BLOOD_MONEY,
   RESPOND_CALL_TO_ARMS,
   REFUSE_CALL_TO_ARMS,
   OFFER_PROTECTORATE,
   ACCEPT_ENVOY,
   DECLINE_ENVOY,
   SUBSIDIZE_CORRIDOR,
   BROKER_PEACE,
   HIRE_MERCENARIES,
   PROPAGANDA_MARKET,
   INTERROGATE_PRISONER,
   PROPOSE_JOINT_WORK,
   OFFER_FOSTERAGE;

   public String i18nKey() {
      // AUTHORIZE_COLONIZATION retained for stored proposals / network decode; UI means resume.
      if (this == AUTHORIZE_COLONIZATION) {
         return "civilization.millenaire.action.resume_colonization";
      }
      if (this == OPEN_CONTROLLED_PROJECTS) {
         return "gui.millenaire.city_admin.open_projects";
      }
      return "civilization.millenaire.action." + name().toLowerCase();
   }

   public boolean mutatesWorld() {
      return switch (this) {
         case SUPPORT_NEED,
            PRIORITIZE_HOUSING,
            SUPPORT_COLONIZATION_MATERIALS,
            AUTHORIZE_COLONIZATION,
            SET_IMMIGRATION_POLICY,
            SET_TRADE_POLICY,
            DECLARE_WAR,
            OFFER_PEACE,
            SET_EMBARGO,
            CLEAR_EMBARGO,
            ASSIGN_GUARD,
            RELIEVE_GUARD,
            OFFER_CARAVAN_CHARTER,
            EMERGENCY_RELIEF,
            PAY_BLOOD_MONEY,
            RESPOND_CALL_TO_ARMS,
            REFUSE_CALL_TO_ARMS,
            OFFER_PROTECTORATE,
            ACCEPT_ENVOY,
            DECLINE_ENVOY,
            SUBSIDIZE_CORRIDOR,
            BROKER_PEACE,
            HIRE_MERCENARIES,
            PROPAGANDA_MARKET,
            INTERROGATE_PRISONER,
            PROPOSE_JOINT_WORK,
            OFFER_FOSTERAGE -> true;
         default -> false;
      };
   }

   public boolean requiresVillageControl() {
      return switch (this) {
         case PRIORITIZE_HOUSING,
            AUTHORIZE_COLONIZATION,
            SET_IMMIGRATION_POLICY,
            SET_TRADE_POLICY,
            DECLARE_WAR,
            OFFER_PEACE,
            SET_EMBARGO,
            CLEAR_EMBARGO,
            OPEN_CONTROLLED_PROJECTS,
            ASSIGN_GUARD,
            RELIEVE_GUARD,
            OFFER_CARAVAN_CHARTER,
            EMERGENCY_RELIEF,
            PAY_BLOOD_MONEY,
            RESPOND_CALL_TO_ARMS,
            REFUSE_CALL_TO_ARMS,
            OFFER_PROTECTORATE,
            ACCEPT_ENVOY,
            DECLINE_ENVOY,
            SUBSIDIZE_CORRIDOR,
            BROKER_PEACE,
            HIRE_MERCENARIES,
            PROPAGANDA_MARKET,
            INTERROGATE_PRISONER,
            PROPOSE_JOINT_WORK,
            OFFER_FOSTERAGE -> true;
         default -> false;
      };
   }

   public boolean requiresSovereign() {
      return switch (this) {
         case SET_IMMIGRATION_POLICY, SET_TRADE_POLICY, DECLARE_WAR, OFFER_PEACE, SET_EMBARGO, CLEAR_EMBARGO,
            ASSIGN_GUARD, RELIEVE_GUARD,
            OFFER_CARAVAN_CHARTER, EMERGENCY_RELIEF, PAY_BLOOD_MONEY, RESPOND_CALL_TO_ARMS, REFUSE_CALL_TO_ARMS,
            OFFER_PROTECTORATE, ACCEPT_ENVOY, DECLINE_ENVOY, SUBSIDIZE_CORRIDOR, BROKER_PEACE, HIRE_MERCENARIES,
            PROPAGANDA_MARKET, INTERROGATE_PRISONER, PROPOSE_JOINT_WORK, OFFER_FOSTERAGE -> true;
         default -> false;
      };
   }
}
