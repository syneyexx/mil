# Millénaire

Millénaire is a Minecraft **1.21.1** / **NeoForge** mod that adds living NPC villages with distinct cultures, progressive construction, trading, reputation, quests, Living Villages needs/requests, an integrated religion system (traditions, daily practice, ceremonies, pilgrimages), deterministic worldgen settlements, and optional civilization-scale faction orchestration (economy, diplomacy, war, migration, colonization).

**Local verification** (builds, CI, Minecraft runs, gametests) remains the responsibility of the repository owner. This document does not claim runtime verification. Agents for this repository do not run GameTests, GitHub CI tests, or other automated verification — owner tests locally.

## Requirements

| Component | Value |
|-----------|--------|
| Minecraft | 1.21.1 (`minecraft_version` / range in `gradle.properties`) |
| Java | 21 |
| NeoForge | `neo_version` in `gradle.properties` (currently 21.1.x; mods.toml range `[21.1,)`) |
| Mod version | `mod_version` in `gradle.properties` (also mirrored in `META-INF/neoforge.mods.toml`) |

Keep `gradle.properties` as the primary version source. When bumping a release, update `mod_version` and the `version=` field in `src/main/resources/META-INF/neoforge.mods.toml` together (and NeoForge/Minecraft ranges if they change).

## Install (player / server)

1. Install a NeoForge 1.21.1 profile matching the mod’s NeoForge range.
2. Place the Millénaire jar in the `mods/` folder (client and dedicated server as needed).
3. Start once to generate server/client configs under the usual NeoForge config path.
4. Do **not** commit API keys. NPC chat keys belong in local config only.

## Configuration highlights

Server config lives in the NeoForge Millénaire server config (keys under `generation`, `worldgen`, `village`, `livingVillages`, `civilization`, NPC chat, etc.).

### Worldgen

- `worldgenEngineEnabled` — deterministic plan → materialize → activate pipeline
- `worldgenMaterializationBudget` — **per-tick** shared placement ops (not per chunk-load reset)
- `worldgenMaterializationScanBudget` — candidate scan cap per tick
- `worldgenResumeQueueBudget` — unfinished chunk jobs drained per tick
- `worldgenActivationBudget` — activation **attempts** per tick (counts failures)
- `worldgenGenerateInitialRoads` / `worldgenGenerateStartingWalls`
- `worldgenGenerateRegionalRoads` — plans continuous inter-settlement routes; places adjacent cells when chunks are loaded
- `worldgenGenerateBridges` — resumable water crossings; a route is never `CONNECTED` after a half-built bridge

### Tick performance

- Soft Post-tick board (`tickGovernor.*`): per-phase ms quotas, WORLDGEN/HIGHWAY reserve, adaptive `effMs` under HEALTHY/SOFT/HARD pressure (default Post ceiling ~24 ms)
- Village Near/Mid deferral + entity LOD / Far sleep + swarm `entityGoalPickBudgetPerTick`
- Diagnostics: `/millenaire dev tick status|metrics|dump` — see `docs/TICK_PERFORMANCE_ARCHITECTURE.md` and `docs/TICK_PERFORMANCE_LOCAL_VALIDATION.md`

### Living Villages / requests

- `livingVillagesEnabled`, `villageRequestsEnabled`
- Deliveries require a **live** need/project; `request.remaining()` is only an upper bound
- Player deliveries preserve exact `ItemStack`s (components included) for deposit, return, and overdelivery recovery
- The unsafe `VillageDeliveryService.deliver(...)` overload without live caps refuses delivery

### World Boss Events

- `worldBossEvents.enabled` — master switch, default off (disabling stops new events; SavedData retained; active-boss policy `force_depart` / `leave`)
- Interval `intervalMinTicks` / `intervalMaxTicks` (default 72000–108000 ≈ 60–90 min at 20 TPS; lag stretches wall-clock time)
- One world timer; eligible survival/adventure Overworld players; extensible boss pool (Hemelwachter registered)
- Admin: `/millenaire worldboss on|off|status|next|start [id]|trigger|end|list`
- See `docs/WORLD_BOSS_EVENTS_ARCHITECTURE.md`, `docs/WORLD_BOSS_EVENTS_IMPLEMENTATION.md`, `docs/WORLD_BOSS_EVENTS_LOCAL_VALIDATION.md`

### Religion

- `religionEnabled` — master switch (disabling pauses simulation; SavedData is retained)
- `religionEvaluationInterval` / `religionGlobalBudget` / `religionCeremonyBudget` — staggered per-village evaluation
- `religionCatchupLimit` — caps ceremony catch-up after sleep/time-skip
- Culture↔tradition is content-driven (not 1:1 hardcoded); `outlawz` has no organized religion
- NPC affiliations persist by villager UUID across migration/respawn; never re-rolled on reload
- Works without LLM and without civilization evolution
- See `docs/RELIGION_ARCHITECTURE.md`, `docs/RELIGION_IMPLEMENTATION.md`, `docs/RELIGION_LOCAL_VALIDATION.md`

### Civilization

