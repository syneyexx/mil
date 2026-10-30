// SavedData equivalents — schema mirrors mil Java SavedData names/versions.
use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::path::{Path, PathBuf};
use uuid::Uuid;

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct BlockPos {
    pub x: i32,
    pub y: i32,
    pub z: i32,
}

impl BlockPos {
    pub fn new(x: i32, y: i32, z: i32) -> Self {
        Self { x, y, z }
    }
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct VillageRecord {
    pub id: String,
    pub culture: String,
    pub village_type: String,
    pub center: Option<BlockPos>,
    pub name: String,
    pub reputation: BTreeMap<String, i32>,
    pub tags: BTreeSet<String>,
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct VillageSavedData {
    pub schema: u32,
    pub villages: Vec<VillageRecord>,
    pub lone_buildings: Vec<LoneBuildingEntry>,
    pub global_tags: BTreeSet<String>,
}

impl VillageSavedData {
    pub const SCHEMA: u32 = 8; // mil VillageSavedData.CURRENT_VERSION
    pub const FILE: &'static str = "villages.json";
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct LoneBuildingEntry {
    pub pos: BlockPos,
    pub type_id: String,
    pub culture: String,
    pub generated_for: Option<String>,
}

macro_rules! simple_saved {
    ($name:ident, $file:expr, $schema:expr) => {
        #[derive(Clone, Debug, Default, Serialize, Deserialize)]
        #[serde(default)]
        pub struct $name {
            pub schema: u32,
            pub payload: serde_json::Value,
        }
        impl $name {
            pub const SCHEMA: u32 = $schema;
            pub const FILE: &'static str = $file;
            pub fn empty() -> Self {
                Self {
                    schema: Self::SCHEMA,
                    payload: serde_json::json!({}),
                }
            }
        }
    };
}

simple_saved!(CultureStandingSavedData, "culture_standing.json", 1);
simple_saved!(DiplomacyEventSavedData, "diplomacy_events.json", 1);
simple_saved!(ReligionSavedData, "religion.json", 1);
simple_saved!(ActivitySavedData, "activity.json", 1);
simple_saved!(LogisticsSavedData, "logistics.json", 1);
simple_saved!(WarCampaignSavedData, "war_campaigns.json", 1);
simple_saved!(PlayerBountySavedData, "player_bounties.json", 1);
simple_saved!(RoadNetworkSavedData, "road_network.json", 2);
simple_saved!(SettlementSavedData, "settlements.json", 1);
simple_saved!(TreatySavedData, "treaties.json", 1);
simple_saved!(FoundingSavedData, "founding.json", 3);
simple_saved!(KnowledgeSavedData, "knowledge.json", 1);
simple_saved!(StorySavedData, "story.json", 1);
simple_saved!(PlayerQuestData, "player_quests.json", 1);

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct WorldStore {
    pub villages: VillageSavedData,
    pub culture_standing: CultureStandingSavedData,
    pub diplomacy_events: DiplomacyEventSavedData,
    pub religion: ReligionSavedData,
    pub activity: ActivitySavedData,
    pub logistics: LogisticsSavedData,
    pub war_campaigns: WarCampaignSavedData,
    pub player_bounties: PlayerBountySavedData,
    pub road_network: RoadNetworkSavedData,
    pub settlements: SettlementSavedData,
    pub treaties: TreatySavedData,
    pub founding: FoundingSavedData,
    pub knowledge: KnowledgeSavedData,
    pub story: StorySavedData,
    pub player_quests: PlayerQuestData,
}

#[derive(Debug, thiserror::Error)]
pub enum PersistError {
    #[error("io: {0}")]
    Io(#[from] std::io::Error),
    #[error("json: {0}")]
    Json(#[from] serde_json::Error),
}

impl WorldStore {
    pub fn load(dir: &Path) -> Result<Self, PersistError> {
        fs::create_dir_all(dir)?;
        Ok(Self {
            villages: read_or_default(dir, VillageSavedData::FILE, || VillageSavedData {
                schema: VillageSavedData::SCHEMA,
                ..Default::default()
            })?,
            culture_standing: read_or_default(dir, CultureStandingSavedData::FILE, CultureStandingSavedData::empty)?,
            diplomacy_events: read_or_default(dir, DiplomacyEventSavedData::FILE, DiplomacyEventSavedData::empty)?,
            religion: read_or_default(dir, ReligionSavedData::FILE, ReligionSavedData::empty)?,
            activity: read_or_default(dir, ActivitySavedData::FILE, ActivitySavedData::empty)?,
            logistics: read_or_default(dir, LogisticsSavedData::FILE, LogisticsSavedData::empty)?,
            war_campaigns: read_or_default(dir, WarCampaignSavedData::FILE, WarCampaignSavedData::empty)?,
            player_bounties: read_or_default(dir, PlayerBountySavedData::FILE, PlayerBountySavedData::empty)?,
            road_network: read_or_default(dir, RoadNetworkSavedData::FILE, RoadNetworkSavedData::empty)?,
            settlements: read_or_default(dir, SettlementSavedData::FILE, SettlementSavedData::empty)?,
            treaties: read_or_default(dir, TreatySavedData::FILE, TreatySavedData::empty)?,
            founding: read_or_default(dir, FoundingSavedData::FILE, FoundingSavedData::empty)?,
            knowledge: read_or_default(dir, KnowledgeSavedData::FILE, KnowledgeSavedData::empty)?,
            story: read_or_default(dir, StorySavedData::FILE, StorySavedData::empty)?,
            player_quests: read_or_default(dir, PlayerQuestData::FILE, PlayerQuestData::empty)?,
        })
    }

    pub fn save(&self, dir: &Path) -> Result<(), PersistError> {
        fs::create_dir_all(dir)?;
        write_json(dir, VillageSavedData::FILE, &self.villages)?;
        write_json(dir, CultureStandingSavedData::FILE, &self.culture_standing)?;
        write_json(dir, DiplomacyEventSavedData::FILE, &self.diplomacy_events)?;
        write_json(dir, ReligionSavedData::FILE, &self.religion)?;
        write_json(dir, ActivitySavedData::FILE, &self.activity)?;
        write_json(dir, LogisticsSavedData::FILE, &self.logistics)?;
        write_json(dir, WarCampaignSavedData::FILE, &self.war_campaigns)?;
        write_json(dir, PlayerBountySavedData::FILE, &self.player_bounties)?;
        write_json(dir, RoadNetworkSavedData::FILE, &self.road_network)?;
        write_json(dir, SettlementSavedData::FILE, &self.settlements)?;
        write_json(dir, TreatySavedData::FILE, &self.treaties)?;
        write_json(dir, FoundingSavedData::FILE, &self.founding)?;
        write_json(dir, KnowledgeSavedData::FILE, &self.knowledge)?;
        write_json(dir, StorySavedData::FILE, &self.story)?;
        write_json(dir, PlayerQuestData::FILE, &self.player_quests)?;
        Ok(())
    }
}

fn read_or_default<T: for<'de> Deserialize<'de>>(
    dir: &Path,
    file: &str,
    default: impl FnOnce() -> T,
) -> Result<T, PersistError> {
    let path = dir.join(file);
    if !path.is_file() {
        return Ok(default());
    }
    let text = fs::read_to_string(path)?;
    Ok(serde_json::from_str(&text)?)
}

fn write_json<T: Serialize>(dir: &Path, file: &str, value: &T) -> Result<(), PersistError> {
    let path = dir.join(file);
    let text = serde_json::to_string_pretty(value)?;
    fs::write(path, text)?;
    Ok(())
}

pub fn new_village_id() -> String {
    Uuid::new_v4().to_string()
}

pub fn world_millenaire_dir(world_dir: impl AsRef<Path>) -> PathBuf {
    world_dir.as_ref().join(crate::id::WORLD_DIR)
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::tempdir;

    #[test]
    fn roundtrip_empty_store() {
        let dir = tempdir().unwrap();
        let store = WorldStore::default();
        store.save(dir.path()).unwrap();
        let loaded = WorldStore::load(dir.path()).unwrap();
        assert_eq!(loaded.villages.schema, 0); // default before explicit schema set
        let mut store = WorldStore::default();
        store.villages.schema = VillageSavedData::SCHEMA;
        store.villages.villages.push(VillageRecord {
            id: "v1".into(),
            culture: "norman".into(),
            village_type: "village_agricultural".into(),
            center: Some(BlockPos::new(0, 64, 0)),
            name: "Test".into(),
            ..Default::default()
        });
        store.save(dir.path()).unwrap();
        let loaded = WorldStore::load(dir.path()).unwrap();
        assert_eq!(loaded.villages.villages.len(), 1);
        assert_eq!(loaded.villages.villages[0].culture, "norman");
    }
}
