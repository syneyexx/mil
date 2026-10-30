// Culture / content loaders — reads the same millenaire/ JSON tree as NeoForge mil.
use serde::Deserialize;
use serde_json::Value;
use std::collections::BTreeMap;
use std::fs;
use std::path::{Path, PathBuf};

pub const KNOWN_CULTURES: &[&str] = &[
    "byzantines",
    "illagers",
    "indian",
    "inuits",
    "japanese",
    "mayan",
    "nederland",
    "norman",
    "orde",
    "seljuk",
];

#[derive(Clone, Debug, Default)]
pub struct ContentIndex {
    pub root: PathBuf,
    pub cultures: BTreeMap<String, CulturePack>,
    pub load_errors: Vec<String>,
}

#[derive(Clone, Debug, Deserialize)]
pub struct CulturePack {
    pub id: String,
    pub display_name: Option<String>,
    pub native_language: Option<String>,
    #[serde(default)]
    pub village_type_ids: Vec<String>,
    #[serde(default)]
    pub villager_type_ids: Vec<String>,
    #[serde(default)]
    pub building_ids: Vec<String>,
    #[serde(default)]
    pub shop_ids: Vec<String>,
    #[serde(default)]
    pub quest_ids: Vec<String>,
    #[serde(default)]
    pub raw_culture_json: Value,
}

impl Default for CulturePack {
    fn default() -> Self {
        Self {
            id: String::new(),
            display_name: None,
            native_language: None,
            village_type_ids: vec![],
            villager_type_ids: vec![],
            building_ids: vec![],
            shop_ids: vec![],
            quest_ids: vec![],
            raw_culture_json: Value::Null,
        }
    }
}

#[derive(Debug, thiserror::Error)]
pub enum ContentError {
    #[error("content root missing: {0}")]
    MissingRoot(String),
    #[error("io: {0}")]
    Io(#[from] std::io::Error),
    #[error("json: {0}")]
    Json(#[from] serde_json::Error),
}

impl ContentIndex {
    pub fn load(root: impl AsRef<Path>) -> Result<Self, ContentError> {
        let root = root.as_ref().to_path_buf();
        let cultures_dir = root.join("cultures");
        if !cultures_dir.is_dir() {
            return Err(ContentError::MissingRoot(cultures_dir.display().to_string()));
        }
        let mut index = ContentIndex {
            root: root.clone(),
            cultures: BTreeMap::new(),
            load_errors: Vec::new(),
        };
        for entry in fs::read_dir(&cultures_dir)? {
            let entry = entry?;
            if !entry.file_type()?.is_dir() {
                continue;
            }
            let id = entry.file_name().to_string_lossy().to_string();
            match load_culture_pack(&entry.path(), &id) {
                Ok(pack) => {
                    index.cultures.insert(id, pack);
                }
                Err(e) => index.load_errors.push(format!("{id}: {e}")),
            }
        }
        Ok(index)
    }

    pub fn culture_ids(&self) -> Vec<&str> {
        self.cultures.keys().map(|s| s.as_str()).collect()
    }

    pub fn summary_line(&self) -> String {
        format!(
            "cultures={} buildings~{} errors={}",
            self.cultures.len(),
            self.cultures.values().map(|c| c.building_ids.len()).sum::<usize>(),
            self.load_errors.len()
        )
    }
}

fn load_culture_pack(dir: &Path, id: &str) -> Result<CulturePack, ContentError> {
    let culture_json = dir.join("culture.json");
    let raw: Value = if culture_json.is_file() {
        serde_json::from_str(&fs::read_to_string(&culture_json)?)?
    } else {
        Value::Object(Default::default())
    };
    let display_name = raw
        .get("display_name")
        .and_then(|v| v.as_str())
        .map(|s| s.to_string());
    let native_language = raw
        .get("native_language")
        .and_then(|v| v.as_str())
        .map(|s| s.to_string());
    Ok(CulturePack {
        id: id.to_string(),
        display_name,
        native_language,
        village_type_ids: list_json_stems(&dir.join("villages")),
        villager_type_ids: list_json_stems(&dir.join("villagers")),
        building_ids: list_json_stems(&dir.join("buildings")),
        shop_ids: list_json_stems(&dir.join("shops")),
        quest_ids: list_json_stems(&dir.join("quests")),
        raw_culture_json: raw,
    })
}

fn list_json_stems(dir: &Path) -> Vec<String> {
    let mut out = Vec::new();
    let Ok(rd) = fs::read_dir(dir) else {
        return out;
    };
    for entry in rd.flatten() {
        let path = entry.path();
        if path.extension().and_then(|e| e.to_str()) == Some("json") {
            if let Some(stem) = path.file_stem().and_then(|s| s.to_str()) {
                out.push(stem.to_string());
            }
        } else if path.is_dir() {
            // nested building folders
            out.extend(list_json_stems(&path));
        }
    }
    out.sort();
    out.dedup();
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn known_cultures_include_norman() {
        assert!(KNOWN_CULTURES.contains(&"norman"));
        assert_eq!(KNOWN_CULTURES.len(), 10);
    }
}
