# World Boss Events — Architecture

**Package root:** `org.millenaire.worldboss`  
**Boss content:** `org.millenaire.entity.HemelwachterEntity` (+ client model/renderer)  
**Independence:** Works with religion, civilization, and NPC-LLM features disabled.

Runtime Minecraft verification remains owner-local (see `docs/WORLD_BOSS_EVENTS_LOCAL_VALIDATION.md`). Agents for this repository do not run GameTests, GitHub CI, or client smoke tests.

---

## 0. Baseline findings (pre-implementation audit)

| Area | Finding |
|------|---------|
| Entity registration | `ModEntities` + `EntityAttributeCreationEvent`; flying combat pattern in `InfernalHarrowEntity` |
| Models / emissive | `MillModelLayers`, `ClientEvents`, `EyesLayer` emissive overlays (Wraith/Harrow/Cult) |
| SavedData | Overworld-anchored pattern (`ReligionSavedData.get`) — reuse for one planner per server |
| Server tick | `Millenaire` `ServerTickEvent.Post` — add planner tick beside existing orchestrators |
| Config | `MillenaireServerConfig` ModConfigSpec sections — add `worldBossEvents` push |
| Commands | `RegisterCommandsEvent` in `Millenaire` |
| Village / structure protection | `VillageManager.findNearestVillage`, `StructureAvoidance`, `RoadPlacementGuard` patterns |
| Bossbars | Vanilla `ServerBossEvent` (not previously used in-mod; add for engaged bosses) |
| Preview tooling | `WraithPreviewSession` / `-Dmillenaire.*Preview=true` — mirror for Hemelwachter |
| Visual reference | Attached files are **1254×1254 texture atlases** (JPEG + PNG + UV JSON). No separate 3D silhouette render was supplied. Geometry follows written size/structure requirements + atlas materials; owner confirms silhouette in preview. |

---

## 1. Design principles

1. **One world timer** — active playtime ticks while ≥1 eligible player is online; never per-player timers; never advanced by sleep/`/time set`.
2. **Extensible boss pool** — weighted registry of definitions; planner never hardcodes “always Hemelwachter”.
3. **At most one active world boss** per world/server.
4. **Server authority** — combat, loot, state transitions, and rewards are server-side; client animates presentation only.
5. **Neutral until attributed provocation** — environment damage and unattributed hits do not engage.
6. **No terrain destruction** — spawn and attacks must not break buildings/terrain.
7. **Config off ≠ wipe** — disabling stops new events; active-boss policy is explicit; SavedData retained.
8. **Conservative reconciliation** — unloaded chunk ≠ missing entity; never replace-spawn from `getEntity()==null` alone.
9. **Crash consistency** — best-effort resume across entity NBT + SavedData; **not** atomic multi-file transactions.

---

## 2. Canonical owners

| Concern | Owner | Notes |
|---------|-------|-------|
| Event timer / next interval | `WorldBossEventSavedData` | Chosen interval persisted; not re-rolled on load |
| Event identity + FSM state | `WorldBossEventSavedData` → `ActiveWorldBossEvent` | Stable UUID per event |
| Boss definition pool | `WorldBossDefinitionRegistry` | Code-registered definitions at bootstrap |
| Selection weights / availability | Definition + config overrides | Unavailable bosses excluded from weight sum |
| Spawn strategy | Per-definition `WorldBossSpawnStrategy` | Hemelwachter: aerial near player |
| Entity health / combat | Entity NBT (`HemelwachterEntity`) | Planner stores UUID + dimension only |
| Provocation / participants | Entity + event record | Explicit participant set for targeting/rewards |
| Bossbar | Entity-owned `ServerBossEvent` | Cleared on death/depart/discard |
| Loot / XP / advancement | Defeat path once (`rewardsGranted` flag) | No rewards on depart/spawn/logout |
| Announcements | `WorldBossAnnouncementService` | Translate keys; dimension-scoped |

### Definition record (`WorldBossDefinition`)

- Stable `ResourceLocation id`
- `EntityType` supplier
- Selection weight
- Availability predicate (dimension, config, custom)
- Spawn strategy
- Bossbar title key + color/overlay
- Neutral dwell ticks / combat timeout / event lifetime caps
- Loot table id, announcement keys, defeat advancement id

---

## 3. State machine

```
WAITING → SELECTING → ANNOUNCING → ARRIVING → NEUTRAL
NEUTRAL → ENGAGED → DEFEATED → FINISHED
NEUTRAL → DEPARTING → FINISHED
ENGAGED → DISENGAGING → DEPARTING → FINISHED
* → FAILED → FINISHED   (bounded spawn failures, invalid world, admin abort)
```

| State | Meaning |
|-------|---------|
| WAITING | Timer counting active ticks toward next event |
| SELECTING | Pick player + boss; locate spawn (bounded retries/backoff) |
| ANNOUNCING | Pre-arrival signal; no entity yet or intro entity staged |
| ARRIVING | Entity descending; not hostile |
| NEUTRAL | Hover/observe; may depart after dwell |
| ENGAGED | Combat + bossbar; phase logic |
| DISENGAGING | Targets lost; short grace then depart |
| DEPARTING | Visible leave; no loot |
| DEFEATED | Death confirmed; grant rewards once |
| FAILED | Terminal error path; start next cooldown |
| FINISHED | Clear active event; roll/start next WAITING interval |

**Cooldown start:** only after a terminal outcome (`FINISHED` from defeat, depart, fail, or safe admin end).

---

## 4. Timer semantics

