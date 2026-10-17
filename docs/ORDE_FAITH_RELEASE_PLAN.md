# De Orde — Overworld Faith / Ritual Release Plan (v1.1)

**Status:** wacht op owner-GO. Geen implementatie tot GO.  
**Versie:** 1.1 — owner-feedback: tick-budget hard; ontvoering **75% speler-poging** (ontsnappen door wegrennen of doden) / **25% random NPC**.  
**Scope:** overworld **fucked-up geloof** van De Orde (Gebondenen / Configuratie / Spijkermeester) — geheime chapters in dorpen, rituelen, offeringen, spietsen, ontvoeringen.  
**IP:** geen Hellraiser-namen (geen Pinhead/Leviathan/Cenobite als productnaam). Lore = **Orde / Gebondenen / Configuratie / De Ruit / Spijkermeester**.

**Relatie tot Labyrinth-psyche plan:**  
[`LABYRINTH_REVIVAL_RELEASE_PLAN.md`](LABYRINTH_REVIVAL_RELEASE_PLAN.md) = *dimensie-feel* (P1–P5).  
Dit document = *overworld religie-laag* die dorpen met die Orde verbindt. Beide mogen parallel; dit plan **vereist** geen P1–P5-GO. Configuratie-theater (morph-UI) blijft out-of-scope hier ook.

---

## 0. Waarom dit nu

Jullie vroegen: kan De Orde (cenobite-achtig) om de **2 Minecraft-dagen** een ritueel doen en iemand **ontvoeren** alsof een **Configuratie** is opgelost?

**Antwoord:** ja — en het past nátuurlijk op wat er al staat:

| Laag | Wat er al is | Wat ontbreekt |
|------|--------------|---------------|
| Secret orders | Last Flame / Closed Eye / Root Circle + chapters + rituals | Orde-template + dark outcomes |
| Ritual pipeline | `ReligionPlanner` → `RitualCoordinator` + `cooldown_days` | Kidnap/sacrifice success-path; multi-order seed |
| Labyrinth | Gebondenen, Configuratie, Spijkermeester, dim | Bridge: dorp → Labyrinth ontvoering |
| Villagers | `markVillagerKilled`, transfer, missing-counts | `ABDUCTED` status die respawn blokkeert |
| Supernatural flag | `religionSupernaturalEnabled` (default **off**, unused) | Wire aan Orde-hard-rituals |

Dit is geen los “monster-mod”-feature. Het is een **religieus horror-systeem** in Millénaire-stijl: ceremonieel, dorpsgebonden, speelbaar te ontdekken/tegenhouden.

---

## 1. Ontwerpbeslissing (architectuur)

### 1.1 Hybride: Order-first, Faith-corrupt later + player founding choice

| Keuze | Besluit |
|-------|---------|
| Culture-default faith? | **Nee.** Norman/Mayan/etc. blijven hun faith. Niet elk NPC-dorp is Orde. |
| Secret Order chapter? | **Ja.** Template `millenaire:order_orde` (“De Orde”). |
| **Player city founding?** | **Ja (v1.1+).** Wizard-stap religie: (1) eigen geloof, (2) **Cenobites**, (3) cultuur-NPC-geloof. Keuze `orde` zet faith `millenaire:orde` + force-seed Orde-chapter (min-pop skip). |
| Faith-corrupt overlay? | **Wave O4+.** Als chapter `ACTIVE` + hoge tension: panel/chronicle spreekt van “gebroken rite” / “nieuwe eredienst” — zonder culture faith-id te wissen. |
| Publieke Mayan-achtige offer-altars? | Hergebruik `sacrifices`-tags + nieuw shrine-plan; Mayan `tend_sacrifices` blijft soft; Orde-hard-path is apart. |

**Waarom:** Last Flame bewijst dat quest → chapter → ritual werkt. Orde als **infectieus geheim** in NPC-dorpen + **expliciete keuze** bij spelersteden (Cenobites-knop).

### 1.2 Tone (fucked-up, maar Millénaire)

- Ceremonieel, koel, gastvrij tot het kantelt — zelfde frenemy-ziel als Gebondenen.
- Spiets / offer / ontvoering: **suggestief + gevolgen**, geen gore-spam of IP-copy.
- Dorp merkt het: missing villagers, tension, chronicle, paniek-spraak — niet alleen particles.

### 1.3 Cadence: “elke 2 dagen” zonder dorpen leeg te eten

