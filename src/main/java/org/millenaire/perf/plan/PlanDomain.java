package org.millenaire.perf.plan;

/**
 * Logical background-work domains that share {@link BackgroundWorkGate}.
 * Domain logic stays in existing services; this enum only coordinates budgets.
 */
public enum PlanDomain {
   RELIGION,
   WAR,
   ROADS,
   ECONOMY,
   DIPLOMACY,
   STORY,
   FOUNDING
}