| Rule | Behaviour |
|------|-----------|
| Interval | Uniform random in `[minTicks, maxTicks]` (default 72_000–108_000 ≈ 60–90 min at 20 TPS) |
| Tick source | Server tick while eligible players online |
| Pause | No eligible players → timer frozen |
| Sleep / `/time set` | Does **not** advance the event timer |
| Persist | `elapsedActiveTicks` + `nextIntervalTicks` saved |
| Reload | Same remaining time; interval not re-randomized |
| Backlog | Never catch up multiple events after downtime |
| Lag | Wall-clock minutes may exceed nominal minutes when TPS < 20 |

**Eligible players (default):** survival/adventure, Overworld, real player (not fake). Spectators/creative excluded unless config says otherwise.

**Player selection:** weighted away from recently chosen UUIDs when multiple eligible players exist.

---

## 5. Boss pool selection

1. Filter definitions by availability.
2. If empty → FAILED/backoff (no spawn).
3. Weighted random among available.
4. If ≥2 available and last boss id matches → re-roll once preferring a different id (if still only one result, allow repeat).
5. Single-boss pools may always select that boss.

### Adding a second boss (no planner rewrite)

1. Implement entity + client assets.
2. Register `EntityType` / attributes in `ModEntities`.
3. Register a `WorldBossDefinition` in `WorldBossDefinitions.bootstrap()`.
4. Add config weight/availability keys if needed.
5. Provide loot/advancement/lang as usual.

The planner only reads the registry.

---

## 6. Hemelwachter visual authority

| Source | Role |
|--------|------|
| Atlas PNG (+ preserved originals under `assets/.../hemelwachter/reference/`) | Materials, UV regions, eye states, palette |
| UV JSON | Pixel regions for model `texOffs` / texture size 1254×1254 |
| Written requirements | ~12 block height, ~20 block wingspan, layered wings, eye core, rings/halo |
| Separate 3D concept render | **Not provided** (both attachments are 1254×1254 atlases) — silhouette choices documented below; owner validates in preview |
| Schematic aid | `docs/hemelwachter_silhouette_schematic.png` — non-authoritative layout note for 3 wing pairs / rings / eye |

### Locked silhouette decisions (pending owner visual confirm)

| Feature | Decision |
|---------|----------|
| Wing pairs | **3 pairs** (upper / mid / lower), six independently posed wing groups |
| Core | Central ivory mass with single large front eye |
| Rings | Two nested gold rings (slow counter-rotation) + upper cyan-gem halo arc |
| Feathers | Layered blocky plates with gold leading edges |
| Hitbox | Tight AABB around core (~3.2×4.5×3.2), not full wingspan |
| Eye UV | Neutral = amber region; provoked = red region |
| Emissive | Eye iris, cyan gems, runes only — not full wings |

---

## 7. Combat summary

| Attack | Wind-up | Damage (pre-armor) | Notes |
|--------|---------|--------------------|-------|
| Eye beam | ~1.5 s | 14 | Direction locked at fire; LOS + server ray |
| Wing buffet | short | 16 | Close range; limited KB; no terrain damage |
| Celestial projectiles | telegraph | 8 each ×3 | Visible; limited tracking; no block break |

Phases: >60% normal; 30–60% shorter gaps; <30% faster combos with retained telegraphs. Core lowers for melee access during engage.

---

## 8. Persistence & recovery guarantees

**Saved:** timer fields, event UUID, boss definition id, entity UUID, dimension, FSM state, timestamps, participant/provoker ids, last terminal outcome, last player/boss selection hints, `rewardsGranted`.

**On load:**

- Restore WAITING countdown as-is.
- If active event: attach to UUID when entity found in loaded chunks; if chunk unloaded, keep waiting (no respawn).
- If entity dead/discarded while state non-terminal → reconcile to DEPARTING/FAILED/DEFEATED conservatively.
- Never second spawn; never health reset from planner; clear orphan bossbars on attach failure.

**Not claimed:** atomicity across entity chunk NBT + SavedData in one crash window.

---

## 9. Configuration & admin

Config section `worldBossEvents`: enable, intervals, dimensions, spawn distance, dwell, combat timeout, stats, effect budgets, weights, eligible gamemodes, disable-with-active policy (`leave` / `force_depart`).

Commands (`/millenaire worldboss …`, permission level 2+):

- `status`, `next`, `start` [bossId], `end`, `list`

No terrain-clearing spawn force command.

---

## 10. Implementation checklist

- [x] Architecture document
- [x] `WorldBossDefinition` + registry + Hemelwachter definition
- [x] `WorldBossEventSavedData` + FSM record
- [x] `WorldBossEventPlanner` (timer, select, announce, spawn, reconcile)
- [x] Spawn locator (space, height, border, chunks, village/structure avoid)
- [x] `HemelwachterEntity` (neutral/engage, phases, attacks, bossbar, persistence)
- [x] Celestial projectile entity
- [x] Model (layered wings, rings, eye UV switch) + renderer + emissive
- [x] Atlas/emissive assets + preserved reference originals
- [x] Items (Hemelkern, Hemelfragment), Hemellantaarn block, loot, XP, advancement
- [x] Config + commands + lang
- [x] Preview session hook
- [x] Unit tests (timer/selection/FSM pure logic)
- [x] `WORLD_BOSS_EVENTS_IMPLEMENTATION.md` + `LOCAL_VALIDATION.md`
- [x] README / inventory doc touch-ups

---

## 11. Acceptance criteria (summary)

1. One 60–90 min (configurable tick) world timer with pause/persist/no-backlog rules.
2. Extensible weighted boss pool; only Hemelwachter registered as complete boss.
3. Neutral arrival → provoke → readable three-attack combat → defeat rewards once **or** peaceful depart.
4. Persistence/reload safe across phases without duplicate spawn/loot.
5. No building/terrain damage from event/boss.
6. Visual: atlas-correct materials + documented silhouette; owner confirms preview match.
