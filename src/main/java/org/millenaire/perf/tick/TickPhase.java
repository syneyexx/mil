package org.millenaire.perf.tick;

/**
 * Ordered server-tick phases under {@link ServerTickGovernor}.
 */
public enum TickPhase {
   APPLY,
   VILLAGE_NEAR,
   VILLAGE_MID,
   AI_SUPPORT,
   CIV_FACTION,
   WORLDGEN,
   HIGHWAY,
   BOSS_QUEST_MAP
}
