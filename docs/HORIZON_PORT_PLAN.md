# Millénaire op Horizon — Port Plan (v1)

**Status:** wacht op owner-GO. Docs only — **geen implementatie** tot GO.  
**Versie:** 1 — eerste analyse van [`horizon-rust/horizon`](https://github.com/horizon-rust/horizon) t.o.v. dit NeoForge-repo.  
**Authoritatief architectuurplaatje:** [`HORIZON_PORT_ARCHITECTURE.md`](HORIZON_PORT_ARCHITECTURE.md)

**Doel van deze PR:** antwoord op “hoe verwerken we Millénaire in de Horizon Rust-engine?” met een uitvoerbaar, gefaseerd plan — niet een half-bakken rewrite-start.

---

## 0. Verdict in één alinea

Horizon is Minecraft **26.3 herschreven in Rust**. Onze jar is NeoForge **1.21.1**. Java-mods draaien daar **niet**. De enige realistische weg is een **hybride port**: content als `.hmod` + een native Rust **Millénaire Core** (`.hnmod`) op Horizon’s hook API (`NATIVE_HOOKS_API` ≥ 4–10). Dat is een **nieuw product-spoor** naast de bestaande NeoForge-mod, geen “jar droppen in de launcher”.

---

## 1. Wat we geanalyseerd hebben

| Bron | Bevinding |
|------|-----------|
| Horizon README / mods docs | Content mods + native mixins; Java jars = data/assets extract only |
| `native-mods.md` + `native_hooks.rs` | Stable hooks t/m API **10** (tick, lifecycle, commands, items, menus, block observe) |
| Native template / Spider-Man / Orbital | Kleine native mods werken; grote gameplay/API-gaps → mixins of forks |
| Dit repo | **958** Java files, ~**88 MB** resources, 10 cultures, 14+ SavedData, Labyrinth/faith/activity/civ — zwaar code-gedreven |
| Package inventory | Zie architecture §2 (A–E klassen, tick-governor, GeckoLib/Waystones/TacZ) |
| LivingMods (`syneyexx/Minecraft`) | Sidecar-sim patroon als **inspiratie**, geen drop-in voor Horizon |

Snapshot Horizon `main` bij analyse: commit prefix `ab6851d5a8a0` (2026-10-30 UTC) — alleen referentie; pins volgen later per golf.

---

## 2. Productbeslissingen (vastzetten bij GO)

| # | Besluit | Default in dit plan |
|---|---------|---------------------|
| P1 | NeoForge 1.21.1-lijn blijft bestaan | **Ja** — Horizon is parallel spoor `millenaire-horizon` |
| P2 | Distributievorm | `.hmod` + `.hnmod` (launcher Native mods instance) |
| P3 | Eerste speelbare scope | **Één culture** (voorstel: `norman`) + dorp spawn + trade MVP + `/millenaire` subset |
| P4 | Entities | **Projection MVP** (geen custom mob registry tot API bestaat) |
| P5 | Labyrinth / Orde / regional activity / full civ | **Out of scope** tot eigen GO na Core green |
| P6 | Dedicated server | **Later** (upstream native-server build) |
| P7 | World save compat met NeoForge | **Nee** — nieuwe Horizon worlds |

Owner mag P3 culture wijzigen bij GO; rest is architectuur-kritisch.

---

## 3. Definition of Done — per golf

### Golf H0 — Scaffold (repo + build proof)

**DoD:**
1. Map `horizon/` (of `millenaire-horizon/`) met native-mod template + lege Core crate.
2. `horizon-mod.toml` / mixin manifest; pin of `hooks` metadata gedocumenteerd.
3. Content stub `.hmod` met 1 test-block of 1 item laadt in Horizon (owner of agent met game files).
4. Native mod compileert tegen gekozen Nightly/hooks; `/millenaire horizon` (of `status`) antwoordt in SP.
5. Docs: build/run stappen in `docs/HORIZON_PORT_LOCAL_VALIDATION.md` (aan te maken bij implementatie).

**Niet:** dorpen, cultures, AI.

### Golf H1 — Content extract (norman-only)

**DoD:**
1. Exporter of handmatige set: norman-relevante blocks/items die als Horizon templates kunnen → `.hmod`.
2. ID-mapping tabel `1.21.1 → 26.3` / missing-list voor wat **niet** poortbaar is als content.
3. Lang keys voor die subset.
4. Culture JSON subset leesbaar door Rust loader (schema vastgelegd; breaking changes gebudgetteerd).

**Niet:** building engine, goals.

### Golf H2 — Village Core MVP

**DoD:**
1. World-dir persistence `millenaire/` onder `native_world_dir()`.
2. Op `native_world_lifecycle` + `native_server_tick`: één dorp registreren (command seed of fixed test spawn).
3. Minimal population projection (zichtbare actors — strategie uit architecture §4).
4. Tick budget cap (hard ms/ops) — geen unbounded scan.
5. Commands: `status`, `village list`, `spawn` (beperkt).

**Speelbaar bewijs:** owner ziet een “Millénaire-dorp” markering + NPCs/projection in SP.

### Golf H3 — Trade + reputation thin slice

**DoD:**
1. Chest-menu trade (API 10) met plain items (API 9 constraints respecteren).
2. Reputation counter persistent; beïnvloedt aanbod of prijzen (eenvoudige tabel).
3. Geen NeoForge packet UI vereist.

### Golf H4 — Building materialize (bounded)

**DoD:**
1. Eén building plan-set plaatsen met per-tick placement budget (hergebruik concept uit worldgen budgets).
2. Resume na unload/reload.
3. Failures → `RECOVERY_REQUIRED`-achtige status, geen destructieve replay.

### Golf H5 — Multi-culture + NeoForge feature parity roadmap

**DoD:**
1. Tweede culture.
2. Gap-lijst vs NeoForge main (faith, activity, labyrinth, civ) met eigen plannen.
3. Pas dan aparte GO-docs (niet in deze v1).

---

## 4. Werkvolgorde (technisch, na GO)

```
H0 scaffold ──► H1 content norman ──► H2 village tick/persist
                                      │
                                      ├──► H3 trade menus
                                      └──► H4 one building pipeline
                                               │
                                               └──► H5 expand + gap roadmap
```

Parallel mogelijk: H1 content tooling terwijl H0 hooks landen — niet H4 vóór H2.

### Repo-layout (voorstel bij implementatie)

```
millenaire-horizon/
  content/                 # → .hmod
  native/                  # → .hnmod / mixins
    horizon-mixin.toml
    src/...
  README.md
docs/HORIZON_PORT_*.md     # blijft in mil docs/
tools/horizon/             # exporters (later)
```

NeoForge-tree (`src/main/java`) blijft source of truth voor 1.21.1 tot features bewust gedeeld/geëxtraheerd worden (JSON culture packs zijn de beste shared surface).

---

## 5. Validatie-beleid (eerlijk)

| Check | Wie |
|-------|-----|
| Docs / plan review | deze PR |
| Horizon client build + in-game | **owner** (Minecraft 26.3 account + GPU); agents claimen geen runtime groen zonder bewijs |
| NeoForge regressie | ongewijzigd laten in plan-only PR; bij code-golven: owner lokale build |

Geen claim “werkt in Horizon” tot H0–H2 DoD door owner is afgevinkt.

---

## 6. Out of scope (expliciet)

- Drop-in van bestaande `mods/millenaire-*.jar`
- Automatische Modrinth “Partly” carry-over als vervanging van Core
- Labyrinth dimension, chase, psyche release bar
- Full religion / regional activity / civilization orchestrator
- GeckoLib animatie-pariteit
- Dedicated server ship
- Prestatieclaims t.o.v. Java Millénaire

---

## 7. Risico → stopcondities

Stop of herplan als:
1. Stable hooks geen world mutate-pad geven voor H4 en mixin-oppervlak onhoudbaar wordt → overweeg upstream issue + wachten, of gecontroleerde fork (apart GO).
2. Projection MVP speelbaar “nep” voelt zonder entity API → pause H3+; documenteer upstream request.
3. Nightly breakage > upgrade-budget per golf → freeze pin; geen feature work op broken pin.

---

## 8. Effort-karakterisering (geen kalender)

| Golf | Invasiviteit | Afhankelijkheden |
|------|--------------|------------------|
| H0 | Laag — nieuw tree, geen Java-wijziging verplicht | Horizon toolchain, mixin template |
| H1 | Middel — content mapping + schema | 26.3 block/item semantic diffs |
| H2 | Hoog — nieuwe sim in Rust | hooks 3–4+, persistence design |
| H3 | Middel | hooks 8–10 |
| H4 | Hoog | block write path + budgets |
| H5+ | Zeer hoog | entity/dim APIs of forks |

---

## GO

Bevestig om te starten met **H0** (en P1–P7 hierboven):

- [ ] Owner-GO: hybride Horizon-port als parallel spoor
- [ ] P3 culture = `norman` (of noem alternatief)
- [ ] Labyrinth/Orde blijven geparkeerd
- [ ] Eerste implementatie-PR = H0 scaffold only

Zonder GO: dit document is de analyse-levering; geen code.
