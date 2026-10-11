# Religion System — Architecture

**Baseline HEAD:** `76d661459d73d72ed32230f0cc3f1b3d477c9087`  
**Package root:** `org.millenaire.religion`  
**Branch:** `cursor/religion-system-239c`

This document is the design authority for the integrated religion system. Runtime verification is owner-local (see `docs/RELIGION_LOCAL_VALIDATION.md`). The agent does not run GameTests, GitHub CI, or Minecraft client/server smoke tests.

---

## 0. Baseline findings (pre-implementation audit)

| Area | Finding |
|------|---------|
| Visit goals `pray`, `hold_service`, `meditate`, `perform_pujas`, `be_puja_performer`, `tend_sacrifices`, `pray_*_muslim`, `pilgrimage`, `preach_on_pulpit` | JSON `visitBuilding` only → `VisitBuildingGoal` walk-and-wait; **no** affiliation, mood, inventory, or chronicle effects |
| Building tags `praying`, `pujas`, `sacrifices`, `mosque`, `mosquefemaleside`, `pilgrimsite` | Used as visit destinations; remain valid location selectors |
| `tend_sacrifices.json` | `goalKey` is `"gopray"` (legacy speech key mismatch); keep identifier `tend_sacrifices` for content id; do not break path id |
| Puja / sacrifice player rituals | Lang strings / advancements exist; gameplay message `sacrifice_not_implemented` — out of religion-community scope except as optional clergy interaction context |
| Culture ↔ religion | Not modeled; `Culture` record has no religion field; temperament stays cultural aggression/behavior |
| Living Villages | Needs, requests, social mood/relationships, events — **reuse**, do not fork |
| Festivals | `FestivalWindow` / `InteractionTicker` — cultural market/festival windows; religion feasts integrate without duplicating rewards |
| Civilization | Optional; village religion must work with `civilizationEvolutionEnabled=false` |

---

## 1. Design principles

1. **Visible daily life** — prayer/meditation, clergy, preparation, gatherings, outcomes in book/dialogue.
2. **Three layers** — Tradition (content) ≠ Local community (village) ≠ Individual faith (NPC).
3. **Not 1:1 with culture** — cultures list supported traditions; villages may diverge; NPCs keep affiliation on migration.
4. **Server authority** — all mutations validated server-side; UI/model text never authorizes goods/rewards/affiliation.
5. **No parallel social/need economy** — mood/relationships via `NpcSocialService`; supplies via inventories + Living requests.
6. **No second scheduler** — `GoalScheduler` + visit/ritual goals; ceremony coordinator only plans/tracks.
7. **Config off ≠ wipe** — disabling pauses simulation; SavedData retained.
8. **Crash consistency** — best-effort idempotent resume across SavedData files; **not** atomic multi-file transactions.

---

## 2. Datamodel and canonical owners

| Concern | Canonical owner | Notes |
|---------|-----------------|-------|
| Tradition / ritual definitions | `ReligionContentRegistry` (loaded content) | Immutable after load; custom overrides via ContentFs |
| Culture → tradition ids | `culture.json` field `religions` + optional `cultures/<id>/religion.json` | Empty/missing = no organized religion |
| Village faith community | `ReligionSavedData` → `VillageFaithCommunity` | Traditions present, clergy cache, calendar cursor, policy, history |
| Ceremony instance | `ReligionSavedData` → `CeremonyInstance` | Stable UUID; state machine; participation/consumption flags |
| NPC affiliation / devotion | `ReligionSavedData` → `NpcFaithProfile` keyed by villager UUID | Survives respawn/migration; init once |
| Building religious role | Existing tags + `BuildingPurpose.SHRINE` + optional designated markers | Operational buildings only |
| Inventories / deliveries | `BuildingInventory` + `VillageRequestService` | Exact stacks / components |
| Mood / relationships | `NpcSocialService` / `SocialSavedData` | Bounded deltas on verified outcomes |
| Living chronicle events | `LivingEventService` + new `LivingEventType` religion variants | Dedupe + retention limits |
| Cultural festivals | `InteractionSavedData` / `FestivalWindow` | Feast linkage by culture+window; no double reward |
| Diplomacy / war | Existing faction diplomacy | Faith difference alone never declares war |
| Temperament / aggression | `CulturalTemperament` | Unchanged |

### Content records