**Basisritme:** chapter mag om de **2 MC-dagen** (`cooldown_days: 2`) een Orde-ritueel plannen.

| Ritueel | Frequentie | Effect |
|---------|------------|--------|
| **Nachtwake van de Orde** (soft) | elke 2 nachten als gates OK | Devotion/secrecy/tension; VFX; geen doden |
| **Spijkerliturgie** (hard) | max 1× / 2 dagen, alleen als hard-gates OK | Offer / spiets-spectacle; 0–1 permanent verlies óf markering |
| **Configuratie-roep** (event) | wanneer Configuratie “opgelost” nabij chapter | Start **ontvoeringspoging** (zie §1.4) |

Hard-gates (allemaal nodig tenzij config override):

1. `ordersEnabled` + `religionEnabled`
2. `religionSupernaturalEnabled` **aan** (default blijft uit tot ship-besluit)
3. Chapter stage ≥ `ACTIVE`, secrecy ≥ drempel, pop ≥ min
4. Geen active ritual; cooldown gerespecteerd
5. Niet `EXPOSED` / `DISBANDED`
6. Soft cap: max **1 hard-loss / 4 MC-dagen** per dorp (anti-wipe)

### 1.4 Ontvoeringspoging: 75% speler / 25% NPC (v1.1)

Na een geldige Configuratie-roep (of hard abduct-outcome) rol **één** target:

| Roll | Target | Gedrag |
|------|--------|--------|
| **75%** | Speler (dichtstbijzijnde survival/adventure in range, anders skip → herrol NPC) | **Poging**, geen instant teleport. Orde-jagers (klein aantal Gebondenen / Spijkermeester-achtig) spawnen en proberen de speler te grijpen. |
| **25%** | Random eligible NPC in het dorp | Zelfde soft-cap / non-leader regels; bij succes → `ABDUCTED` + Labyrinth-bridge |

**Speler ontsnappen (verplicht speelbaar):**

1. **Wegrennen** — buiten chase-radius / timeout (bv. 30–45s of X blokken van shrine) → poging **FAILED**; cooldown telt wel; chronicle “de roep mist”.
2. **Doden** — alle actieve jagers van deze poging doden → **FAILED** (zelfde); optioneel kleine secrecy/tension hit voor de chapter.
3. **Gegrepen** — jager bereikt grab-range + short channel (interruptible door damage) → teleport naar Labyrinth Arrival (als dim aan) of soft fail als dim uit.

Geen “je bent sowieso weg” — de 75%-tak is een **fight-or-flight encounter**.

NPC-tak (25%): geen chase op de speler; ritual completes met villager-abduct zoals eerder gepland.

### 1.5 Tick-budget: game moet blijven lopen (harde eis)

Orde mag de tick **niet** volspammen. Dit is ship-blocker, geen “later optimaliseren”.

| Regel | Eis |
|-------|-----|
| Planning | Alleen op bestaande religion night-edge / deadline queue — **geen** per-tick village scan voor Orde |
| Configuratie-call | **Event-driven** (item use / Scharnier return / quest actionData) — O(1) nearest-chapter lookup in chunk-range, geen world iterate |
| Active chase | Max **1** abduction-attempt wereldwijd tegelijk (of 1 per player); tick via budgeted handler ≤ **N entities** (default 2–3 jagers) |
| Chase tick | Skip als speler unloaded / ver weg; max **1 AI-herpath / 10–20 ticks**; hergebruik Rammelaar-achtige blink spaarzaam |
| Rituals | Blijf binnen `RitualCoordinator.tickActiveRituals` budget (bestaande religion budgets) |
| VFX/audio | Alleen als player ≤ 48 blokken; particle cap per attempt |
| Planner threads | Geen extra threads; Orde deelt `religionPlannerThreads` |
| Fail-safe | Als Post-tick board HARD pressure: **geen nieuwe** chase spawns; bestaande attempt mag time-outen naar FAILED |

SLO (owner smoke): 1 Orde-chapter + 1 actieve player-chase mag de server Post-tick board niet stelselmatig overschrijden t.o.v. idle dorp.

---

## 2. Definition of Done — Orde Overworld Faith (harde bar)

Alles groen vóór “shippable Orde-faith”.

