# Horizon Millénaire — Local Validation

**Agent does not run full Horizon client builds here** (needs MC 26.3 prepare + Rust ≥ workspace `rust-version`). Owner validates.

## Layout

```
syneyexx/mil/
  horizon/                         # vendored Horizon fork (engine)
    crates/millenaire/             # Millénaire in-engine
    crates/horizon-client/.../millenaire_bridge.rs
    MILLENAIRE_FORK.md
  src/main/resources/millenaire/   # NeoForge content SoT (also loaded by Rust)
  docs/HORIZON_*.md
```

GitHub could not create `syneyexx/horizon` (API 403). This `horizon/` directory **is** the fork working tree until published.

## Owner checks

```bash
# From mil root — content load + engine unit tests (needs compatible rustc)
cd horizon
cargo test -p millenaire

# Optional: set content explicitly
export MILLENAIRE_CONTENT=/absolute/path/to/mil/src/main/resources/millenaire

# Full client (upstream prepare first — see horizon README)
cargo build -p horizon-client --profile fast
# Run with prepared resources, open SP world, then:
# /millenaire status
# /millenaire spawn norman 0 64 0 Testville
# /millenaire village list
```

## Expected

- `/millenaire status` shows culture count when content root is found
- Spawn persists under `world/millenaire/villages.json`
- Reload world keeps the village list
