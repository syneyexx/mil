# Millénaire → Horizon — Architecture mapping

**Status:** plan only. Geen runtime-claims. Geen code tot owner-GO op [`HORIZON_PORT_PLAN.md`](HORIZON_PORT_PLAN.md).  
**Bronnen (2026-10-30 Europe/Amsterdam):**
- Horizon engine: [`horizon-rust/horizon`](https://github.com/horizon-rust/horizon) (Minecraft Java **26.3** in Rust, MIT)
- Horizon mods docs: [playhorizon.dev/docs/mods](https://playhorizon.dev/docs/mods), `docs/native-mods.md`, `NATIVE_HOOKS_API = 10`
- Native tooling: [`horizon-mixins`](https://github.com/horizon-rust/horizon-mixins), [`horizon-native-mod-template`](https://github.com/horizon-rust/horizon-native-mod-template)
- Dit repo: Millénaire **9.0.2** / NeoForge **1.21.1** / Java 21 (~958 `.java`, ~4k culture/content files, ~3k assets)

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

Inventaris (java-bestanden per top-package, snapshot main):

| Package | ~files | Horizon-laag | Toelichting |
|---------|-------:|--------------|-------------|
| `village` | 108 | **native** (+ SavedData-achtige world dir) | Dorpskern, panels, diplomacy, paths — tick + persistent state |
| `goal` | 93 | **native** | NPC goal machine; geen datapack-equivalent |
| `network` / `net` | 82 | **native** (of herontwerp) | Custom payloads → Horizon heeft geen NeoForge networking; menus/commands/hooks i.p.v. packets waar mogelijk |
| `block` | 63 | **content** + stukje **native** | Templates in `.hmod`; BE/logic (locked chest, silk worm, …) native of vereenvoudigd |
| `client` | 58 | **native** (client hooks) / later UI | GUIs, render; GeckoLib → herbouw of static models |
| `world` | 52 | **native** (+ later worldgen API) | Settlement plan/materialize/roads; Horizon content worldgen is beperkt |
| `content` | 47 | **content pipeline** | Loaders van `millenaire/` JSON — poort naar Rust readers |
| `command` | 46 | **native** hooks API 8+ | `/millenaire …` → `native_register_commands` |
| `labyrinth` | 44 | **fase later / native++** | Custom dim + entities + chase — grootste API-gap |
| `item` | 36 | **content** (+ gedrag native) | Declaratief waar template volstaat |
| `building` | 34 | **native** + structure data | Plan-sets / placement; structures deels content |
| `faith` | 31 | **native** + JSON content | Ritual pipeline blijft code |
| `entity` | 23 | **native++** | Custom villagers — Horizon heeft nog geen “registreer mob”-mod API |
| `activity` | 22 | **native** | Regional activity state machine |
| `culture` | 21 | **content** | Culture packs blijven data |
| `combat` / `quest` / `economy` / … | rest | **native** | Simulatie-logica |

Resources (orde-grootte):
- `millenaire/cultures/*` — 10 cultures, ~1150 JSON
- `millenaire/` totaal — ~4100 files (languages, plans, faith, encyclopedia, …)
- `assets/millenaire` — ~3000 files (textures, models, geo, lang)

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
