// Exact post-tick orchestration mirrored from Millenaire.java ServerTickEvent.Post.
use crate::activity;
use crate::commit_budget::CommitBudget;
use crate::combat;
use crate::config::MillenaireConfig;
use crate::content::ContentIndex;
use crate::diplomacy;
use crate::economy;
use crate::faith;
use crate::founding;
use crate::goal::GoalRegistry;
use crate::labyrinth;
use crate::logistics;
use crate::persist::{BlockPos, WorldStore};
use crate::quest;
use crate::story;
use crate::village::VillageManager;
use crate::world::{RoadNetworkService, SettlementTakeover, SpawnQueue};
use std::path::{Path, PathBuf};

#[derive(Debug)]
pub struct Engine {
    pub dir: PathBuf,
    pub config: MillenaireConfig,
    pub content: Option<ContentIndex>,
    pub store: WorldStore,
    pub villages: VillageManager,
    pub goals: GoalRegistry,
    pub budget: CommitBudget,
    pub spawn_queue: SpawnQueue,
    pub labyrinth_spawn_queue: SpawnQueue,
    pub roads: RoadNetworkService,
    pub settlements: SettlementTakeover,
    pub faith: crate::faith::Service,
    pub war: crate::combat::Service,
    pub economy: crate::economy::Service,
    pub logistics: crate::logistics::Service,
    pub story: crate::story::Service,
    pub treaties: crate::diplomacy::Service,
    pub founding: crate::founding::Service,
    pub activity: crate::activity::Service,
    pub labyrinth: crate::labyrinth::Service,
    pub quests: crate::quest::Service,
    pub game_time: i64,
    pub ticks: u64,
    pub dirty: bool,
}

impl Engine {
    pub fn open(dir: PathBuf, content_root: Option<&Path>) -> Self {
        let config_path = dir.join("config.json");
        let config: MillenaireConfig = read_json_or_default(&config_path);
        if !config_path.exists() {
            let _ = write_json(&config_path, &config);
        }
        let store = WorldStore::load(&dir).unwrap_or_default();
        let villages = VillageManager::from_saved(&store.villages);
        let content = content_root.and_then(|p| ContentIndex::load(p).ok());
        Self {
            dir,
            config,
            content,
            store,
            villages,
            goals: GoalRegistry::with_builtins(),
            budget: CommitBudget::new(),
            spawn_queue: SpawnQueue::new(),
            labyrinth_spawn_queue: SpawnQueue::new(),
            roads: RoadNetworkService::default(),
            settlements: SettlementTakeover::default(),
            faith: crate::faith::Service::new(),
            war: crate::combat::Service::new(),
            economy: crate::economy::Service::new(),
            logistics: crate::logistics::Service::new(),
            story: crate::story::Service::new(),
            treaties: crate::diplomacy::Service::new(),
            founding: crate::founding::Service::new(),
            activity: crate::activity::Service::new(),
            labyrinth: crate::labyrinth::Service::new(),
            quests: crate::quest::Service::new(),
            game_time: 0,
            ticks: 0,
            dirty: false,
        }
    }

    pub fn save(&mut self) -> Result<(), crate::persist::PersistError> {
        self.store.villages = self.villages.to_saved();
        self.store.save(&self.dir)?;
        write_json(&self.dir.join("config.json"), &self.config).ok();
        Ok(())
    }

    /// mil ServerTickEvent.Post order (overworld slice + global activity + labyrinth + quests).
    pub fn server_tick_end(&mut self, game_time: i64) {
        self.game_time = game_time;
        self.ticks = self.ticks.wrapping_add(1);

        self.budget.reset_tick(
            self.config.plan_gate_commit_units_per_tick,
            self.config.plan_gate_candidate_units_per_tick,
        );

        // Pending road registrations (budget 8 like mil)
        let mut road_reg_budget = 8;
        while road_reg_budget > 0 {
            if self.villages.poll_pending_road_registration().is_none() {
                break;
            }
            road_reg_budget -= 1;
            self.dirty = true;
        }

        self.villages.tick(game_time);

        if !self.config.natural_spawn_suppressed {
            if let Some(pending) = self.spawn_queue.try_spawn_next() {
                self.villages.spawn_village(
                    &pending.culture,
                    &pending.village_type,
                    pending.pos,
                    &format!("{} settlement", pending.culture),
                );
                self.dirty = true;
            }
        }

        self.roads.tick();
        self.settlements.tick();
        self.faith.tick(self.config.religion_enabled, game_time);
        self.war.tick(game_time);
        self.economy.tick(game_time);
        self.logistics.tick(game_time);
        self.story.tick(game_time);
        self.treaties.tick(game_time);
        self.founding.tick(game_time);

        self.activity
            .on_server_tick(self.config.regional_activity_enabled);

        if self.config.labyrinth_enabled {
            self.labyrinth.tick(game_time);
            if !self.config.natural_spawn_suppressed {
                let _ = self.labyrinth_spawn_queue.try_spawn_next();
            }
        }

        let quest_cadence = game_time % 1000 == 0;
        let special_cadence = game_time % 10 == 0;
        if quest_cadence || special_cadence {
            self.quests.tick(game_time);
        }

        if self.villages.take_dirty() || self.dirty {
            let _ = self.save();
            self.dirty = false;
        }
    }

    pub fn status_lines(&self) -> Vec<String> {
        let content = self
            .content
            .as_ref()
            .map(|c| c.summary_line())
            .unwrap_or_else(|| "content=not-loaded".into());
        vec![
            format!(
                "{} {} — exact Horizon rebuild of {}",
                crate::id::NAME,
                crate::id::VERSION,
                crate::id::NEOFORGE_SOURCE
            ),
            format!(
                "ticks={} villages={} goals={} {}",
                self.ticks,
                self.villages.len(),
                self.goals.len(),
                content
            ),
            format!(
                "budget commits={} candidates={}",
                self.budget.remaining_commits(),
                self.budget.remaining_candidates()
            ),
        ]
    }

    pub fn admin_spawn_village(
        &mut self,
        culture: &str,
        village_type: &str,
        x: i32,
        y: i32,
        z: i32,
        name: &str,
    ) -> String {
        let v = self
            .villages
            .spawn_village(culture, village_type, BlockPos::new(x, y, z), name);
        self.dirty = true;
        let id = v.record.id.clone();
        let _ = self.save();
        format!("spawned village {name} ({culture}/{village_type}) id={id} at {x},{y},{z}")
    }
}

fn read_json_or_default<T: for<'de> serde::Deserialize<'de> + Default>(path: &Path) -> T {
    match std::fs::read_to_string(path) {
        Ok(t) => serde_json::from_str(&t).unwrap_or_default(),
        Err(_) => T::default(),
    }
}

fn write_json<T: serde::Serialize>(path: &Path, value: &T) -> std::io::Result<()> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    std::fs::write(path, serde_json::to_string_pretty(value).unwrap_or_else(|_| "{}".into()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[test]
    fn tick_order_runs_and_persists_spawn() {
        let dir = tempdir().unwrap();
        let content = Path::new("/workspace/src/main/resources/millenaire");
        let mut engine = Engine::open(dir.path().to_path_buf(), Some(content));
        assert!(engine.content.as_ref().unwrap().cultures.contains_key("norman"));
        let msg = engine.admin_spawn_village("norman", "agricultural", 0, 64, 0, "Testville");
        assert!(msg.contains("Testville"));
        engine.server_tick_end(1000);
        assert_eq!(engine.villages.len(), 1);
        let engine2 = Engine::open(dir.path().to_path_buf(), None);
        assert_eq!(engine2.villages.len(), 1);
    }
}
