package org.millenaire.perf.plan;

/**
 * Submit priority for background planning. PLAYER work is never starved by SIM.
 */
public enum PlanPriority {
   PLAYER,
   MUST_FINISH,
   SIM
}
