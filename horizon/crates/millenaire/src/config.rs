// Server-facing Millénaire config defaults mirrored from MillenaireServerConfig.
use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(default)]
pub struct MillenaireConfig {
    pub plan_gate_commit_units_per_tick: i32,
    pub plan_gate_candidate_units_per_tick: i32,
    pub worldgen_engine_enabled: bool,
    pub living_villages_enabled: bool,
    pub village_requests_enabled: bool,
    pub religion_enabled: bool,
    pub religion_supernatural_enabled: bool,
    pub orders_enabled: bool,
    pub civilization_evolution_enabled: bool,
    pub civilization_auto_trade_enabled: bool,
    pub civilization_offscreen_enabled: bool,
    pub civilization_autonomous_founding_enabled: bool,
    pub regional_activity_enabled: bool,
    pub labyrinth_enabled: bool,
    pub natural_spawn_suppressed: bool,
    pub npc_chat_enabled: bool,
}

impl Default for MillenaireConfig {
    fn default() -> Self {
        Self {
            plan_gate_commit_units_per_tick: 8,
            plan_gate_candidate_units_per_tick: 32,
            worldgen_engine_enabled: true,
            living_villages_enabled: true,
            village_requests_enabled: true,
            religion_enabled: true,
            religion_supernatural_enabled: false,
            orders_enabled: true,
            civilization_evolution_enabled: true,
            civilization_auto_trade_enabled: true,
            civilization_offscreen_enabled: true,
            civilization_autonomous_founding_enabled: false,
            regional_activity_enabled: true,
            labyrinth_enabled: true,
            natural_spawn_suppressed: false,
            npc_chat_enabled: false,
        }
    }
}
