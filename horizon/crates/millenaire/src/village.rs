// VillageManager / Village — behavioral port of org.millenaire.village.*
use crate::persist::{BlockPos, VillageRecord, VillageSavedData, new_village_id};

#[derive(Clone, Debug)]
pub struct Village {
    pub record: VillageRecord,
}

impl Village {
    pub fn new(culture: impl Into<String>, village_type: impl Into<String>, center: BlockPos, name: impl Into<String>) -> Self {
        Self {
            record: VillageRecord {
                id: new_village_id(),
                culture: culture.into(),
                village_type: village_type.into(),
                center: Some(center),
                name: name.into(),
                reputation: Default::default(),
                tags: Default::default(),
            },
        }
    }
}

#[derive(Clone, Debug, Default)]
pub struct VillageManager {
    villages: Vec<Village>,
    pending_road_registration: Vec<String>,
    dirty: bool,
}

impl VillageManager {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn from_saved(data: &VillageSavedData) -> Self {
        let villages = data
            .villages
            .iter()
            .cloned()
            .map(|record| Village { record })
            .collect();
        Self {
            villages,
            pending_road_registration: Vec::new(),
            dirty: false,
        }
    }

    pub fn to_saved(&self) -> VillageSavedData {
        VillageSavedData {
            schema: VillageSavedData::SCHEMA,
            villages: self.villages.iter().map(|v| v.record.clone()).collect(),
            lone_buildings: Vec::new(),
            global_tags: Default::default(),
        }
    }

    pub fn len(&self) -> usize {
        self.villages.len()
    }

    pub fn is_empty(&self) -> bool {
        self.villages.is_empty()
    }

    pub fn list(&self) -> &[Village] {
        &self.villages
    }

    pub fn spawn_village(
        &mut self,
        culture: &str,
        village_type: &str,
        center: BlockPos,
        name: &str,
    ) -> &Village {
        let village = Village::new(culture, village_type, center, name);
        self.pending_road_registration
            .push(village.record.id.clone());
        self.villages.push(village);
        self.dirty = true;
        self.villages.last().expect("just pushed")
    }

    pub fn poll_pending_road_registration(&mut self) -> Option<String> {
        if self.pending_road_registration.is_empty() {
            None
        } else {
            Some(self.pending_road_registration.remove(0))
        }
    }

    /// Per-tick village simulation (mil VillageManager.tick) — expands toward full parity.
    pub fn tick(&mut self, _game_time: i64) {
        // Goal scheduling / construction / needs land in later parity slices.
        let _ = self.dirty;
    }

    pub fn take_dirty(&mut self) -> bool {
        let d = self.dirty;
        self.dirty = false;
        d
    }

    pub fn find_by_id(&self, id: &str) -> Option<&Village> {
        self.villages.iter().find(|v| v.record.id == id)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn spawn_and_list() {
        let mut m = VillageManager::new();
        m.spawn_village("norman", "agricultural", BlockPos::new(10, 64, -4), "Normandie");
        assert_eq!(m.len(), 1);
        assert_eq!(m.poll_pending_road_registration().unwrap().len(), 36);
        assert!(m.poll_pending_road_registration().is_none());
    }
}
