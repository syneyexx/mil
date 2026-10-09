# World Generation Overhaul — Architecture

**Planner version:** 1  
**Package:** `org.millenaire.worldgen`  
**Minecraft / NeoForge:** 1.21.1 / 21.1.x

## Chosen integration

Primary path: **deterministic region plans + resumable per-chunk materialization**.

- New chunks: `ChunkEvent.Load(isNewChunk)` → `WorldgenEngine.onNewChunk`
- Existing chunks with unfinished work: `onChunkAvailable` + tick `MaterializationResumeQueue` (never force-load)
- Shared per-tick `MaterializationTickBudget` across all chunk-load and resume work
- `LivingEventService` is called directly on activation (Living Villages is integrated; not a separate PR hook)

Native Structure JSON registration is reserved (`worldgenUseNativeStructures`) but not required.

## Three stages

| Stage | Owner | Thread | Mutates blocks? | Mutates villages? |
|-------|--------|--------|-----------------|-------------------|
| A Planning | `SettlementPlanner`, `SettlementLayoutPlanner` | Server | No | No |
| B Materialization | `ChunkSettlementMaterializer` | Server | Yes (chunk-local) | No |
| C Activation | `SettlementActivator` | Server | No (post-place finalizers only) | Yes |

## Determinism

All natural decisions derive from `WorldgenSeeds.stream(worldSeed, salt, regionX, regionZ)` with separated salts (`SALT_POSITION`, `SALT_TYPE`, `SALT_LAYOUT`, …).

Settlement UUID = mix(seed, region, village-type path). Exploration order cannot change plan identity or center.

Neighbor conflicts use unsigned priority from candidate identity (`SALT_ARBITER`), not generation order.

## Persistence

`SettlementPlanSavedData` (`millenaire_worldgen_plans`) is independent from `VillageSavedData` (`millenaire_villages`). Existing villages are never rewritten by the Phase 4 schema.

Progress schema v3 stores `ChunkMaterializationProgress` with **independent cursors per building / wall / road / terrain / bridge column**, plus the resume queue. Legacy unfinished `materializedChunks` and schema-v2 mid-progress are reopened ambiguously (skip destructive re-flatten/deletion replay; resume placement). Missing required plans mark the unit `BLOCKED` — they are never silently treated as materialized.

Statuses: `CANDIDATE → PLANNED → RESERVED → MATERIALIZING → PHYSICAL_COMPLETE → ACTIVATING → ACTIVE` (+ `INVALID` / `FAILED`).

## Legacy bridge

When `worldgenEngineEnabled=true`, natural `VillageSpawnQueue.enqueue` / `trySpawnNext` are suppressed. Manual `SpawnVillageCommand` / `VillageSpawner.spawnVillage` remain live-world APIs.

Set `worldgenEngineEnabled=false` to restore the legacy post-load queue.

## Content snapshot

`WorldgenContentSnapshot.rebuildFromLoadedCultures()` runs after `CultureLoader.loadAll()`. Planners read the immutable snapshot; invalid village types are quarantined from natural eligibility.

## Roads / walls / hamlets

- Internal roads planned as centre→starter polylines; path materials from village type.
- Walls planned via existing `VillageWallGenerator` location math; materialized chunk-locally.
- Hamlets stored on parent plan and expanded to child plans with parent UUID link.
- Regional connectors: `RegionalRoadPlanner` + `RegionalRoadMaterializer` (off by default). Bridges via `worldgenGenerateBridges`.

## Activation

Creates `Village` with deterministic `VillageId`, registers buildings with stable `BuildingId`s from the plan, spawns occupants once, initializes inventories once, runs faction lifecycle, emits `LivingEventService` `VILLAGE_FOUNDED`. Activation attempts are budgeted on the server tick (including failed attempts); `onNewChunk` does not bypass that budget.

## Existing-world safety

New chunks and reloaded unfinished chunks resume materialization. Force-load is never used to finish work. Legacy villages load via existing `VillageSavedData` unchanged.
