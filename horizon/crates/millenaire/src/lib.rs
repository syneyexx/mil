//! Millénaire — exact in-engine rebuild of `syneyexx/mil` for Horizon.
//! Wired from `horizon-client` native server hooks (not a side-car Java jar).

pub mod activity;
pub mod building;
pub mod combat;
pub mod commerce;
pub mod commit_budget;
pub mod config;
pub mod content;
pub mod diplomacy;
pub mod economy;
pub mod engine;
pub mod entity;
pub mod faith;
pub mod founding;
pub mod goal;
pub mod id;
pub mod labyrinth;
pub mod logistics;
pub mod persist;
pub mod quest;
pub mod story;
pub mod village;
pub mod world;

pub use engine::Engine;
pub use id::{DESCRIPTION, ID, NAME, NEOFORGE_SOURCE, TARGET_ENGINE, VERSION, WORLD_DIR};
