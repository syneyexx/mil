//! In-engine Millénaire bridge: owns the sim for the open world and is called
//! from the native server hook points (exact rebuild of syneyexx/mil).

use super::{
    Game,
    native_hooks::NativeMod,
    native_lifecycle::{ServerTickPhase, WorldPhase},
    native_server::{NativeCommand, NativeServerPlayer},
};
use crate::text::Text;
use millenaire::Engine;
use std::{path::PathBuf, sync::Mutex};

static STATE: Mutex<Option<Engine>> = Mutex::new(None);

fn content_root() -> Option<PathBuf> {
    if let Ok(p) = std::env::var("MILLENAIRE_CONTENT") {
        let path = PathBuf::from(p);
        if path.is_dir() {
            return Some(path);
        }
    }
    // Vendored fork lives under syneyexx/mil/horizon — NeoForge content stays in mil.
    const CANDIDATES: &[&str] = &[
        "src/main/resources/millenaire",
        "../src/main/resources/millenaire",
        "../../src/main/resources/millenaire",
        "millenaire-content",
    ];
    for c in CANDIDATES {
        let p = PathBuf::from(c);
        if p.join("cultures").is_dir() {
            return Some(p);
        }
    }
    None
}

fn with_engine<R>(game: &Game<'_>, f: impl FnOnce(&mut Engine) -> R) -> Option<R> {
    let world = game.native_world_dir()?;
    let dir = world.join(millenaire::WORLD_DIR);
    let mut guard = STATE.lock().unwrap_or_else(|e| e.into_inner());
    let needs_open = match guard.as_ref() {
        None => true,
        Some(e) => e.dir != dir,
    };
    if needs_open {
        if let Some(old) = guard.as_mut() {
            let _ = old.save();
        }
        let content = content_root();
        *guard = Some(Engine::open(dir, content.as_deref()));
    }
    Some(f(guard.as_mut().expect("opened")))
}

pub(super) fn on_world_lifecycle(phase: WorldPhase) {
    if matches!(
        phase,
        WorldPhase::WorldCloseFinal | WorldPhase::WorldCloseFailed
    ) {
        let mut guard = STATE.lock().unwrap_or_else(|e| e.into_inner());
        if let Some(mut eng) = guard.take() {
            let _ = eng.save();
        }
    }
}

pub(super) fn on_server_tick(game: &mut Game<'_>, phase: ServerTickPhase) {
    let ServerTickPhase::ServerTickEnd { world_running } = phase else {
        return;
    };
    if !world_running {
        return;
    }
    let game_time = game.native_world_dir().map(|_| 0_i64).unwrap_or(0);
    // Prefer increasing tick counter from engine itself when game time is unavailable.
    let _ = with_engine(game, |eng| {
        let t = eng.game_time.saturating_add(1).max(eng.ticks as i64 + 1);
        eng.server_tick_end(t);
    });
    let _ = game_time;
}

pub(super) fn register_commands(commands: &mut Vec<NativeCommand>) {
    for usage in [
        "millenaire",
        "millenaire status",
        "millenaire village list",
        "millenaire spawn <culture:word> <x:integer> <y:integer> <z:integer> [name:text]",
    ] {
        commands.push(NativeCommand::new(usage));
    }
    commands.push(NativeCommand::op("millenaire save", 2));
}

pub(super) fn perform_command(
    game: &mut Game<'_>,
    player: &NativeServerPlayer,
    command: &str,
    ran: &mut bool,
) {
    let mut parts = command.split_whitespace();
    let Some(head) = parts.next() else {
        return;
    };
    if head != "millenaire" {
        return;
    }
    *ran = true;
    let sub = parts.next().unwrap_or("status");
    match sub {
        "status" | "horizon" => {
            let lines = with_engine(game, |eng| eng.status_lines()).unwrap_or_else(|| {
                vec![
                    format!(
                        "{} {} — world not open",
                        millenaire::NAME,
                        millenaire::VERSION
                    ),
                    "Open a world (SP/LAN host) so Millénaire can bind native_world_dir."
                        .into(),
                ]
            });
            for line in lines {
                game.native_feedback(Text::literal(line));
            }
        }
        "village" => {
            let what = parts.next().unwrap_or("list");
            if what == "list" {
                let msg = with_engine(game, |eng| {
                    if eng.villages.is_empty() {
                        "no villages".into()
                    } else {
                        eng.villages
                            .list()
                            .iter()
                            .map(|v| {
                                let c = v
                                    .record
                                    .center
                                    .as_ref()
                                    .map(|p| format!("{},{},{}", p.x, p.y, p.z))
                                    .unwrap_or_else(|| "?".into());
                                format!(
                                    "{} [{}] {} @ {}",
                                    v.record.name, v.record.culture, v.record.village_type, c
                                )
                            })
                            .collect::<Vec<_>>()
                            .join(" | ")
                    }
                })
                .unwrap_or_else(|| "world not open".into());
                game.native_feedback(Text::literal(msg));
            } else {
                game.native_feedback(Text::literal("usage: /millenaire village list"));
            }
        }
        "spawn" => {
            let culture = parts.next().unwrap_or("norman");
            let x: i32 = parts.next().and_then(|s| s.parse().ok()).unwrap_or(0);
            let y: i32 = parts.next().and_then(|s| s.parse().ok()).unwrap_or(64);
            let z: i32 = parts.next().and_then(|s| s.parse().ok()).unwrap_or(0);
            let name = parts.collect::<Vec<_>>().join(" ");
            let name = if name.is_empty() {
                format!("{culture} village")
            } else {
                name
            };
            let msg = with_engine(game, |eng| {
                eng.admin_spawn_village(culture, "agricultural", x, y, z, &name)
            })
            .unwrap_or_else(|| "world not open".into());
            let _ = player;
            game.native_feedback(Text::literal(msg));
        }
        "save" => {
            let msg = with_engine(game, |eng| match eng.save() {
                Ok(()) => "millenaire saved".into(),
                Err(e) => format!("save failed: {e}"),
            })
            .unwrap_or_else(|| "world not open".into());
            game.native_feedback(Text::literal(msg));
        }
        _ => {
            game.native_feedback(Text::literal(
                "usage: /millenaire [status|village list|spawn|save]",
            ));
        }
    }
}

pub(super) fn push_mod_list(mods: &mut Vec<NativeMod>) {
    mods.push(NativeMod {
        id: millenaire::ID.into(),
        title: format!("{} {}", millenaire::NAME, millenaire::VERSION),
        description: millenaire::DESCRIPTION.into(),
        keys: Vec::new(),
    });
}