### A. Content & seed
1. Order `order_orde` laadt; rituals geregistreerd; NL/EN strings.
2. `ReligionPlanner` kan Orde seedén (niet alleen Last Flame); spawn-kans lager dan Last Flame.
3. Chapter lifecycle: SEEDED → SHRINE_READY → ACTIVE werkt voor Orde.

### B. Cadence
4. Soft-ritueel plant zich rond nacht, cooldown **2 dagen**, aanwezigheid bij shrine vereist.
5. Hard-ritueel respecteer soft-cap + supernatural flag.
6. Geen double-book: max 1 active ritual per chapter.

### C. Offer / spiets
7. Spijkerliturgie: deelnemers verzamelen; VFX (ketting/soul-fire/spikes suggestief); chronicle event.
8. Optionele **echte** offer-outcome: 1 non-leader target → `ABDUCTED` of `KILLED` (config: `ordeHardSacrifice`).
9. Leaders/keepers zijn protected tenzij chapter zelf implodeert.

### D. Configuratie → ontvoeringspoging
10. Trigger “Configuratie opgelost” scheduleert `configuratie_roep` binnen ≤200 ticks als chapter in range (event-driven).
11. Target roll: **75% speler-poging** / **25% random eligible NPC** (§1.4). Geen speler in range → herrol NPC.
12. Speler-poging: max 2–3 jagers; ontsnappen door **wegrennen (timeout/radius)** of **jagers doden**; grab → Labyrinth. Geen instant force-teleport.
13. NPC-succes: entity discard + `ABDUCTED`; **IntegrityChecker respawnt niet**; Labyrinth Pelgrim/Marionet of soft chronicle.
14. Overworld: missing-panel / chronicle / speech cue binnen 1 dag (NPC); speler-fail → “roep mist” cue.

### E. Speler agency
15. Quest-arc (minimaal): fluister → ontdek shrine → bijwonen/weigeren → expose **of** join.
16. Expose path: chapter `EXPOSED` / disband; hard-rituals stoppen; tension spike + EventDirector `FAITH_TENSION`.
17. Config-defaults documenteren; supernatural default **off** tot owner ship-besluit.

### F. Release engineering & tick-budget
18. `./gradlew compileJava` groen (owner-verify).
19. Docs: architecture + local validation smoke.
20. Geen Hellraiser-IP strings in code/lang.
21. **§1.5 volledig**: event-driven calls; ≤1 actieve chase; ≤3 jagers; geen per-tick village scans; HARD-pressure spawn-block; chase SLO smoke.

---

## 3. Campagne O0 → O5 (ship-volgorde)

### Wave O0 — Fundament (data + status + seed)

| # | Levering |
|---|----------|
| O0.1 | `orders/orde.json` — secrecy hoog, lage spawn_chance (~0.08–0.12), building tags `religious`/`sacrifices`/`praying`, rituals lijst |
| O0.2 | Rituals JSON: `orde_nachtwake` (cd 2), `orde_spijkerliturgie` (cd 2), `orde_configuratie_roep` (cd 2, event-driven) |
| O0.3 | `RitualLoader` / `OrderLoader` builtins + registry ids |
| O0.4 | `VillagerRecord` status `ABDUCTED` (+ persist); IntegrityChecker skip; panel “ontvoerd / voorbij het Scharnier” |
| O0.5 | `ReligionPlanner`: multi-order seed (gewicht: Last Flame > Root Circle > Closed Eye > **Orde** zeldzaam) |
| O0.6 | Config keys onder `religion` of `orde`: `ordeEnabled`, hard-sacrifice, soft-cap dagen, supernatural re-use |

**Wave-DoD:** order laadt; chapter kan seedén; abducted status compileert; nog geen hard kill in prod-path.

---

### Wave O1 — Cadence & soft/hard rituals

| # | Levering |
|---|----------|
| O1.1 | Planner plant `orde_nachtwake` i.p.v. alleen `night_wake` voor Orde-chapters (dusk window) |
| O1.2 | Cooldown 2 dagen wired via bestaande `lastRitualGameTime` |
| O1.3 | `RitualCoordinator.succeed` outcomes uitgebreid: `outcome` veld op ritual def (`bless` / `sacrifice` / `abduct`) |
| O1.4 | Soft VFX + Spijkermeester-achtige particle/sound wanneer supernatural aan |
| O1.5 | Hard path: select victim → mark killed **of** abducted (feature flag); household links; chronicle `RITUAL` + nieuw `ABDUCTION` event type |
| O1.6 | Soft-cap 1 hard-loss / 4 dagen |

