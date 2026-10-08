package org.millenaire.faction.politics;

/**
 * Centralized policy consequence evaluation. Does not mutate systems directly.
 */
public final class PolicyEffects {
   private PolicyEffects() {
   }

   public static int groupApprovalDelta(PoliticalGroup group, PolicyType type, PolicyStance stance) {
      if (group == null || type == null || stance == null) {
         return 0;
      }

      InterestType interest = switch (type) {
         case MILITARY_SPENDING, CONSCRIPTION -> InterestType.MILITARY_SPENDING;
         case TRADE_POLICY -> InterestType.TRADE;
         case TAX_POLICY -> InterestType.TAXATION;
         case CENTRALIZATION -> InterestType.CENTRALIZATION;
         case LOCAL_AUTONOMY -> InterestType.LOCAL_AUTONOMY;
         case DIPLOMATIC_POSTURE -> InterestType.PEACE;
         case CULTURAL_POLICY -> InterestType.CULTURAL_PRESERVATION;
         case OCCUPATION_POLICY -> InterestType.EXPANSION;
      };

      int care = group.getInterestStrength(interest);
      if (care <= 0) {
         return 0;
      }

      boolean aligned = switch (type) {
         case MILITARY_SPENDING, CONSCRIPTION ->
            (stance == PolicyStance.HIGH || stance == PolicyStance.AGGRESSIVE) == (care >= 50);
         case TAX_POLICY ->
            (stance == PolicyStance.LOW) == (care < 40) || (stance == PolicyStance.HIGH) == (care >= 60);
         case TRADE_POLICY -> stance == PolicyStance.OPEN || stance == PolicyStance.NORMAL;
         case LOCAL_AUTONOMY -> stance == PolicyStance.HIGH || stance == PolicyStance.NORMAL;
         case CENTRALIZATION -> stance == PolicyStance.HIGH || stance == PolicyStance.NORMAL;
         case CULTURAL_POLICY -> stance == PolicyStance.TRADITIONAL || stance == PolicyStance.REFORMIST;
         case DIPLOMATIC_POSTURE -> stance == PolicyStance.OPEN || stance == PolicyStance.PASSIVE;
         case OCCUPATION_POLICY -> stance == PolicyStance.NORMAL || stance == PolicyStance.LOW;
      };

      if (aligned) {
         return care >= 60 ? PoliticsConfig.POPULARITY_POLICY_MATCH + 1 : PoliticsConfig.POPULARITY_POLICY_MATCH;
      }

      return care >= 60 ? PoliticsConfig.POPULARITY_POLICY_MISMATCH - 1 : PoliticsConfig.POPULARITY_POLICY_MISMATCH;
   }

   public static int stabilityDelta(PolicyType type, PolicyStance stance) {
      if (type == PolicyType.TAX_POLICY && stance == PolicyStance.HIGH) {
         return -2;
      }

      if (type == PolicyType.MILITARY_SPENDING && stance == PolicyStance.AGGRESSIVE) {
         return -1;
      }

      if (type == PolicyType.LOCAL_AUTONOMY && stance == PolicyStance.HIGH) {
         return 1;
      }

      if (type == PolicyType.CULTURAL_POLICY && stance == PolicyStance.REFORMIST) {
         return -1;
      }

      return 0;
   }

   public static int centralizationTarget(Government government) {
      if (government == null) {
         return PoliticsConfig.CENTRALIZATION_BASE;
      }

      GovernmentPolicy policy = government.getPolicy(PolicyType.CENTRALIZATION);
      if (policy == null) {
         return government.getCentralization();
      }

      return switch (policy.getStance()) {
         case LOW, PASSIVE -> 30;
         case NORMAL, OPEN, TRADITIONAL, REFORMIST, RESTRICTIVE -> 50;
         case HIGH -> 75;
         case AGGRESSIVE -> 90;
      };
   }

   public static int localAutonomyFromCentralization(int centralization) {
      return PoliticsConfig.clamp(100 - centralization);
   }
}
