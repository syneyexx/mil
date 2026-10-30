# Millénaire → Horizon — Architecture mapping

**Status:** plan only. Geen runtime-claims. Geen code tot owner-GO op [`HORIZON_EXACT_REBUILD_PLAN.md`](HORIZON_EXACT_REBUILD_PLAN.md).  
**Mandaat:** bouw `syneyexx/mil` **exact** na **in** de Horizon-engine (vendored fork `horizon/`, crate `crates/millenaire`).  
**Bronnen (2026-10-30 Europe/Amsterdam):**
- Horizon engine: [`horizon-rust/horizon`](https://github.com/horizon-rust/horizon) (Minecraft Java **26.3** in Rust, MIT)
- Horizon mods docs: [playhorizon.dev/docs/mods](https://playhorizon.dev/docs/mods), `docs/native-mods.md`, `NATIVE_HOOKS_API = 10`
- Native tooling: [`horizon-mixins`](https://github.com/horizon-rust/horizon-mixins), [`horizon-native-mod-template`](https://github.com/horizon-rust/horizon-native-mod-template)
- Gedrag-SoT: dit repo Millénaire **9.0.2** / NeoForge **1.21.1** / Java 21 (**958** `.java`, ~**88 MB** resources)

---

## 0. Kernfeit (niet onderhandelbaar)

| Vraag | Antwoord |
|-------|----------|
| Draait de NeoForge-jar op Horizon? | **Nee.** Horizon heeft geen Mojang/NeoForge Java-runtime. Java mod jars worden niet uitgevoerd. |
| Wat wél? | **Content mods** (`.hmod`: data + assets) en **native mods** (`.hnmod`: Rust via horizon-mixins, gecompileerd ín de game). |
| Versiegat | Millénaire target **1.21.1**. Horizon target **26.3**. Dat is geen “remap”, dat is een **port**. |
| GeckoLib / Mixins / NeoForge events | Geen equivalent out-of-the-box. Custom entities/animaties = native Rust of later Horizon-API. |

**Conclusie:** “deze mod in de engine verwerken” = **nieuwe Horizon-distributie** van Millénaire (hybride content + native), geen drop-in van `millenaire-9.0.2.jar`.

---

## 1. Horizon-modelfamilie (wat de engine biedt)

```
┌─────────────────────────────────────────────────────────────┐
│  Horizon launcher / instance (Native mods experimental)     │
│                                                             │
│  .hmod  ──► registries, models, loot, tags, structures      │
│             (geen code; API 0.2 + capability `registries`)   │
│                                                             │
│  .hnmod ─► horizon-mixins bundle → patch Horizon Rust src   │
│             → cargo build horizon-client (eigen instance)   │
│                                                             │
│  Stable hooks (NATIVE_HOOKS_API 10, o.a.):                  │
│    world lifecycle · server tick · commands · players       │
│    plain items · chest menus · block read/observe           │
│    HUD / keys / zoom / minimap / options page               │
│                                                             │
│  NIET vandaag: custom entity registry, custom dimensions,   │
│  NeoForge-achtige BlockEntity machines, GeckoLib geo,       │
│  dedicated-server native build (pipeline nog niet),         │
│  “run arbitrary Java jar”.                                  │
└─────────────────────────────────────────────────────────────┘
```

Voorbeeld-mods die de grenzen tonen:
- **Veinminer / Zoom / Minimap** — native hooks, klein oppervlak.
- **Spider-Man** — diepere mixins (movement, render); pin op Nightly-commit.
- **Orbital Laser** — had een **fork** nodig voor input/UI/render/world API die upstream nog niet had.

Millénaire is ordes groter dan die voorbeelden. De architectuur moet die schaal expliciet accepteren.

---

## 2. Millénaire-subsystemen → Horizon-lagen

Inventaris (Java-bestanden per top-package, snapshot `main`; A–E = portklasse):

| Package | files | Klasse | Horizon-laag | Toelichting |
|---------|------:|:------:|--------------|-------------|
| `village` | 108 | B+D | **native** + world-dir | Manager, growth, paths, panels, diplomacy |
| `goal` | 93 | B | **native** | ~43 goal impls + scheduler; geen datapack-equivalent |
| `network` | 75 | D | **herontwerp** | CustomPacketPayloads → menus/commands/hooks i.p.v. NeoForge packets |
| `block` | 63 | A+B | **content** + native | Templates in `.hmod`; BE/logic native of vereenvoudigd |
| `client` | 58 | D | **later / native** | ~32 screens; GeckoLib → static of herbouw |
| `world` | 52 | C | **native** | Spawn queue, roads, settlements, processors |
| `content` | 47 | A | **content pipeline** | FS loaders → Rust readers van `millenaire/` |
| `command` | 46 | D | **native** hooks ≥8 | `/millenaire …` |
| `labyrinth` | 44 | B+C | **fase later** | Custom dim + entities + chase — grootste API-gap |
| `item` | 36 | A+B | **content** (+ gedrag) | Declaratief waar template volstaat |
| `building` | 34 | A+B | **native** + NBT/JSON | Plan-sets; ~1815 structure NBTs blijven data |
| `faith` | 31 | A+B | **native** + JSON | Ritual pipeline = code |
| `entity` | 23 | B | **native++ / projection** | `MillVillager` — geen stabiele Horizon entity-mod API |
| `activity` | 22 | B | **later native** | Regional hunts/patrols/raids |
| `culture` | 21 | A | **content** | 10 cultures (norman…orde; nederland/illagers thinner) |
| `monster` | 20 | B+E | **later / drop v0** | GeckoLib `GeoEntity` pack monsters |
| `combat` | 20 | B | **later native** | Raids / war |
| `quest` | 16 | A+B | **later** | Definitions JSON + runtime |
| `economy` / `logistics` / `founding` / `diplomacy` / `story` | ~27 | B | **later native** | Civ-orchestration |
| `perf` | 7 | B | **native** | `CommitBudget`, stagger — hergebruik in Core |
| `net` | 7 | — | **skip/optional** | Version/donor HTTP, geen gameplay |
| `compat` | 4 | E | **skip v0** | Waystones / TacZ optional |
| `catalog` | 17 | E | **skip v0** | Incl. één `LevelRenderer` mixin |

**Portklassen:** **A** data/assets · **B** entity/AI/tick · **C** worldgen/dim · **D** UI/network · **E** NeoForge/GeckoLib/extern.

### 2.1 Resources (~88 MB)

| Tree | Schaal |
|------|--------|
| `millenaire/` | ~31 MB — cultures, buildings (~573 JSON), village/villager types, shops, quests (~130), faiths, languages (~27 locales) |
| `assets/millenaire/` | ~45 MB — ~1210 textures, ~1169 models, ~256 blockstates, geo/animations (GeckoLib) |
| `data/` | ~13 MB — structures, recipes, loot, advancements, `millenaire:labyrinth` dim, biome modifiers |
| Cultures | byzantines, indian, inuits, japanese, mayan, norman, seljuk, orde (+ thinner nederland, illagers) |
| Structure NBT | ~1815 under cultures (+ mirrors under `data/millenaire/structure/`) |

### 2.2 Tick + persistence (wat Core moet nabootsen)

**Entrypoint:** `Millenaire.java` — NeoForge `ServerTickEvent.Post` orkestreert o.a. `CommitBudget` → `VillageManager.tick` → spawn/roads/settlement → religion/war/economy/logistics/story/treaty/founding → `RegionalActivityService` → labyrinth dim tick → quest cadence.

**SavedData (14+):** `VillageSavedData`, `CultureStandingSavedData`, `DiplomacyEventSavedData`, `ReligionSavedData`, `ActivitySavedData`, `LogisticsSavedData`, `WarCampaignSavedData`, `PlayerBountySavedData`, `RoadNetworkSavedData`, `SettlementSavedData`, `TreatySavedData`, `FoundingSavedData`, `KnowledgeSavedData`, `StorySavedData` (+ player quest state). Horizon-vertaling: bestanden onder `native_world_dir()/millenaire/` met eigen schema’s — **geen** 1.21.1 save-compat.

### 2.3 Hard blockers (non-Java)

1. ~342 files raken `ServerLevel`; ~147 importeren NeoForge — geen JVM-shim.
2. `MillVillager` + goals koppelen aan vanilla pathfinding/attributes/`ItemStack`.
3. Structures/processors/chunk tickets = MC-specifiek.
4. Custom dim + biome modifiers (Labyrinth) zonder Horizon dim-mod API.
5. 75 network payloads + ~32 screens → volledige D-laag herbouw.
6. **GeckoLib required** voor pack monsters; catalog-mixin droppable.
7. Geen schone “logic core” jar — port = **behavioral reimplementation**, A-content als voornaamste portable asset.

---

## 3. Gekozen architectuur: exacte Rust-nabouw in Horizon

### 3.1 Besluit

| Optie | Oordeel |
|-------|---------|
| A. Alleen `.hmod` content-extract | **Nee** — geen dorpen/AI |
| B. Dunne MVP forever | **Nee** — owner eist **exacte** nabouw van mil |
| C. **Exacte hybride nabouw (keuze):** volledige mil-gedrag in Rust `.hnmod` + content `.hmod`; mil Java = SoT | **Ja** |
| D. Fork Horizon | **Toegestaan per golf** waar hooks entity/dim/UI blokkeren — nooit stille feature-cut |

### 3.2 Runtime sketch

```
Horizon 26.3 (native-build instance)
  ├─ millenaire.hmod          # ALLE poortbare blocks/items/assets/tags/structures
  ├─ millenaire.hnmod         # EXACTE Rust-nabouw van org.millenaire.*
  │     hooks + mixins naar behoefte
  │     modules 1:1 met mil packages:
  │       village, goal, entity, building, world, faith, activity,
  │       labyrinth, combat, quest, economy, logistics, founding, …
  │     persist/ = alle SavedData-equivalenten
  │     tick.rs  = zelfde post-tick orde als Millenaire.java
  └─ content blijft gedeeld vanuit mil resources waar mogelijk
```

**Authority:**
- Canonical sim = Rust Millénaire Core — **zelfde** invariants als mil Java.
- Fysieke wereld = Horizon `Level` via hooks/mixins (write-pad per golf hard maken, niet versimpelen).
- UI: Horizon-native mag, **flows/data** = mil exact.
- Projection-entities alleen als tijdelijke brug met ledger `DEFERRED` — einddoel echte `MillVillager`-pariteit.

### 3.3 Scope

**In scope (exact, gefaseerd R0–R9):** hele mil-feature set inclusief Labyrinth, faith/Orde, activity, civ, quests, commerce UI.  
**Niet exact-claimbaar zonder ledger:** TacZ/Waystones/GeckoLib/LLM als Horizon geen equivalent heeft — dan `DEFERRED` + mil-referentie, geen stille drop.  
**Geen:** NeoForge 1.21.1 save openen in Horizon; Java jar execution.

### 3.4 Relatie tot LivingMods (`syneyexx/Minecraft`)

LivingMods is een **ander** product. Voor Horizon-nabouw van mil: geen merge. Optioneel hergebruik van “canonical state + projection”-denken binnen één Rust `.hnmod`.

---

## 4. API-gap matrix (eerlijk)

| Millénaire-behoefte | Horizon vandaag | Gevolg |
|---------------------|-----------------|--------|
| Custom blocks/items (statisch) | Content `.hmod` templates | Poortbaar |
| Custom block entity / machine | Niet als content; native/mixin | Vereenvoudigen of later |
| Custom NPC entities + AI | Geen stabiele entity-mod API | **Exact:** mixins/upstream; projection alleen tijdelijk + ledger |
| Server tick budget / governor | `native_server_tick` (API 4) | Kernpad OK — mil tick-orde 1:1 |
| Commands | API 8 | OK — alle mil commands |
| Trade / rich UI | API 10 chest menus + mixins | Exacte flows; screens mogen native zijn |
| Worldgen settlements | Content structures beperkt; planners = native | Exacte materializer/budgets |
| Custom dimension (Labyrinth) | Geen mod-dim API | Exact via mixin/fork — niet schrappen |
| Networking packets | Geen NeoForge channel | Zelfde contracts op hooks/menus/UI |
| Persistence | `native_world_dir()` + eigen bestanden | Alle SavedData 1:1 |
| Multiplayer dedicated | Native mods nog niet op dedicated pipeline | SP/LAN exact eerst; dedicated = R9 blocker |

---

## 5. Content-port pipeline (technisch)

1. **Inventory:** `millenaire/` JSON + `assets/millenaire` + `data/millenaire` scheiden in *declaratief* vs *Java-gedreven*.
2. **Block/item catalog:** waar gedrag = vanilla template → `data/millenaire/horizon/block|item/*.json`.
3. **Culture packs:** blijven JSON; Rust `culture_loader` leest zelfde (of licht genormaliseerde) schema’s — schema-freeze per golf.
4. **Structures/buildings:** Millénaire plan-format ≠ vanilla structure NBT; building engine blijft native tot er een export naar Horizon structure-format bestaat.
5. **Lang:** `assets/.../lang` + `millenaire/languages` — content/lang files waar mogelijk; native strings voor commands.

Tooling (later implementatie, niet nu): `tools/horizon/` exporter die `.hmod` + embedded content tree bouwt; check via `cargo horizon-mod check`.

---

## 6. Risico’s

| Risico | Impact | Mitigatie |
|--------|--------|-----------|
| Nightly pin drift | Build breekt | Alleen stable `hooks` API waar mogelijk; pin + upgrade-golf |
| Entity API ontbreekt | Exacte villagers geblokkeerd | Mixins/fork/upstream; geen permanente projection |
| Schaal tick-kosten | MSPT spike | Zelfde `CommitBudget`/stagger als mil |
| Labyrinth dim gap | R7 geblokkeerd | Fork/upstream — feature niet schrappen |
| Licentie/content | “All Rights Reserved” mod | Horizon-port in dit repo/eigen distributie; geen Mojang/Horizon endorsement |
| 1.21.1 → 26.3 item/block IDs | Content breaks | Volledige mapping-tabel in R1; `UNMAPPED` in ledger |

---

## 7. Definition of architecture-ready

Dit document is **architecture-ready** wanneer owner akkoord is met:
1. Exacte Rust-nabouw van mil in Horizon (optie C)
2. mil Java/resources = gedrags-SoT; NeoForge blijft parallel tot ship
3. Hele feature set in scope via R0–R9 (geen stille cuts)
4. Entity/dim gaps → mixin/fork, niet versimpelen

Implementatie start alleen na GO in [`HORIZON_EXACT_REBUILD_PLAN.md`](HORIZON_EXACT_REBUILD_PLAN.md) §GO.
