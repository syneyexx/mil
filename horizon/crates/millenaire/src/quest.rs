// Behavioral port surface for org.millenaire.quest.*
// Exact parity expands behind these tick entrypoints (see HORIZON_PARITY_LEDGER).

#[derive(Clone, Debug, Default)]
pub struct Service {
    pub ticks: u64,
}

impl Service {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn tick(&mut self, _game_time: i64) {
        self.ticks = self.ticks.wrapping_add(1);
    }
}
