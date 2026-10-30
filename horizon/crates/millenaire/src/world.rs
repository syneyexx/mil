// World spawn / roads / settlements — org.millenaire.world.*
use crate::persist::BlockPos;

#[derive(Clone, Debug, Default)]
pub struct SpawnQueue {
    pending: Vec<PendingSpawn>,
}

#[derive(Clone, Debug)]
pub struct PendingSpawn {
    pub culture: String,
    pub village_type: String,
    pub pos: BlockPos,
}

impl SpawnQueue {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn enqueue(&mut self, culture: &str, village_type: &str, pos: BlockPos) {
        self.pending.push(PendingSpawn {
            culture: culture.into(),
            village_type: village_type.into(),
            pos,
        });
    }

    pub fn try_spawn_next(&mut self) -> Option<PendingSpawn> {
        if self.pending.is_empty() {
            None
        } else {
            Some(self.pending.remove(0))
        }
    }

    pub fn len(&self) -> usize {
        self.pending.len()
    }
}

#[derive(Clone, Debug, Default)]
pub struct RoadNetworkService {
    pub ticks: u64,
}

impl RoadNetworkService {
    pub fn tick(&mut self) {
        self.ticks = self.ticks.wrapping_add(1);
    }
}

#[derive(Clone, Debug, Default)]
pub struct SettlementTakeover {
    pub ticks: u64,
}

impl SettlementTakeover {
    pub fn tick(&mut self) {
        self.ticks = self.ticks.wrapping_add(1);
    }
}
