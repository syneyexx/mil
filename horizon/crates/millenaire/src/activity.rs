// Regional activity — org.millenaire.activity.*
// Lifecycle: PROPOSED → RESERVED → ASSEMBLING → TRAVELLING → EXECUTING → …
use serde::{Deserialize, Serialize};

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum ActivityPhase {
    Proposed,
    Reserved,
    Assembling,
    Travelling,
    Executing,
    Returning,
    Retreating,
    Settling,
    Completed,
    WaitingChunks,
    RecoveryRequired,
    Cancelled,
}

#[derive(Clone, Debug, Default)]
pub struct Service {
    pub ticks: u64,
    pub active_ops: usize,
}

impl Service {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn on_server_tick(&mut self, _enabled: bool) {
        self.ticks = self.ticks.wrapping_add(1);
        // Full admission/planner/lifecycle port tracks mil RegionalActivityService.
    }
}
