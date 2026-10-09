# Millénaire

Millénaire is a Minecraft **1.21.1** / **NeoForge** mod that adds living NPC villages with distinct cultures, progressive construction, trading, reputation, quests, Living Villages needs/requests, deterministic worldgen settlements, and optional civilization-scale faction orchestration (economy, diplomacy, war, migration, colonization).

**Local verification** (builds, CI, Minecraft runs, gametests) remains the responsibility of the repository owner. This document does not claim runtime verification.

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

### Living Villages / requests

- `livingVillagesEnabled`, `villageRequestsEnabled`
- Deliveries require a **live** need/project; `request.remaining()` is only an upper bound
- Player deliveries preserve exact `ItemStack`s (components included) for deposit, return, and overdelivery recovery
- The unsafe `VillageDeliveryService.deliver(...)` overload without live caps refuses delivery

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

- `npcChatEnabled`, `npcChatProvider`, `npcChatBaseUrl`, `npcChatModel`, `npcChatApiKey`
- Supports LM Studio / OpenAI-compatible providers when configured
- **API keys must not be stored in the repository**
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
- Older colonization schema v2 worlds migrate `intendedFounders` into reserved founder slots
- Request schema v2: optional recovery ledger for incomplete overdelivery pullback
- Civilization migration recovery consumer drains `needsRecovery` ops on the demographics stage
- Road/bridge placement uses `RoadPlacementGuard` (natural terrain only; village/planned footprints and ordinary building blocks are not free overwrite targets)

## Known limitations

- Offscreen reconcile refreshes economic **profiles** only; it does not simulate combat, block placement, or invent inventory goods
- Regional roads wait on naturally loaded chunks (`WAITING_CHUNKS`); unfinished routes stay `PARTIAL` / `BLOCKED` / `RECOVERY_REQUIRED` rather than claiming completion
- Bridge placement uses a deterministic stone/plank strategy when templates are absent; depth is capped
- Autonomous founding stays off by default; when worldgen is disabled it records an explicit block marker instead of leaving silent ops
- Founding costs use summed building `requiredResources` (BuildingCostCalculator content); if empty, a documented minimum levy of 32 any-log + 32 cobblestone applies. Levy is held until physical materialization begins; incomplete refunds persist as `refundOutstanding`.
- Player-placed block provenance is imperfect — road protection is conservative (natural terrain + footprints/structures), not a guarantee every player build is detected.
- Crash consistency across multiple SavedData + chunk files is best-effort idempotent recovery, not a single atomic transaction

## Documentation

- `docs/CIVILIZATION_ARCHITECTURE.md`
- `docs/FINAL_FEATURE_INVENTORY.md`
- `docs/WORLDGEN_OVERHAUL_ARCHITECTURE.md` / `WORLDGEN_OVERHAUL_IMPLEMENTATION.md`
- `docs/LIVING_VILLAGES_ARCHITECTURE.md` / `LIVING_VILLAGES_IMPLEMENTATION.md`
- Local validation docs describe owner-run checks; this agent does not execute them

## Development

```bash
./gradlew build
```

Owner-run only for this change set’s verification policy when an agent was instructed not to build.
