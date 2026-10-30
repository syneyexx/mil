# Millénaire → Horizon — Architecture mapping

**Status:** plan only. Geen runtime-claims. Geen code tot owner-GO op [`HORIZON_PORT_PLAN.md`](HORIZON_PORT_PLAN.md).  
**Bronnen (2026-10-30 Europe/Amsterdam):**
- Horizon engine: [`horizon-rust/horizon`](https://github.com/horizon-rust/horizon) (Minecraft Java **26.3** in Rust, MIT)
- Horizon mods docs: [playhorizon.dev/docs/mods](https://playhorizon.dev/docs/mods), `docs/native-mods.md`, `NATIVE_HOOKS_API = 10`
- Native tooling: [`horizon-mixins`](https://github.com/horizon-rust/horizon-mixins), [`horizon-native-mod-template`](https://github.com/horizon-rust/horizon-native-mod-template)
- Dit repo: Millénaire **9.0.2** / NeoForge **1.21.1** / Java 21 (**958** `.java`, ~**88 MB** resources)

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

## 3. Gekozen architectuur: hybride “Core + Content”

### 3.1 Besluit

| Optie | Oordeel |
|-------|---------|
| A. Alleen `.hmod` content-extract | **Onvoldoende** — dorpen/NPC/goals/tick verdwijnen |
| B. Volledige 1:1 Java→Rust rewrite in één `.hnmod` | **Te groot** als eerste levering; Nightly-pin hell |
| C. **Hybride (keuze):** `.hmod` content + dunne native **Millénaire Core** op stable hooks, sim in eigen crate/world-dir | **Ja** — past bij Horizon’s model én bij LivingMods-achtige authority-split |
| D. Fork Horizon (eigen engine-API) | Alleen als upstream hooks structureel tekort schieten (Labyrinth/entities); niet default |

### 3.2 Runtime sketch

```
Horizon 26.3 (native-build instance)
  ├─ millenaire.hmod          # blocks/items/assets/tags die content-API aankan
  ├─ millenaire.hnmod         # mixins + Rust modules
  │     hooks: lifecycle, server_tick, commands, menus, block observe
  │     millenaire_core::
  │       culture_loader  ← embedded of world-dir copy of millenaire/ JSON
  │       village_sim     ← SavedData-equivalent under world/millenaire/
  │       goals / faith / activity (gefaseerd)
  │       projection      ← spawn/despawn/place via beschikbare world APIs
  └─ (optioneel later) millenaire_labyrinth als aparte bundle
```

**Authority:**
- Canonical dorps-/civ-state: **Millénaire Core** (Rust), niet vanilla villagers.
- Fysieke blokken: Horizon `Level` via hooks / toekomstige write-API (vandaag: observe + beperkte mutatie-paden; write moet upstream of mixin zijn — expliciet in plan-golven).
- Speler-UI v0: chat commands + chest menus (API 8–10), geen volledige NeoForge Screen-port.

### 3.3 Wat we bewust níet meenemen in v0–v1

- Labyrinth-dimensie, Gebondenen chase, Orde-horror ship bar
- TACZ / externe soft-integrations
- GeckoLib geo 1:1
- Volledige village book GUI / alle client screens
- Dedicated Horizon server native mods (upstream gap)
- Save-compatibiliteit met NeoForge 1.21.1 werelden

### 3.4 Relatie tot LivingMods (`syneyexx/Minecraft`)

LivingMods splitst plan / sidecar-sim / NeoForge-presentatie. Voor Horizon is het **lespatroon** bruikbaar (canonical sim + projection), maar de **codebase** is een ander product (Java sidecar + NeoForge). Geen merge aannemen. Wel: Millénaire Core mag intern een “sidecar-achtige” modulegrens houden (sim thread vs tick hook) zonder apart JVM-proces tenzij metingen dat eisen.

---

## 4. API-gap matrix (eerlijk)

| Millénaire-behoefte | Horizon vandaag | Gevolg |
|---------------------|-----------------|--------|
| Custom blocks/items (statisch) | Content `.hmod` templates | Poortbaar |
| Custom block entity / machine | Niet als content; native/mixin | Vereenvoudigen of later |
| Custom NPC entities + AI | Geen stabiele entity-mod API | **Projectie** op vanilla villagers/armor stands óf diepe mixins |
| Server tick budget / governor | `native_server_tick` (API 4) | Kernpad OK |
| Commands | API 8 | OK |
| Trade UI | API 10 chest menus | MVP OK; rich GUI later |
| Worldgen settlements | Content structures beperkt; custom planners = native | Native materializer |
| Custom dimension (Labyrinth) | Geen mod-dim API | **Later / fork-risk** |
| Networking packets | Geen NeoForge channel | Herontwerp op hooks/menus |
| Persistence | `native_world_dir()` + eigen bestanden | OK |
| Multiplayer dedicated | Native mods nog niet op dedicated pipeline | SP/LAN-first |

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
| Entity API ontbreekt | “Living villages” voelen dood | Projection MVP + upstream feature requests documenteren |
| Schaal tick-kosten | MSPT spike | Hergebruik tick-governor ideeën; budgetten in Core |
| Scope creep (Labyrinth/Orde) | Plan sterft | Strikte golven; Labyrinth = eigen GO |
| Licentie/content | “All Rights Reserved” mod | Horizon-port blijft in dit repo/eigen distributie; geen claim op Mojang/Horizon endorsement |
| 1.21.1 → 26.3 item/block IDs | Content breaks | Mapping-tabel per golf; start met subset cultures |

---

## 7. Definition of architecture-ready

Dit document is **architecture-ready** wanneer owner akkoord is met:
1. Hybride Core + Content (optie C)
2. SP/LAN-first, NeoForge jar blijft parallel bestaan
3. Labyrinth/Orde buiten v0–v1
4. Entity-strategie = projection tot betere API

Implementatie start alleen na GO in [`HORIZON_PORT_PLAN.md`](HORIZON_PORT_PLAN.md) §GO.
