// Religion / faith / orders — org.millenaire.faith.*
#[derive(Clone, Debug, Default)]
pub struct Service {
    pub ticks: u64,
    pub enabled: bool,
}

impl Service {
    pub fn new() -> Self {
        Self {
            enabled: true,
            ..Default::default()
        }
    }

    pub fn tick(&mut self, enabled: bool, _game_time: i64) {
        self.enabled = enabled;
        if enabled {
            self.ticks = self.ticks.wrapping_add(1);
        }
    }
}