**Wave-DoD:** DoD B + C in singleplayer smoke met `/millenaire` religion/orde debug.

---

### Wave O2 — Configuratie-roep + target roll + chase (de “puzzle box” beat)

| # | Levering |
|---|----------|
| O2.1 | Hook-punten (**alleen** event-driven, geen tick-poll): |
| | (a) Player gebruikt Configuratie **buiten** Labyrinth terwijl nearest Orde-chapter ≤ N chunks |
| | (b) Player keert terug via Scharnier met recente Labyrinth-progress tag |
| | (c) Quest `actionData` `orde_configuration_solved=1` (fallenking / Orde-quest) |
| O2.2 | `OrdeCallService.onConfigurationResolved` → schedule `orde_configuratie_roep` |
| O2.3 | Target roll: **75% player attempt** / **25% random NPC** (`playerAbductChance=0.75`) |
| O2.4 | `OrdeAbductionAttempt` state machine: PENDING → CHASING → GRABBED / ESCAPED_RUN / ESCAPED_KILL / NPC_TAKEN / EXPIRED |
| O2.5 | Chase: spawn ≤3 jagers; grab channel; run-away radius/timeout; kill-all → fail |
| O2.6 | Tick path: budgeted handler (max 1 attempt; AI throttle); HARD pressure → no new spawns |
| O2.7 | Messaging: “De Configuratie is gehoord” / chase cues / “de roep mist” (NL/EN) |

**Wave-DoD:** DoD D10–D12 + F21 chase-deel; speler kan ontsnappen door rennen of doden.

---

### Wave O3 — Labyrinth bridge

| # | Levering |
|---|----------|
| O3.1 | Speler GRABBED + dim aan → teleport Arrival; attempt terminal |
| O3.2 | NPC_TAKEN: discard entity; `ABDUCTED`; `labyrinthAnchor` in SavedData |
| O3.3 | Dim aan: spawn `PELGRIM`/`MARIONET` met link UUID; dim uit: soft chronicle only |
| O3.4 | Rescue path (optioneel light): vind abductee → terug-transfer (zeldzaam) |
| O3.5 | EventDirector: `FAITH_TENSION` na hard rituals / failed player hunts |

**Wave-DoD:** DoD D13–D14.

---

### Wave O4 — Quest, conflict, faith-corrupt presentatie

| # | Levering |
|---|----------|
| O4.1 | Quest line `orde_0`…`orde_5` (spiegel Last Flame): whisper → watch → shrine → initiation → conflict branches |
| O4.2 | `OrderConflictService` generaliseren of `OrdeConflictService` (niet lastflame_-prefix hardcoded) |
| O4.3 | Expose / leave / support / oppose |
| O4.4 | Religion panels: Orde chapter toont “verboden rite” copy; tension meter |
| O4.5 | Light faith-corrupt: als ACTIVE+tension hoog, festival/pray goals kunnen “wrong” speech trekken (geen culture swap) |

**Wave-DoD:** DoD E15–E16.

---

### Wave O5 — Balance, defaults, ship lock

| # | Levering |
|---|----------|
| O5.1 | Balance pass: spawn chance, secrecy thresholds, hard-cap, victim rules |
| O5.2 | Ship decision: `religionSupernaturalEnabled` default aan **alleen** als owner GO + content warning in README |
| O5.3 | `docs/ORDE_FAITH_LOCAL_VALIDATION.md` smoke checklist |
| O5.4 | README sectie Orde + link architecture/release plan |
| O5.5 | Sync `ORDE_FAITH_ARCHITECTURE.md` met final APIs |
| O5.6 | Compat: Last Flame dorpen blijven; Orde seed niet als chapter al bestaat |

**Wave-DoD:** **Alle §2 items afgevinkt.** Anders geen “Orde faith release-ready”.

---

## 4. Data shapes (richtinggevend)

### 4.1 Order (`millenaire/orders/orde.json`)

```json
{
  "id": "millenaire:order_orde",
  "display_name": "De Orde",
  "min_village_pop": 10,
  "spawn_chance": 0.10,
  "secrecy_default": 90,
  "building_tags": ["sacrifices", "religious", "praying"],
  "clergy_preference_tags": ["religious"],
  "rituals": [
    "millenaire:orde_nachtwake",
    "millenaire:orde_spijkerliturgie",
    "millenaire:orde_configuratie_roep"
  ],
  "themes": { "symbol": "configuration", "mood": "bound_hospitality" },
  "allows_orders": true
}
```

