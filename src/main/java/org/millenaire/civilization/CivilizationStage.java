package org.millenaire.civilization;

/**
 * Causal ordering for civilization updates. Stages are a dependency order, not a per-tick full scan.
 */
public enum CivilizationStage {
   EVENT_INGEST,
   MEMBERSHIP_RECONCILE,
   ECONOMY,
   POLITICS,
   DIPLOMACY,
   MILITARY,
   WAR,
   DEMOGRAPHICS,
   DEVELOPMENT,
   HISTORY_PUBLISH,
   OFFSCREEN_RECONCILE
}