- `civilizationEvolutionEnabled` — master orchestrator switch
- `civilizationAutoTradeEnabled` — conserved settlement transfers from concrete local need/stock
- `civilizationIntraFactionLogistics` — same-faction logistics (not diplomatic trade booking)
- `civilizationOffscreenEnabled` — **profile refresh** for inactive settlements (not free goods / combat)
- `civilizationCatchupDays` — caps offscreen elapsed profile refresh
- `civilizationMigrationBudget` — housing-pressure migrations; success is `MigrationResult.completed()`, not a non-null operation id
- `civilizationAutonomousFoundingEnabled` — **default false**; complete colonization lifecycle (layout → costs → materialize → activate → faction → migrate). Disabling stops new proposals but still drains refunds and advances already-active ops.
- `civilizationColonizationOpsBudget` / `CandidateBudget` / `CooldownDays`
- Village book **Civilization** section + NPC action buttons use server-derived snapshots (`CivilizationPresentationService`); visitors may support needs/view proposals, controllers may prioritize housing and authorize colonization progress (never flips the master switch)

### NPC chat

- **LM Studio / Ollama only** (local OpenAI-compatible server). Cloud APIs (OpenAI, xAI/Grok, etc.) are rejected.
- `npcChatEnabled`, `npcChatBaseUrl` (default `http://127.0.0.1:1234/v1` on **new worlds**), optional `npcChatModel` / `npcChatApiKey`
- Legacy `npcChatProvider` is ignored; keep `lmstudio`. Edit the **world** `serverconfig/millenaire-server.toml`.
- Stale cloud `baseUrl` values are auto-migrated to the LM Studio default when the world loads.
- Diagnose: `/millenaire npcchat probe` and `/millenaire npcchat models refresh`
- Optional token via config or `LM_API_TOKEN` — **never store secrets in the repository**
- Model output never authorizes game mutations

## Generation modes

| Mode | Behaviour |
|------|-----------|
| Natural / worldgen engine | Seed-stable region plans; per-unit resumable materialization; activation when physically complete |
| Manual spawn | Existing `/millenaire` spawn / `VillageSpawner` paths remain |
| Autonomous colonization | Optional; `FoundingMode.COLONIZATION` plans, layouts, spends exact reserved stacks, queues loaded chunks without a new chunk event, skips free natural occupants/goods, assigns founding faction, migrates intended founders |

## Existing worlds

- Active villages and faction data are preserved
- Progress schema v3: per-building / per-wall / per-road / terrain / bridge cursors
- Legacy unfinished `materializedChunks` / schema-v2 mid-progress: **ambiguous** — no destructive terrain/deletion replay; placement may resume
- Regional roads schema v2: old coarse-only `CONNECTED` routes become `RECOVERY_REQUIRED` and expand to continuous cells without blind overwrite
- Colonization SavedData schema v3: advance/refund cursors, exclusive founder reservations, founder slots, exact reserved/refund stacks (components via registry encode when available)
- Settlement plans may persist `colonizationOp` linking a COLONIZATION plan to its parent operation; physical work is gated on full founding-cost coverage
- Older colonization schema v2 worlds migrate `intendedFounders` into reserved founder slots
- Civilization SavedData schema v4: trade ops load through `HolderLookup.Provider`; unresolved stack NBT is retained; finalization-only recovery is eligible
- Request schema v2: optional recovery ledger for incomplete overdelivery pullback
- Civilization migration recovery reconciles from physical village records (including persisted `RESERVED`); `needsRecovery` clears only after postconditions
- Road/bridge placement uses `RoadPlacementGuard` (natural terrain only; village/planned footprints and ordinary building blocks are not free overwrite targets)

## Known limitations

- Offscreen reconcile refreshes economic **profiles** only; it does not simulate combat, block placement, or invent inventory goods
- Regional roads wait on naturally loaded chunks (`WAITING_CHUNKS`); unfinished routes stay `PARTIAL` / `BLOCKED` / `RECOVERY_REQUIRED` rather than claiming completion
- Bridge placement uses a deterministic stone/plank strategy when templates are absent; depth is capped
- Autonomous founding stays off by default; when worldgen is disabled it records an explicit block marker instead of leaving silent ops
- Founding costs use authoritative `FoundingCostService` (same result for execution, player support, and presentation). Exact item lines stay exact; only Anywood accepts mixed logs. If content defines no costs, a documented minimum levy of 32 any-log + 32 cobblestone applies. Missing required item content blocks founding rather than skipping a line. Levy is held until the first real block mutation; incomplete refunds persist as `refundOutstanding`.
- Player-placed block provenance is imperfect — road protection is conservative (natural terrain + footprints/structures), not a guarantee every player build is detected.
- Crash consistency across multiple SavedData + chunk files is best-effort idempotent recovery, not a single atomic transaction and not a general exactly-once guarantee

## Documentation

- `docs/CIVILIZATION_ARCHITECTURE.md`
- `docs/FINAL_FEATURE_INVENTORY.md`
- `docs/WORLDGEN_OVERHAUL_ARCHITECTURE.md` / `WORLDGEN_OVERHAUL_IMPLEMENTATION.md`
- `docs/LIVING_VILLAGES_ARCHITECTURE.md` / `LIVING_VILLAGES_IMPLEMENTATION.md`
- `docs/RELIGION_ARCHITECTURE.md` / `RELIGION_IMPLEMENTATION.md` / `RELIGION_LOCAL_VALIDATION.md`
- Local validation docs describe owner-run checks; this agent does not execute them

## Development

```bash
./gradlew build
```

Owner-run verification for this integrity milestone (agent did not execute GameTests, CI workflows, or build/smoke checks):

```bash
./gradlew build
# optional owner: NeoForge GameTest / dedicated-server smoke as desired
```

See `docs/INTEGRITY_MILESTONE_PLAN.md` for the executable A/B/C plan and remaining backlog.