### 4.2 Ritual outcome extensie

Nieuw optioneel veld op ritual JSON (O1.3):

```json
"outcome": "abduct_attempt",
"tension_delta": 8,
"secrecy_delta": -5,
"cooldown_days": 2
```

`RitualCoordinator.succeed` → start `OrdeAbductionAttempt` (niet direct kill). Target via §1.4 roll.

### 4.3 Config (voorstel)

```toml
[orde]
enabled = true
hardSacrificeEnabled = false
abductionEnabled = true
playerAbductChance = 0.75          # rest = NPC (0.25)
maxHuntersPerAttempt = 3
maxActiveAttemptsGlobal = 1
chaseTimeoutTicks = 800            # ~40s @ 20 TPS
chaseEscapeRadius = 48
grabChannelTicks = 40
hunterAiIntervalTicks = 15         # throttle pathing
configurationCallRangeChunks = 8
hardLossCooldownDays = 4
labyrinthBridgeEnabled = true
blockNewAttemptsOnHardPressure = true
```

`religionSupernaturalEnabled` blijft master voor spectaculaire/supernatural path.

---

## 5. Buiten scope

| Out | Reden |
|-----|--------|
| Hellraiser-IP namen/assets | Policy (zelfde als Labyrinth) |
| Configuratie morph-UI theater | Owner; Labyrinth P-plan |
| Orde als default culture faith | Zou alle dorpen besmetten |
| Elke 2 dagen **altijd** een dode | Dorp-wipe; soft-cap verplicht |
| Instant force-teleport speler (geen escape) | Owner: 75% is **poging** met run/kill escape |
| Per-tick world/village Orde scans | §1.5 verboden; event + budgeted chase only |
| Multiplayer grief-forced abduct zonder flags | Config + supernatural gate |
| Volledige custom gore models | Suggestieve VFX + Gebondene art hergebruik |

---

## 6. Afhankelijkheden & risico’s

| Risico | Mitigatie |
|--------|-----------|
| IntegrityChecker respawnt “doden” | `ABDUCTED` + skip in checker (O0.4) |
| Planner hardcodes Last Flame | Multi-order weights (O0.5) |
| Labyrinth uit → broken bridge | Soft chronicle ending (O3.3) |
| Te agressieve cadence | Soft vs hard split + soft-cap |
| Conflict met Last Flame chapter | 1 chapter / village invariant behouden |
| Tone te edgy voor Millénaire-publiek | Supernatural + hardSacrifice default uit |
| Tick spam / TPS dip tijdens chase | §1.5 caps + HARD-pressure block + AI throttle |
| Speler AFK / unloaded tijdens 75%-roll | Skip player → NPC herrol; of expire attempt |

---

## 7. Release discipline

1. Eén wave → commit → push → PR updaten.  
2. Na elke wave: compile + smoke uit local-validation doc (incl. chase tick spot-check).  
3. O5 eindigt alleen op volledige §2.  
4. Geen code vóór owner-GO op dit v1.1-plan.  
5. Parallel OK met Labyrinth P1–P5, maar **niet blokkeren** op psyche-feel.

---

## 8. Owner-GO (antwoord hierop)

1. Akkoord met **Order-first** (niet culture-default faith)?  
2. Cadence: soft elke 2d + hard max 1/4d OK?  
3. `religionSupernaturalEnabled` default **uit** tot ship-besluit OK?  
4. Hard sacrifice default **uit**, abduction default **aan** (als supernatural aan) OK?  
5. Labyrinth-bridge (Pelgrim/Marionet) OK als dim enabled?  
6. Full O0 bij start (geen half fundament)?  
7. **v1.1 locked:** 75% speler-poging (run/kill escape) / 25% NPC + §1.5 tick-budget? *(owner vroeg dit — default ja)*  

**Aanbevolen:** alles ja, dan O0→O1 eerst.

---

## 9. Eindantwoord

**Ja — haalbaar en realistisch.**  
Shipbaar overworld-geloof: Orde-chapters, 2-dagen ritme, Spijkerliturgie, en Configuratie-roep die **meestal de speler jaagt** (ontsnappen mag) en soms een NPC meeneemt — **zonder** de tick te verzuipen.

**Geen code tot jij GO geeft op v1.1.**
