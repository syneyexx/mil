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
- `worldgenGenerateRegionalRoads` — plans + places bounded inter-settlement connectors when chunks are loaded
- `worldgenGenerateBridges` — allows deterministic short water crossings on roads/regional routes

### Living Villages / requests

- `livingVillagesEnabled`, `villageRequestsEnabled`
- Deliveries are capped by **live** need quantities, not stale `request.remaining()` alone

### Civilization

- `civilizationEvolutionEnabled` — master orchestrator switch
- `civilizationAutoTradeEnabled` — conserved settlement transfers from concrete local need/stock
- `civilizationIntraFactionLogistics` — same-faction logistics (not diplomatic trade booking)
- `civilizationOffscreenEnabled` — **profile refresh** for inactive settlements (not free goods / combat)
- `civilizationCatchupDays` — caps offscreen elapsed profile refresh
- `civilizationMigrationBudget` — housing-pressure migrations via `Village.transferVillagerPermanently`
- `civilizationAutonomousFoundingEnabled` — **default false**; bounded colonization using existing worldgen + migration
- `civilizationColonizationOpsBudget` / `CandidateBudget` / `CooldownDays`

### NPC chat

- `npcChatEnabled`, `npcChatProvider`, `npcChatBaseUrl`, `npcChatModel`, `npcChatApiKey`
- Supports LM Studio / OpenAI-compatible providers when configured
- **API keys must not be stored in the repository**

## Generation modes

| Mode | Behaviour |
|------|-----------|
| Natural / worldgen engine | Seed-stable region plans; chunk materialization; activation when physically complete |
| Manual spawn | Existing `/millenaire` spawn / `VillageSpawner` paths remain |
| Autonomous colonization | Optional; plans a nearby settlement in **already loaded** chunks, spends real goods, migrates real residents |

## Existing worlds

- Active villages and faction data are preserved
- Legacy worldgen `materializedChunks` on unfinished plans are treated as **ambiguous**: terrain is assumed prepared, placement resumes, blocks are not blindly rebuilt
- Civilization SavedData schema v2 adds independent stage cursors and per-faction offscreen clocks
- Request deliveries become stricter (current need caps); expired requests no longer accept items

## Known limitations

- Offscreen reconcile refreshes economic **profiles** only; it does not simulate combat, block placement, or invent inventory goods
- Regional roads wait on naturally loaded chunks; unfinished routes stay `PARTIAL` / `BLOCKED` rather than claiming completion
- Bridge placement uses a deterministic stone/plank strategy when templates are absent
- Autonomous founding stays off by default and only acts when destination chunks are already available
- Crash consistency across multiple SavedData + chunk files is best-effort, not a single atomic transaction

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
