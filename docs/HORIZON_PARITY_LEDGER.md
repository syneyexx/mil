# Horizon Millénaire — Parity Ledger

**Engine:** vendored fork `horizon/` (base `horizon-rust/horizon` @ `59e71f0`)  
**In-tree crate:** `horizon/crates/millenaire`  
**SoT:** NeoForge `src/main/java/org/millenaire` + `src/main/resources/millenaire`

Status keys: `TODO` | `PORTING` | `PARITY` | `DEFERRED(reason)`

| Package (mil) | ≈files | Status | Horizon location |
|---------------|-------:|--------|------------------|
| root / Millenaire tick | 5 | PORTING | `millenaire::engine` + `millenaire_bridge` |
| `perf` / CommitBudget | 7 | PORTING | `millenaire::commit_budget` |
| `config` | 6 | PORTING | `millenaire::config` |
| `content` | 47 | PORTING | `millenaire::content` (culture index) |
| `culture` | 21 | PORTING | via content loader |
| `village` | 108 | PORTING | `millenaire::village` + `persist::VillageSavedData` |
| `goal` | 93 | PORTING | `millenaire::goal` (builtin id registry) |
| `building` | 34 | TODO | `millenaire::building` stub |
| `world` | 52 | PORTING | `millenaire::world` spawn/roads stubs |
| `faith` | 31 | TODO | `millenaire::faith` stub |
| `activity` | 22 | TODO | `millenaire::activity` stub |
| `combat` | 20 | TODO | `millenaire::combat` stub |
| `quest` | 16 | TODO | `millenaire::quest` stub |
| `economy` | 9 | TODO | `millenaire::economy` stub |
| `logistics` | 7 | TODO | `millenaire::logistics` stub |
| `founding` | 4 | TODO | `millenaire::founding` stub |
| `diplomacy` | 4 | TODO | `millenaire::diplomacy` stub |
| `story` | 3 | TODO | `millenaire::story` stub |
| `labyrinth` | 44 | TODO | `millenaire::labyrinth` stub |
| `entity` | 23 | TODO | `millenaire::entity` stub |
| `commerce` | 9 | TODO | `millenaire::commerce` stub |
| `command` | 46 | PORTING | `/millenaire` via native_register/perform |
| `network` | 75 | TODO | rewrite to menus/hooks |
| `client` | 58 | TODO | Horizon UI later |
| `block` / `item` | 99 | TODO | content `.hmod` / registries |
| `monster` | 20 | DEFERRED(GeckoLib→Horizon render) | — |
| `compat` | 4 | DEFERRED(TacZ/Waystones) | — |
| `catalog` mixin | 17 | DEFERRED(tooling) | — |
| SavedData (14+) | — | PORTING | `millenaire::persist` |

Update this table as modules reach behavioral parity with mil Java.
