# Horizon fork — Millénaire in-engine

This tree is a **vendored fork base** of [`horizon-rust/horizon`](https://github.com/horizon-rust/horizon)
with **Millénaire** (`syneyexx/mil`) rebuilt **inside** the engine as `crates/millenaire`,
wired from `horizon-client` native server hooks.

GitHub API could not create `syneyexx/horizon` from this agent (403). Until that
repo exists, this directory in `syneyexx/mil` **is** the fork working tree.

## Integration

| Piece | Location |
|-------|----------|
| Simulation / content / persist | `crates/millenaire` |
| Hook wiring | `crates/horizon-client/src/millenaire_bridge.rs` + calls in `native_hooks.rs` |
| NeoForge SoT | parent repo `../` (`syneyexx/mil` Java + `src/main/resources/millenaire`) |

## Build

Requires Horizon's Rust toolchain (workspace `rust-version` / edition 2024). See upstream README for `horizon-prepare` and game files (Minecraft 26.3).

```sh
cargo test -p millenaire
# full client (needs prepared resources):
# cargo build -p horizon-client --profile fast
```