- **`ReligionDefinition`** — `ResourceLocation id`, name/desc keys, roles, location tags, daily practices, ceremony ritual ids, feast ritual ids, compatible culture ids, optional `allowsMultipleAffiliations`.
- **`RitualDefinition`** — id, kind (`DAILY`, `SERVICE`, `FEAST`, `MEMORIAL`, `FAMILY`, `CONSECRATION`, `PILGRIMAGE`), required/optional roles, location tags, supplies, schedule, capacity, min participants, durations, cooldown, effects profile id, sounds/particles/held items.
- **`ReligionRoleDefinition`** — role id, matching villager tags/type path fragments, clergy flag, canLeadCeremony.

### Runtime records

- **`NpcFaithProfile`** — villager UUID, list of affiliations `(traditionId, devotion 0–100, initSeed)`, participation history (bounded), lastDailyPracticeDay, flags (`initialized`).
- **`VillageFaithCommunity`** — village UUID, active tradition ids (subset of culture-supported + migrant-introduced), policy (`OPEN`, `HOST_PREFERRED`, `HOST_ONLY` visits), designated shrine building ids, next/last ceremony ids, bounded local history.
- **`CeremonyInstance`** — ceremony UUID, ritualId, villageId, traditionId, state, buildingId, leaderVillagerId, plannedStart/end, participant set, consumed supply ledger, outcome applied flag, interrupt reason, festivalWindowLink optional.

---

## 3. State machines

### Ceremony

```
PLANNED → PREPARING → GATHERING → ACTIVE → COMPLETED
                ↘        ↘         ↘
              DEFERRED  INTERRUPTED  CANCELLED
```

- **PLANNED** — calendar/event accepted; identity reserved; no consumption.
- **PREPARING** — supply checks; Living requests may open; clergy reserved.
- **GATHERING** — NPCs receive attend goals; capacity/deadline enforced.
- **ACTIVE** — minimum participants met; practices run; effects not yet applied.
- **COMPLETED** — outcome applied **once** (`outcomeApplied`); history written.
- **DEFERRED** — missing location/leader/supplies/safety; cooldown before retry.
- **INTERRUPTED** — attack/urgent safety; may resume to GATHERING if still valid.
- **CANCELLED** — hard fail; no rewards; partial prep not refunded as success.

Stuck participants never block forever: gather deadline → proceed if `minParticipants` met, else DEFERRED/CANCELLED.

### NPC faith init

```
profile missing → deterministic seed(UUID, tradition rules) → write once (initialized=true)
reload / content reload → never re-roll affiliation or devotion
unknown tradition id → retain string id for later content restore
```

### Affiliation change

Only via explicit server-validated event (conversion ritual, policy-driven joining) — never silent reset to host culture on migration.

---

## 4. Integration points

| System | Integration |
|--------|-------------|
| `CultureLoader.loadAll()` | Call `ReligionContentLoader.loadAll()` after cultures; validate refs; soft-fail invalid culture religion |
| `VillageManager` tick | `ReligionLifecycle.onVillageManagerTick` staggered per village |
| `VisitBuildingGoal.VisitTask.stop(COMPLETED)` | Daily practice / ceremony attendance hooks |
| `GoalScheduler` | New `AttendCeremonyGoal` + priority boosts from coordinator; combat/urgent still wins |
| Living events | `RESIDENT_DIED` (confirmed permanent loss) → memorial candidate; `ATTACK_*` → interrupt; `BUILDING_COMPLETED` with religious tag → consecration candidate |
| Family data | Spouse/parent/child names on `VillagerRecord` only — family rites when those links exist |
| `FestivalWindow` | Feast rituals may align with active window; shared reward gate via ceremony outcome id |
| Migration | `NpcFaithProfile` UUID-keyed — no copy/reset in `transferVillagerPermanently` |
| Pilgrimage | Temporary visitor travel via existing visitor/inn patterns; distinct from permanent migration |
| Village Book | New `PanelType.RELIGION` section |
| Travel Book | Tradition pages from content registry |
| NPC dialogue | `ReligionDialogueContext` injected like Living context; works without LLM |
| Designated buildings | `BuildingPurpose.SHRINE` + marker/tag validation |
| Diplomacy | `ReligionIncidentService` emits living/diplomatic notes on shrine damage; no auto-war |

---

## 5. Calendar

Minecraft day = `dayTime / 24000`. Configurable:

- Intra-day hour windows for daily practice / services (aligned with visit goal `minimumHour`/`maximumHour` where set).
- Recurring day-of-season or day-modulus feast schedules in ritual JSON.
- Event-driven ceremonies ignore calendar day but honor cooldowns and safety.
- Sleep / time skip: evaluate at most **one** catch-up ceremony opportunity per village per tick budget; never replay unbounded backlog.
- Unloaded chunks: no force-load; no invented offscreen completion/consumption.

