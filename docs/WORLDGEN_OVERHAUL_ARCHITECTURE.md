# World Generation Overhaul — Architecture

**Planner version:** 1  
**Package:** `org.millenaire.worldgen`  
**Minecraft / NeoForge:** 1.21.1 / 21.1.x

## Chosen integration

Primary path: **deterministic region plans + resumable per-chunk materialization**.

- New/existing chunks: `ChunkEvent.Load` → `WorldgenEngine.offerChunkWork` (enqueue only)
- Server tick drains `WorldgenPlanningQueue` + `MaterializationResumeQueue` under shared op + time budgets (never force-load)
- Planning uses loaded-chunk-only reads (`getChunkNow`); missing columns are WAITING, not INVALID
- Terrain layout scans are resumable; placement steps are cached without world-dependent filtering
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

When `worldgenEngineEnabled=true` and `worldgenLegacyFallback=false` (default), natural `VillageSpawnQueue.enqueue` / `trySpawnNext` are suppressed. Manual `SpawnVillageCommand` / `VillageSpawner.spawnVillage` remain live-world APIs.

Set `worldgenLegacyFallback=true` to also run the legacy post-load queue alongside Phase 4 (temporary rescue). Set `worldgenEngineEnabled=false` to use legacy only.

Planning waits until culture content is loaded (`WorldgenContentSnapshot.isReady()`). Start-region chunks before that are queued as WAITING, not sticky INVALID rejects. On content load, premature `no_candidates` rejects for `millenaire:none` are cleared so those regions re-plan.

## Content snapshot

`WorldgenContentSnapshot.rebuildFromLoadedCultures()` runs after `CultureLoader.loadAll()`. Planners read the immutable snapshot; invalid village types are quarantined from natural eligibility.

## Roads / walls / hamlets

- Internal roads planned as centre→starter polylines; path materials from village type.
- Walls planned via existing `VillageWallGenerator` location math; materialized chunk-locally.
- Hamlets stored on parent plan and expanded to child plans with parent UUID link.
- Regional connectors: `RegionalRoadPlanner` + `RegionalRoadMaterializer` (off by default). Bridges via `worldgenGenerateBridges`.
- **Sand highways** (`worldgenSandHighways`, default off): discovery-driven 4-wide `path_sandstone` / Macaw sand network via `HighwayEngine` — Millenaire villages/plans, vanilla `#minecraft:village`, and `#millenaire:highway_endpoint` mod structures. Enqueue on chunk load; plan/materialize/travelers on server tick. When enabled, regional dirt connectors are skipped (single inter-settlement pipeline). Bridges reuse `BridgePlacementHelper` (4-wide band); tunnels via `TunnelPlacementHelper` + `TUNNEL_*` guard intents. Travelers: logical `RoadJourney` + near-player `MillVillager` materialization (`/millenaire highway status|audit`).
- Placement permission: `RoadPlacementGuard` (shared by local roads, regional roads, highways, and bridges). Only natural suitable terrain / clearable vegetation / bridge water routes may be overwritten; ordinary building blocks, containers, village footprints, planned buildings, and structure conflicts block placement. Slope continuity uses persisted `lastPlaced*` after resume. Detours must remain continuous route segments. A bridge is not `COMPLETE` while passage above the deck stays blocked.

## Activation

Creates `Village` with deterministic `VillageId`, registers buildings with stable `BuildingId`s from the plan, spawns occupants once, initializes inventories once, runs faction lifecycle, emits `LivingEventService` `VILLAGE_FOUNDED`. Activation attempts are budgeted on the server tick (including failed attempts); `onNewChunk` does not bypass that budget.

## Existing-world safety

New chunks and reloaded unfinished chunks resume materialization. Force-load is never used to finish work. Legacy villages load via existing `VillageSavedData` unchanged.
