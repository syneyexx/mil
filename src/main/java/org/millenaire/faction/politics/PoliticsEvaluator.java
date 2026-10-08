package org.millenaire.faction.politics;

import javax.annotation.Nullable;

/**
 * Read-only political evaluation hooks for future AI (Phase 9). Deterministic, no mutation.
 */
public final class PoliticsEvaluator {
   private PoliticsEvaluator() {
   }

   public static boolean supportsPolicy(PoliticalGroup group, PolicyType type, PolicyStance stance) {
      return PolicyEffects.groupApprovalDelta(group, type, stance) >= 0;
   }

   public static boolean opposesLeader(PoliticalGroup group, PoliticalLeader leader, Government government) {
      if (group == null || leader == null || government == null) {
         return false;
      }

      return group.getApproval() < 40 && leader.getPopularity() < 45 && government.getLegitimacy() < 50;
   }

   public static boolean supportsReform(PoliticalGroup group) {
      return group != null && group.getInterestStrength(InterestType.REFORM) >= 50;
   }

   public static boolean isGovernmentStable(FactionPoliticalState state, Government government) {
      if (state == null || government == null) {
         return false;
      }

      return government.getStability() >= PoliticsConfig.RULING_SUPPORT_SAFE
         && state.getPoliticalPressure() < PoliticsConfig.CRISIS_PRESSURE_THRESHOLD
         && !state.getConflictState().isUnstable();
   }

   public static boolean isGovernmentVulnerable(FactionPoliticalState state, Government government) {
      if (state == null || government == null) {
         return true;
      }

      return government.getLegitimacy() <= PoliticsConfig.COUP_LEGITIMACY_MAX
         || government.getStability() <= PoliticsConfig.COUP_STABILITY_MAX
         || state.getPoliticalPressure() >= PoliticsConfig.COUP_PRESSURE_THRESHOLD;
   }

   public static int foreignPolicyPressure(PoliticalGroup group, ForeignPolicyPreference preference) {
      if (group == null || preference == null || !group.isActive()) {
         return 0;
      }

      if (group.getForeignPreference() != preference) {
         return 0;
      }

      return Math.min(100, (group.getPoliticalPower() + group.getPopulationSupport()) / 2);
   }

   public static int rulingSupport(@Nullable PoliticalCoalition ruling) {
      return ruling == null || !ruling.isActive() ? 0 : ruling.getSupport();
   }
}