---

## 6. Storage and migration

**SavedData name:** `millenaire_religion` (overworld), schema **v1**.

```
version: int
communities: list of VillageFaithCommunity NBT
profiles: list of NpcFaithProfile NBT
ceremonies: list of CeremonyInstance NBT (active + recent completed for resume/dedupe)
```

| Scenario | Behaviour |
|----------|-----------|
| Old world, no data | Empty maps; communities/profiles init lazily |
| Corrupt entry | Skip + warn |
| Newer schema | Warn + defensive load |
| Config disable | Pause ticks; keep data |
| Content removed | Keep unknown ids; UI shows missing-content labels |
| Orphan ceremony (village gone) | Cleanup on village remove / periodic sweep |
| NPC respawn | Same UUID profile reused |

**Recovery guarantee:** Idempotent flags (`outcomeApplied`, supply ledger keys, participation credit keys). Not a single atomic commit across chunk + SavedData files.

---

## 7. Configuration and performance

Server config section `religion` (defaults on):

| Key | Default | Role |
|-----|---------|------|
| `religionEnabled` | true | Master switch |
| `religionEvaluationInterval` | 100 | Ticks between village religion passes |
| `religionGlobalBudget` | 4 | Villages evaluated / tick |
| `religionCeremonyBudget` | 8 | Ceremony state advances / tick |
| `religionMaxHistoryPerVillage` | 32 | Local religion history |
| `religionMaxCeremoniesTracked` | 64 | Active+recent instances retained |
| `religionCatchupLimit` | 1 | Max catch-up plans after time jump |
| `religionDebugLogging` | false | Verbose logs |

Budgets stagger by village id hash. No LLM for simulation. No world/NPC full scan every tick.

---

## 8. Functional scope (acceptance summary)

### Daily & ceremonies
Daily practice; communal services; feasts with prep/meal; memorials after confirmed losses; family rites when family links exist; consecration of religious buildings; pilgrimages to tagged sites.

### Effects (first complete execution)
Temporary mood; bounded relationship deltas; knowledge/memories; real supply demand; visitors/trade hooks where systems exist; village history messages. **No required supernatural effects.**

### Player
Help prepare; deliver supplies; help build/repair shrines; observe/join; escort pilgrims; support after attack — via Living requests + religion actions, server-validated.

### Mixed communities & policy
Migrants keep faith; sites respect tradition tags; UI shows pluralism; policy gates visitor rites; incidents for shrine damage without auto-war.

---

## 9. Cultural content mapping

| Culture | Tradition(s) | Notes |
|---------|--------------|-------|
| norman | `millenaire:western_christianity` | Mass, prayer, priests/monks, church tags `praying` |
| byzantines | `millenaire:eastern_christianity` | Liturgy, monks, icons |
| indian | `millenaire:sanatana_dharma` | Puja, sadhu/pandit, `pujas` |
| japanese | `millenaire:nihon_way` | Buddhist meditation + kami reverence (two compatible affiliations allowed) |
| seljuk | `millenaire:sunni_islam` | Mosque prayer (male/female sides), imam |
| mayan | `millenaire:maya_cosmovision` | Shaman, `sacrifices` site care (blood ritual gameplay remains content-gated) |
| inuits | `millenaire:angakkuq_way` | Shaman, spirit propitiation |
| gabbers | `millenaire:pulse_circle` | Fictional communal rhythm rites |
| outlawz | *(none)* | Explicitly no organized religion |
| illagers | `millenaire:totem_shadow` | Fictional totemic fear rites; low participation default |

Differences are in rituals, roles, tags, hours, supplies, and sounds — not rename-only clones.

---

## 10. Phased implementation plan

1. Definitions, loader, validation, storage, migration  
2. Daily activities, clergy, ceremony coordination  
3. Supplies, social effects, event links  
4. Calendar, cultural content, presentation (book/travel/dialogue)  
5. Player actions & player-built shrines  
6. Mixed communities, pilgrimages, policy, diplomatic incidents  
7. Integration checklist, recovery docs, README updates  

Acceptance criteria per phase are tracked in `docs/RELIGION_IMPLEMENTATION.md`.

---

## 11. Explicit non-goals / limits

- Supernatural blessings as required gameplay (optional later).
- Auto-war from faith difference.
- Force-loading chunks for rites.
- Invented offscreen ceremony success.
- Parallel reputation system.
- Replacing `CulturalTemperament`.
- Atomic multi-SavedData transactions (not offered by platform).
