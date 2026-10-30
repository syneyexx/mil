# Millénaire Exact Rebuild in Horizon (Rust) — Plan v1

**Status:** wacht op owner-GO. Docs only — geen code tot GO.  
**Bron (gedrag + content):** [`syneyexx/mil`](https://github.com/syneyexx/mil) — Millénaire NeoForge 1.21.1 zoals die hier werkt.  
**Doel-engine:** [`horizon-rust/horizon`](https://github.com/horizon-rust/horizon) — Minecraft Java **26.3** in Rust.  
**Architectuur:** [`HORIZON_PORT_ARCHITECTURE.md`](HORIZON_PORT_ARCHITECTURE.md)  
**Eerdere dunne-MVP-schets:** [`HORIZON_PORT_PLAN.md`](HORIZON_PORT_PLAN.md) — **vervangen door dit document** als authoritatief port-plan.

---

## 0. Opdracht (letterlijk)

1. **Pak Horizon** (Rust-engine + native-mod tooling).
2. **Bekijk hoe `syneyexx/mil` werkt** (Java + content = source of truth).
3. **Bouw Millénaire EXACT na** als Horizon-mod in **Rust** (native `.hnmod` + content `.hmod`).

**Exact** betekent: zelfde speelbare systemen, dezelfde culture-content, dezelfde commands/flows, dezelfde SavedData-semantiek — herschreven in Rust tegen Horizon, **niet** “ongeveer dorpjes”.  
**Exact betekent niet:** dezelfde Java-bytecode, NeoForge events, of 1.21.1 world saves openen in 26.3.

---

## 1. Harde feiten

| Feit | Gevolg |
|------|--------|
| Horizon voert **geen** NeoForge/Java jars uit | `millenaire-*.jar` droppen = dood. Alles wordt Rust + Horizon content. |
| mil ≈ **958** Java files, **~88 MB** resources, **10** cultures, **14+** SavedData | Exacte nabouw = volledige behavioral reimplementation + content-port. |
| Horizon target **26.3**, mil target **1.21.1** | Block/item/protocol-mapping hoort bij de nabouw; geen stille ID-aannames. |
| Stable hooks vandaag: `NATIVE_HOOKS_API` **10** | Tick, lifecycle, commands, plain items, chest menus, block observe — basis OK; **custom entities / custom dims / rich GUI** vragen diepere mixins of upstream/fork. |

---

## 2. Wat “EXACT” meetbaar maakt

Een subsystem is **exact-nagedaan** wanneer ALLES waar is:

1. **Gedrag:** dezelfde state machine / outcomes als mil (zelfde gates, budgets, failure modes, recovery).
2. **Content:** dezelfde culture JSON / buildings / quests / faiths / languages laden (schema-compat of gedocumenteerde 1:1 mapping).
3. **Persistency:** alle velden die mil in SavedData houdt, hebben een Rust-equivalent onder `world/millenaire/`; reload = zelfde logische staat.
4. **Speler-oppervlak:** commands, trade, village book/panels, reputation, founding flows — functioneel parity (UI mag Horizon-native zijn als flow/data gelijk is).
5. **Parity-ledger:** elk Java-package/service heeft een rij `TODO | PORTING | PARITY | DEFERRED(reason)` in `docs/HORIZON_PARITY_LEDGER.md` (aan te maken bij H0).

Geen “voelt ongeveer hetzelfde” zonder ledger-rij op `PARITY`.

---

## 3. Productbeslissingen

| # | Besluit | Keuze |
|---|---------|--------|
| E1 | Source of truth gedrag | **mil `main` Java + resources** — bij conflict wint mil, tenzij 26.3 vanilla dat onmogelijk maakt (dan ledger + owner-keuze) |
| E2 | Distributie | `millenaire.hmod` (content) + `millenaire.hnmod` (volledige Rust-core) |
| E3 | NeoForge-lijn | Blijft parallel tot Horizon-parity ship; daarna owner beslist freeze/migrate |
| E4 | Exact-scope | **Hele mil-feature set** inclusief Labyrinth, faith/Orde, activity, civ, quests — in golven, maar **niet** permanent out-of-scope |
| E5 | Entities | Doel = **echte MillVillager-pariteit** in Rust (eigen entity via mixins/API). Tijdelijke projection alleen als build-brug mét ledger `DEFERRED`, nooit als eindstaat |
| E6 | Labyrinth | Exacte nabouw van dim + entities + chase — eigen golf; mag Horizon-fork/upstream eisen |
| E7 | Compat (TacZ, Waystones, GeckoLib, LLM chat) | Exact waar Horizon equivalent bestaat; anders ledger `DEFERRED` met mil-gedrag als referentie |
| E8 | Saves | Geen NeoForge→Horizon world migrate in v1; wél mil-schema-logica 1:1 in nieuwe Horizon worlds |
| E9 | Multiplayer | SP + LAN host eerst (waar native mods draaien); dedicated server zodra Horizon native-server pipeline bestaat |

---

## 4. Engine-integratie (hoe mil ín Horizon komt)

```
syneyexx/mil (Java SoT)
  │
  ├─ resources/millenaire/** + assets + data  ──► tools/horizon/content_port ──► millenaire.hmod
  │
  └─ org.millenaire.* gedrag                 ──► millenaire-horizon/native (Rust)
         culture_loader, village, goals, building, faith, activity,
         labyrinth, combat, quest, economy, … 1:1 modules
         │
         ▼
Horizon native build (horizon-mixins + hooks)
  hooks: world_lifecycle, server_tick, commands, menus, blocks, …
  mixins: entity spawn/AI, block place, dim (waar hooks tekort schieten)
```

**Runtime authority (exact):**
- Canonical sim = Rust Millénaire Core (zoals `VillageManager` + SavedData in mil).
- Fysieke wereld = Horizon `Level` (plaatsen/breken/entities) via hooks/mixins — zelfde intent-regels als mil.
- Geen “Java sidecar”; alles native Rust in de `.hnmod`-bundle (interne modulegrenzen wél toegestaan).

---

## 5. Nabouw-golven (exact, mil-volgorde)

Elke golf: **port Java → Rust** + **parity tests/checklist t.o.v. mil** + ledger update. Geen golf “klaar” zonder speelbare DoD.

### R0 — Horizon scaffold + parity machinery

**DoD:**
1. `millenaire-horizon/` native template bouwt tegen gepinde Horizon Nightly / hooks API.
2. Stub `.hmod` + `/millenaire status` in SP.
3. `docs/HORIZON_PARITY_LEDGER.md` met alle top-packages uit mil (958-file inventory).
4. `docs/HORIZON_PORT_LOCAL_VALIDATION.md` build/run stappen.
5. CI of owner-script: `cargo check` op native tree.

### R1 — Content pipeline (hele `millenaire/` tree)

**DoD:**
1. Exporter: `millenaire/` + assets/data → `.hmod` / embedded content dir.
2. Rust loaders lezen **dezelfde** culture/building/villager/shop/quest/faith JSON als mil (of byte-identieke na normalisatie).
3. Block/item catalog: alle mil-registraties gemapt 1.21.1→26.3 of expliciet `UNMAPPED` in ledger.
4. Alle 10 cultures laden zonder crash (speelbaar gedrag volgt later).

### R2 — Persistence + tick governor (exact mil orchestratie)

**DoD:**
1. Alle 14+ SavedData-types hebben Rust structs + load/save (+ schema versies zoals mil migrations beschrijven).
2. `native_server_tick` orkestreert **dezelfde volgorde** als `Millenaire` post-tick: `CommitBudget` → villages → spawn/roads/settlement → religion/war/economy/logistics/story/treaty/founding → activity → labyrinth → quests.
3. Budgets/stagger/`BackgroundWorkGate`-semantiek behouden (zelfde caps/defaults uit config).
4. Config keys: mil server/common/client opties die Core raakt, aanwezig in Horizon mod-settings of world config.

### R3 — Village + MillVillager + goals (hart)

**DoD:**
1. `Village` / `VillageManager` / growth / paths / panels / Living Villages needs = parity.
2. `MillVillager` + `GoalRegistry` + alle goal impls uit mil (idle, build, trade, raid, activity mission, …).
3. Entity-strategie eindigt op **echte** villagers in Horizon (mixins), niet projection-only.
4. Chunk-loading / village tickets: equivalent gedrag of ledger-deferred met mil-referentie.

### R4 — Building + worldgen settlements + roads

**DoD:**
1. Building plans/costs/placement/export parity.
2. Village spawn queue / spawner / biome gating / materialization budgets = mil.
3. Road network + bridges + settlement registry/takeover = mil.
4. Structure processors / mock blocks gedrag nagedaan.

### R5 — Commerce UI + network surface

**DoD:**
1. Trade, locked chests, shop profiles, reputation, denier — parity.
2. Alle mil commands die spelers/admins gebruiken.
3. Network payloads: herbouwd als Horizon menus/hooks/custom UI met **zelfde data contracts** (ledger per payload).
4. Village book / create wizard / hire / travel — functionele parity (screens mogen Rust/Horizon-native zijn).

### R6 — Faith, quests, combat, activity, economy, founding, diplomacy, story

**DoD:** elk mil-subsysteem in architecture §2 op `PARITY` of expliciet owner-`DEFERRED` met reden. Inclusief Orde/religion hard paths zoals in mil docs.

### R7 — Labyrinth exact

**DoD:**
1. Dim `millenaire:labyrinth` (of Horizon-equivalent) met layout v2 gedrag uit mil.
2. Gebondenen, chase, puzzles, Architect, portals, audio slots — parity checklist uit bestaande Labyrinth docs.
3. Upstream/fork eisen gedocumenteerd en afgevinkt.

### R8 — Monsters / GeckoLib / optional compat

**DoD:** pack monsters + bosses speelbaar (geo→Horizon render path of goedgekeurde visual substitute met zelfde hitbox/AI). Waystones/TacZ/LLM: parity of ledger deferred.

### R9 — Ship bar

**DoD:**
1. Ledger: geen open `TODO`/`PORTING` op core packages zonder owner-signoff.
2. Owner smoke: NeoForge mil vs Horizon mil op dezelfde scenarios (spawn village, trade, build, raid, labyrinth enter).
3. Distributie: `.hmod` + `.hnmod` + install docs.
4. NeoForge freeze/migrate beslissing door owner.

---

## 6. Werkmethode per Java-module (exactheid)

Voor **elke** class/service in mil die gedrag heeft:

1. **Lees** mil-implementatie + tests/docs.
2. **Schrijf** Rust module met dezelfde publieke invariants (input→output, ticks, budgets).
3. **Ledger-rij** + owner validation scenario.
4. **Geen** “vereenvoudigde” variant tenzij owner `DEFERRED` tekent.

Shared surface die wél 1:1 blijft: `src/main/resources/millenaire/**` (culture packs). Java blijft SoT tot de Rust-loader parity heeft; daarna mag content single-source vanuit die tree.

---

## 7. Repo-layout

```
millenaire-horizon/           # nieuw parallel product
  content/                    # → millenaire.hmod
  native/                     # → millenaire.hnmod (Rust exact core)
    horizon-mixin.toml
    src/
      lib.rs
      tick.rs                 # mil post-tick order
      persist/                # SavedData equivalents
      village/ goal/ building/ world/ faith/ …
  README.md
tools/horizon/
  content_port/               # jar/resources → hmod + ID map
  parity_ledger_gen/          # optioneel: scan Java packages → ledger stubs
docs/
  HORIZON_EXACT_REBUILD_PLAN.md   # dit plan
  HORIZON_PORT_ARCHITECTURE.md
  HORIZON_PARITY_LEDGER.md        # bij R0
  HORIZON_PORT_LOCAL_VALIDATION.md
```

`src/main/java` (NeoForge) wijzigt niet voor de port, behalve als content-schema’s bewust gedeeld moeten worden.

---

## 8. API-gaps → exactheid forceren

| Gap | Exact-strategie |
|-----|-----------------|
| Custom entities | Diepe horizon-mixins / upstream entity API — projection alleen tijdelijk + ledger |
| Custom dimension | Mixin/fork Horizon dim support tot upstream; Labyrinth niet schrappen |
| Rich GUI | Native screens of mixin UI; data/flow = mil |
| Block entity machines | Native block logic via mixins |
| GeckoLib | Vervang door Horizon render/anim path met zelfde models of goedgekeurde substitute |
| Dedicated server | Wachten op Horizon native-server mods; LAN/SP exact eerst |

Als een gap **exacte** nabouw blokkeert: stop de betreffende golf, open upstream/fork-spoor, **niet** stilzwijgend versimpelen.

---

## 9. Validatie

| Laag | Wie |
|------|-----|
| Plan/ledger review | deze PR-lijn |
| `cargo check` / mixin build | agent of owner |
| In-game Horizon | **owner** (MC 26.3 + native build) |
| Exactheid vs mil | owner side-by-side scenarios; agent claimt geen PARITY zonder bewijs |

---

## 10. Effort (technisch, geen kalender)

| Golf | Invasiviteit | Blokkers |
|------|--------------|----------|
| R0 | Laag | Horizon toolchain |
| R1 | Middel | 26.3 ID mapping |
| R2 | Hoog | persistence design |
| R3 | Zeer hoog | entity AI in Horizon |
| R4 | Zeer hoog | world mutate + structures |
| R5 | Hoog | UI rewrite |
| R6 | Zeer hoog | volume services |
| R7 | Extreem | custom dim |
| R8–R9 | Hoog | render/compat + ship |

---

## GO

Bevestig om te starten met **R0** onder dit exact-rebuild mandaat:

- [ ] Owner-GO: **exacte** nabouw van `syneyexx/mil` in Horizon Rust (dit plan)
- [ ] E1–E9 geaccepteerd (of noteer afwijkingen)
- [ ] Eerste implementatie-PR = **R0 only** (scaffold + parity ledger)
- [ ] Geen stille scope-cut zonder ledger `DEFERRED` + owner initialen

Zonder GO: analyse/plan only.
